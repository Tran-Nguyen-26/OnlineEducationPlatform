package com.oep.coremodel.event;

import java.time.Instant;
import java.util.UUID;

public record QuizPublishedEvent(UUID quizId, UUID courseId, String title, Instant occurredAt)
        implements DomainEvent {}
