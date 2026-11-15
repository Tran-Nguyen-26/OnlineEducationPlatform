package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.AssignmentResponse;
import com.oep.corelms.dto.admin.CreateAssignmentRequest;
import com.oep.corelms.dto.admin.UpdateAssignmentRequest;
import com.oep.corelms.service.AdminAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AdminAssignmentController {

    private final AdminAssignmentService assignmentService;

    @PostMapping("/api/admin/courses/{courseId}/assignments")
    public AssignmentResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                      @Valid @RequestBody CreateAssignmentRequest request) {
        return assignmentService.create(courseId, UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping("/api/admin/courses/{courseId}/assignments")
    public List<AssignmentResponse> list(@PathVariable UUID courseId) {
        return assignmentService.list(courseId);
    }

    @PatchMapping("/api/admin/assignments/{assignmentId}")
    public AssignmentResponse update(@PathVariable UUID assignmentId, @Valid @RequestBody UpdateAssignmentRequest request) {
        return assignmentService.update(assignmentId, request);
    }

    @DeleteMapping("/api/admin/assignments/{assignmentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID assignmentId) {
        assignmentService.delete(assignmentId);
        return ResponseEntity.noContent().build();
    }
}
