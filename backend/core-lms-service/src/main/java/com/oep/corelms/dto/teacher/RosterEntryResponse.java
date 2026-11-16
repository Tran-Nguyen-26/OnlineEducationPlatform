package com.oep.corelms.dto.teacher;

import com.oep.coremodel.enums.EnrollmentStatus;

import java.time.Instant;
import java.util.UUID;

public record RosterEntryResponse(
        UUID enrollmentId,
        UUID studentId,
        String studentEmail,
        String studentFullName,
        EnrollmentStatus status,
        Instant enrolledAt,
        int totalLessons,
        int completedLessons,
        double progressPercent,
        String avatarUrl) {
}
