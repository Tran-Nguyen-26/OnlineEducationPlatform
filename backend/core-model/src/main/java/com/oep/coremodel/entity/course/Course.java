package com.oep.coremodel.entity.course;

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
@Table(name = "courses", indexes = {@Index(name = "idx_courses_lifecycle_status_visibility", columnList = "lifecycle_status, visibility"), @Index(name = "idx_courses_created_by", columnList = "created_by")})
public class Course extends BaseEntity {

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 40)
    private CourseVisibility visibility = CourseVisibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 40)
    private CourseLifecycleStatus lifecycleStatus = CourseLifecycleStatus.DRAFT;

    @Column(name = "capacity", nullable = true)
    private Integer capacity;

    @Column(name = "start_at", nullable = true)
    private Instant startAt;

    @Column(name = "end_at", nullable = true)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_mode", nullable = false, length = 40)
    private LearningMode learningMode = LearningMode.FREE_NAVIGATION;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
