package com.oep.corelms.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PresignPartRequest(
        @NotBlank(message = "uploadId không được để trống")
        String uploadId,

        @NotBlank(message = "objectKey không được để trống")
        String objectKey,

        @NotNull(message = "partNumber không được để trống")
        @Min(value = 1, message = "partNumber tối thiểu là 1")
        Integer partNumber
) {}
