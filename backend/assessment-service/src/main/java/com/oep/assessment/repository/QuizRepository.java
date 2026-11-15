package com.oep.assessment.repository;

import com.oep.coremodel.entity.quiz.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {
    List<Quiz> findByCourse_IdOrderByCreatedAtDesc(UUID courseId);

    List<Quiz> findByLockedFalseAndDeadlineBetween(Instant from, Instant to);
}
