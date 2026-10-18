package com.oep.corelms.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InitUploadRequest(
        @NotBlank(message = "Tên file không được để trống")
        String fileName,

        @NotBlank(message = "MIME type không được để trống")
        String mimeType,

        @NotNull(message = "Dung lượng file không được để trống")
        @Min(value = 1, message = "Dung lượng file phải lớn hơn 0")
        @Max(value = 4L * 1024 * 1024 * 1024, message = "Dung lượng file tối đa là 4GB")
        Long fileSize,

        String title
) {}
