package com.oep.corelms.repository;

import com.oep.coremodel.entity.quiz.QuizResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface QuizResultRepository extends JpaRepository<QuizResult, UUID> {
    Optional<QuizResult> findByAttemptId(UUID attemptId);

    @Query("SELECT qr FROM QuizResult qr JOIN qr.attempt a WHERE a.quiz.id = :quizId")
    Page<QuizResult> findByQuizId(@Param("quizId") UUID quizId, Pageable pageable);

    @Query("SELECT qr FROM QuizResult qr JOIN qr.attempt a WHERE qr.id = :resultId AND a.quiz.id = :quizId")
    Optional<QuizResult> findByIdAndQuizId(@Param("resultId") UUID resultId, @Param("quizId") UUID quizId);
}
