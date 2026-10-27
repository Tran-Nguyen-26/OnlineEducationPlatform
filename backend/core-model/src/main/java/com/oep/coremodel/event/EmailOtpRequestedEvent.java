package com.oep.coremodel.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a user requests an OTP code to change their password.
 * Worker service consumes this to send the OTP email.
 */
public record EmailOtpRequestedEvent(
        UUID userId,
        String recipientEmail,
        String fullName,
        String otpCode,
        long expiresInSeconds,
        Instant occurredAt
) implements DomainEvent {}
