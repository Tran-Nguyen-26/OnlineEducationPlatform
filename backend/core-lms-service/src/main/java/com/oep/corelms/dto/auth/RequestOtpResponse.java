package com.oep.corelms.dto.auth;

public record RequestOtpResponse(
        String message,
        String emailMasked,
        long expiresInSeconds,
        long cooldownSeconds
) {}
