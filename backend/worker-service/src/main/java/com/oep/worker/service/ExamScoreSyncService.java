package com.oep.worker.service;

import com.oep.coremodel.entity.quiz.QuizResult;
import com.oep.coremodel.enums.QuizAttemptStatus;
import com.oep.coremodel.event.ExamScoreSyncEvent;
import com.oep.worker.repository.QuizAttemptSyncRepository;
import com.oep.worker.repository.QuizQuestionSyncRepository;
import com.oep.worker.repository.QuizResultSyncRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ExamScoreSyncService {

    private final QuizAttemptSyncRepository attempts;
    private final QuizResultSyncRepository results;
    private final QuizQuestionSyncRepository questions;

    /**
     * Applies a completed exam event once. The attempt row lock serializes duplicate deliveries,
     * while the unique result-per-attempt constraint is the final idempotency guard.
     */
    @Transactional
    public void sync(ExamScoreSyncEvent event) {
        Objects.requireNonNull(event, "Exam score event is required");
        if (event.attemptId() == null || event.quizId() == null || event.enrollmentId() == null
                || event.score() == null || event.submittedAt() == null) {
            throw new IllegalArgumentException("Exam score event is missing required fields");
        }
        if (event.score().signum() < 0 || event.score().scale() > 2) {
            throw new IllegalArgumentException("Exam score must be non-negative with at most two decimals");
        }

        var attempt = attempts.findForScoreSync(event.attemptId())
                .orElseThrow(() -> new IllegalStateException("Exam attempt does not exist: " + event.attemptId()));
        if (!event.quizId().equals(attempt.getQuiz().getId())
                || !event.enrollmentId().equals(attempt.getEnrollment().getId())) {
            throw new IllegalArgumentException("Exam score event references a different quiz or enrollment");
        }
        if (attempt.getSubmittedAt() != null && event.submittedAt().isBefore(attempt.getSubmittedAt())) {
            return; // Ignore a delayed event that predates the persisted submission.
        }

        BigDecimal maximum = questions.sumPointsByQuizId(event.quizId());
        if (event.score().compareTo(maximum) > 0) {
            throw new IllegalArgumentException("Exam score exceeds the quiz's available points");
        }

        var existing = results.findByAttemptId(event.attemptId()).orElse(null);
        if (existing != null) {
            // Never overwrite a previously persisted grade or an instructor score adjustment.
            if (existing.getAdjustedBy() == null && existing.getAutoScore().compareTo(event.score()) != 0) {
                throw new IllegalStateException("Conflicting score event for an already graded attempt");
            }
            markGraded(attempt, event);
            return;
        }

        QuizResult result = new QuizResult();
        result.setAttempt(attempt);
        result.setAutoScore(event.score());
        result.setFinalScore(event.score());
        results.save(result);
        markGraded(attempt, event);
    }

    private void markGraded(com.oep.coremodel.entity.quiz.QuizAttempt attempt, ExamScoreSyncEvent event) {
        attempt.setStatus(QuizAttemptStatus.GRADED);
        if (attempt.getSubmittedAt() == null || event.submittedAt().isAfter(attempt.getSubmittedAt())) {
            attempt.setSubmittedAt(event.submittedAt());
        }
        attempts.save(attempt);
    }
}
