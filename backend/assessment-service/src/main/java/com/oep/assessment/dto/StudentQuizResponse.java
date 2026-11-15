package com.oep.assessment.dto;

import com.oep.coremodel.enums.QuizGradingPolicy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StudentQuizResponse(
        UUID id,
        UUID courseId,
        UUID lessonId,
        String title,
        String description,
        Integer timeLimitSeconds,
        Instant deadline,
        int maxAttempts,
        QuizGradingPolicy gradingPolicy,
        boolean locked,
        int attemptsUsed,
        boolean canAttempt,
        BigDecimal bestScore) {
}
