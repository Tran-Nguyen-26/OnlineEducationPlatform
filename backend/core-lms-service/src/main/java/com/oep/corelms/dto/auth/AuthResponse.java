package com.oep.corelms.dto.auth;

import com.oep.coremodel.enums.UserRole;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserSummary user
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn, UserSummary user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }

    public record UserSummary(
            UUID id,
            String email,
            String fullName,
            UserRole role,
            boolean mustChangePassword
    ) {}
}
