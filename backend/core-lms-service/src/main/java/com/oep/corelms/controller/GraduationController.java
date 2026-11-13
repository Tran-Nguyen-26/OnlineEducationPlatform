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
