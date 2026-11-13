package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.LearningOutcome;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LearningOutcomeRepository extends JpaRepository<LearningOutcome, UUID> {
    List<LearningOutcome> findByCourseIdOrderByCode(UUID courseId);
    Optional<LearningOutcome> findByIdAndCourseId(UUID id, UUID courseId);
    boolean existsByCourseIdAndCode(UUID courseId, String code);
}
