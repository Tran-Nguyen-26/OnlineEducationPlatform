package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.AddQuestionRequest;
import com.oep.corelms.dto.admin.QuizQuestionResponse;
import com.oep.corelms.service.AdminQuizQuestionService;
import com.oep.corelms.service.AuditLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/quizzes/{quizId}/questions")
@RequiredArgsConstructor
public class AdminQuizQuestionController {

    private final AdminQuizQuestionService quizQuestionService;
    private final AuditLogService auditLogService;

    @PostMapping
    public QuizQuestionResponse add(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID quizId,
                                     @Valid @RequestBody AddQuestionRequest request) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        QuizQuestionResponse response = quizQuestionService.addQuestion(quizId, adminId, request);
        auditLogService.record(adminId, "ADD_QUESTION", "QUIZ_QUESTION", response.questionId(), Map.of("quizId", quizId.toString()));
        return response;
    }

    @GetMapping
    public List<QuizQuestionResponse> list(@PathVariable UUID quizId) {
        return quizQuestionService.list(quizId);
    }

    @DeleteMapping("/{questionId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID quizId, @PathVariable UUID questionId) {
        quizQuestionService.removeQuestion(quizId, questionId);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "REMOVE_QUESTION", "QUIZ_QUESTION", questionId,
                Map.of("quizId", quizId.toString()));
        return ResponseEntity.noContent().build();
    }
}
