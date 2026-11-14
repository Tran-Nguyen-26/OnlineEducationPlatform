package com.oep.corelms.controller;

import com.oep.corelms.service.LearnerQuizService;
import com.oep.corelms.service.MistakeExplanationService;
import com.oep.corelms.dto.ai.MistakeExplanationResponse;
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
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/learner-assessments")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class LearnerQuizController {
    private final LearnerQuizService quizzes;
    private final MistakeExplanationService mistakeExplanations;

    public record Submission(@NotNull @Size(max = 500)
            Map<UUID, @NotNull @Size(max = 32) List<@NotNull @Size(max = 500) String>> answers) {}
    public record Delta(@jakarta.validation.constraints.Positive long sequence,
            @NotNull @Size(max = 500) Map<UUID, @NotNull @Size(max = 32) List<@NotNull @Size(max = 500) String>> answers) {}
    public record DraftSyncAck(long sequence, Instant serverTime) {}

    @GetMapping("/quizzes")
    public List<LearnerQuizService.AvailableQuiz> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return quizzes.list(UUID.fromString(jwt.getSubject()), courseId);
    }

    @PostMapping("/quizzes/{quizId}/attempts")
    public ResponseEntity<LearnerQuizService.Start> start(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId, @PathVariable UUID quizId,
            @RequestHeader(value = "X-Exam-Device", required = false) UUID device) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(quizzes.start(UUID.fromString(jwt.getSubject()), courseId, quizId, device));
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public ResponseEntity<LearnerQuizService.Score> submit(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId, @PathVariable UUID attemptId, @Valid @RequestBody Submission submission,
            @RequestHeader(value = "X-Exam-Device", required = false) UUID device) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(quizzes.submit(UUID.fromString(jwt.getSubject()), courseId, attemptId, submission.answers(), device));
    }

    @PutMapping("/attempts/{attemptId}/draft")
    public DraftSyncAck sync(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
            @PathVariable UUID attemptId, @RequestHeader("X-Exam-Device") UUID device, @Valid @RequestBody Delta delta) {
        long sequence = quizzes.sync(UUID.fromString(jwt.getSubject()), courseId, attemptId, device,
                delta.sequence(), delta.answers());
        return new DraftSyncAck(sequence, Instant.now());
    }

    @PostMapping("/attempts/{attemptId}/questions/{questionId}/options/{optionId}/mistake-explanation")
    public ResponseEntity<MistakeExplanationResponse> explainMistake(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId, @PathVariable UUID attemptId, @PathVariable UUID questionId,
            @PathVariable UUID optionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mistakeExplanations.explain(
                UUID.fromString(jwt.getSubject()), courseId, attemptId, questionId, optionId));
    }

    @PostMapping("/quizzes/{quizId}/transfer-device")
    public ResponseEntity<LearnerQuizService.Start> transfer(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId, @PathVariable UUID quizId, @RequestHeader("X-Exam-Device") UUID device,
            jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(quizzes.transferDevice(
                UUID.fromString(jwt.getSubject()), courseId, quizId, device, request.getRemoteAddr(), request.getHeader("User-Agent")));
    }
}
