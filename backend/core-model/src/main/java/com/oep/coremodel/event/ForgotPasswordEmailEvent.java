package com.oep.coremodel.event;

import java.time.Instant;
import java.util.UUID;

public record ForgotPasswordEmailEvent(
        UUID userId,
        String eventType,
        String recipientEmail,
        String fullName,
        String otpCode,
        String actionTime,
        Instant occurredAt
) implements DomainEvent {}
