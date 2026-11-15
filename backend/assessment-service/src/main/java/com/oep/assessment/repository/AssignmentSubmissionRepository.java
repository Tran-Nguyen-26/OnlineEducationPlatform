package com.oep.assessment.repository;

import com.oep.coremodel.entity.assignment.AssignmentSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, UUID> {
    Optional<AssignmentSubmission> findByAssignment_IdAndEnrollment_Id(UUID assignmentId, UUID enrollmentId);
    List<AssignmentSubmission> findByAssignment_IdOrderBySubmittedAtDesc(UUID assignmentId);
}
