package com.oep.corelms.dto.student;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 255) String fullName,
        @Size(max = 50) String phone,
        LocalDate dateOfBirth,
        @Size(max = 255) String organization,
        @Size(max = 2000) String bio) {
}
