package com.oep.corelms.controller;

import com.oep.corelms.service.AdminCourseIssueService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/course-issues")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCourseIssueController {
    private final AdminCourseIssueService issues;

    public record DecisionRequest(@NotNull AdminCourseIssueService.Decision decision,
                                  @NotBlank @Size(min = 3, max = 2000) String resolution) {}

    @GetMapping
    public List<AdminCourseIssueService.Issue> list(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestParam(required = false) String status) {
        return issues.list(actor(jwt), status);
    }

    @PatchMapping("/{issueId}")
    @ResponseStatus(HttpStatus.OK)
    public AdminCourseIssueService.Issue decide(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID issueId,
                                                 @Valid @RequestBody DecisionRequest request) {
        return issues.decide(actor(jwt), issueId, request.decision(), request.resolution());
    }

    private UUID actor(Jwt jwt) {
        if (jwt == null) throw new AccessDeniedException("Authentication required");
        return UUID.fromString(jwt.getSubject());
    }
}
