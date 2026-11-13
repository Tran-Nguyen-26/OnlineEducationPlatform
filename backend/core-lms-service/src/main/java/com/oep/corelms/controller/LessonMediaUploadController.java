package com.oep.corelms.controller;

import com.oep.corelms.service.LessonMediaUploadService;
import com.oep.corelms.service.LessonMediaUploadService.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/lessons/{lessonId}/media")
@PreAuthorize("hasRole('ADMIN')")
@ConditionalOnProperty(name = "oep.media.endpoint")
@RequiredArgsConstructor
public class LessonMediaUploadController {
    private final LessonMediaUploadService media;

    @PostMapping
    public ResponseEntity<UploadTicket> initiate(@PathVariable UUID courseId, @PathVariable UUID lessonId,
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UploadRequest upload) {
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store")
                .body(media.initiate(courseId, lessonId, UUID.fromString(jwt.getSubject()), upload));
    }

    @PostMapping("/{contentId}/confirm")
    public Confirmation confirm(@PathVariable UUID courseId, @PathVariable UUID lessonId, @PathVariable UUID contentId) {
        return media.confirm(courseId, lessonId, contentId);
    }
}
