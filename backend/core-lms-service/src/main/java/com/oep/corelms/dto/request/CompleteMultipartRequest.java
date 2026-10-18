package com.oep.corelms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CompleteMultipartRequest(
        @NotBlank(message = "uploadId không được để trống")
        String uploadId,

        @NotBlank(message = "objectKey không được để trống")
        String objectKey,

        @NotEmpty(message = "Danh sách parts không được để trống")
        @Valid
        List<PartInfo> parts
) {
    public record PartInfo(
            @NotNull(message = "partNumber không được null")
            @Min(1)
            Integer partNumber,

            @NotBlank(message = "etag không được để trống")
            String etag
    ) {}
}
