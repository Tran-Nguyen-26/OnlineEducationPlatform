package com.oep.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.security.Principal;

@Configuration
public class RateLimitConfig {

    /** Rate-limit per logged-in user (JWT subject); fall back to client IP for anonymous calls. */
    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    var addr = exchange.getRequest().getRemoteAddress();
                    return addr != null ? addr.getAddress().getHostAddress() : "unknown";
                }));
    }
}
