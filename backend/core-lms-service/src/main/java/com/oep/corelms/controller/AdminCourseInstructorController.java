package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.AssignInstructorRequest;
import com.oep.corelms.dto.admin.CourseInstructorResponse;
import com.oep.corelms.service.AdminCourseInstructorService;
import com.oep.corelms.service.AuditLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/courses/{courseId}/instructors")
@RequiredArgsConstructor
public class AdminCourseInstructorController {

    private final AdminCourseInstructorService service;
    private final AuditLogService auditLogService;

    @PostMapping
    public CourseInstructorResponse assign(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                            @Valid @RequestBody AssignInstructorRequest request) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        CourseInstructorResponse response = service.assign(courseId, request.instructorId(), adminId);
        auditLogService.record(adminId, "ASSIGN_INSTRUCTOR", "COURSE", courseId,
                Map.of("instructorId", request.instructorId().toString()));
        return response;
    }

    @GetMapping
    public List<CourseInstructorResponse> list(@PathVariable UUID courseId) {
        return service.list(courseId);
    }

    @DeleteMapping("/{instructorId}")
    public ResponseEntity<Void> unassign(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                          @PathVariable UUID instructorId) {
        service.unassign(courseId, instructorId);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "UNASSIGN_INSTRUCTOR", "COURSE", courseId,
                Map.of("instructorId", instructorId.toString()));
        return ResponseEntity.noContent().build();
    }
}
