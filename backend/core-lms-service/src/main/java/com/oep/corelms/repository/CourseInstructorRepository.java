package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.CourseInstructor;
import com.oep.coremodel.entity.course.CourseInstructorId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseInstructorRepository extends JpaRepository<CourseInstructor, CourseInstructorId> {
    List<CourseInstructor> findByCourse_Id(UUID courseId);

    boolean existsByCourse_IdAndInstructor_Id(UUID courseId, UUID instructorId);

    long deleteByCourse_IdAndInstructor_Id(UUID courseId, UUID instructorId);
}
