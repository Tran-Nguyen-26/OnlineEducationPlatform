package com.oep.corelms.dto.student;

import java.time.Instant;
import java.util.UUID;

public record CertificateResponse(
        UUID id,
        String certificateNumber,
        Instant issuedAt,
        String studentFullName,
        String studentEmail,
        String courseTitle,
        String subjectName) {
}
