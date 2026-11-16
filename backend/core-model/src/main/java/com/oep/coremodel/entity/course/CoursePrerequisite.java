package com.oep.coremodel.entity.course;

import com.oep.coremodel.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.Column;

import java.time.Instant;

/** course must not open for a student until prerequisiteCourse is COMPLETED by them (enforced in EnrollmentService). */
@Getter
@Setter
@Entity
@Table(name = "course_prerequisites",
        uniqueConstraints = {@UniqueConstraint(name = "uq_course_prerequisites_course_id_prerequisite_course_id",
                columnNames = {"course_id", "prerequisite_course_id"})})
public class CoursePrerequisite extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prerequisite_course_id", nullable = false)
    private Course prerequisiteCourse;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
