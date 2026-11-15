package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.CompleteUploadRequest;
import com.oep.corelms.dto.admin.CreateLessonContentRequest;
import com.oep.corelms.dto.admin.LessonContentResponse;
import com.oep.corelms.dto.admin.RequestUploadUrlRequest;
import com.oep.corelms.dto.admin.UpdateLessonContentRequest;
import com.oep.corelms.dto.admin.UploadUrlResponse;
import com.oep.corelms.service.AdminLessonContentService;
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
public class AdminLessonContentController {

    private final AdminLessonContentService contentService;

    @PostMapping("/api/admin/lessons/{lessonId}/contents")
    public LessonContentResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId,
                                         @Valid @RequestBody CreateLessonContentRequest request) {
        return contentService.create(lessonId, UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping("/api/admin/lessons/{lessonId}/contents")
    public List<LessonContentResponse> list(@PathVariable UUID lessonId) {
        return contentService.list(lessonId);
    }

    @PatchMapping("/api/admin/lesson-contents/{contentId}")
    public LessonContentResponse update(@PathVariable UUID contentId, @Valid @RequestBody UpdateLessonContentRequest request) {
        return contentService.update(contentId, request);
    }

    @DeleteMapping("/api/admin/lesson-contents/{contentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID contentId) {
        contentService.delete(contentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/lesson-contents/{contentId}/upload-url")
    public UploadUrlResponse requestUploadUrl(@PathVariable UUID contentId, @Valid @RequestBody RequestUploadUrlRequest request) {
        return contentService.requestUploadUrl(contentId, request);
    }

    @PostMapping("/api/admin/lesson-contents/{contentId}/complete-upload")
    public LessonContentResponse completeUpload(@PathVariable UUID contentId, @Valid @RequestBody CompleteUploadRequest request) {
        return contentService.completeUpload(contentId, request);
    }
}
