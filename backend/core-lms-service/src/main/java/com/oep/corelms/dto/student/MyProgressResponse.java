package com.oep.corelms.dto.student;

import com.oep.coremodel.enums.EnrollmentStatus;

import java.util.UUID;

public record MyProgressResponse(
        UUID enrollmentId,
        UUID courseId,
        String courseTitle,
        String subjectName,
        EnrollmentStatus status,
        int totalLessons,
        int completedLessons,
        double percent) {
}
