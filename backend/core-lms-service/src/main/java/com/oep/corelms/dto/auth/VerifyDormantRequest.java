package com.oep.corelms.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyDormantRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "OTP phải gồm 6 chữ số") String otpCode
) {}
