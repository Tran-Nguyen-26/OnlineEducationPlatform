package com.oep.corelms.service;

import com.oep.corelms.dto.auth.*;
import com.oep.corelms.repository.AuthIdentityRepository;
import com.oep.corelms.repository.RefreshTokenRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.entity.identity.AuthIdentity;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.AuthProvider;
import com.oep.coremodel.event.ForgotPasswordEmailEvent;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForgotPasswordService {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------
    private static final String REDIS_OTP_PREFIX        = "auth:otp:forgot-password:";
    private static final String REDIS_COOLDOWN_PREFIX   = "auth:cooldown:forgot-password:";
    private static final String REDIS_RATE_LIMIT_PREFIX = "auth:rate-limit:forgot-password:";
    private static final String REDIS_RESET_TOKEN_PREFIX = "auth:reset-token:";

    private static final long   OTP_TTL_SECONDS         = 300;   // 5 min
    private static final long   COOLDOWN_SECONDS         = 60;
    private static final long   RATE_LIMIT_WINDOW_SECONDS = 3600; // 1 hr
    private static final int    RATE_LIMIT_MAX           = 10;
    private static final int    OTP_MAX_ATTEMPTS         = 5;
    private static final long   RESET_TOKEN_TTL_SECONDS  = 600;   // 10 min

    private static final String OTP_HASH_FIELD        = "otp_hash";
    private static final String USER_ID_FIELD         = "user_id";
    private static final String FAILED_ATTEMPTS_FIELD = "failed_attempts";
    private static final String EMAIL_FIELD           = "email";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // -----------------------------------------------------------------------
    // Dependencies
    // -----------------------------------------------------------------------
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;
    private final RabbitTemplate rabbitTemplate;

    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase();

        // --- Silent success: email not found / account disabled (anti-enumeration) ---
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty() || userOpt.get().getAccountStatus() == AccountStatus.DISABLED) {
            log.debug("Forgot-password: email not found or disabled, silent 200 for {}", email);
            return silentOkResponse(email);
        }

        User user = userOpt.get();

        // --- Google-only account: no local password exists ---
        boolean hasLocalIdentity = authIdentityRepository
                .findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL)
                .isPresent();
        if (!hasLocalIdentity) {
            log.debug("Forgot-password: Google-only account for {}", email);
            return new ForgotPasswordResponse(
                    "Tài khoản này đăng nhập bằng Google. Vui lòng sử dụng nút 'Đăng nhập với Google'.",
                    maskEmail(email), 0, 0
            );
        }

        // --- Rate-limit check ---
        String cooldownKey   = REDIS_COOLDOWN_PREFIX + email;
        String rateLimitKey  = REDIS_RATE_LIMIT_PREFIX + email;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            Long remainTtl = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "Vui lòng đợi " + (remainTtl != null ? remainTtl : COOLDOWN_SECONDS) + " giây trước khi yêu cầu mã mới.");
        }

        String countStr = redisTemplate.opsForValue().get(rateLimitKey);
        int count = countStr != null ? Integer.parseInt(countStr) : 0;
        if (count >= RATE_LIMIT_MAX) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "Bạn đã vượt quá số lần yêu cầu mã trong giờ này. Vui lòng thử lại sau 60 phút.");
        }

        // --- Generate OTP ---
        int otp = 100_000 + SECURE_RANDOM.nextInt(900_000);
        String otpCode = String.valueOf(otp);
        String otpHash = sha256Hex(otpCode);

        String fullName = userProfileRepository.findById(user.getId())
                .map(UserProfile::getFullName)
                .orElse(email);

        // --- Persist OTP to Redis ---
        String otpKey = REDIS_OTP_PREFIX + email;
        redisTemplate.opsForHash().put(otpKey, OTP_HASH_FIELD, otpHash);
        redisTemplate.opsForHash().put(otpKey, USER_ID_FIELD, user.getId().toString());
        redisTemplate.opsForHash().put(otpKey, FAILED_ATTEMPTS_FIELD, "0");
        redisTemplate.expire(otpKey, Duration.ofSeconds(OTP_TTL_SECONDS));

        // --- Set cooldown ---
        redisTemplate.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(COOLDOWN_SECONDS));

        // --- Increment rate-limit counter ---
        if (count == 0) {
            redisTemplate.opsForValue().set(rateLimitKey, "1", Duration.ofSeconds(RATE_LIMIT_WINDOW_SECONDS));
        } else {
            redisTemplate.opsForValue().increment(rateLimitKey);
        }

        // --- Publish async email event ---
        ForgotPasswordEmailEvent event = new ForgotPasswordEmailEvent(
                user.getId(),
                "FORGOT_PASSWORD_OTP",
                email,
                fullName,
                otpCode,
                null,
                Instant.now()
        );
        rabbitTemplate.convertAndSend(
                MessagingConstants.EXCHANGE,
                MessagingConstants.RK_FORGOT_PASSWORD_EMAIL,
                event
        );

        log.info("Forgot-password OTP published for user id={}", user.getId());

        return new ForgotPasswordResponse(
                "Mã xác thực đã được gửi tới email của bạn.",
                maskEmail(email),
                COOLDOWN_SECONDS,
                OTP_TTL_SECONDS
        );
    }

    public ForgotPasswordVerifyOtpResponse verifyForgotPasswordOtp(ForgotPasswordVerifyOtpRequest request) {
        String email  = request.email().trim().toLowerCase();
        String otpKey = REDIS_OTP_PREFIX + email;

        // --- Check OTP session existence ---
        Boolean exists = redisTemplate.hasKey(otpKey);
        if (!Boolean.TRUE.equals(exists)) {
            throw new BusinessException(ErrorCode.OTP_EXPIRED,
                    "Mã xác thực đã hết hạn hoặc không tồn tại. Vui lòng yêu cầu mã mới.");
        }

        // --- Read hash data ---
        String storedHash     = (String) redisTemplate.opsForHash().get(otpKey, OTP_HASH_FIELD);
        String userId         = (String) redisTemplate.opsForHash().get(otpKey, USER_ID_FIELD);
        String failedAttempts = (String) redisTemplate.opsForHash().get(otpKey, FAILED_ATTEMPTS_FIELD);

        int attempts = failedAttempts != null ? Integer.parseInt(failedAttempts) : 0;

        // --- Pre-check: already exceeded max attempts ---
        if (attempts >= OTP_MAX_ATTEMPTS) {
            redisTemplate.delete(otpKey);
            throw new BusinessException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED,
                    "Bạn đã nhập sai mã quá 5 lần. Phiên xác thực đã bị hủy vì lý do an toàn.");
        }

        // --- Verify OTP ---
        String submittedHash = sha256Hex(request.otpCode());
        if (!submittedHash.equals(storedHash)) {
            int newAttempts = attempts + 1;
            if (newAttempts >= OTP_MAX_ATTEMPTS) {
                redisTemplate.delete(otpKey);
                throw new BusinessException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED,
                        "Bạn đã nhập sai mã quá 5 lần. Phiên xác thực đã bị hủy vì lý do an toàn.");
            }
            redisTemplate.opsForHash().put(otpKey, FAILED_ATTEMPTS_FIELD, String.valueOf(newAttempts));
            int remaining = OTP_MAX_ATTEMPTS - newAttempts;
            throw new BusinessException(ErrorCode.INVALID_OTP,
                    "Mã xác thực không đúng. Bạn còn " + remaining + " lần thử.");
        }

        // --- OTP correct: consume it ---
        redisTemplate.delete(otpKey);

        // --- Issue single-use reset token ---
        String resetToken    = UUID.randomUUID().toString();
        String resetTokenKey = REDIS_RESET_TOKEN_PREFIX + resetToken;

        redisTemplate.opsForHash().put(resetTokenKey, USER_ID_FIELD, userId);
        redisTemplate.opsForHash().put(resetTokenKey, EMAIL_FIELD, email);
        redisTemplate.expire(resetTokenKey, Duration.ofSeconds(RESET_TOKEN_TTL_SECONDS));

        log.info("OTP verified, reset token issued for user id={}", userId);

        return new ForgotPasswordVerifyOtpResponse(
                resetToken,
                RESET_TOKEN_TTL_SECONDS,
                "Xác thực OTP thành công. Vui lòng đặt mật khẩu mới."
        );
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // --- Validate password confirmation ---
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH,
                    "Mật khẩu xác nhận không khớp.");
        }

        // --- Look up reset token ---
        String resetTokenKey = REDIS_RESET_TOKEN_PREFIX + request.resetToken();
        Boolean exists = redisTemplate.hasKey(resetTokenKey);
        if (!Boolean.TRUE.equals(exists)) {
            throw new BusinessException(ErrorCode.INVALID_RESET_TOKEN,
                    "Phiên đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Vui lòng thực hiện lại từ đầu.");
        }

        String userIdStr = (String) redisTemplate.opsForHash().get(resetTokenKey, USER_ID_FIELD);
        String email     = (String) redisTemplate.opsForHash().get(resetTokenKey, EMAIL_FIELD);
        UUID   userId    = UUID.fromString(userIdStr);

        // --- Delete reset token immediately (single-use) ---
        redisTemplate.delete(resetTokenKey);

        // --- Update password in DB ---
        AuthIdentity identity = authIdentityRepository.findByUserIdAndProvider(userId, AuthProvider.LOCAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Local auth identity not found for user " + userId));

        identity.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        identity.setMustChangePassword(false);
        authIdentityRepository.save(identity);

        // --- Revoke all existing refresh tokens (invalidate all sessions) ---
        refreshTokenRepository.revokeAllByUserId(userId);

        // --- Publish security alert email ---
        String fullName = userProfileRepository.findById(userId)
                .map(UserProfile::getFullName)
                .orElse(email);

        String actionTime = DateTimeFormatter
                .ofPattern("HH:mm:ss dd/MM/yyyy")
                .withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(Instant.now());

        ForgotPasswordEmailEvent alertEvent = new ForgotPasswordEmailEvent(
                userId,
                "PASSWORD_RESET_SUCCESS_ALERT",
                email,
                fullName,
                null,
                actionTime,
                Instant.now()
        );
        rabbitTemplate.convertAndSend(
                MessagingConstants.EXCHANGE,
                MessagingConstants.RK_FORGOT_PASSWORD_EMAIL,
                alertEvent
        );

        log.info("Password reset successful and all sessions revoked for user id={}", userId);
    }

    private ForgotPasswordResponse silentOkResponse(String email) {
        return new ForgotPasswordResponse(
                "Nếu email tồn tại trong hệ thống, chúng tôi đã gửi mã xác thực tới hộp thư của bạn.",
                maskEmail(email),
                0,
                0
        );
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 2) return email;
        return email.substring(0, 2) + "***" + email.substring(at);
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
