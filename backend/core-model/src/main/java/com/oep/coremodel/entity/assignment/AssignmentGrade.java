package com.oep.coremodel.entity.assignment;

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
@Table(name = "assignment_grades")
public class AssignmentGrade extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false, unique = true)
    private AssignmentSubmission submission;

    @Column(name = "score", nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "feedback", nullable = true, columnDefinition = "text")
    private String feedback;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "graded_by", nullable = false)
    private User gradedBy;

    @Column(name = "graded_at", nullable = false)
    private Instant gradedAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "previous_score", nullable = true, precision = 5, scale = 2)
    private BigDecimal previousScore;

    @Column(name = "regraded_at", nullable = true)
    private Instant regradedAt;

    @Column(name = "regrade_reason", nullable = true, columnDefinition = "text")
    private String regradeReason;
}
