package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.CoursePrerequisite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoursePrerequisiteRepository extends JpaRepository<CoursePrerequisite, UUID> {
    List<CoursePrerequisite> findByCourse_Id(UUID courseId);
    boolean existsByCourse_IdAndPrerequisiteCourse_Id(UUID courseId, UUID prerequisiteCourseId);
    Optional<CoursePrerequisite> findByCourse_IdAndPrerequisiteCourse_Id(UUID courseId, UUID prerequisiteCourseId);
}
