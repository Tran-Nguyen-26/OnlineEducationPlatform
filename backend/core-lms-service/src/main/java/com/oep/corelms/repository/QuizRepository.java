package com.oep.corelms.repository;

import com.oep.coremodel.entity.quiz.Quiz;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    Page<Quiz> findByCourseId(UUID courseId, Pageable pageable);

    Optional<Quiz> findByIdAndCourseId(UUID id, UUID courseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM Quiz q WHERE q.id = :id AND q.course.id = :courseId")
    Optional<Quiz> findByIdAndCourseIdForUpdate(@Param("id") UUID id, @Param("courseId") UUID courseId);

    @Query("SELECT q FROM Quiz q WHERE (:courseId IS NULL OR q.course.id = :courseId)")
    Page<Quiz> findAllAdmin(@Param("courseId") UUID courseId, Pageable pageable);

    @Query("SELECT COUNT(a) FROM QuizAttempt a WHERE a.quiz.id = :quizId")
    long countAttemptsByQuizId(@Param("quizId") UUID quizId);
}
