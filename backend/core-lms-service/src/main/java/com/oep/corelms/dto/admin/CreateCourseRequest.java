package com.oep.corelms.dto.admin;

import com.oep.coremodel.enums.LearningMode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateCourseRequest(
        @NotNull UUID subjectId,
        @NotBlank @Size(max = 255) String title,
        String description,
        @Positive Integer capacity,
        Instant startAt,
        Instant endAt,
        LearningMode learningMode) {
}
