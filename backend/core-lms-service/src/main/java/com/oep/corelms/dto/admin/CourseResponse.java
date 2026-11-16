package com.oep.corelms.dto.admin;

import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.LearningMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CourseResponse(
        UUID id,
        UUID subjectId,
        String subjectName,
        String title,
        String description,
        CourseLifecycleStatus lifecycleStatus,
        Integer capacity,
        Instant startAt,
        Instant endAt,
        LearningMode learningMode,
        BigDecimal feeAmount,
        String createdByEmail,
        Instant createdAt,
        Instant updatedAt) {

    public static CourseResponse from(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getSubject().getId(),
                course.getSubject().getName(),
                course.getTitle(),
                course.getDescription(),
                course.getLifecycleStatus(),
                course.getCapacity(),
                course.getStartAt(),
                course.getEndAt(),
                course.getLearningMode(),
                course.getFeeAmount(),
                course.getCreatedBy().getEmail(),
                course.getCreatedAt(),
                course.getUpdatedAt());
    }
}
