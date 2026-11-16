package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.AssignmentResponse;
import com.oep.corelms.dto.admin.CreateAssignmentRequest;
import com.oep.corelms.dto.admin.UpdateAssignmentRequest;
import com.oep.corelms.service.AdminAssignmentService;
import com.oep.corelms.service.AuditLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AdminAssignmentController {

    private final AdminAssignmentService assignmentService;
    private final AuditLogService auditLogService;

    @PostMapping("/api/admin/courses/{courseId}/assignments")
    public AssignmentResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                      @Valid @RequestBody CreateAssignmentRequest request) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        AssignmentResponse response = assignmentService.create(courseId, adminId, request);
        auditLogService.record(adminId, "CREATE_ASSIGNMENT", "ASSIGNMENT", response.id(), Map.of("title", response.title()));
        return response;
    }

    @GetMapping("/api/admin/courses/{courseId}/assignments")
    public List<AssignmentResponse> list(@PathVariable UUID courseId) {
        return assignmentService.list(courseId);
    }

    @PatchMapping("/api/admin/assignments/{assignmentId}")
    public AssignmentResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID assignmentId,
                                      @Valid @RequestBody UpdateAssignmentRequest request) {
        AssignmentResponse response = assignmentService.update(assignmentId, request);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "UPDATE_ASSIGNMENT", "ASSIGNMENT", assignmentId,
                Map.of("title", response.title()));
        return response;
    }

    @DeleteMapping("/api/admin/assignments/{assignmentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID assignmentId) {
        assignmentService.delete(assignmentId);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "DELETE_ASSIGNMENT", "ASSIGNMENT", assignmentId, null);
        return ResponseEntity.noContent().build();
    }
}
