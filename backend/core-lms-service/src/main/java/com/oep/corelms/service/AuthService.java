package com.oep.corelms.service;

import com.oep.corelms.dto.auth.AuthResponse;
import com.oep.corelms.dto.auth.ChangePasswordRequest;
import com.oep.corelms.dto.auth.LoginRequest;
import com.oep.corelms.dto.auth.RefreshTokenRequest;
import com.oep.corelms.dto.auth.RegisterRequest;
import com.oep.corelms.dto.auth.RegistrationPendingResponse;
import com.oep.corelms.repository.AuthIdentityRepository;
import com.oep.corelms.repository.RefreshTokenRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.entity.identity.AuthIdentity;
import com.oep.coremodel.entity.identity.RefreshToken;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.AuthProvider;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.event.ForgotPasswordEmailEvent;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
    private final RedisTemplate<String, String> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final JdbcTemplate jdbc;

    @Value("${jwt.refresh-token-validity-days:14}")
    private long refreshTokenValidityDays;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String REGISTRATION_OTP_PREFIX = "auth:otp:registration:";
    private static final String REGISTRATION_COOLDOWN_PREFIX = "auth:cooldown:registration:";
    private static final String REGISTRATION_RATE_PREFIX = "auth:rate-limit:registration:";
    private static final long REGISTRATION_OTP_TTL_SECONDS = 300;
    private static final String CONSENT_VERSION = "OEP-PRIVACY-1";

    @Transactional
    public RegistrationPendingResponse register(RegisterRequest request, String deviceInfo) {
        if (!request.explicitConsent()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Cần đồng ý với thông báo quyền riêng tư để tiếp tục đăng ký");
        }
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_TAKEN, "Email is already registered: " + normalizedEmail);
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setRole(UserRole.STUDENT);
        user.setAccountStatus(AccountStatus.PENDING_VERIFICATION);
        user.setConsentAt(Instant.now());
        user.setConsentVersion(CONSENT_VERSION);
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

        sendRegistrationOtp(user, profile.getFullName(), true);
        log.info("Created pending learner registration id={}", user.getId());
        return new RegistrationPendingResponse("Mã xác minh đã được gửi tới email của bạn.", maskEmail(normalizedEmail), REGISTRATION_OTP_TTL_SECONDS);
    }

    @Transactional
    public RegistrationPendingResponse resendRegistrationOtp(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user != null && user.getRole() == UserRole.STUDENT && user.getAccountStatus() == AccountStatus.PENDING_VERIFICATION) {
            String cooldownKey = REGISTRATION_COOLDOWN_PREFIX + normalizedEmail;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
                Long remaining = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                        "Vui lòng đợi " + (remaining == null ? 60 : remaining) + " giây trước khi yêu cầu mã mới.");
            }
            sendRegistrationOtp(user, userProfileRepository.findById(user.getId())
                    .map(UserProfile::getFullName).orElse(normalizedEmail), false);
        }
        return new RegistrationPendingResponse("Nếu tài khoản đang chờ xác minh, mã mới sẽ được gửi tới email đã đăng ký.", maskEmail(normalizedEmail), REGISTRATION_OTP_TTL_SECONDS);
    }

    @Transactional
    public AuthResponse verifyRegistration(String email, String otpCode, String deviceInfo) {
        String normalizedEmail = email.trim().toLowerCase();
        String otpKey = REGISTRATION_OTP_PREFIX + normalizedEmail;
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(otpKey))) {
            throw new BusinessException(ErrorCode.OTP_EXPIRED, "Mã xác minh đã hết hạn hoặc không tồn tại. Hãy yêu cầu mã mới.");
        }
        String storedHash = (String) redisTemplate.opsForHash().get(otpKey, "otp_hash");
        String userId = (String) redisTemplate.opsForHash().get(otpKey, "user_id");
        String failedAttempts = (String) redisTemplate.opsForHash().get(otpKey, "failed_attempts");
        int attempts = failedAttempts == null ? 0 : Integer.parseInt(failedAttempts);
        if (attempts >= 5) {
            redisTemplate.delete(otpKey);
            throw new BusinessException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED, "Bạn đã nhập sai mã quá 5 lần. Hãy yêu cầu mã mới.");
        }
        if (storedHash == null || !MessageDigest.isEqual(storedHash.getBytes(StandardCharsets.UTF_8), sha256Hex(otpCode).getBytes(StandardCharsets.UTF_8))) {
            int next = attempts + 1;
            if (next >= 5) redisTemplate.delete(otpKey);
            else redisTemplate.opsForHash().put(otpKey, "failed_attempts", String.valueOf(next));
            throw new BusinessException(next >= 5 ? ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED : ErrorCode.INVALID_OTP,
                    next >= 5 ? "Bạn đã nhập sai mã quá 5 lần. Hãy yêu cầu mã mới." : "Mã xác minh không đúng.");
        }
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy tài khoản đăng ký"));
        int changed = jdbc.update("update users set account_status = 'ACTIVE' where id = ? and account_status = 'PENDING_VERIFICATION'", user.getId());
        if (changed != 1) {
            redisTemplate.delete(otpKey);
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED, "Tài khoản không còn chờ xác minh");
        }
        redisTemplate.delete(otpKey);
        user.setAccountStatus(AccountStatus.ACTIVE);
        String fullName = userProfileRepository.findById(user.getId()).map(UserProfile::getFullName).orElse(user.getEmail());
        return issueTokens(user, fullName, false, deviceInfo);
    }

    private void sendRegistrationOtp(User user, String fullName, boolean initial) {
        String email = user.getEmail();
        String rateKey = REGISTRATION_RATE_PREFIX + email;
        String countRaw = redisTemplate.opsForValue().get(rateKey);
        int count = countRaw == null ? 0 : Integer.parseInt(countRaw);
        if (!initial && count >= 10) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "Đã vượt quá số lần gửi mã xác minh trong một giờ.");
        }
        int otpNumber = 100_000 + SECURE_RANDOM.nextInt(900_000);
        String otpCode = String.valueOf(otpNumber);
        String otpKey = REGISTRATION_OTP_PREFIX + email;
        redisTemplate.opsForHash().put(otpKey, "otp_hash", sha256Hex(otpCode));
        redisTemplate.opsForHash().put(otpKey, "user_id", user.getId().toString());
        redisTemplate.opsForHash().put(otpKey, "failed_attempts", "0");
        redisTemplate.expire(otpKey, Duration.ofSeconds(REGISTRATION_OTP_TTL_SECONDS));
        redisTemplate.opsForValue().set(REGISTRATION_COOLDOWN_PREFIX + email, "1", Duration.ofSeconds(60));
        if (countRaw == null) redisTemplate.opsForValue().set(rateKey, "1", Duration.ofHours(1));
        else redisTemplate.opsForValue().increment(rateKey);
        rabbitTemplate.convertAndSend(MessagingConstants.EXCHANGE, MessagingConstants.RK_FORGOT_PASSWORD_EMAIL,
                new ForgotPasswordEmailEvent(user.getId(), "REGISTRATION_OTP", email, fullName, otpCode, null, Instant.now()));
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(Math.max(at, 0));
        return email.substring(0, 2) + "***" + email.substring(at);
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
        if (user.getAccountStatus() == AccountStatus.PENDING_VERIFICATION) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED, "Hãy xác minh email trước khi đăng nhập");
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
