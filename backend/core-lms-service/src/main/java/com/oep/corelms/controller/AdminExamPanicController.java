package com.oep.corelms.controller;

import com.oep.corelms.service.ExamPanicCircuitBreaker;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/settings/exam-panic")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminExamPanicController {
    private final ExamPanicCircuitBreaker breaker;

    public record UpdateRequest(boolean enabled, @NotBlank @Size(min = 3, max = 1000) String reason) {}

    @GetMapping
    public ExamPanicCircuitBreaker.State get(@AuthenticationPrincipal Jwt jwt) {
        return breaker.state(actor(jwt));
    }

    @PutMapping
    public ExamPanicCircuitBreaker.State update(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody UpdateRequest request) {
        return breaker.set(actor(jwt), request.enabled(), request.reason());
    }

    private UUID actor(Jwt jwt) {
        if (jwt == null) throw new AccessDeniedException("Authentication required");
        return UUID.fromString(jwt.getSubject());
    }
}
