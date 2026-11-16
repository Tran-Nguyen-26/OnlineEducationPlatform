package com.oep.assessment.controller;

import com.oep.assessment.dto.GradebookResponse;
import com.oep.assessment.dto.PendingGradingResponse;
import com.oep.assessment.service.GradebookService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class GradebookController {

    private final GradebookService gradebookService;

    @GetMapping("/api/gradebook/{courseId}")
    public GradebookResponse gradebook(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return gradebookService.gradebook(courseId, UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role"));
    }

    @GetMapping("/api/gradebook/pending-grading")
    public List<PendingGradingResponse> pendingGrading(@AuthenticationPrincipal Jwt jwt) {
        return gradebookService.pendingGrading(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role"));
    }
}
