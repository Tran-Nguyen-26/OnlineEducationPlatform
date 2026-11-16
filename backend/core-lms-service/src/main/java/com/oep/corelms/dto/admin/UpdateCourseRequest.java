package com.oep.corelms.dto.admin;

import com.oep.coremodel.enums.LearningMode;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UpdateCourseRequest(
        @NotNull UUID subjectId,
        @NotBlank @Size(max = 255) String title,
        String description,
        @Positive Integer capacity,
        Instant startAt,
        Instant endAt,
        @NotNull LearningMode learningMode,
        @DecimalMin("0.00") BigDecimal feeAmount,
        @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(100) Integer quizWeightPercent,
        @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(100) Integer passingPercent) {
}
