package com.oep.corelms.repository;

import com.oep.coremodel.entity.assignment.AssignmentSubmission;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlindGradingRepository extends JpaRepository<AssignmentSubmission, UUID> {
    Optional<AssignmentSubmission> findByIdAndAssignmentCourseId(UUID id, UUID courseId);
    Optional<AssignmentSubmission> findByAssignmentIdAndEnrollmentId(UUID assignment, UUID enrollment);

    @Query("""
            select s from AssignmentSubmission s
            join fetch s.assignment a
            where a.course.id = :courseId
              and s.textContent is not null and s.textContent <> ''
              and (s.lockExpiresAt is null or s.lockExpiresAt <= :now or s.claimedBy = :actor)
              and (s.status = com.oep.coremodel.enums.AssignmentStatus.SUBMITTED
                   or (s.status = com.oep.coremodel.enums.AssignmentStatus.PENDING_REGRADE
                       and exists (select g.id from AssignmentGrade g
                                   where g.submission = s and g.gradedBy.id <> :actor)))
            order by s.submittedAt, s.id
            """)
    List<AssignmentSubmission> queue(UUID courseId, UUID actor, Instant now, Pageable page);
}
