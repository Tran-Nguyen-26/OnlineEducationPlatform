package com.oep.corelms.dto.auth;

import java.util.UUID;

public record MeResponse(
        UUID id,
        String email,
        String role,
        String fullName,
        boolean mustChangePassword,
        String accountStatus) {
}
