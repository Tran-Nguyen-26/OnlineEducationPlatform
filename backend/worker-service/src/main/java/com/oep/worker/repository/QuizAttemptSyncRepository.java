package com.oep.worker.repository;

import com.oep.coremodel.entity.quiz.QuizAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface QuizAttemptSyncRepository extends JpaRepository<QuizAttempt, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from QuizAttempt a join fetch a.quiz join fetch a.enrollment where a.id = :attemptId")
    Optional<QuizAttempt> findForScoreSync(@Param("attemptId") UUID attemptId);
}
