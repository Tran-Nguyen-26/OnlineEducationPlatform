package com.oep.coremodel.entity.enrollment;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "course_credentials")
public class CourseCredential {
    @Id
    private UUID id;
    @Column(name = "enrollment_id", nullable = false, unique = true)
    private UUID enrollmentId;
    @Column(name = "signed_credential", nullable = false, columnDefinition = "text")
    private String signedCredential;
}
