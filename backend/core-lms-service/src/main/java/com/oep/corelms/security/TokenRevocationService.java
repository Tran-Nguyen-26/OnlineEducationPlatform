package com.oep.corelms.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Access tokens are stateless JWTs (15 min), so disabling an account or changing a password cannot "delete" them.
 * Instead we publish a "revoked-after" timestamp in Redis; the gateway rejects any token of that user issued before it.
 * The key lives only as long as the longest-lived access token, so it cleans itself up.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    public static final String KEY_PREFIX = "oep:revoked-after:";

    private final StringRedisTemplate redis;

    @Value("${oep.jwt.access-ttl-seconds:900}")
    private long accessTtlSeconds;

    /** Revokes tokens issued before now (a replacement token issued right after, in the same second, stays valid). */
    public void revokeAccessTokens(UUID userId) {
        publish(userId, Instant.now().getEpochSecond());
    }

    /** Also revokes tokens issued within the current second (account disabled: nothing new must survive). */
    public void revokeAllAccessTokens(UUID userId) {
        publish(userId, Instant.now().getEpochSecond() + 1);
    }

    private void publish(UUID userId, long revokedAfterEpochSecond) {
        try {
            redis.opsForValue().set(KEY_PREFIX + userId, String.valueOf(revokedAfterEpochSecond), Duration.ofSeconds(accessTtlSeconds + 120));
        } catch (Exception e) {
            // Redis down must not block the admin action; tokens then simply expire naturally (<= 15 min).
            log.warn("Could not publish token revocation for {}: {}", userId, e.getMessage());
        }
    }
}
