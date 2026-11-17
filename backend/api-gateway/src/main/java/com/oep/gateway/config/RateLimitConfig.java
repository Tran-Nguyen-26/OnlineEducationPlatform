package com.oep.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.security.Principal;

@Configuration
public class RateLimitConfig {

    /** Key by client IP (X-Forwarded-For first hop when behind a proxy) — used for unauthenticated, abuse-prone endpoints. */
    @org.springframework.beans.factory.annotation.Value("${oep.gateway.trusted-proxy-hops:0}")
    private int trustedProxyHops;

    @Bean
    KeyResolver ipKeyResolver() {
        return exchange -> {
            // X-Forwarded-For is client-controlled at its left end; only the entries appended by OUR proxies are trustworthy.
            // With N trusted proxies (ALB, nginx...) the real client is the Nth entry from the right.
            String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (trustedProxyHops > 0 && xff != null && !xff.isBlank()) {
                String[] parts = xff.split(",");
                return Mono.just(parts[Math.max(0, parts.length - trustedProxyHops)].trim());
            }
            var addr = exchange.getRequest().getRemoteAddress();
            return Mono.just(addr != null ? addr.getHostString() : "unknown");
        };
    }

    /** Rate-limit per logged-in user (JWT subject); fall back to client IP for anonymous calls. */
    @Bean
    @org.springframework.context.annotation.Primary
    KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    var addr = exchange.getRequest().getRemoteAddress();
                    return addr != null ? addr.getHostString() : "unknown";
                }));
    }
}
