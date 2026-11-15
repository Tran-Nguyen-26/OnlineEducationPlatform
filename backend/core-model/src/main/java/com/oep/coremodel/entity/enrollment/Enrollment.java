package com.oep.coremodel.entity.enrollment;

import com.oep.coremodel.entity.BaseEntity;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.registration.CourseInviteCode;
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
@Table(name = "enrollments", uniqueConstraints = {@UniqueConstraint(name = "uq_enrollments_course_id_student_id", columnNames = {"course_id", "student_id"})}, indexes = {@Index(name = "idx_enrollments_student_id_status", columnList = "student_id, status"), @Index(name = "idx_enrollments_invite_code_id", columnList = "invite_code_id")})
public class Enrollment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    /** The invite code the student entered to unlock this course; every enrollment must trace back to one. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invite_code_id", nullable = false)
    private CourseInviteCode inviteCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt = Instant.now();

    @Column(name = "completed_at", nullable = true)
    private Instant completedAt;

    @Column(name = "withdrawn_at", nullable = true)
    private Instant withdrawnAt;

    @Column(name = "removed_at", nullable = true)
    private Instant removedAt;
}
