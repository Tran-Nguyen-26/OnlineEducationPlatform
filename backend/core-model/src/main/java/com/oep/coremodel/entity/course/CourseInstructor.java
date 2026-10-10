package com.oep.coremodel.entity.course;

import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.CourseInstructorRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "course_instructors")
public class CourseInstructor {

    @EmbeddedId
    private CourseInstructorId id = new CourseInstructorId();

    @MapsId("courseId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @MapsId("instructorId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private User instructor;

    @Enumerated(EnumType.STRING)
    @Column(name = "course_role", nullable = false, length = 40)
    private CourseInstructorRole courseRole = CourseInstructorRole.CO_INSTRUCTOR;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by")
    private User addedBy;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt = Instant.now();
}
