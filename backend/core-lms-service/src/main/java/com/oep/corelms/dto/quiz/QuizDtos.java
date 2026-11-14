package com.oep.corelms.dto.quiz;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oep.coremodel.enums.QuestionType;
import com.oep.coremodel.enums.QuizGradingPolicy;
import com.oep.coremodel.entity.course.LearningOutcome;
import com.oep.coremodel.entity.quiz.Quiz;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class QuizDtos {

    private QuizDtos() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PageInfo(int page, int pageSize, long total, boolean hasNext) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DeleteResponse(boolean success, UUID deletedId) {}

    // --- Quiz DTOs ---

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateQuizRequest(
            @NotBlank @Size(max = 255) String title,
            String description,
            UUID lessonId,
            String questionSource,
            Integer timeLimitSeconds,
            Instant deadline,
            @Min(1) Integer maxAttempts,
            QuizGradingPolicy gradingPolicy,
            Boolean randomizeQuestions,
            Boolean randomizeAnswers,
            Boolean showScore,
            Boolean showCorrectAnswers,
            Quiz.Kind assessmentKind
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UpdateQuizRequest(
            @Size(min = 1, max = 255) String title,
            String description,
            UUID lessonId,
            String questionSource,
            Integer timeLimitSeconds,
            Instant deadline,
            @Min(1) Integer maxAttempts,
            QuizGradingPolicy gradingPolicy,
            Boolean randomizeQuestions,
            Boolean randomizeAnswers,
            Boolean showScore,
            Boolean showCorrectAnswers,
            Quiz.Kind assessmentKind
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record QuizResponse(
            UUID id,
            UUID courseId,
            UUID lessonId,
            String title,
            String description,
            String questionSource,
            Integer timeLimitSeconds,
            Instant deadline,
            int maxAttempts,
            QuizGradingPolicy gradingPolicy,
            Boolean randomizeQuestions,
            Boolean randomizeAnswers,
            Boolean showScore,
            Boolean showCorrectAnswers,
            boolean isLocked,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt,
            Quiz.Kind assessmentKind
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record QuizPage(List<QuizResponse> items, PageInfo pagination) {}

    // --- Question Bank DTOs ---

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateBankRequest(@NotBlank @Size(max = 255) String name) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UpdateBankRequest(@NotBlank @Size(max = 255) String name) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BankResponse(
            UUID id,
            UUID courseId,
            String name,
            UUID createdBy,
            Instant createdAt
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BankList(List<BankResponse> items) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BankPage(List<BankResponse> items, PageInfo pagination) {}

    // --- Question & Option DTOs ---

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record OptionRequest(
            @NotBlank String optionText,
            boolean isCorrect,
            Integer position
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record OptionResponse(
            UUID id,
            String optionText,
            boolean isCorrect,
            int position
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateQuestionRequest(
            @NotNull QuestionType questionType,
            @NotBlank String questionText,
            String keywordAnswer,
            UUID questionBankId,
            UUID learningOutcomeId,
            LearningOutcome.Bloom bloomLevel,
            BigDecimal points,
            Integer position,
            List<OptionRequest> options
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UpdateQuestionRequest(
            QuestionType questionType,
            String questionText,
            String keywordAnswer,
            UUID questionBankId,
            UUID learningOutcomeId,
            LearningOutcome.Bloom bloomLevel,
            BigDecimal points,
            Integer position,
            List<OptionRequest> options,
            boolean clearQuestionBank,
            boolean clearLearningOutcome
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record QuestionResponse(
            UUID id,
            UUID questionBankId,
            UUID learningOutcomeId,
            LearningOutcome.Bloom bloomLevel,
            QuestionType questionType,
            String questionText,
            String keywordAnswer,
            BigDecimal points,
            Integer position,
            List<OptionResponse> options,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record QuestionOutcomeResponse(UUID id, String code, String description, LearningOutcome.Bloom bloom) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record QuestionPage(List<QuestionResponse> items, PageInfo pagination) {}

    // --- Reorder DTOs ---

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReorderItem(
            @NotNull UUID questionId,
            @NotNull Integer newPosition
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReorderRequest(
            List<UUID> orderedIds,
            List<ReorderItem> items
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReorderResponse(boolean success, int reorderedCount, List<UUID> orderedIds) {}

    // --- Result & Adjustment DTOs ---

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record QuizResultResponse(
            UUID id,
            UUID attemptId,
            BigDecimal autoScore,
            BigDecimal finalScore,
            UUID adjustedBy,
            String adjustmentReason,
            Instant createdAt,
            Instant updatedAt
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ResultPage(List<QuizResultResponse> items, PageInfo pagination) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AdjustScoreRequest(
            @NotNull BigDecimal finalScore,
            @NotBlank String adjustmentReason
    ) {}

    // --- Admin DTOs ---

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AdminQuizSummary(
            UUID id,
            UUID courseId,
            String courseTitle,
            String title,
            boolean isLocked,
            long totalAttempts,
            Instant createdAt
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AdminQuizPage(List<AdminQuizSummary> items, PageInfo pagination) {}
}
