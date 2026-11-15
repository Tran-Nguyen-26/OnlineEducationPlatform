package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.CourseModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseModuleRepository extends JpaRepository<CourseModule, UUID> {
    List<CourseModule> findByCourse_IdOrderByPositionAsc(UUID courseId);
}
