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

import java.time.Instant;
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

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password"));
        AuthIdentity identity = authIdentities.findByUser_IdAndProvider(user.getId(), AuthProvider.LOCAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password"));

        if (identity.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), identity.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "Account is disabled");
        }

        return issueTokens(user, identity.isMustChangePassword());
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
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        AuthIdentity identity = authIdentities.findByUser_IdAndProvider(userId, AuthProvider.LOCAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Local credentials not found"));
        if (!passwordEncoder.matches(request.currentPassword(), identity.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD, "Current password is incorrect");
        }
        identity.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        identity.setMustChangePassword(false);
        authIdentities.save(identity);
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
