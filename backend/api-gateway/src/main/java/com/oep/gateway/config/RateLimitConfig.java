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

    /**
     * The real client address. X-Forwarded-For is client-controlled at its left end; only the entries appended by OUR
     * proxies can be trusted. With N trusted proxies (ALB, nginx...) the real client is the Nth entry from the right.
     * Without trusted proxies the TCP peer address is used.
     */
    private String clientIp(org.springframework.web.server.ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (trustedProxyHops > 0 && xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            return parts[Math.max(0, parts.length - trustedProxyHops)].trim();
        }
        var addr = exchange.getRequest().getRemoteAddress();
        return addr != null ? addr.getHostString() : "unknown";
    }

    /**
     * Spring's Redis limiter keeps ONE bucket per key and ignores the route, so a route-specific limiter and the default
     * limiter would drain the same bucket (each request costing two tokens, and the last writer's rate winning).
     * Every limiter therefore gets its own key namespace.
     */
    private static String routeId(org.springframework.web.server.ServerWebExchange exchange) {
        org.springframework.cloud.gateway.route.Route route = exchange.getAttribute(
                org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route == null ? "none" : route.getId();
    }

    /** Per client IP, scoped to the route (login/register/...): abuse-prone endpoints. */
    @Bean
    KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(routeId(exchange) + "|ip|" + clientIp(exchange));
    }

    /** Per user (or IP when anonymous), scoped to the route: stricter limits on specific endpoints (quiz writes, uploads). */
    @Bean
    KeyResolver routeUserKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .switchIfEmpty(Mono.fromSupplier(() -> clientIp(exchange)))
                .map(id -> routeId(exchange) + "|user|" + id);
    }

    /** Rate-limit per logged-in user (JWT subject); anonymous calls are limited per real client IP (never per proxy). */
    @Bean
    @org.springframework.context.annotation.Primary
    KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .switchIfEmpty(Mono.fromSupplier(() -> clientIp(exchange)))
                .map(id -> "default|" + id);
    }
}
