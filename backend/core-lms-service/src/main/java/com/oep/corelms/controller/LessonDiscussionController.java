package com.oep.corelms.controller;

import com.oep.corelms.service.LessonDiscussionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/courses/{course}")
public class LessonDiscussionController {
    private final LessonDiscussionService service;
    public record Ask(@NotNull UUID videoId, @NotNull @PositiveOrZero Integer timestampSec, @NotBlank @Size(max = 4000) String body) {}
    public record Answer(@NotBlank @Size(max = 4000) String body) {}

    @GetMapping("/discussions")
    public List<LessonDiscussionService.LessonLink> lessons(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course) {
        return service.lessons(UUID.fromString(jwt.getSubject()), course);
    }
    @GetMapping("/lessons/{lesson}/discussion")
    public LessonDiscussionService.ThreadPage list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course,
            @PathVariable UUID lesson, @RequestParam(defaultValue = "0") @Min(0) int page) {
        return service.list(UUID.fromString(jwt.getSubject()), course, lesson, page);
    }
    @PostMapping("/lessons/{lesson}/discussion")
    public Map<String, UUID> ask(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course, @PathVariable UUID lesson,
            @Valid @RequestBody Ask request) {
        return Map.of("id", service.ask(UUID.fromString(jwt.getSubject()), course, lesson,
                request.videoId(), request.timestampSec(), request.body()));
    }
    @GetMapping("/lessons/{lesson}/discussion/{question}/replies")
    public List<LessonDiscussionService.Reply> replies(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course,
            @PathVariable UUID lesson, @PathVariable UUID question, @RequestParam(defaultValue = "0") @Min(0) int page) {
        return service.replies(UUID.fromString(jwt.getSubject()), course, lesson, question, page);
    }
    @GetMapping("/lessons/{lesson}/discussion/suggestions")
    public List<LessonDiscussionService.VerifiedSuggestion> suggestions(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID course, @PathVariable UUID lesson, @RequestParam String query) {
        return service.suggestions(UUID.fromString(jwt.getSubject()), course, lesson, query);
    }
    @PostMapping("/lessons/{lesson}/discussion/{question}/replies")
    public Map<String, UUID> reply(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course, @PathVariable UUID lesson,
            @PathVariable UUID question, @Valid @RequestBody Answer request) {
        return Map.of("id", service.reply(UUID.fromString(jwt.getSubject()), course, lesson, question, request.body()));
    }
    @PutMapping("/lessons/{lesson}/discussion/{question}/replies/{reply}/verify")
    public void verify(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course, @PathVariable UUID lesson,
            @PathVariable UUID question, @PathVariable UUID reply) {
        service.verify(UUID.fromString(jwt.getSubject()), course, lesson, question, reply);
    }
}
