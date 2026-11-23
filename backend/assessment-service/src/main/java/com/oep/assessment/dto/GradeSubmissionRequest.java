package com.oep.assessment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** version = the submission version the grader was looking at (optimistic lock); null skips the check. */
public record GradeSubmissionRequest(@NotNull @PositiveOrZero BigDecimal score, String feedback, Long version) {
}
