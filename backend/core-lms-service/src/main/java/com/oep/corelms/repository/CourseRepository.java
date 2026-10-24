package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {
}
