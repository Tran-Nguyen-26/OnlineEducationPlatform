package com.oep.corelms.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record RequestUploadUrlRequest(@NotBlank String fileName, @NotBlank String mimeType) {
}
