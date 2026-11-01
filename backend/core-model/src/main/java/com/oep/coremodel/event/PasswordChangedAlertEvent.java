package com.oep.coremodel.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after a successful password change.
 * Worker service consumes this to send the security-alert email.
 */
public record PasswordChangedAlertEvent(
        UUID userId,
        String recipientEmail,
        String fullName,
        String ipAddress,
        String userAgent,
        Instant occurredAt
) implements DomainEvent {}
