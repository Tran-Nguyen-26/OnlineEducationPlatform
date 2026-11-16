package com.oep.corelms.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID actorId,
        String actorEmail,
        String action,
        String targetType,
        UUID targetId,
        String metadata,
        Instant createdAt) {
}
