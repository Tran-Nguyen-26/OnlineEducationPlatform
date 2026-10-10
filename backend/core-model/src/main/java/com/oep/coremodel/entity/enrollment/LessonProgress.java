package com.oep.coremodel.entity.enrollment;

import com.oep.coremodel.entity.BaseEntity;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.course.LessonContent;
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
@Table(name = "lesson_progress", uniqueConstraints = {@UniqueConstraint(name = "uq_lesson_progress_enrollment_id_lesson_id", columnNames = {"enrollment_id", "lesson_id"})}, indexes = {@Index(name = "idx_lesson_progress_enrollment_id", columnList = "enrollment_id")})
public class LessonProgress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private LessonProgressStatus status = LessonProgressStatus.NOT_STARTED;

    @Column(name = "time_spent_seconds", nullable = false)
    private int timeSpentSeconds = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "last_content_id", nullable = true)
    private LessonContent lastContent;

    @Column(name = "started_at", nullable = true)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = true)
    private Instant completedAt;

    @Column(name = "last_accessed_at", nullable = false)
    private Instant lastAccessedAt = Instant.now();
}
