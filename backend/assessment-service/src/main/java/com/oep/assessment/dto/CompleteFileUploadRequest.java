package com.oep.assessment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CompleteFileUploadRequest(@NotNull @Positive Long fileSize) {
}
