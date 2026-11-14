package com.oep.worker.repository;

import com.oep.coremodel.entity.quiz.QuizQuestion;
import com.oep.coremodel.entity.quiz.QuizQuestionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface QuizQuestionSyncRepository extends JpaRepository<QuizQuestion, QuizQuestionId> {

    @Query("select coalesce(sum(qq.points), 0) from QuizQuestion qq where qq.quiz.id = :quizId")
    BigDecimal sumPointsByQuizId(@Param("quizId") UUID quizId);
}
