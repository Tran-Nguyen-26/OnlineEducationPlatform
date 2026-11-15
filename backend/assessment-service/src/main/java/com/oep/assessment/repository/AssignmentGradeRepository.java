package com.oep.assessment.repository;

import com.oep.coremodel.entity.assignment.AssignmentGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssignmentGradeRepository extends JpaRepository<AssignmentGrade, UUID> {
    Optional<AssignmentGrade> findBySubmission_Id(UUID submissionId);
}
