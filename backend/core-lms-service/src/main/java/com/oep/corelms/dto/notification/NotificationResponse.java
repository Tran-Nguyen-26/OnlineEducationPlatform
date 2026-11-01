package com.oep.corelms.dto.notification;

import com.oep.coremodel.entity.notification.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String eventType,
        String title,
        String message,
        String targetType,
        UUID targetId,
        String actionUrl,
        String metadata,
        boolean isRead,
        Instant readAt,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getEventType(),
                n.getTitle(),
                n.getMessage(),
                n.getTargetType(),
                n.getTargetId(),
                n.getActionUrl(),
                n.getMetadata(),
                n.isRead(),
                n.getReadAt(),
                n.getCreatedAt()
        );
    }
}
