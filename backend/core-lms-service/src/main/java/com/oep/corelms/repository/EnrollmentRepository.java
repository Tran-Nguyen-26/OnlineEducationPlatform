package com.oep.corelms.repository;

import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.enums.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {
    long countByCourseIdAndStatus(UUID courseId, EnrollmentStatus status);
    boolean existsByCourseIdAndStudentId(UUID courseId, UUID studentId);
    boolean existsByCourseIdAndStudentIdAndStatus(UUID courseId, UUID studentId, EnrollmentStatus status);
}
