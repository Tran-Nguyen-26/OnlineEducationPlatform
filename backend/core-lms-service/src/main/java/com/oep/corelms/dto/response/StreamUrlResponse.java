package com.oep.corelms.dto.response;

import java.time.Instant;
import java.util.UUID;

public record StreamUrlResponse(
        UUID contentId,
        String streamUrl,
        String thumbnailUrl,
        String spriteSheetUrl,
        Integer durationSeconds,
        Instant expiresAt
) {}
