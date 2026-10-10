package com.oep.coremodel.entity.quiz;

import com.oep.coremodel.entity.BaseEntity;
import com.oep.coremodel.entity.enrollment.Enrollment;
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
@Table(name = "quiz_attempts", uniqueConstraints = {@UniqueConstraint(name = "uq_quiz_attempts_quiz_id_enrollment_id_attempt_number", columnNames = {"quiz_id", "enrollment_id", "attempt_number"})}, indexes = {@Index(name = "idx_quiz_attempts_quiz_id_status", columnList = "quiz_id, status")})
public class QuizAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private QuizAttemptStatus status = QuizAttemptStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "submitted_at", nullable = true)
    private Instant submittedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_payload", nullable = true, columnDefinition = "jsonb")
    private String snapshotPayload;

    @Column(name = "idempotency_key", nullable = true, length = 255, unique = true)
    private String idempotencyKey;
}
