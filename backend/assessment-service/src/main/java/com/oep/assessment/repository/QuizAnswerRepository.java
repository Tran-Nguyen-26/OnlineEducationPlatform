package com.oep.assessment.repository;

import com.oep.coremodel.entity.quiz.QuizAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, UUID> {
    List<QuizAnswer> findByAttempt_Id(UUID attemptId);
}
