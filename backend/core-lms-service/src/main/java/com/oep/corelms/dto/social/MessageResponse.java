package com.oep.corelms.dto.social;

import com.oep.coremodel.enums.UserRole;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(UUID id, UUID senderId, String senderName, UserRole senderRole, UUID lessonId, String content, Instant createdAt) {
}
