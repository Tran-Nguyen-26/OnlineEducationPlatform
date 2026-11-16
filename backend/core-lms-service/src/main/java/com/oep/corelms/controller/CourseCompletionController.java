package com.oep.corelms.controller;

import com.oep.corelms.dto.student.CourseCompletionResponse;
import com.oep.corelms.service.AuditLogService;
import com.oep.corelms.service.CourseCompletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CourseCompletionController {

    private final CourseCompletionService completionService;
    private final AuditLogService auditLogService;

    @GetMapping("/api/courses/{courseId}/completion")
    public CourseCompletionResponse status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return completionService.status(courseId, UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/api/courses/{courseId}/complete")
    public CourseCompletionResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        UUID studentId = UUID.fromString(jwt.getSubject());
        CourseCompletionResponse r = completionService.complete(courseId, studentId);
        auditLogService.record(studentId, "SELF_COMPLETE_COURSE", "COURSE", courseId, Map.of());
        return r;
    }
}
