package com.oep.assessment.repository;

import com.oep.coremodel.entity.enrollment.CompletionWaiver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompletionWaiverRepository extends JpaRepository<CompletionWaiver, UUID> {
    List<CompletionWaiver> findByEnrollment_Id(UUID enrollmentId);
}
