package com.oep.corelms.dto.auth;

public record RegistrationPendingResponse(String message, String maskedEmail, long otpTtlSeconds) {}
