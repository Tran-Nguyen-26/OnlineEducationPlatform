package com.oep.assessment.controller;

import com.oep.assessment.dto.AppealDtos;
import com.oep.assessment.service.AppealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/appeals")
@RequiredArgsConstructor
public class AppealController {

    private final AppealService service;

    private static UUID uid(Jwt j) { return UUID.fromString(j.getSubject()); }

    @PostMapping
    public AppealDtos.AppealResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AppealDtos.CreateRequest req) {
        return service.create(uid(jwt), req);
    }

    @GetMapping("/mine")
    public List<AppealDtos.AppealResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return service.mine(uid(jwt));
    }

    /** Appeals for the instructor's assigned courses (or all, for admin). */
    @GetMapping
    public List<AppealDtos.AppealResponse> forStaff(@AuthenticationPrincipal Jwt jwt) {
        return service.forStaff(uid(jwt), jwt.getClaimAsString("role"));
    }

    @PostMapping("/{id}/resolve")
    public AppealDtos.AppealResponse resolve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody AppealDtos.ResolveRequest req) {
        return service.resolve(id, uid(jwt), jwt.getClaimAsString("role"), req);
    }
}
