package com.oep.gateway.config;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Public API versioning: clients may call {@code /api/v1/...}; the gateway maps it onto the current internal paths
 * ({@code /api/...}). A future breaking change ships as /api/v2 routes while /api/v1 keeps serving old clients.
 */
@Component
public class ApiVersionFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (path.startsWith("/api/v1/")) {
            var rewritten = exchange.getRequest().mutate().path("/api/" + path.substring("/api/v1/".length())).build();
            return chain.filter(exchange.mutate().request(rewritten).build());
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
