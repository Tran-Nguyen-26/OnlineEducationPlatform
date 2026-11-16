package com.oep.corelms.dto.admin;

import com.oep.coremodel.entity.quiz.Quiz;
import com.oep.coremodel.enums.QuizGradingPolicy;

import java.time.Instant;
import java.util.UUID;

public record QuizResponse(
        UUID id,
        UUID courseId,
        UUID lessonId,
        String title,
        String description,
        Integer timeLimitSeconds,
        Instant deadline,
        int maxAttempts,
        QuizGradingPolicy gradingPolicy,
        boolean randomizeQuestions,
        boolean randomizeAnswers,
        boolean showScore,
        boolean showCorrectAnswers,
        boolean locked,
        int questionCount,
        Integer randomQuestionCount) {

    public static QuizResponse from(Quiz quiz, int questionCount) {
        return new QuizResponse(
                quiz.getId(),
                quiz.getCourse().getId(),
                quiz.getLesson() == null ? null : quiz.getLesson().getId(),
                quiz.getTitle(),
                quiz.getDescription(),
                quiz.getTimeLimitSeconds(),
                quiz.getDeadline(),
                quiz.getMaxAttempts(),
                quiz.getGradingPolicy(),
                Boolean.TRUE.equals(quiz.getRandomizeQuestions()),
                Boolean.TRUE.equals(quiz.getRandomizeAnswers()),
                Boolean.TRUE.equals(quiz.getShowScore()),
                Boolean.TRUE.equals(quiz.getShowCorrectAnswers()),
                quiz.isLocked(),
                questionCount,
                quiz.getRandomQuestionCount());
    }
}
