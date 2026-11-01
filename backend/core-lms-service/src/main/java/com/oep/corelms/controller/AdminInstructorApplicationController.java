package com.oep.corelms.controller;

import com.oep.corelms.dto.instructor.InstructorApplicationResponse;
import com.oep.corelms.dto.instructor.RejectApplicationRequest;
import com.oep.corelms.service.InstructorApplicationService;
import com.oep.coremodel.enums.InstructorApplicationStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/instructor-applications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInstructorApplicationController {

    private final InstructorApplicationService applicationService;

    @GetMapping
    public ResponseEntity<Page<InstructorApplicationResponse>> getApplications(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 8, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        InstructorApplicationStatus appStatus = null;
        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("ALL")) {
            try {
                appStatus = InstructorApplicationStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        Page<InstructorApplicationResponse> response = applicationService.getApplications(appStatus, search, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<com.oep.corelms.dto.instructor.InstructorApplicationStatsResponse> getApplicationStats() {
        return ResponseEntity.ok(applicationService.getApplicationStats());
    }

    @GetMapping("/{id}")
    public ResponseEntity<InstructorApplicationResponse> getApplicationDetail(@PathVariable UUID id) {
        InstructorApplicationResponse response = applicationService.getApplicationDetail(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveApplication(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID adminId = (jwt != null && jwt.getSubject() != null) ? UUID.fromString(jwt.getSubject()) : null;
        Map<String, Object> data = applicationService.approveApplication(id, adminId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Phê duyệt đơn đăng ký giảng viên thành công. Tài khoản đã được tạo và mật khẩu ngẫu nhiên đã được gửi về email của giảng viên.",
                "data", data
        ));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectApplication(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RejectApplicationRequest request
    ) {
        UUID adminId = (jwt != null && jwt.getSubject() != null) ? UUID.fromString(jwt.getSubject()) : null;
        InstructorApplicationResponse response = applicationService.rejectApplication(id, adminId, request);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Từ chối đơn đăng ký thành công. Email thông báo lý do đã được gửi đến ứng viên.",
                "data", response
        ));
    }
}
