package com.oep.assessment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class QuizGradingDtos {
    private QuizGradingDtos() {}

    public record PendingEssay(UUID answerId, UUID attemptId, UUID quizId, String quizTitle, UUID courseId, String courseTitle,
                               String studentName, String studentEmail, String questionText, BigDecimal maxPoints,
                               String textAnswer, Instant submittedAt) {}

    public record GradeEssayRequest(@NotNull @DecimalMin("0.00") BigDecimal score, String feedback) {}

    public record AdjustScoreRequest(@NotNull @DecimalMin("0.00") BigDecimal finalScore, String reason) {}
}
