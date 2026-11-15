package com.oep.assessment.repository;

import com.oep.coremodel.entity.enrollment.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {
    Optional<Enrollment> findByCourseIdAndStudentId(UUID courseId, UUID studentId);
}
