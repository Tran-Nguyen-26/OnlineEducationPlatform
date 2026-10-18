package com.oep.corelms.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PresignedUploadResponse(
        UUID contentId,
        String presignedUrl,
        String objectKey,
        Instant expiresAt
) {}
