package com.oep.corelms.dto.student;

import java.time.Instant;
import java.util.UUID;

public record MyCertificateResponse(
        UUID enrollmentId,
        String certificateNumber,
        Instant issuedAt,
        String courseTitle,
        String subjectName) {
}
