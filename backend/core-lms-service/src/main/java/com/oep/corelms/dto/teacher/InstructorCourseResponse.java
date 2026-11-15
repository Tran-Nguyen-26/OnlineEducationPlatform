package com.oep.corelms.dto.teacher;

import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.enums.CourseLifecycleStatus;

import java.util.UUID;

public record InstructorCourseResponse(UUID id, String title, String description, String subjectName, CourseLifecycleStatus lifecycleStatus) {
    public static InstructorCourseResponse from(Course course) {
        return new InstructorCourseResponse(course.getId(), course.getTitle(), course.getDescription(),
                course.getSubject().getName(), course.getLifecycleStatus());
    }
}
