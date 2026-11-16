package com.oep.assessment.repository;

import com.oep.coremodel.entity.quiz.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {
    List<QuizAttempt> findByQuiz_IdAndEnrollment_IdOrderByAttemptNumberAsc(UUID quizId, UUID enrollmentId);
    long countByQuiz_IdAndEnrollment_Id(UUID quizId, UUID enrollmentId);
    Optional<QuizAttempt> findByIdAndEnrollment_Id(UUID id, UUID enrollmentId);
    List<QuizAttempt> findByQuiz_IdOrderByStartedAtDesc(UUID quizId);
}
