package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.AdminEnrollmentResponse;
import com.oep.corelms.dto.admin.UpdateEnrollmentStatusRequest;
import com.oep.corelms.service.AdminEnrollmentService;
import com.oep.corelms.service.AuditLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminEnrollmentController {

    private final AdminEnrollmentService enrollmentService;
    private final AuditLogService auditLogService;

    @GetMapping("/courses/{courseId}/enrollments")
    public List<AdminEnrollmentResponse> list(@PathVariable UUID courseId) {
        return enrollmentService.list(courseId);
    }

    @PatchMapping("/enrollments/{enrollmentId}/status")
    public AdminEnrollmentResponse updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID enrollmentId,
                                                 @Valid @RequestBody UpdateEnrollmentStatusRequest request) {
        AdminEnrollmentResponse response = enrollmentService.updateStatus(enrollmentId, request.status());
        auditLogService.record(UUID.fromString(jwt.getSubject()), "UPDATE_ENROLLMENT_STATUS", "ENROLLMENT", enrollmentId,
                Map.of("status", request.status().name(), "studentId", response.studentId().toString()));
        return response;
    }
}
