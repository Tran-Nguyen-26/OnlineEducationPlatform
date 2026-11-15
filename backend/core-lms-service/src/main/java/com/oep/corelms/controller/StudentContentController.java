package com.oep.corelms.controller;

import com.oep.corelms.dto.student.UpdateLessonProgressRequest;
import com.oep.corelms.dto.student.ViewUrlResponse;
import com.oep.corelms.service.StudentCourseContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class StudentContentController {

    private final StudentCourseContentService contentService;

    @GetMapping("/api/lesson-contents/{contentId}/view-url")
    public ViewUrlResponse viewUrl(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID contentId) {
        return contentService.getContentViewUrl(contentId, UUID.fromString(jwt.getSubject()));
    }

    @PatchMapping("/api/lessons/{lessonId}/progress")
    public ResponseEntity<Void> updateProgress(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId,
                                                @Valid @RequestBody UpdateLessonProgressRequest request) {
        contentService.updateLessonProgress(lessonId, UUID.fromString(jwt.getSubject()), request.status());
        return ResponseEntity.noContent().build();
    }
}
