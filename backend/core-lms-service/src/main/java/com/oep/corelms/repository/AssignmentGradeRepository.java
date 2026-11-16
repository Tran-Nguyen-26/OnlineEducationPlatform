package com.oep.corelms.repository;

import com.oep.coremodel.entity.assignment.AssignmentGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Read-only mirror of assessment-service's repository (shared table). */
public interface AssignmentGradeRepository extends JpaRepository<AssignmentGrade, UUID> {
    Optional<AssignmentGrade> findBySubmission_Id(UUID submissionId);
}
