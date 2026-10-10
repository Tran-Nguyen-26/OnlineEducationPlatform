package com.oep.coremodel.entity.course;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class CourseInstructorId implements Serializable {

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "instructor_id")
    private UUID instructorId;
}
