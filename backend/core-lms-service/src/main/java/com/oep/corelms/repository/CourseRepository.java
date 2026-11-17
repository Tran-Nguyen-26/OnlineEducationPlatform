package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    List<Course> findAllByOrderByCreatedAtDesc();
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "subject")
    List<Course> findByLifecycleStatusOrderByCreatedAtDesc(CourseLifecycleStatus status);
    long countByLifecycleStatus(CourseLifecycleStatus status);

    /** Row lock so concurrent enrollments/promotions on one course are serialized (capacity check + insert must be atomic). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Course c where c.id = :id")
    java.util.Optional<Course> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
