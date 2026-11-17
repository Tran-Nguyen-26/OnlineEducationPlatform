package com.oep.corelms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Cache-aside on Redis (JSON values, TTL). Always fails open: if Redis is down or a cached value cannot be read,
 * the loader runs and the request is served from the database — the cache is an optimisation, never a dependency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public <T> T get(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        String redisKey = "oep:cache:" + key;
        try {
            String hit = redis.opsForValue().get(redisKey);
            if (hit != null) return mapper.readValue(hit, type);
        } catch (Exception e) {
            log.debug("cache read skipped for {}: {}", key, e.getMessage());
        }
        T value = loader.get();
        try {
            redis.opsForValue().set(redisKey, mapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.debug("cache write skipped for {}: {}", key, e.getMessage());
        }
        return value;
    }

    public void evict(String key) {
        try { redis.delete("oep:cache:" + key); } catch (Exception ignored) { /* best effort */ }
    }
}
