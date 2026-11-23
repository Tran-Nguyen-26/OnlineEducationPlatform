package com.oep.corelms.service;

import com.oep.corelms.dto.auth.AuthResponse;
import com.oep.corelms.dto.auth.ChangePasswordRequest;
import com.oep.corelms.dto.auth.LoginRequest;
import com.oep.corelms.dto.auth.MeResponse;
import com.oep.corelms.dto.admin.ResetLinkResponse;
import com.oep.corelms.dto.auth.RegisterRequest;
import com.oep.corelms.repository.AuthIdentityRepository;
import com.oep.corelms.repository.PasswordResetTokenRepository;
import com.oep.corelms.repository.RefreshTokenRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.corelms.security.JwtTokenService;
import com.oep.coremodel.entity.identity.AuthIdentity;
import com.oep.coremodel.entity.identity.PasswordResetToken;
import com.oep.coremodel.entity.identity.RefreshToken;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.AuthProvider;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Student self-registration + login/refresh/logout/change-password for every role. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final long RESET_TOKEN_TTL_SECONDS = 1800; // 30 minutes

    private final UserRepository users;
    private final AuthIdentityRepository authIdentities;
    private final UserProfileRepository userProfiles;
    private final RefreshTokenRepository refreshTokens;
    private final com.oep.corelms.security.TokenRevocationService tokenRevocation;
    private final com.oep.corelms.security.LoginAttemptService loginAttempts;
    private final AuditLogService audit;
    private final org.springframework.transaction.PlatformTransactionManager txManager;
    private final io.micrometer.core.instrument.MeterRegistry meters;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (users.findByEmail(request.email()).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED, "Email already registered");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setRole(UserRole.STUDENT);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setConsentAt(java.time.Instant.now());
        user = users.save(user);

        AuthIdentity identity = new AuthIdentity();
        identity.setUser(user);
        identity.setProvider(AuthProvider.LOCAL);
        identity.setPasswordHash(passwordEncoder.encode(request.password()));
        identity.setMustChangePassword(false);
        authIdentities.save(identity);

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFullName(request.fullName());
        userProfiles.save(profile);

        return issueTokens(user, false);
    }

    /**
     * Deliberately NOT one big transaction: the bcrypt check takes ~100 ms of CPU, and holding a database connection
     * for that long capped login throughput at (pool size / 0.1 s) and exhausted the pool when a whole class logged in
     * at once. The lookup and the token issuing are separate short transactions; the hashing runs without a connection.
     */
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        loginAttempts.assertNotLocked(email);
        TransactionTemplate tx = new TransactionTemplate(txManager);
        User[] found = new User[1];
        AuthIdentity[] foundIdentity = new AuthIdentity[1];
        tx.executeWithoutResult(s -> {
            found[0] = users.findByEmail(request.email()).orElse(null);
            foundIdentity[0] = found[0] == null ? null : authIdentities.findByUser_IdAndProvider(found[0].getId(), AuthProvider.LOCAL).orElse(null);
        });
        User user = found[0];
        AuthIdentity identity = foundIdentity[0];

        if (identity == null || identity.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), identity.getPasswordHash())) {
            meters.counter("oep.business.events", "type", "login_failed").increment();
            boolean locked = loginAttempts.recordFailure(email);
            if (user != null) {
                audit.recordIndependent(user.getId(), locked ? "ACCOUNT_LOCKED" : "LOGIN_FAILED", "USER", user.getId(), Map.of());
            }
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "Account is disabled");
        }

        meters.counter("oep.business.events", "type", "login_success").increment();
        loginAttempts.reset(email);
        boolean mustChange = identity.isMustChangePassword();
        return tx.execute(s -> {
            audit.record(user.getId(), "LOGIN_SUCCESS", "USER", user.getId(), Map.of());
            return issueTokens(user, mustChange);
        });
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken existing = refreshTokens.findByTokenHash(tokenService.hashToken(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Invalid refresh token"));
        if (existing.isRevoked() || existing.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token expired or revoked");
        }
        existing.setRevoked(true);
        refreshTokens.save(existing);

        User user = existing.getUser();
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "Account is disabled");
        }
        boolean mustChangePassword = authIdentities.findByUser_IdAndProvider(user.getId(), AuthProvider.LOCAL)
                .map(AuthIdentity::isMustChangePassword)
                .orElse(false);

        return issueTokens(user, mustChangePassword);
    }

    @Transactional
    public void logout(UUID userId, String rawRefreshToken) {
        RefreshToken existing = refreshTokens.findByTokenHash(tokenService.hashToken(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Invalid refresh token"));
        if (!existing.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Invalid refresh token");
        }
        existing.setRevoked(true);
        refreshTokens.save(existing);
    }

    @Transactional
    public AuthResponse changePassword(UUID userId, ChangePasswordRequest request) {
        AuthIdentity identity = authIdentities.findByUser_IdAndProvider(userId, AuthProvider.LOCAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Local credentials not found"));
        if (!passwordEncoder.matches(request.currentPassword(), identity.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD, "Current password is incorrect");
        }
        identity.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        identity.setMustChangePassword(false);
        authIdentities.save(identity);
        tokenRevocation.revokeAccessTokens(userId);
        audit.record(userId, "PASSWORD_CHANGED", "USER", userId, Map.of());
        // Fresh tokens: the old access token still carries must_change_password=true, which the gateway blocks on.
        return issueTokens(identity.getUser(), false);
    }

    /**
     * Always returns normally whether or not the email exists, to avoid account enumeration.
     * No SMTP is configured yet (see GAPS.md G4), so the reset link is only logged here, standing in for
     * what a real email send would deliver. Admins can also mint a link directly — see {@link #adminGenerateResetToken}.
     */
    @Transactional
    public void forgotPassword(String email) {
        users.findByEmail(email).ifPresent(user ->
                authIdentities.findByUser_IdAndProvider(user.getId(), AuthProvider.LOCAL).ifPresent(identity -> {
                    String rawToken = issueResetToken(user);
                    log.info("Password reset requested for {}: token={} (expires in {} minutes)",
                            email, rawToken, RESET_TOKEN_TTL_SECONDS / 60);
                }));
    }

    /** Admin-initiated reset: mints a token and hands the raw value back so an admin can relay it to the user. */
    @Transactional
    public ResetLinkResponse adminGenerateResetToken(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
        authIdentities.findByUser_IdAndProvider(userId, AuthProvider.LOCAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Local credentials not found"));
        Instant expiresAt = Instant.now().plusSeconds(RESET_TOKEN_TTL_SECONDS);
        return new ResetLinkResponse(issueResetToken(user, expiresAt), expiresAt);
    }

    private String issueResetToken(User user) {
        return issueResetToken(user, Instant.now().plusSeconds(RESET_TOKEN_TTL_SECONDS));
    }

    private String issueResetToken(User user, Instant expiresAt) {
        String rawToken = tokenService.generateRefreshTokenRaw();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenHash(tokenService.hashToken(rawToken));
        resetToken.setExpiresAt(expiresAt);
        passwordResetTokens.save(resetToken);
        return rawToken;
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokens.findByTokenHash(tokenService.hashToken(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_TOKEN, "Invalid or expired reset token"));
        if (token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.INVALID_RESET_TOKEN, "Invalid or expired reset token");
        }

        User user = token.getUser();
        AuthIdentity identity = authIdentities.findByUser_IdAndProvider(user.getId(), AuthProvider.LOCAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Local credentials not found"));
        identity.setPasswordHash(passwordEncoder.encode(newPassword));
        identity.setMustChangePassword(false);
        authIdentities.save(identity);
        tokenRevocation.revokeAccessTokens(user.getId());

        token.setUsed(true);
        passwordResetTokens.save(token);

        // Force re-login everywhere, in case the account was compromised.
        refreshTokens.findByUser_IdAndRevokedFalse(user.getId())
                .forEach(rt -> rt.setRevoked(true));
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
        boolean mustChangePassword = authIdentities.findByUser_IdAndProvider(userId, AuthProvider.LOCAL)
                .map(AuthIdentity::isMustChangePassword)
                .orElse(false);
        String fullName = userProfiles.findById(userId).map(UserProfile::getFullName).orElse(null);
        return new MeResponse(user.getId(), user.getEmail(), user.getRole().name(), fullName,
                mustChangePassword, user.getAccountStatus().name());
    }

    private AuthResponse issueTokens(User user, boolean mustChangePassword) {
        String accessToken = tokenService.issueAccessToken(user, mustChangePassword);
        String rawRefreshToken = tokenService.generateRefreshTokenRaw();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(tokenService.hashToken(rawRefreshToken));
        refreshToken.setExpiresAt(Instant.now().plusSeconds(tokenService.refreshTtlSeconds()));
        refreshTokens.save(refreshToken);

        return new AuthResponse(accessToken, rawRefreshToken, tokenService.accessTtlSeconds(),
                user.getRole().name(), mustChangePassword);
    }
}
