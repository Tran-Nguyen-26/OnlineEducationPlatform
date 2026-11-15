package com.oep.corelms.service;

import com.oep.corelms.dto.auth.AuthResponse;
import com.oep.corelms.dto.auth.ChangePasswordRequest;
import com.oep.corelms.dto.auth.LoginRequest;
import com.oep.corelms.dto.auth.MeResponse;
import com.oep.corelms.dto.auth.RegisterRequest;
import com.oep.corelms.repository.AuthIdentityRepository;
import com.oep.corelms.repository.RefreshTokenRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.corelms.security.JwtTokenService;
import com.oep.coremodel.entity.identity.AuthIdentity;
import com.oep.coremodel.entity.identity.RefreshToken;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.AuthProvider;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Student self-registration + login/refresh/logout/change-password for every role. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final AuthIdentityRepository authIdentities;
    private final UserProfileRepository userProfiles;
    private final RefreshTokenRepository refreshTokens;
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
