package com.oep.corelms.dto.auth;

public record ForgotPasswordVerifyOtpResponse(
        String resetToken,
        long expiresInSeconds,
        String message
) {}
