package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    List<Course> findAllByOrderByCreatedAtDesc();
    List<Course> findByLifecycleStatusOrderByCreatedAtDesc(CourseLifecycleStatus status);
    long countByLifecycleStatus(CourseLifecycleStatus status);
}
