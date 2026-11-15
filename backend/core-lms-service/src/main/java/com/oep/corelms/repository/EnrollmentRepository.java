package com.oep.corelms.repository;

import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.enums.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {
    long countByCourseIdAndStatus(UUID courseId, EnrollmentStatus status);
    boolean existsByCourseIdAndStudentId(UUID courseId, UUID studentId);
    boolean existsByCourseIdAndStudentIdAndStatus(UUID courseId, UUID studentId, EnrollmentStatus status);
    Optional<Enrollment> findByCourseIdAndStudentId(UUID courseId, UUID studentId);
    List<Enrollment> findByStudentIdOrderByEnrolledAtDesc(UUID studentId);
    List<Enrollment> findByCourseIdAndStatus(UUID courseId, EnrollmentStatus status);
    long countByStatus(EnrollmentStatus status);
    List<Enrollment> findByCourseIdOrderByEnrolledAtDesc(UUID courseId);
}
