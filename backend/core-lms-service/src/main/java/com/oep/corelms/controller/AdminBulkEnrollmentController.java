package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.BulkEnrollRequest;
import com.oep.corelms.dto.admin.BulkEnrollRowResult;
import com.oep.corelms.service.AdminBulkEnrollmentService;
import com.oep.corelms.service.AuditLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/courses/{courseId}/bulk-enroll")
@RequiredArgsConstructor
public class AdminBulkEnrollmentController {

    private final AdminBulkEnrollmentService bulkEnrollmentService;
    private final AuditLogService auditLogService;

    @PostMapping
    public List<BulkEnrollRowResult> bulkEnroll(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                                 @Valid @RequestBody BulkEnrollRequest request) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        List<BulkEnrollRowResult> results = bulkEnrollmentService.bulkEnroll(courseId, request.inviteCodeId(), request.emails());
        auditLogService.record(adminId, "BULK_ENROLL", "COURSE", courseId,
                Map.of("rowCount", results.size()));
        return results;
    }
}
