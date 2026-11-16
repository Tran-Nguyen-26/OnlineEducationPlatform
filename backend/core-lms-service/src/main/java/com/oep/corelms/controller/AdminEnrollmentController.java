package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.AdminEnrollmentResponse;
import com.oep.corelms.dto.admin.RecordPaymentRequest;
import com.oep.corelms.dto.admin.TuitionPaymentResponse;
import com.oep.corelms.dto.admin.UpdateEnrollmentStatusRequest;
import com.oep.corelms.service.AdminEnrollmentService;
import com.oep.corelms.service.AuditLogService;
import com.oep.corelms.service.TuitionPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminEnrollmentController {

    private final AdminEnrollmentService enrollmentService;
    private final TuitionPaymentService tuitionPaymentService;
    private final AuditLogService auditLogService;

    @GetMapping("/courses/{courseId}/enrollments")
    public List<AdminEnrollmentResponse> list(@PathVariable UUID courseId) {
        return enrollmentService.list(courseId);
    }

    @GetMapping("/courses/{courseId}/enrollments/export")
    public ResponseEntity<byte[]> exportCsv(@PathVariable UUID courseId) {
        byte[] body = enrollmentService.exportCsv(courseId).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("roster-" + courseId + ".csv").build().toString())
                .body(body);
    }

    @PatchMapping("/enrollments/{enrollmentId}/status")
    public AdminEnrollmentResponse updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID enrollmentId,
                                                 @Valid @RequestBody UpdateEnrollmentStatusRequest request) {
        AdminEnrollmentResponse response = enrollmentService.updateStatus(enrollmentId, request.status());
        auditLogService.record(UUID.fromString(jwt.getSubject()), "UPDATE_ENROLLMENT_STATUS", "ENROLLMENT", enrollmentId,
                Map.of("status", request.status().name(), "studentId", response.studentId().toString()));
        return response;
    }

    @GetMapping("/enrollments/{enrollmentId}/payments")
    public List<TuitionPaymentResponse> listPayments(@PathVariable UUID enrollmentId) {
        return tuitionPaymentService.list(enrollmentId);
    }

    @PostMapping("/enrollments/{enrollmentId}/payments")
    public TuitionPaymentResponse recordPayment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID enrollmentId,
                                                 @Valid @RequestBody RecordPaymentRequest request) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        TuitionPaymentResponse response = tuitionPaymentService.record(enrollmentId, adminId, request);
        auditLogService.record(adminId, "RECORD_PAYMENT", "ENROLLMENT", enrollmentId,
                Map.of("amount", request.amount().toString()));
        return response;
    }
}
