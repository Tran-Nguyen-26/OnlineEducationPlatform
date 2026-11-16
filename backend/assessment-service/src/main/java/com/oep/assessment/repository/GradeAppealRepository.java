package com.oep.assessment.repository;

import com.oep.coremodel.entity.enrollment.GradeAppeal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GradeAppealRepository extends JpaRepository<GradeAppeal, UUID> {
    List<GradeAppeal> findByEnrollment_Student_IdOrderByCreatedAtDesc(UUID studentId);

    List<GradeAppeal> findByStatusOrderByCreatedAtAsc(String status);

    List<GradeAppeal> findByCourseIdInOrderByCreatedAtDesc(Collection<UUID> courseIds);

    List<GradeAppeal> findAllByOrderByCreatedAtDesc();

    boolean existsByTargetTypeAndTargetIdAndEnrollment_IdAndStatus(String targetType, UUID targetId, UUID enrollmentId, String status);
}
