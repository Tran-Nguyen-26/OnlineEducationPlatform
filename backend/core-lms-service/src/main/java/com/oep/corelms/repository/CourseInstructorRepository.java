package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.CourseInstructor;
import com.oep.coremodel.entity.course.CourseInstructorId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseInstructorRepository extends JpaRepository<CourseInstructor, CourseInstructorId> {
    Optional<CourseInstructor> findByIdCourseIdAndIdInstructorId(UUID courseId, UUID instructorId);

    @Query("select ci from CourseInstructor ci join fetch ci.instructor where ci.course.id = :courseId order by ci.addedAt asc")
    List<CourseInstructor> findAllForCourse(@Param("courseId") UUID courseId);
}
