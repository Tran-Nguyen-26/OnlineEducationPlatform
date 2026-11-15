package com.oep.assessment.repository;

import com.oep.coremodel.entity.assignment.SubmissionFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionFileRepository extends JpaRepository<SubmissionFile, UUID> {
    List<SubmissionFile> findBySubmission_Id(UUID submissionId);
    Optional<SubmissionFile> findByIdAndSubmission_Id(UUID id, UUID submissionId);
}
