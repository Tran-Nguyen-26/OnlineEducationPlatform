package com.oep.corelms.dto.admin;

import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.UserRole;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        UserRole role,
        AccountStatus accountStatus,
        String fullName,
        Instant createdAt) {
}
