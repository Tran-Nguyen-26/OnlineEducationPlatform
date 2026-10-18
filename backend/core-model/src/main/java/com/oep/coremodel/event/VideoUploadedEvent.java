package com.oep.coremodel.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Được publish bởi core-lms-service sau khi giảng viên xác nhận upload xong.
 * worker-service lắng nghe để bắt đầu pipeline transcode HLS.
 */
public record VideoUploadedEvent(
        UUID contentId,
        UUID jobId,
        String rawObjectKey,   // key trong bucket oep-raw
        String mimeType,
        long fileSize,
        Instant occurredAt
) implements DomainEvent {}
