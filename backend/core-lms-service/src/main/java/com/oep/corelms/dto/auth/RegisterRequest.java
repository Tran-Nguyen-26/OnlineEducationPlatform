package com.oep.corelms.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 100) @com.oep.corelms.validation.StrongPassword String password,
        @NotBlank @Size(max = 255) String fullName,
        /** Explicit consent to the processing of learning data (progress, exam logs); must be true to self-register. */
        @jakarta.validation.constraints.NotNull(message = "Bạn cần đồng ý với việc thu thập dữ liệu học tập để đăng ký") @jakarta.validation.constraints.AssertTrue(message = "Bạn cần đồng ý với việc thu thập dữ liệu học tập để đăng ký") Boolean consent) {
}
