package com.oep.corelms.dto.student;

import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.enums.LearningMode;

import java.util.UUID;

/** Every published course is browsable (title/description/intro); only enrolled=true unlocks the outline. */
public record StudentCourseResponse(
        UUID id,
        String title,
        String description,
        String subjectName,
        Integer capacity,
        LearningMode learningMode,
        boolean enrolled) {

    public static StudentCourseResponse from(Course course, boolean enrolled) {
        return new StudentCourseResponse(course.getId(), course.getTitle(), course.getDescription(),
                course.getSubject().getName(), course.getCapacity(), course.getLearningMode(), enrolled);
    }
}
