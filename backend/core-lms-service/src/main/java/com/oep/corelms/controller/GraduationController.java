package com.oep.corelms.controller;

import com.oep.corelms.service.GraduationService;
import com.oep.corelms.service.GraduationService.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/graduation")
@RequiredArgsConstructor
public class GraduationController {
    private final GraduationService graduation;
    private final com.oep.corelms.service.OutcomeMasteryService outcomes;
    private final com.oep.corelms.service.AdaptiveRemediationService remediation;

    public record DrillAnswer(@jakarta.validation.constraints.NotNull UUID selectedOptionId,
                              @jakarta.validation.constraints.NotNull com.oep.corelms.service.AdaptiveRemediationService.Rating rating) {}

    @GetMapping("/outcomes")
    @PreAuthorize("hasRole('STUDENT')")
    public java.util.List<com.oep.corelms.service.OutcomeMasteryService.Outcome> outcomes(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return outcomes.forLearner(UUID.fromString(jwt.getSubject()), courseId);
    }

    @GetMapping("/remediation")
    @PreAuthorize("hasRole('STUDENT')")
    public com.oep.corelms.service.AdaptiveRemediationService.Plan remediation(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return remediation.plan(UUID.fromString(jwt.getSubject()), courseId);
    }

    @PostMapping("/remediation/{questionId}/answer")
    @PreAuthorize("hasRole('STUDENT')")
    public com.oep.corelms.service.AdaptiveRemediationService.ReviewResult answerDrill(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId, @PathVariable UUID questionId,
            @Valid @RequestBody DrillAnswer request) {
        return remediation.answer(UUID.fromString(jwt.getSubject()), courseId, questionId,
                request.selectedOptionId(), request.rating());
    }

    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    public Progress get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return graduation.get(UUID.fromString(jwt.getSubject()), courseId);
    }

    @PostMapping("/survey")
    @PreAuthorize("hasRole('STUDENT')")
    public Progress survey(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId, @Valid @RequestBody Survey survey) {
        return graduation.submitSurvey(UUID.fromString(jwt.getSubject()), courseId, survey);
    }

    @GetMapping("/threshold")
    @PreAuthorize("hasRole('ADMIN')")
    public Threshold threshold(@PathVariable UUID courseId) {
        return graduation.threshold(courseId);
    }

    @PutMapping("/threshold")
    @PreAuthorize("hasRole('ADMIN')")
    public Threshold updateThreshold(@PathVariable UUID courseId, @Valid @RequestBody Threshold threshold) {
        return graduation.updateThreshold(courseId, threshold);
    }
}
