package com.oep.assessment.repository;

import com.oep.coremodel.entity.assignment.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {
    List<Assignment> findByCourse_IdOrderByCreatedAtDesc(UUID courseId);
    List<com.oep.coremodel.entity.assignment.Assignment> findByDeadlineBetween(java.time.Instant from, java.time.Instant to);
}
