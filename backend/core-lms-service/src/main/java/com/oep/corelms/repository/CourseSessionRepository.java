package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.CourseSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseSessionRepository extends JpaRepository<CourseSession, UUID> {
    List<CourseSession> findByCourse_IdOrderByStartsAtAsc(UUID courseId);
}
