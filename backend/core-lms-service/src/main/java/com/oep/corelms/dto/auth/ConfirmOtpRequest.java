package com.oep.corelms.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConfirmOtpRequest(
        @NotBlank(message = "OTP code is required")
        @Size(min = 6, max = 6, message = "OTP code must be exactly 6 digits")
        @Pattern(regexp = "\\d{6}", message = "OTP code must contain exactly 6 digits")
        String otpCode
) {}
