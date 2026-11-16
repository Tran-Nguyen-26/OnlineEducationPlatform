package com.oep.assessment.repository;

import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.enums.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {
    Optional<Enrollment> findByCourseIdAndStudentId(UUID courseId, UUID studentId);

    List<Enrollment> findByCourse_IdAndStatus(UUID courseId, EnrollmentStatus status);

    List<Enrollment> findByCourse_IdAndStatusIn(UUID courseId, Collection<EnrollmentStatus> statuses);
}
