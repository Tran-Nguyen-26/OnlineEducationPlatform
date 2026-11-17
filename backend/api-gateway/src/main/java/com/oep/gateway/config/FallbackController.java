package com.oep.gateway.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Answer returned when a circuit breaker is open or an upstream timed out: a clear, retryable 503 instead of a raw gateway error. */
@RestController
public class FallbackController {

    @RequestMapping("/fallback")
    public ResponseEntity<Map<String, String>> fallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Retry-After", "10")
                .body(Map.of("code", "SERVICE_UNAVAILABLE", "message", "Dịch vụ tạm thời không phản hồi, vui lòng thử lại sau ít giây"));
    }
}
