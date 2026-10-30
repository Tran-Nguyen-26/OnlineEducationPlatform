package com.oep.coremodel.event;

import java.time.Instant;
import java.util.UUID;

public record InstructorApplicationApprovedEvent(
        UUID applicationId,
        UUID userId,
        String recipientEmail,
        String fullName,
        String temporaryPassword,
        String loginUrl,
        Instant occurredAt
) implements DomainEvent {}
