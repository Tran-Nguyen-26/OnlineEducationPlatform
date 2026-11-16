package com.oep.corelms.repository;

import com.oep.coremodel.entity.quiz.QuizAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/** Read-only mirror of assessment-service's repository (shared table). */
public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, UUID> {
    @Query("select count(a) > 0 from QuizAnswer a where a.attempt.id = :attemptId "
            + "and a.question.questionType = com.oep.coremodel.enums.QuestionType.ESSAY and a.awardedScore is null")
    boolean hasPendingEssay(@Param("attemptId") UUID attemptId);
}
