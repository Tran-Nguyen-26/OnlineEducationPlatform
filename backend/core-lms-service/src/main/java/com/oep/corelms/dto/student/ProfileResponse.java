package com.oep.corelms.dto.student;

import java.time.LocalDate;

public record ProfileResponse(
        String email,
        String role,
        String fullName,
        String phone,
        LocalDate dateOfBirth,
        String organization,
        String bio) {
}
