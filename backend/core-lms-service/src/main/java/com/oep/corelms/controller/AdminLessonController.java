package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.CreateLessonRequest;
import com.oep.corelms.dto.admin.LessonResponse;
import com.oep.corelms.dto.admin.UpdateLessonRequest;
import com.oep.corelms.service.AdminLessonService;
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
public class AdminLessonController {

    private final AdminLessonService lessonService;
    private final AuditLogService auditLogService;

    @PostMapping("/api/admin/modules/{moduleId}/lessons")
    public LessonResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID moduleId,
                                  @Valid @RequestBody CreateLessonRequest request) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        LessonResponse response = lessonService.create(moduleId, request);
        auditLogService.record(adminId, "CREATE_LESSON", "LESSON", response.id(), Map.of("moduleId", moduleId.toString()));
        return response;
    }

    @GetMapping("/api/admin/modules/{moduleId}/lessons")
    public List<LessonResponse> list(@PathVariable UUID moduleId) {
        return lessonService.list(moduleId);
    }

    @PatchMapping("/api/admin/lessons/{lessonId}")
    public LessonResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId,
                                  @Valid @RequestBody UpdateLessonRequest request) {
        LessonResponse response = lessonService.update(lessonId, request);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "UPDATE_LESSON", "LESSON", lessonId, Map.of("title", response.title()));
        return response;
    }

    @DeleteMapping("/api/admin/lessons/{lessonId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId) {
        lessonService.delete(lessonId);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "DELETE_LESSON", "LESSON", lessonId, null);
        return ResponseEntity.noContent().build();
    }
}
