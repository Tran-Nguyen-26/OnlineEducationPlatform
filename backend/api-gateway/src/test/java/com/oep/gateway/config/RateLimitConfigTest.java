package com.oep.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RateLimitConfigTest {

    private RateLimitConfig config(int hops) {
        RateLimitConfig c = new RateLimitConfig();
        ReflectionTestUtils.setField(c, "trustedProxyHops", hops);
        return c;
    }

    private MockServerWebExchange exchange(String xff, String routeId) {
        MockServerHttpRequest.BaseBuilder<?> b = MockServerHttpRequest.post("/api/auth/login");
        if (xff != null) b.header("X-Forwarded-For", xff);
        MockServerWebExchange ex = MockServerWebExchange.from(b.build());
        if (routeId != null) {
            ex.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                    Route.async().id(routeId).uri(URI.create("http://x")).predicate(e -> true).build());
        }
        return ex;
    }

    @Test
    void clientIsTheNthAddressFromTheRightBehindTrustedProxies() {
        // a client may forge the left part of X-Forwarded-For; only entries appended by our proxies count
        String key = config(1).ipKeyResolver().resolve(exchange("6.6.6.6, 203.0.113.9", "auth")).block();
        assertEquals("auth|ip|203.0.113.9", key);
        String two = config(2).ipKeyResolver().resolve(exchange("6.6.6.6, 203.0.113.9, 10.0.0.5", "auth")).block();
        assertEquals("auth|ip|203.0.113.9", two);
    }

    @Test
    void limitersDoNotShareBucketsAcrossRoutes() {
        String a = config(1).ipKeyResolver().resolve(exchange("1.1.1.1", "auth-sensitive")).block();
        String b = config(1).routeUserKeyResolver().resolve(exchange("1.1.1.1", "quiz-write")).block();
        assertNotEquals(a, b);
        assertEquals("quiz-write|user|1.1.1.1", b);
    }

    @Test
    void anonymousRequestsFallBackToClientIp() {
        assertEquals("default|9.9.9.9", config(1).userKeyResolver().resolve(exchange("9.9.9.9", null)).block());
    }
}
