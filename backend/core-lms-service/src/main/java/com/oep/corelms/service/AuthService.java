package com.oep.corelms.service;

import com.oep.corelms.dto.auth.AuthResponse;
import com.oep.corelms.dto.auth.ChangePasswordRequest;
import com.oep.corelms.dto.auth.LoginRequest;
import com.oep.corelms.dto.auth.RefreshTokenRequest;
import com.oep.corelms.dto.auth.RegisterRequest;
import com.oep.corelms.repository.AuthIdentityRepository;
import com.oep.corelms.repository.RefreshTokenRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.corelms.repository.UserRepository;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${jwt.refresh-token-validity-days:14}")
    private long refreshTokenValidityDays;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public AuthResponse register(RegisterRequest request, String deviceInfo) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_TAKEN, "Email is already registered: " + normalizedEmail);
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setRole(UserRole.STUDENT);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user = userRepository.save(user);

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFullName(request.fullName().trim());
        profile.setPhone(request.phone() != null && !request.phone().isBlank() ? request.phone().trim() : null);
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setOrganization(request.organization() != null && !request.organization().isBlank() ? request.organization().trim() : null);
        profile.setBio(request.bio() != null && !request.bio().isBlank() ? request.bio().trim() : null);
        userProfileRepository.save(profile);

        AuthIdentity identity = new AuthIdentity();
        identity.setUser(user);
        identity.setProvider(AuthProvider.LOCAL);
        identity.setPasswordHash(passwordEncoder.encode(request.password()));
        identity.setMustChangePassword(false);
        authIdentityRepository.save(identity);

        log.info("Registered new user with id={} email={}", user.getId(), normalizedEmail);

        return issueTokens(user, profile.getFullName(), false, deviceInfo);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String deviceInfo) {
        String normalizedEmail = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_CREDENTIALS, "Invalid email or password"));

        if (user.getAccountStatus() == AccountStatus.DISABLED) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "Account is disabled");
        }

        AuthIdentity identity = authIdentityRepository.findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_CREDENTIALS, "Local login is not available for this account"));

        if (!passwordEncoder.matches(request.password(), identity.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "Invalid email or password");
        }

        String fullName = userProfileRepository.findById(user.getId())
                .map(UserProfile::getFullName)
                .orElse(user.getEmail());

        log.info("User logged in id={} email={}", user.getId(), normalizedEmail);

        return issueTokens(user, fullName, identity.isMustChangePassword(), deviceInfo);
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request, String deviceInfo) {
        String rawToken = request.refreshToken();
        String hash = sha256Hex(rawToken);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashAndRevokedFalse(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN, "Invalid or revoked refresh token"));

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "Refresh token has expired");
        }

        User user = refreshToken.getUser();
        if (user.getAccountStatus() == AccountStatus.DISABLED) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "Account is disabled");
        }

        // Token rotation: revoke old token
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        String fullName = userProfileRepository.findById(user.getId())
                .map(UserProfile::getFullName)
                .orElse(user.getEmail());

        boolean mustChangePassword = authIdentityRepository
                .findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL)
                .map(AuthIdentity::isMustChangePassword)
                .orElse(false);

        log.info("Rotated refresh token for user id={}", user.getId());

        return issueTokens(user, fullName, mustChangePassword, deviceInfo);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String hash = sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHashAndRevokedFalse(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.info("Revoked refresh token for user id={}", token.getUser().getId());
        });
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));

        AuthIdentity identity = authIdentityRepository.findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Local auth identity not found"));

        if (!passwordEncoder.matches(request.oldPassword(), identity.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "Incorrect old password");
        }

        identity.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        identity.setMustChangePassword(false);
        authIdentityRepository.save(identity);

        // Invalidate all existing refresh tokens
        refreshTokenRepository.revokeAllByUserId(userId);

        log.info("Changed password and revoked all sessions for user id={}", userId);
    }

    private AuthResponse issueTokens(User user, String fullName, boolean mustChangePassword, String deviceInfo) {
        String accessToken = jwtService.generateAccessToken(user);

        // Generate raw opaque refresh token (64 hex characters)
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String tokenHash = sha256Hex(rawRefreshToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setDeviceInfo(deviceInfo);
        refreshToken.setExpiresAt(Instant.now().plus(refreshTokenValidityDays, ChronoUnit.DAYS));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        AuthResponse.UserSummary userSummary = new AuthResponse.UserSummary(
                user.getId(),
                user.getEmail(),
                fullName,
                user.getRole(),
                mustChangePassword
        );

        return AuthResponse.of(accessToken, rawRefreshToken, jwtService.getAccessTokenValiditySeconds(), userSummary);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
