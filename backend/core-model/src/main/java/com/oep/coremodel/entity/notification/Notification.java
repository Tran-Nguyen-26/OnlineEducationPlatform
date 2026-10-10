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
@Table(name = "notifications", indexes = {@Index(name = "idx_notifications_user_id_is_read_created_at", columnList = "user_id, is_read, created_at")})
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "dedupe_key", nullable = false, length = 255, unique = true)
    private String dedupeKey;

    @Column(name = "target_type", nullable = true, length = 50)
    private String targetType;

    @Column(name = "target_id", nullable = true)
    private UUID targetId;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "read_at", nullable = true)
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
