package com.oep.corelms.repository;

import com.oep.coremodel.entity.quiz.QuizQuestion;
import com.oep.coremodel.entity.quiz.QuizQuestionId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, QuizQuestionId> {

    List<QuizQuestion> findByQuizIdOrderByPositionAsc(UUID quizId);

    Page<QuizQuestion> findByQuizIdOrderByPositionAsc(UUID quizId, Pageable pageable);

    Optional<QuizQuestion> findByQuizIdAndQuestionId(UUID quizId, UUID questionId);

    boolean existsByQuestionId(UUID questionId);

    void deleteByQuizId(UUID quizId);

    void deleteByQuizIdAndQuestionId(UUID quizId, UUID questionId);

    @Query("SELECT COALESCE(MAX(qq.position), -1) FROM QuizQuestion qq WHERE qq.quiz.id = :quizId")
    int findMaxPositionByQuizId(@Param("quizId") UUID quizId);

    @Query("SELECT COALESCE(SUM(qq.points), 0) FROM QuizQuestion qq WHERE qq.quiz.id = :quizId")
    BigDecimal sumPointsByQuizId(@Param("quizId") UUID quizId);
}
