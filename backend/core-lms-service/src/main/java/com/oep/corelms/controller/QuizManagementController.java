package com.oep.corelms.controller;

import com.oep.corelms.dto.quiz.QuizDtos;
import com.oep.corelms.service.QuizManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}")
@RequiredArgsConstructor
public class QuizManagementController {

    private final QuizManagementService quizService;

    // ==========================================
    // QUIZ ENDPOINTS
    // ==========================================

    @GetMapping("/quizzes")
    public QuizDtos.QuizPage listQuizzes(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        return quizService.listQuizzes(userId(jwt), courseId, page, pageSize);
    }

    @PostMapping("/quizzes")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizDtos.QuizResponse createQuiz(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId,
            @Valid @RequestBody QuizDtos.CreateQuizRequest request
    ) {
        return quizService.createQuiz(userId(jwt), courseId, request);
    }

    @GetMapping("/quizzes/{quizId}")
    public QuizDtos.QuizResponse getQuiz(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId,
            @PathVariable UUID quizId
    ) {
        return quizService.getQuiz(userId(jwt), courseId, quizId);
    }

    @PatchMapping("/quizzes/{quizId}")
    public QuizDtos.QuizResponse updateQuiz(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @Valid @RequestBody QuizDtos.UpdateQuizRequest request
    ) {
        return quizService.updateQuiz(userId(jwt), courseId, quizId, request);
    }

    @DeleteMapping("/quizzes/{quizId}")
    public QuizDtos.DeleteResponse deleteQuiz(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId,
            @PathVariable UUID quizId
    ) {
        return quizService.deleteQuiz(userId(jwt), courseId, quizId);
    }

    // ==========================================
    // QUESTION BANK ENDPOINTS
    // ==========================================

    @GetMapping("/question-banks")
    public QuizDtos.BankPage listQuestionBanks(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        return quizService.listQuestionBanks(userId(jwt), courseId, page, pageSize);
    }

    @PostMapping("/question-banks")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizDtos.BankResponse createQuestionBank(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId,
            @Valid @RequestBody QuizDtos.CreateBankRequest request
    ) {
        return quizService.createQuestionBank(userId(jwt), courseId, request);
    }

    @GetMapping("/question-banks/{bankId}")
    public QuizDtos.BankResponse getQuestionBank(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId,
            @PathVariable UUID bankId
    ) {
        return quizService.getQuestionBank(userId(jwt), courseId, bankId);
    }

    @PatchMapping("/question-banks/{bankId}")
    public QuizDtos.BankResponse updateQuestionBank(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId,
            @PathVariable UUID bankId,
            @Valid @RequestBody QuizDtos.UpdateBankRequest request
    ) {
        return quizService.updateQuestionBank(userId(jwt), courseId, bankId, request);
    }

    @DeleteMapping("/question-banks/{bankId}")
    public QuizDtos.DeleteResponse deleteQuestionBank(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId,
            @PathVariable UUID bankId
    ) {
        return quizService.deleteQuestionBank(userId(jwt), courseId, bankId);
    }

    // ==========================================
    // QUESTION & OPTIONS ENDPOINTS
    // ==========================================

    @GetMapping("/quizzes/{quizId}/questions")
    public QuizDtos.QuestionPage listQuestions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        return quizService.listQuestions(userId(jwt), courseId, quizId, page, pageSize);
    }

    @PostMapping("/quizzes/{quizId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizDtos.QuestionResponse createQuestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @Valid @RequestBody QuizDtos.CreateQuestionRequest request
    ) {
        return quizService.createQuestion(userId(jwt), courseId, quizId, request);
    }

    @GetMapping("/quizzes/{quizId}/questions/{questionId}")
    public QuizDtos.QuestionResponse getQuestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @PathVariable UUID questionId
    ) {
        return quizService.getQuestion(userId(jwt), courseId, quizId, questionId);
    }

    @PatchMapping("/quizzes/{quizId}/questions/{questionId}")
    public QuizDtos.QuestionResponse updateQuestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @PathVariable UUID questionId,
            @Valid @RequestBody QuizDtos.UpdateQuestionRequest request
    ) {
        return quizService.updateQuestion(userId(jwt), courseId, quizId, questionId, request);
    }

    @DeleteMapping("/quizzes/{quizId}/questions/{questionId}")
    public QuizDtos.DeleteResponse deleteQuestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @PathVariable UUID questionId
    ) {
        return quizService.deleteQuestion(userId(jwt), courseId, quizId, questionId);
    }

    @PostMapping("/quizzes/{quizId}/questions/reorder")
    public QuizDtos.ReorderResponse reorderQuestions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @Valid @RequestBody QuizDtos.ReorderRequest request
    ) {
        return quizService.reorderQuestions(userId(jwt), courseId, quizId, request);
    }

    // ==========================================
    // RESULT & ADJUSTMENT ENDPOINTS
    // ==========================================

    @GetMapping("/quizzes/{quizId}/results")
    public QuizDtos.ResultPage listResults(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        return quizService.listResults(userId(jwt), courseId, quizId, page, pageSize);
    }

    @PostMapping("/quizzes/{quizId}/results/{resultId}/adjust-score")
    public QuizDtos.QuizResultResponse adjustScore(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @PathVariable UUID resultId,
            @Valid @RequestBody QuizDtos.AdjustScoreRequest request
    ) {
        return quizService.adjustScore(userId(jwt), courseId, quizId, resultId, request);
    }

    private UUID userId(Jwt jwt) {
        if (jwt == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return UUID.fromString(jwt.getSubject());
    }
}
