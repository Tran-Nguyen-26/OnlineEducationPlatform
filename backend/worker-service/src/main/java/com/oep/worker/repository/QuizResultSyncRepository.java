package com.oep.worker.repository;

import com.oep.coremodel.entity.quiz.QuizResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QuizResultSyncRepository extends JpaRepository<QuizResult, UUID> {
    Optional<QuizResult> findByAttemptId(UUID attemptId);
}
