package com.oep.corelms.repository;

import com.oep.coremodel.entity.quiz.QuizAttempt;
import com.oep.coremodel.enums.QuizAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Read-only mirror of assessment-service's own repository (shared table, same pattern as EnrollmentRepository). */
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {
    boolean existsByQuiz_IdAndEnrollment_Id(UUID quizId, UUID enrollmentId);
    java.util.List<com.oep.coremodel.entity.quiz.QuizAttempt> findByQuiz_IdAndEnrollment_IdOrderByAttemptNumberAsc(UUID quizId, UUID enrollmentId);
    boolean existsByEnrollment_IdAndQuiz_Course_IdAndStatus(UUID enrollmentId, UUID courseId, QuizAttemptStatus status);
}
