package com.oep.coremodel.entity.registration;

import com.oep.coremodel.entity.BaseEntity;
import com.oep.coremodel.entity.course.Course;
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
@Table(name = "enrollment_requests")
public class EnrollmentRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private EnrollmentRequestStatus status = EnrollmentRequestStatus.PENDING;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "reviewed_at", nullable = true)
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reviewed_by", nullable = true)
    private User reviewedBy;

    @Column(name = "rejection_reason", nullable = true, columnDefinition = "text")
    private String rejectionReason;
}
