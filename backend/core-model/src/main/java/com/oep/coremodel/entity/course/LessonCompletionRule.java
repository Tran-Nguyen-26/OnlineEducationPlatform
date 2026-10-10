package com.oep.coremodel.entity.course;

import com.oep.coremodel.entity.BaseEntity;
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
@Table(name = "lesson_completion_rules")
public class LessonCompletionRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, unique = true)
    private Lesson lesson;

    @Column(name = "minimum_time_seconds", nullable = false)
    private int minimumTimeSeconds = 0;

    @Column(name = "min_video_percentage", nullable = true, precision = 5, scale = 2)
    private BigDecimal minVideoPercentage = new BigDecimal("80.00");

    @Column(name = "require_quiz", nullable = false)
    private boolean requireQuiz = false;

    @Column(name = "require_assignment", nullable = false)
    private boolean requireAssignment = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
