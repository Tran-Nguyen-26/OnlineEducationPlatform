package com.oep.coremodel.entity.enrollment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "course_surveys")
public class CourseSurvey {
    @Id
    @Column(name = "enrollment_id")
    private UUID enrollmentId;
    @Column(nullable = false)
    private int csat;
    @Column(nullable = false)
    private int nps;
    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
}
