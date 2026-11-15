package com.oep.assessment.dto;

import jakarta.validation.constraints.NotBlank;

public record RequestFileUploadRequest(@NotBlank String fileName, @NotBlank String mimeType) {
}
