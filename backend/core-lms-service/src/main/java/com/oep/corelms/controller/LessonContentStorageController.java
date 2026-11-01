package com.oep.corelms.controller;

import com.oep.corelms.dto.course.LessonContentDtos;
import com.oep.corelms.service.LessonContentStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contents/{contentId}")
@RequiredArgsConstructor
public class LessonContentStorageController {
    private final LessonContentStorageService service;

    @PostMapping("/upload-url")
    public LessonContentDtos.UploadUrlResponse issueUploadUrl(@AuthenticationPrincipal Jwt jwt,
                                                               @PathVariable UUID contentId) {
        return service.issueUploadUrl(userId(jwt), contentId);
    }

    @PostMapping("/multipart-upload")
    public LessonContentDtos.MultipartUploadResponse issueMultipartUploadUrls(@AuthenticationPrincipal Jwt jwt,
                                                                               @PathVariable UUID contentId) {
        return service.issueMultipartUploadUrls(userId(jwt), contentId);
    }

    @PostMapping("/complete-multipart-upload")
    public LessonContentDtos.Response completeMultipartUpload(@AuthenticationPrincipal Jwt jwt,
                                                               @PathVariable UUID contentId,
                                                               @Valid @RequestBody LessonContentDtos.CompleteMultipartRequest request) {
        return service.completeMultipartUpload(userId(jwt), contentId, request);
    }

    @DeleteMapping("/multipart-upload")
    public void cancelMultipartUpload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID contentId) {
        service.cancelMultipartUpload(userId(jwt), contentId);
    }

    @PostMapping("/complete-upload")
    public LessonContentDtos.Response completeUpload(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable UUID contentId) {
        return service.completeUpload(userId(jwt), contentId);
    }

    @PostMapping("/fail-upload")
    public void failUpload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID contentId) {
        service.failUpload(userId(jwt), contentId);
    }

    @GetMapping("/access-url")
    public LessonContentDtos.AccessUrlResponse getAccessUrl(@AuthenticationPrincipal Jwt jwt,
                                                             @PathVariable UUID contentId) {
        return service.getAccessUrl(userId(jwt), contentId);
    }

    private UUID userId(Jwt jwt) {
        if (jwt == null) throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        return UUID.fromString(jwt.getSubject());
    }
}
