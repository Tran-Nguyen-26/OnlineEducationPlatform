package com.oep.corelms.repository;

import com.oep.coremodel.entity.quiz.QuizAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, UUID> {
    long countByAttemptIdAndCorrectTrue(UUID attemptId);
}
