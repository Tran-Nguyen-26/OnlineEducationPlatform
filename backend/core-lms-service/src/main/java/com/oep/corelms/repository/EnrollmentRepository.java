package com.oep.corelms.repository;

import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.enums.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {
    long countByCourseIdAndStatus(UUID courseId, EnrollmentStatus status);
    boolean existsByCourseIdAndStudentId(UUID courseId, UUID studentId);
    boolean existsByCourseIdAndStudentIdAndStatus(UUID courseId, UUID studentId, EnrollmentStatus status);
    Optional<Enrollment> findByCourseIdAndStudentId(UUID courseId, UUID studentId);
    long countByCourseIdAndStatusIn(UUID courseId, Collection<EnrollmentStatus> statuses);
    Page<Enrollment> findByStudentId(UUID studentId, Pageable pageable);
    Page<Enrollment> findByCourseId(UUID courseId, Pageable pageable);

    // Read only the key before locking; do not cache stale enrollment state.
    @Query("select e.course.id from Enrollment e where e.id = :id")
    Optional<UUID> findCourseIdById(@Param("id") UUID id);
}
