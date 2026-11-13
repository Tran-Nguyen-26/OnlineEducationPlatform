package com.oep.coremodel.entity.enrollment;

import com.oep.coremodel.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "access_codes", indexes = @Index(name = "idx_access_codes_batch", columnList = "batch_id"))
public class AccessCode extends BaseEntity {
    public enum Kind { ACTIVE, EXTENSION }
    public enum Status { AVAILABLE, USED, REVOKED, EXPIRED }

    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    private String codeHash;
    @Column(name = "batch_id", nullable = false)
    private UUID batchId;
    @Column(name = "course_id", nullable = false)
    private UUID courseId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;
    @Column(name = "access_days", nullable = false)
    private int accessDays;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "used_by")
    private UUID usedBy;
    @Column(name = "used_at")
    private Instant usedAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    public Status statusAt(Instant now) {
        if (usedAt != null) return Status.USED;
        if (revokedAt != null) return Status.REVOKED;
        return now.isBefore(expiresAt) ? Status.AVAILABLE : Status.EXPIRED;
    }
}
