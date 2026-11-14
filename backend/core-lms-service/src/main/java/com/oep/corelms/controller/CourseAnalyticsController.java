package com.oep.corelms.controller;

import com.oep.corelms.service.CourseAnalyticsService;
import com.oep.corelms.service.CourseAnalyticsService.Analytics;
import com.oep.corelms.service.CourseAnalyticsService.IssueCategory;
import com.oep.corelms.service.CourseAnalyticsService.RewindBucket;
import com.oep.corelms.service.CourseAnalyticsService.Misconception;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/courses/{course}/analytics")
@RequiredArgsConstructor
public class CourseAnalyticsController {
    private final CourseAnalyticsService service;

    public record FlagRequest(@NotNull IssueCategory category, @NotBlank @Size(max = 1000) String detail) {}

    @GetMapping
    public Analytics analytics(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course) {
        return service.analytics(UUID.fromString(jwt.getSubject()), course);
    }

    @GetMapping("/rewind-heatmap")
    public List<RewindBucket> rewindHeatmap(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course) {
        return service.rewindHeatmap(UUID.fromString(jwt.getSubject()), course);
    }

    @GetMapping("/misconception-radar")
    public List<Misconception> misconceptionRadar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course) {
        return service.misconceptionRadar(UUID.fromString(jwt.getSubject()), course);
    }

    @GetMapping(value = "/report", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> report(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course) {
        String csv = CourseAnalyticsService.csv(service.analytics(UUID.fromString(jwt.getSubject()), course));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"course-analytics.csv\"").body(csv);
    }

    @PostMapping("/issues")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void flag(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course,
                     @Valid @RequestBody FlagRequest request) {
        service.flag(UUID.fromString(jwt.getSubject()), course, request.category(), request.detail());
    }
}
