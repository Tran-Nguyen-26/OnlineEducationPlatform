package com.oep.corelms.controller;
import com.oep.corelms.dto.enrollment.*;
import com.oep.corelms.service.EnrollmentService;
import com.oep.coremodel.exception.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {
    private final EnrollmentService service;
    @PostMapping
    public EnrollmentResponse enroll(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EnrollRequest request) {
        return service.enroll(request.courseId(), actor(jwt));
    }
    @PostMapping("/{id}/withdraw")
    public EnrollmentResponse withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.withdraw(id, actor(jwt));
    }
    @PostMapping("/{id}/remove")
    public EnrollmentResponse remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.remove(id, actor(jwt));
    }
    @GetMapping
    public Page<EnrollmentResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(actor(jwt), courseId, page, size);
    }
    private UUID actor(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Token subject must be a user UUID");
        }
    }
}