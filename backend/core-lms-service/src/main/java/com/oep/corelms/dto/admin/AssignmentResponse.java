package com.oep.corelms.dto.admin;

import com.oep.coremodel.entity.assignment.Assignment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AssignmentResponse(
        UUID id,
        UUID courseId,
        UUID lessonId,
        String title,
        String instructions,
        Instant deadline,
        int maxSubmissions,
        boolean allowLateSubmission,
        boolean allowText,
        boolean allowFiles,
        BigDecimal maxScore,
        String createdByEmail,
        Instant createdAt) {

    public static AssignmentResponse from(Assignment assignment) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getCourse().getId(),
                assignment.getLesson() == null ? null : assignment.getLesson().getId(),
                assignment.getTitle(),
                assignment.getInstructions(),
                assignment.getDeadline(),
                assignment.getMaxSubmissions(),
                assignment.isAllowLateSubmission(),
                assignment.isAllowText(),
                assignment.isAllowFiles(),
                assignment.getMaxScore(),
                assignment.getCreatedBy() == null ? null : assignment.getCreatedBy().getEmail(),
                assignment.getCreatedAt());
    }
}
