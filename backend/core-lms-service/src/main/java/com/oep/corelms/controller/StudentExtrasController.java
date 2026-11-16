package com.oep.corelms.controller;

import com.oep.corelms.dto.student.CourseOverviewResponse;
import com.oep.corelms.service.CalendarService;
import com.oep.corelms.service.CourseOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class StudentExtrasController {

    private final CourseOverviewService overviewService;
    private final CalendarService calendarService;

    @GetMapping("/api/courses/{courseId}/overview")
    public CourseOverviewResponse overview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return overviewService.overview(courseId, UUID.fromString(jwt.getSubject()));
    }

    /** Defaults to the last 7 days through the next 60 days. */
    @GetMapping("/api/users/me/calendar")
    public List<CalendarService.Event> calendar(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Instant from,
                                                @RequestParam(required = false) Instant to) {
        Instant now = Instant.now();
        return calendarService.forStudent(UUID.fromString(jwt.getSubject()),
                from != null ? from : now.minus(7, ChronoUnit.DAYS), to != null ? to : now.plus(60, ChronoUnit.DAYS));
    }
}
