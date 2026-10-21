package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.CourseModule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseModuleRepository extends JpaRepository<CourseModule, UUID> {
    Page<CourseModule> findByCourseIdOrderByPositionAsc(UUID courseId, Pageable pageable);
    List<CourseModule> findByCourseIdOrderByPositionAsc(UUID courseId);
    Optional<CourseModule> findByIdAndCourseId(UUID id, UUID courseId);
    boolean existsByCourseIdAndPosition(UUID courseId, int position);
    boolean existsByCourseIdAndDefaultModuleTrueAndIdNot(UUID courseId, UUID id);
}
