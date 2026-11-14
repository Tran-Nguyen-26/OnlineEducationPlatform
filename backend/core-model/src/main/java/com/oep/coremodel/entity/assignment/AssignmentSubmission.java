package com.oep.coremodel.entity.assignment;

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
@Table(name = "assignment_submissions", uniqueConstraints = {@UniqueConstraint(name = "uq_assignment_submissions_assignment_id_enrollment_id", columnNames = {"assignment_id", "enrollment_id"})}, indexes = {@Index(name = "idx_assignment_submissions_assignment_id_submitted_at", columnList = "assignment_id, submitted_at")})
public class AssignmentSubmission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Column(name = "text_content", nullable = true, columnDefinition = "text")
    private String textContent;

    @Column(name = "submission_count", nullable = false)
    private int submissionCount = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private AssignmentStatus status = AssignmentStatus.SUBMITTED;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "is_late", nullable = false)
    private boolean late = false;

    @Version
    private long version;

    @Column(name = "claimed_by")
    private UUID claimedBy;

    @Column(name = "lock_token")
    private UUID lockToken;

    @Column(name = "lock_expires_at")
    private Instant lockExpiresAt;

    @Column(name = "appeal_criterion")
    private String appealCriterion;

    @Column(name = "appeal_reason", columnDefinition = "text")
    private String appealReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "viva_questions", columnDefinition = "jsonb")
    private java.util.List<String> vivaQuestions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "viva_answers", columnDefinition = "jsonb")
    private java.util.List<String> vivaAnswers;

    @Column(name = "viva_consistency_score", precision = 5, scale = 2)
    private BigDecimal vivaConsistencyScore;

    @Column(name = "viva_consistency_note", columnDefinition = "text")
    private String vivaConsistencyNote;

    @Column(name = "viva_completed_at")
    private Instant vivaCompletedAt;

}
