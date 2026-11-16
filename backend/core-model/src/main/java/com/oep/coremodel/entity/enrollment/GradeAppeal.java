package com.oep.coremodel.entity.enrollment;

import com.oep.coremodel.entity.BaseEntity;
import com.oep.coremodel.entity.identity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A student's request to have a grade reviewed (phúc khảo). targetType = QUIZ_ATTEMPT | ASSIGNMENT_SUBMISSION. */
@Getter
@Setter
@Entity
@Table(name = "grade_appeals", indexes = {@Index(name = "idx_grade_appeals_course_status", columnList = "course_id, status")})
public class GradeAppeal extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    /** Human-readable label of the graded item, kept so the queue stays readable without joins. */
    @Column(name = "target_title", nullable = false, length = 255)
    private String targetTitle;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    /** OPEN | ACCEPTED | REJECTED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "response", nullable = true, columnDefinition = "text")
    private String response;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "resolved_by", nullable = true)
    private User resolvedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at", nullable = true)
    private Instant resolvedAt;
}
