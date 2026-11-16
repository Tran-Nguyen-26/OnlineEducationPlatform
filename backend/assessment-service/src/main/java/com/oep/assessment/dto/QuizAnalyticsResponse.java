package com.oep.assessment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Per-quiz drill-down for instructors: every attempt plus item analysis per question. */
public record QuizAnalyticsResponse(UUID quizId, String quizTitle, BigDecimal maxScore, int maxAttempts,
                                    int attemptCount, int studentCount, BigDecimal averageScore,
                                    List<AttemptRow> attempts, List<QuestionStat> questions) {

    public record AttemptRow(UUID attemptId, UUID studentId, String studentName, String studentEmail, int attemptNumber,
                             String status, Instant startedAt, Instant submittedAt, Long durationSeconds,
                             BigDecimal autoScore, BigDecimal finalScore, String adjustmentReason) {}

    /** correctCount / answeredCount is the item difficulty; low ratios flag hard or mis-keyed questions. */
    public record QuestionStat(UUID questionId, int position, String text, String type, BigDecimal points,
                               int answeredCount, int correctCount) {}
}
