package com.oep.assessment.repository;

import com.oep.coremodel.entity.quiz.QuizQuestion;
import com.oep.coremodel.entity.quiz.QuizQuestionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, QuizQuestionId> {
    List<QuizQuestion> findByQuiz_IdOrderByPositionAsc(UUID quizId);
}
