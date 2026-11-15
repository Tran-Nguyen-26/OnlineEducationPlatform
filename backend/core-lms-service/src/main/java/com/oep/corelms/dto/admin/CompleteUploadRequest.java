package com.oep.corelms.dto.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CompleteUploadRequest(@NotNull @Positive Long fileSize, Integer durationSeconds) {
}
