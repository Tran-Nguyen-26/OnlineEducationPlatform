package com.oep.coremodel.entity.assignment;

import com.oep.coremodel.entity.BaseEntity;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.Lesson;
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
@Table(name = "assignments", indexes = {@Index(name = "idx_assignments_course_id", columnList = "course_id"), @Index(name = "idx_assignments_lesson_id", columnList = "lesson_id")})
public class Assignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "lesson_id", nullable = true)
    private Lesson lesson;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "instructions", nullable = true, columnDefinition = "text")
    private String instructions;

    @Column(name = "deadline", nullable = true)
    private Instant deadline;

    @Column(name = "max_submissions", nullable = false)
    private int maxSubmissions = 1;

    @Column(name = "allow_late_submission", nullable = false)
    private boolean allowLateSubmission = false;

    @Column(name = "allow_text", nullable = false)
    private boolean allowText = true;

    @Column(name = "allow_files", nullable = false)
    private boolean allowFiles = true;

    @Column(name = "max_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore = new BigDecimal("100.00");

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "created_by", nullable = true)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
