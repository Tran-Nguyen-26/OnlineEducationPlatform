package com.oep.assessment.controller;

import com.oep.assessment.dto.QuizGradingDtos;
import com.oep.assessment.service.QuizGradingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class QuizGradingController {

    private final QuizGradingService service;

    private static UUID uid(Jwt j) { return UUID.fromString(j.getSubject()); }
    private static String role(Jwt j) { return j.getClaimAsString("role"); }

    @GetMapping("/api/attempts/pending-essays")
    public List<QuizGradingDtos.PendingEssay> pendingEssays(@AuthenticationPrincipal Jwt jwt) {
        return service.pendingEssays(uid(jwt), role(jwt));
    }

    @PostMapping("/api/attempts/answers/{answerId}/grade")
    public ResponseEntity<Void> gradeEssay(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID answerId,
                                           @Valid @RequestBody QuizGradingDtos.GradeEssayRequest req) {
        service.gradeEssay(answerId, uid(jwt), role(jwt), req);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/attempts/{attemptId}/score")
    public ResponseEntity<Void> adjustScore(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId,
                                            @Valid @RequestBody QuizGradingDtos.AdjustScoreRequest req) {
        service.adjustScore(attemptId, uid(jwt), role(jwt), req);
        return ResponseEntity.noContent().build();
    }
}
