package com.oep.corelms.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTeacherRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(max = 255) String fullName) {
}
