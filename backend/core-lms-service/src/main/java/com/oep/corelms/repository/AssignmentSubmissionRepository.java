package com.oep.corelms.repository;

import com.oep.coremodel.entity.assignment.AssignmentSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Read-only mirror of assessment-service's own repository (shared table, same pattern as EnrollmentRepository). */
public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, UUID> {
    boolean existsByAssignment_IdAndEnrollment_Id(UUID assignmentId, UUID enrollmentId);
    java.util.List<com.oep.coremodel.entity.assignment.AssignmentSubmission> findByAssignment_Course_IdInAndStatusIn(java.util.Collection<UUID> courseIds, java.util.Collection<com.oep.coremodel.enums.AssignmentStatus> statuses);
    java.util.Optional<com.oep.coremodel.entity.assignment.AssignmentSubmission> findByAssignment_IdAndEnrollment_Id(UUID assignmentId, UUID enrollmentId);
}
