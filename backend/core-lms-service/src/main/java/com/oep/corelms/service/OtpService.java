package com.oep.corelms.service;

import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int OTP_TTL_SECONDS = 300;           // 5 minutes
    private static final int COOLDOWN_SECONDS = 60;           // 1 minute resend cooldown
    private static final int HOURLY_LIMIT = 5;
    private static final int HOURLY_TTL_SECONDS = 3600;       // 1 hour
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private static final String PREFIX_OTP = "auth:otp:change-pwd:";
    private static final String PREFIX_COOLDOWN = "auth:rate-limit:change-pwd-resend:";
    private static final String PREFIX_HOURLY = "auth:rate-limit:change-pwd-hourly:";

    private static final String FIELD_OTP_HASH = "otp_hash";
    private static final String FIELD_PWD_HASH = "new_pwd_hash";
    private static final String FIELD_ATTEMPTS = "failed_attempts";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    public String createOtpSession(UUID userId, String newPasswordHash) {
        String userKey = userId.toString();

        // --- Rate Limit: cooldown (1 request per 60 seconds) ---
        String cooldownKey = PREFIX_COOLDOWN + userKey;
        if (redis.hasKey(cooldownKey)) {
            Long ttl = redis.getExpire(cooldownKey, TimeUnit.SECONDS);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "Please wait " + (ttl != null ? ttl : COOLDOWN_SECONDS) + " second(s) before requesting a new code");
        }

        // --- Rate Limit: hourly cap (max 5 requests per hour) ---
        String hourlyKey = PREFIX_HOURLY + userKey;
        String hourlyCountStr = redis.opsForValue().get(hourlyKey);
        int hourlyCount = hourlyCountStr == null ? 0 : Integer.parseInt(hourlyCountStr);
        if (hourlyCount >= HOURLY_LIMIT) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "You have exceeded the maximum number of OTP requests per hour. Please try again later.");
        }

        // --- Generate OTP ---
        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        String otpHash = sha256Hex(otp);

        // --- Store OTP session in Redis (Hash) ---
        String otpKey = PREFIX_OTP + userKey;
        redis.opsForHash().put(otpKey, FIELD_OTP_HASH, otpHash);
        redis.opsForHash().put(otpKey, FIELD_PWD_HASH, newPasswordHash);
        redis.opsForHash().put(otpKey, FIELD_ATTEMPTS, "0");
        redis.expire(otpKey, Duration.ofSeconds(OTP_TTL_SECONDS));

        // --- Set cooldown lock ---
        redis.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(COOLDOWN_SECONDS));

        // --- Increment hourly counter ---
        if (hourlyCountStr == null) {
            redis.opsForValue().set(hourlyKey, "1", Duration.ofSeconds(HOURLY_TTL_SECONDS));
        } else {
            redis.opsForValue().increment(hourlyKey);
        }

        log.info("[OTP] Created OTP session for userId={}", userId);
        return otp;
    }

    /**
     * Verifies the submitted OTP code against what is stored in Redis.
     * On success, returns the stored new-password BCrypt hash and deletes the session.
     * On failure, increments failed attempts; deletes session if max attempts exceeded.
     *
     * @param userId  user's UUID
     * @param rawCode plaintext 6-digit code submitted by the user
     * @return BCrypt hash of the new password to apply
     */
    public String verifyOtpAndGetNewPasswordHash(UUID userId, String rawCode) {
        String otpKey = PREFIX_OTP + userId;

        Map<Object, Object> session = redis.opsForHash().entries(otpKey);
        if (session.isEmpty()) {
            throw new BusinessException(ErrorCode.OTP_EXPIRED,
                    "Verification code has expired or does not exist. Please request a new one.");
        }

        int failedAttempts = Integer.parseInt((String) session.get(FIELD_ATTEMPTS));
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            redis.delete(otpKey);
            throw new BusinessException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED,
                    "You have exceeded the maximum number of attempts. Please restart the process.");
        }

        String storedHash = (String) session.get(FIELD_OTP_HASH);
        String submittedHash = sha256Hex(rawCode);

        // Constant-time comparison to mitigate timing attacks
        if (!MessageDigest.isEqual(storedHash.getBytes(StandardCharsets.UTF_8),
                                   submittedHash.getBytes(StandardCharsets.UTF_8))) {
            int newFailedAttempts = failedAttempts + 1;
            redis.opsForHash().put(otpKey, FIELD_ATTEMPTS, String.valueOf(newFailedAttempts));

            int remaining = MAX_FAILED_ATTEMPTS - newFailedAttempts;
            if (remaining <= 0) {
                redis.delete(otpKey);
                throw new BusinessException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED,
                        "You have exceeded the maximum number of attempts. Please restart the process.");
            }
            throw new BusinessException(ErrorCode.INVALID_OTP,
                    "Invalid verification code. You have " + remaining + " attempt(s) remaining.");
        }

        // Success: extract new password hash and clean up
        String newPasswordHash = (String) session.get(FIELD_PWD_HASH);
        redis.delete(otpKey);

        log.info("[OTP] OTP verified successfully for userId={}", userId);
        return newPasswordHash;
    }

    /**
     * Returns the remaining TTL in seconds of the OTP session, or 0 if none.
     */
    public long getOtpTtlSeconds(UUID userId) {
        Long ttl = redis.getExpire(PREFIX_OTP + userId, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    // ---------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public int getOtpTtlSecondsConstant() {
        return OTP_TTL_SECONDS;
    }

    public int getCooldownSecondsConstant() {
        return COOLDOWN_SECONDS;
    }
}
