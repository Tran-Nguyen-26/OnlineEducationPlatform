package com.oep.corelms.dto.admin;

import com.oep.coremodel.enums.QuizGradingPolicy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record UpdateQuizRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        UUID lessonId,
        @Positive Integer timeLimitSeconds,
        Instant deadline,
        @Positive Integer maxAttempts,
        QuizGradingPolicy gradingPolicy,
        Boolean randomizeQuestions,
        Boolean randomizeAnswers,
        Boolean showScore,
        Boolean showCorrectAnswers,
        @jakarta.validation.constraints.Positive Integer randomQuestionCount,
        Long version) {
}
