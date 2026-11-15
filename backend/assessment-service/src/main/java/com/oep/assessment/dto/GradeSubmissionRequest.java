package com.oep.assessment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record GradeSubmissionRequest(@NotNull @PositiveOrZero BigDecimal score, String feedback) {
}
