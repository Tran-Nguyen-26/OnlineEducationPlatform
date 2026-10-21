package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.CourseInstructor;
import com.oep.coremodel.entity.course.CourseInstructorId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CourseInstructorRepository extends JpaRepository<CourseInstructor, CourseInstructorId> {
    Optional<CourseInstructor> findByIdCourseIdAndIdInstructorId(UUID courseId, UUID instructorId);
}
