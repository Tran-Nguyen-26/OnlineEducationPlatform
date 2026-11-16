package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.CompleteUploadRequest;
import com.oep.corelms.dto.admin.CreateLessonContentRequest;
import com.oep.corelms.dto.admin.LessonContentResponse;
import com.oep.corelms.dto.admin.RequestUploadUrlRequest;
import com.oep.corelms.dto.admin.UpdateLessonContentRequest;
import com.oep.corelms.dto.admin.UploadUrlResponse;
import com.oep.corelms.service.AdminLessonContentService;
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
public class AdminLessonContentController {

    private final AdminLessonContentService contentService;
    private final AuditLogService auditLogService;
    private final com.oep.corelms.service.ContentChangeLogService changeLog;

    @PostMapping("/api/admin/lessons/{lessonId}/contents")
    public LessonContentResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId,
                                         @Valid @RequestBody CreateLessonContentRequest request) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        LessonContentResponse response = contentService.create(lessonId, adminId, request);
        changeLog.record(response.id(), adminId, "CREATED");
        auditLogService.record(adminId, "CREATE_LESSON_CONTENT", "LESSON_CONTENT", response.id(),
                Map.of("lessonId", lessonId.toString(), "contentType", response.contentType().name()));
        return response;
    }

    @GetMapping("/api/admin/lessons/{lessonId}/contents")
    public List<LessonContentResponse> list(@PathVariable UUID lessonId) {
        return contentService.list(lessonId);
    }

    @PatchMapping("/api/admin/lesson-contents/{contentId}")
    public LessonContentResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID contentId,
                                         @Valid @RequestBody UpdateLessonContentRequest request) {
        LessonContentResponse response = contentService.update(contentId, request);
        changeLog.record(contentId, UUID.fromString(jwt.getSubject()), "UPDATED");
        auditLogService.record(UUID.fromString(jwt.getSubject()), "UPDATE_LESSON_CONTENT", "LESSON_CONTENT", contentId, null);
        return response;
    }

    @DeleteMapping("/api/admin/lesson-contents/{contentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID contentId) {
        contentService.delete(contentId);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "DELETE_LESSON_CONTENT", "LESSON_CONTENT", contentId, null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/lesson-contents/{contentId}/upload-url")
    public UploadUrlResponse requestUploadUrl(@PathVariable UUID contentId, @Valid @RequestBody RequestUploadUrlRequest request) {
        return contentService.requestUploadUrl(contentId, request);
    }

    @PostMapping("/api/admin/lesson-contents/{contentId}/complete-upload")
    public LessonContentResponse completeUpload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID contentId, @Valid @RequestBody CompleteUploadRequest request) {
        LessonContentResponse response = contentService.completeUpload(contentId, request);
        changeLog.record(contentId, UUID.fromString(jwt.getSubject()), "FILE_REPLACED");
        return response;
    }

    @GetMapping("/api/admin/lesson-contents/{contentId}/history")
    public List<com.oep.corelms.service.ContentChangeLogService.Entry> history(@PathVariable UUID contentId) {
        return changeLog.history(contentId);
    }
}
