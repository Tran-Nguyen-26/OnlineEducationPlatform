package com.oep.corelms.dto.admin;

import com.oep.coremodel.enums.EnrollmentStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminEnrollmentResponse(
        UUID enrollmentId,
        UUID studentId,
        String studentEmail,
        String studentFullName,
        EnrollmentStatus status,
        Instant enrolledAt,
        Instant completedAt,
        Instant withdrawnAt,
        Instant removedAt) {
}
