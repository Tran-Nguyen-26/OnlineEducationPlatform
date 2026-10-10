package com.oep.coremodel.entity.quiz;

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
@Table(name = "quiz_results")
public class QuizResult extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, unique = true)
    private QuizAttempt attempt;

    @Column(name = "auto_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal autoScore;

    @Column(name = "final_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal finalScore;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "adjusted_by", nullable = true)
    private User adjustedBy;

    @Column(name = "adjustment_reason", nullable = true, columnDefinition = "text")
    private String adjustmentReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
