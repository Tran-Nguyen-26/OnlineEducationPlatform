package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.AddQuestionRequest;
import com.oep.corelms.dto.admin.QuizQuestionResponse;
import com.oep.corelms.service.AdminQuizQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/quizzes/{quizId}/questions")
@RequiredArgsConstructor
public class AdminQuizQuestionController {

    private final AdminQuizQuestionService quizQuestionService;

    @PostMapping
    public QuizQuestionResponse add(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID quizId,
                                     @Valid @RequestBody AddQuestionRequest request) {
        return quizQuestionService.addQuestion(quizId, UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping
    public List<QuizQuestionResponse> list(@PathVariable UUID quizId) {
        return quizQuestionService.list(quizId);
    }

    @DeleteMapping("/{questionId}")
    public ResponseEntity<Void> remove(@PathVariable UUID quizId, @PathVariable UUID questionId) {
        quizQuestionService.removeQuestion(quizId, questionId);
        return ResponseEntity.noContent().build();
    }
}
