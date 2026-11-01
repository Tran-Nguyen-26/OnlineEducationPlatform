package com.oep.corelms.repository;

import com.oep.coremodel.entity.quiz.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    boolean existsByQuizId(UUID quizId);

    long countByQuizId(UUID quizId);

    @Query("SELECT COUNT(a) FROM QuizAttempt a WHERE a.quiz.course.id = :courseId")
    long countByCourseId(@Param("courseId") UUID courseId);
}
