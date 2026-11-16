package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.AddPrerequisiteRequest;
import com.oep.corelms.dto.admin.CoursePrerequisiteResponse;
import com.oep.corelms.service.AdminCoursePrerequisiteService;
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
@RequestMapping("/api/admin/courses/{courseId}/prerequisites")
@RequiredArgsConstructor
public class AdminCoursePrerequisiteController {

    private final AdminCoursePrerequisiteService prerequisiteService;
    private final AuditLogService auditLogService;

    @GetMapping
    public List<CoursePrerequisiteResponse> list(@PathVariable UUID courseId) {
        return prerequisiteService.list(courseId);
    }

    @PostMapping
    public CoursePrerequisiteResponse add(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                           @Valid @RequestBody AddPrerequisiteRequest request) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        CoursePrerequisiteResponse response = prerequisiteService.add(courseId, request.prerequisiteCourseId());
        auditLogService.record(adminId, "ADD_PREREQUISITE", "COURSE", courseId,
                Map.of("prerequisiteCourseId", request.prerequisiteCourseId().toString()));
        return response;
    }

    @DeleteMapping("/{prerequisiteCourseId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                        @PathVariable UUID prerequisiteCourseId) {
        prerequisiteService.remove(courseId, prerequisiteCourseId);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "REMOVE_PREREQUISITE", "COURSE", courseId,
                Map.of("prerequisiteCourseId", prerequisiteCourseId.toString()));
        return ResponseEntity.noContent().build();
    }
}
