package com.oep.assessment.repository;

import com.oep.coremodel.entity.assignment.AssignmentSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import com.oep.coremodel.enums.AssignmentStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, UUID> {
    Optional<AssignmentSubmission> findByAssignment_IdAndEnrollment_Id(UUID assignmentId, UUID enrollmentId);
    List<AssignmentSubmission> findByAssignment_IdOrderBySubmittedAtDesc(UUID assignmentId);

    List<AssignmentSubmission> findByAssignment_Course_IdInAndStatusInOrderBySubmittedAtAsc(Collection<UUID> courseIds, Collection<AssignmentStatus> statuses);

    List<AssignmentSubmission> findByStatusInOrderBySubmittedAtAsc(Collection<AssignmentStatus> statuses);
}
