package com.oep.assessment.controller;

import com.oep.assessment.dto.AttemptResultResponse;
import com.oep.assessment.dto.AttemptSummaryResponse;
import com.oep.assessment.dto.StartAttemptResponse;
import com.oep.assessment.dto.StudentQuizResponse;
import com.oep.assessment.dto.SubmitAttemptRequest;
import com.oep.assessment.service.QuizAttemptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class QuizAttemptController {

    private final QuizAttemptService quizAttemptService;

    @GetMapping("/api/quizzes")
    public List<StudentQuizResponse> listForCourse(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID courseId) {
        return quizAttemptService.listForCourse(courseId, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/api/quizzes/{quizId}/attempts")
    public List<AttemptSummaryResponse> myAttempts(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID quizId) {
        return quizAttemptService.listMyAttempts(quizId, UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/api/quizzes/{quizId}/attempts")
    public StartAttemptResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID quizId) {
        return quizAttemptService.startAttempt(quizId, UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/api/attempts/{attemptId}/submit")
    public AttemptResultResponse submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId,
                                         @Valid @RequestBody SubmitAttemptRequest request) {
        return quizAttemptService.submitAttempt(attemptId, UUID.fromString(jwt.getSubject()), request);
    }
}
