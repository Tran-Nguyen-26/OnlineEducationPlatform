package com.oep.corelms.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record CourseInstructorResponse(
        UUID instructorId,
        String email,
        String fullName,
        Instant addedAt) {
}
