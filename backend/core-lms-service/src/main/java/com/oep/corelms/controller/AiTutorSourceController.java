package com.oep.corelms.controller;

import com.oep.corelms.security.AiTutorIndexerAssertionVerifier;
import com.oep.corelms.service.AiTutorSourceReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Private M2 source snapshot for M7 ingestion; absent from the public gateway. */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "oep.ai-tutor", name = "enabled", havingValue = "true")
public class AiTutorSourceController {
    private final AiTutorIndexerAssertionVerifier verifier;
    private final AiTutorSourceReadService sources;

    @GetMapping("/internal/v1/ai/sources/{contentId}")
    public AiTutorSourceReadService.Source read(
            @PathVariable UUID contentId,
            @RequestParam("course_id") UUID courseId,
            @RequestParam("lesson_id") UUID lessonId,
            @RequestParam("version_number") int versionNumber,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        verifier.verify(authorization, requestId);
        return sources.read(contentId, courseId, lessonId, versionNumber);
    }
}
