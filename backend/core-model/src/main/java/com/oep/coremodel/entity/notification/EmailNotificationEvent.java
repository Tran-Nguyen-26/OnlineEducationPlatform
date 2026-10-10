package com.oep.coremodel.entity.notification;

import com.oep.coremodel.entity.BaseEntity;
import com.oep.coremodel.entity.identity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import com.oep.coremodel.enums.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "email_notification_events")
public class EmailNotificationEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "event_type", nullable = true, length = 100)
    private String eventType;

    @Column(name = "recipient_email", nullable = true, length = 255)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private NotificationDeliveryStatus status = NotificationDeliveryStatus.PENDING;

    @Column(name = "scheduled_at", nullable = true)
    private Instant scheduledAt;

    @Column(name = "sent_at", nullable = true)
    private Instant sentAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;
}
