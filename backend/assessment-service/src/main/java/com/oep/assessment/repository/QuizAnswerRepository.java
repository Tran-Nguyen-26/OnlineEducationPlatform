package com.oep.assessment.repository;

import com.oep.coremodel.entity.quiz.QuizAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, UUID> {
    List<QuizAnswer> findByAttempt_Id(UUID attemptId);

    @org.springframework.data.jpa.repository.Query("select count(a) > 0 from QuizAnswer a where a.attempt.id = :attemptId "
            + "and a.question.questionType = com.oep.coremodel.enums.QuestionType.ESSAY and a.awardedScore is null")
    boolean hasPendingEssay(@org.springframework.data.repository.query.Param("attemptId") UUID attemptId);

    /** Essay answers of finished attempts still waiting for a manual score. */
    @org.springframework.data.jpa.repository.Query("select a from QuizAnswer a where a.question.questionType = com.oep.coremodel.enums.QuestionType.ESSAY "
            + "and a.awardedScore is null and a.attempt.status <> com.oep.coremodel.enums.QuizAttemptStatus.IN_PROGRESS "
            + "order by a.attempt.submittedAt asc")
    List<QuizAnswer> findPendingEssays();
}
