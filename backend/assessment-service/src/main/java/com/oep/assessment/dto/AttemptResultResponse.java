package com.oep.assessment.dto;

import com.oep.coremodel.enums.QuizAttemptStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AttemptResultResponse(
        UUID attemptId,
        int attemptNumber,
        QuizAttemptStatus status,
        Instant submittedAt,
        BigDecimal score,
        List<QuestionResult> questionResults,
        int pendingManual) {

    public record QuestionResult(UUID questionId, Boolean correct, BigDecimal awardedScore) {
    }
}
