package com.oep.coremodel.entity.registration;

import com.oep.coremodel.entity.BaseEntity;
import com.oep.coremodel.entity.identity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import com.oep.coremodel.enums.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "instructor_applications")
public class InstructorApplication extends BaseEntity {

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "phone", nullable = true, length = 50)
    private String phone;

    @Column(name = "organization", nullable = true, length = 255)
    private String organization;

    @Column(name = "job_title", nullable = true, length = 255)
    private String jobTitle;

    @Column(name = "expertise", nullable = true, length = 255)
    private String expertise;

    @Column(name = "years_of_experience", nullable = true)
    private Integer yearsOfExperience;

    @Column(name = "professional_bio", nullable = true, columnDefinition = "text")
    private String professionalBio;

    @Column(name = "motivation", nullable = true, columnDefinition = "text")
    private String motivation;

    @Column(name = "verification_document_object_key", nullable = true, length = 500)
    private String verificationDocumentObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private InstructorApplicationStatus status = InstructorApplicationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reviewed_by", nullable = true)
    private User reviewedBy;

    @Column(name = "reviewed_at", nullable = true)
    private Instant reviewedAt;

    @Column(name = "rejection_reason", nullable = true, columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();
}
