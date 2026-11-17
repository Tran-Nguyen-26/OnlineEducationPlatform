package com.oep.corelms.security;

import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Brute-force protection per account (email): after {@code max-failures} wrong passwords within the window the account
 * is locked for {@code lock-minutes}; a successful login clears the counters. Complements the per-IP gateway limit
 * (which an attacker can rotate) by limiting guesses against ONE account regardless of where they come from.
 * Redis down ⇒ fail open (never block legitimate logins because of infrastructure).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final StringRedisTemplate redis;

    @Value("${oep.security.login.max-failures:5}") private int maxFailures;
    @Value("${oep.security.login.lock-minutes:15}") private int lockMinutes;

    public void assertNotLocked(String email) {
        try {
            Long ttl = redis.getExpire(lockKey(email));
            if (ttl != null && ttl > 0) {
                throw new BusinessException(ErrorCode.TOO_MANY_ATTEMPTS,
                        "Đăng nhập sai quá nhiều lần. Tài khoản bị khóa tạm thời, thử lại sau " + (ttl / 60 + 1) + " phút");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Login lock check skipped (Redis unavailable): {}", e.getMessage());
        }
    }

    /** @return true when this failure just triggered the lock. */
    public boolean recordFailure(String email) {
        try {
            Long n = redis.opsForValue().increment(failKey(email));
            if (n != null && n == 1) redis.expire(failKey(email), Duration.ofMinutes(lockMinutes));
            if (n != null && n >= maxFailures) {
                redis.opsForValue().set(lockKey(email), "1", Duration.ofMinutes(lockMinutes));
                redis.delete(failKey(email));
                return true;
            }
        } catch (Exception e) {
            log.warn("Login failure not counted (Redis unavailable): {}", e.getMessage());
        }
        return false;
    }

    public void reset(String email) {
        try {
            redis.delete(failKey(email));
            redis.delete(lockKey(email));
        } catch (Exception ignored) { /* best effort */ }
    }

    private static String failKey(String e) { return "oep:login-fail:" + e; }
    private static String lockKey(String e) { return "oep:login-lock:" + e; }
}
