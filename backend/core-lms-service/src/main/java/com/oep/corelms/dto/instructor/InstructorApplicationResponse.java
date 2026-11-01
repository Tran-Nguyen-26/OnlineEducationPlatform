package com.oep.corelms.dto.instructor;

import com.oep.coremodel.entity.registration.InstructorApplication;
import com.oep.coremodel.enums.InstructorApplicationStatus;

import java.time.Instant;
import java.util.UUID;

public record InstructorApplicationResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        String organization,
        String jobTitle,
        String expertise,
        Integer yearsOfExperience,
        String professionalBio,
        String motivation,
        InstructorApplicationStatus status,
        Instant submittedAt,
        UUID reviewedBy,
        Instant reviewedAt,
        String rejectionReason
) {
    public static InstructorApplicationResponse fromEntity(InstructorApplication app) {
        return new InstructorApplicationResponse(
                app.getId(),
                app.getEmail(),
                app.getFullName(),
                app.getPhone(),
                app.getOrganization(),
                app.getJobTitle(),
                app.getExpertise(),
                app.getYearsOfExperience(),
                app.getProfessionalBio(),
                app.getMotivation(),
                app.getStatus(),
                app.getSubmittedAt(),
                app.getReviewedBy() != null ? app.getReviewedBy().getId() : null,
                app.getReviewedAt(),
                app.getRejectionReason()
        );
    }
}
