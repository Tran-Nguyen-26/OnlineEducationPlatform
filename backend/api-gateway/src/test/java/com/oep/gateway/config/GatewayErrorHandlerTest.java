package com.oep.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayErrorHandlerTest {

    private final GatewayErrorHandler handler = new GatewayErrorHandler();

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/x").build());
    }

    @Test
    void unreachableUpstreamBecomesRetryable503() {
        MockServerWebExchange ex = exchange();
        handler.handle(ex, new RuntimeException("wrapper", new ConnectException("refused"))).block();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getResponse().getStatusCode());
        assertNotNull(ex.getResponse().getHeaders().getFirst("Retry-After"));
        assertTrue(ex.getResponse().getBodyAsString().block().contains("SERVICE_UNAVAILABLE"));
    }

    @Test
    void openCircuitBreakerIs503() {
        MockServerWebExchange ex = exchange();
        handler.handle(ex, new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).block();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getResponse().getStatusCode());
    }

    @Test
    void slowUpstreamBecomes504() {
        MockServerWebExchange ex = exchange();
        handler.handle(ex, new TimeoutException("slow")).block();
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.getResponse().getStatusCode());
        assertTrue(ex.getResponse().getBodyAsString().block().contains("GATEWAY_TIMEOUT"));
    }

    @Test
    void unrelatedErrorsFallThroughToTheDefaultHandler() {
        assertThrows(IllegalStateException.class, () -> handler.handle(exchange(), new IllegalStateException("bug")).block());
    }
}
