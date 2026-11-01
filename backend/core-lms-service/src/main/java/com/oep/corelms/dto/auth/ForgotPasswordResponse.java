package com.oep.corelms.dto.auth;

public record ForgotPasswordResponse(
        String message,
        String emailMasked,
        long cooldownSeconds,
        long expiresInSeconds
) {}
