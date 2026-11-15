package com.oep.corelms.dto.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        String role,
        boolean mustChangePassword) {
}
