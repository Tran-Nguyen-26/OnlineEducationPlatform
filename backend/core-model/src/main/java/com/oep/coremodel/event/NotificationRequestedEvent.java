package com.oep.coremodel.event;

import java.time.Instant;
import java.util.UUID;

/** dedupeKey maps to notifications.dedupe_key, so redelivered messages never create duplicates. */
public record NotificationRequestedEvent(UUID userId, String eventType, String title, String message,
                                         String dedupeKey, String targetType, UUID targetId,
                                         String actionUrl, String metadata,
                                         Instant occurredAt) implements DomainEvent {}
