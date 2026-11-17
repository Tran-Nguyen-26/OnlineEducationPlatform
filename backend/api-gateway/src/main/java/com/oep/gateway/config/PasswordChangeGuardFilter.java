package com.oep.gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Accounts created by an admin carry must_change_password=true until the first password change. The SPA already routes
 * them to the change-password screen, but that is only UI: here the API itself refuses everything else for such tokens.
 */
@Component
public class PasswordChangeGuardFilter implements GlobalFilter, Ordered {

    private static final Set<String> ALLOWED = Set.of("/api/auth/change-password", "/api/auth/me", "/api/auth/logout", "/api/auth/refresh");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (ALLOWED.contains(path) || !path.startsWith("/api/")) {
            return chain.filter(exchange);
        }
        return exchange.getPrincipal()
                .filter(p -> p instanceof Authentication)
                .cast(Authentication.class)
                .filter(a -> a instanceof JwtAuthenticationToken)
                .map(a -> ((JwtAuthenticationToken) a).getToken())
                .filter(PasswordChangeGuardFilter::mustChange)
                .flatMap(jwt -> reject(exchange))
                .switchIfEmpty(Mono.defer(() -> chain.filter(exchange)));
    }

    private static boolean mustChange(Jwt jwt) {
        return Boolean.TRUE.equals(jwt.getClaim("must_change_password"));
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buf = exchange.getResponse().bufferFactory().wrap(
                "{\"code\":\"PASSWORD_CHANGE_REQUIRED\",\"message\":\"Bạn phải đổi mật khẩu lần đầu trước khi dùng hệ thống\"}".getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buf));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
