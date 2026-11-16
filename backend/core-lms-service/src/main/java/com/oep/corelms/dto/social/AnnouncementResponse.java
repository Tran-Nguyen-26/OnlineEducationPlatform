package com.oep.corelms.dto.social;

import java.time.Instant;
import java.util.UUID;

public record AnnouncementResponse(UUID id, String title, String content, String authorName, String authorRole, Instant createdAt) {
}
