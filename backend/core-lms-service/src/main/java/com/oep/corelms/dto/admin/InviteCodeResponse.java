package com.oep.corelms.dto.admin;

import com.oep.coremodel.entity.registration.CourseInviteCode;

import java.time.Instant;
import java.util.UUID;

public record InviteCodeResponse(UUID id, String code, Instant expiresAt, boolean active, Instant createdAt, Integer maxUses, int usedCount) {
    public static InviteCodeResponse from(CourseInviteCode code) {
        return new InviteCodeResponse(code.getId(), code.getCode(), code.getExpiresAt(), code.isActive(), code.getCreatedAt(),
                code.getMaxUses(), code.getUsedCount());
    }
}
