package com.oep.corelms.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ConfirmUploadRequest(
        @NotBlank(message = "objectKey không được để trống")
        String objectKey
) {}
