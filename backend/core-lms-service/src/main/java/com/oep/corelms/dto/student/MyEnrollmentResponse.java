package com.oep.corelms.dto.student;

import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.enums.EnrollmentStatus;

import java.time.Instant;
import java.util.UUID;

public record MyEnrollmentResponse(
        UUID enrollmentId,
        UUID courseId,
        String courseTitle,
        String subjectName,
        EnrollmentStatus status,
        Instant enrolledAt,
        Instant completedAt,
        Instant withdrawnAt,
        Instant removedAt) {

    public static MyEnrollmentResponse from(Enrollment enrollment) {
        return new MyEnrollmentResponse(
                enrollment.getId(),
                enrollment.getCourse().getId(),
                enrollment.getCourse().getTitle(),
                enrollment.getCourse().getSubject().getName(),
                enrollment.getStatus(),
                enrollment.getEnrolledAt(),
                enrollment.getCompletedAt(),
                enrollment.getWithdrawnAt(),
                enrollment.getRemovedAt());
    }
}
