package com.oep.assessment.dto;

import com.oep.coremodel.enums.QuizAttemptStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AttemptSummaryResponse(UUID attemptId, int attemptNumber, QuizAttemptStatus status, Instant submittedAt, BigDecimal score) {
}
