package com.oep.corelms.controller;

import com.oep.corelms.service.LearnerQuizService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/learner-assessments")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class LearnerQuizController {
    private final LearnerQuizService quizzes;

    public record Submission(@NotNull @Size(max = 500)
            Map<UUID, @NotNull @Size(max = 32) List<@NotNull @Size(max = 500) String>> answers) {}

    @GetMapping("/quizzes")
    public List<LearnerQuizService.AvailableQuiz> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return quizzes.list(UUID.fromString(jwt.getSubject()), courseId);
    }

    @PostMapping("/quizzes/{quizId}/attempts")
    public ResponseEntity<LearnerQuizService.Start> start(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId, @PathVariable UUID quizId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(quizzes.start(UUID.fromString(jwt.getSubject()), courseId, quizId));
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public ResponseEntity<LearnerQuizService.Score> submit(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId, @PathVariable UUID attemptId, @Valid @RequestBody Submission submission) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(quizzes.submit(UUID.fromString(jwt.getSubject()), courseId, attemptId, submission.answers()));
    }
}
