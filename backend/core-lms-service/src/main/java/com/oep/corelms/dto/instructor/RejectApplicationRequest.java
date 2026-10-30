package com.oep.corelms.dto.instructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectApplicationRequest(
        @NotBlank(message = "Lý do từ chối không được để trống")
        @Size(min = 5, max = 2000, message = "Lý do từ chối phải từ 5 đến 2000 ký tự")
        String rejectionReason
) {}
