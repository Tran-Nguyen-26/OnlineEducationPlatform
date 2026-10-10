package com.oep.coremodel.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Assessment service -> Worker: flush a finished attempt from Redis to PostgreSQL. */
public record ExamScoreSyncEvent(UUID attemptId, UUID quizId, UUID enrollmentId, BigDecimal score,
                                 Instant submittedAt, Instant occurredAt) implements DomainEvent {}
