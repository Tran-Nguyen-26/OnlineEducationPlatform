package com.oep.gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Rejects access tokens issued before the user's "revoked-after" mark (set by core-lms when an account is disabled or a
 * password is changed/reset). Fails open if Redis is unreachable — tokens then just expire naturally within 15 minutes.
 */
@Component
public class TokenRevocationFilter implements GlobalFilter, Ordered {

    private static final String KEY_PREFIX = "oep:revoked-after:";

    private final ReactiveStringRedisTemplate redis;

    public TokenRevocationFilter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .flatMap(jwt -> isRevoked(jwt).filter(revoked -> revoked).map(r -> jwt))
                .flatMap(jwt -> reject(exchange))
                .switchIfEmpty(Mono.defer(() -> chain.filter(exchange)));
    }

    private Mono<Boolean> isRevoked(Jwt jwt) {
        if (jwt.getIssuedAt() == null || jwt.getSubject() == null) return Mono.just(false);
        return redis.opsForValue().get(KEY_PREFIX + jwt.getSubject())
                .map(v -> jwt.getIssuedAt().getEpochSecond() < Long.parseLong(v))
                .defaultIfEmpty(false)
                .onErrorReturn(false);
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buf = exchange.getResponse().bufferFactory().wrap(
                "{\"code\":\"TOKEN_REVOKED\",\"message\":\"Phiên đăng nhập đã bị thu hồi, vui lòng đăng nhập lại\"}".getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buf));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
