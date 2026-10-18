package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.CourseInstructor;
import com.oep.coremodel.entity.course.CourseInstructorId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CourseInstructorRepository extends JpaRepository<CourseInstructor, CourseInstructorId> {

    @Query("SELECT COUNT(ci) > 0 FROM CourseInstructor ci WHERE ci.id.courseId = :courseId AND ci.id.instructorId = :instructorId")
    boolean isInstructorOfCourse(@Param("courseId") UUID courseId, @Param("instructorId") UUID instructorId);
}
