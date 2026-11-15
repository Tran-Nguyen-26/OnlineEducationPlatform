package com.oep.assessment.repository;

import com.oep.coremodel.entity.quiz.QuizResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QuizResultRepository extends JpaRepository<QuizResult, UUID> {
    Optional<QuizResult> findByAttempt_Id(UUID attemptId);
}
