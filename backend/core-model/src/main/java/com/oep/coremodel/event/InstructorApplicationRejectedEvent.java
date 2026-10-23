package com.oep.coremodel.event;

import java.time.Instant;
import java.util.UUID;

public record InstructorApplicationRejectedEvent(
        UUID applicationId,
        String recipientEmail,
        String fullName,
        String rejectionReason,
        Instant occurredAt
) implements DomainEvent {}
