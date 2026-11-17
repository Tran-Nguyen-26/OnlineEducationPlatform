package com.oep.coremodel.event;

import java.time.Instant;
import java.util.UUID;

/** Emitted (through the outbox) when a lesson video upload is confirmed; worker-service turns it into an HLS ladder. */
public record VideoTranscodeRequestedEvent(UUID contentId, String objectKey, Instant requestedAt) {
}
