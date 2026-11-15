package com.oep.corelms.service;

import com.oep.corelms.dto.admin.CreateQuizRequest;
import com.oep.corelms.dto.admin.QuizResponse;
import com.oep.corelms.dto.admin.UpdateQuizRequest;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.corelms.repository.QuizQuestionRepository;
import com.oep.corelms.repository.QuizRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.quiz.Quiz;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminQuizService {

    private final CourseRepository courses;
    private final LessonRepository lessons;
    private final QuizRepository quizzes;
    private final QuizQuestionRepository quizQuestions;
    private final UserRepository users;

    @Transactional
    public QuizResponse create(UUID courseId, UUID adminId, CreateQuizRequest request) {
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));

        Quiz quiz = new Quiz();
        quiz.setCourse(course);
        quiz.setLesson(resolveLesson(request.lessonId()));
        quiz.setCreatedBy(users.getReferenceById(adminId));
        applyFields(quiz, request.title(), request.description(), request.timeLimitSeconds(), request.deadline(),
                request.maxAttempts(), request.gradingPolicy(), request.randomizeQuestions(), request.randomizeAnswers(),
                request.showScore(), request.showCorrectAnswers());

        return QuizResponse.from(quizzes.saveAndFlush(quiz), 0);
    }

    @Transactional(readOnly = true)
    public List<QuizResponse> list(UUID courseId) {
        return quizzes.findByCourse_IdOrderByCreatedAtDesc(courseId).stream()
                .map(quiz -> QuizResponse.from(quiz, quizQuestions.findByQuiz_IdOrderByPositionAsc(quiz.getId()).size()))
                .toList();
    }

    @Transactional
    public QuizResponse update(UUID quizId, UpdateQuizRequest request) {
        Quiz quiz = findOrThrow(quizId);
        if (quiz.isLocked()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Quiz is locked");
        }
        quiz.setLesson(resolveLesson(request.lessonId()));
        applyFields(quiz, request.title(), request.description(), request.timeLimitSeconds(), request.deadline(),
                request.maxAttempts(), request.gradingPolicy(), request.randomizeQuestions(), request.randomizeAnswers(),
                request.showScore(), request.showCorrectAnswers());

        int questionCount = quizQuestions.findByQuiz_IdOrderByPositionAsc(quizId).size();
        return QuizResponse.from(quizzes.saveAndFlush(quiz), questionCount);
    }

    @Transactional
    public QuizResponse setLocked(UUID quizId, boolean locked) {
        Quiz quiz = findOrThrow(quizId);
        quiz.setLocked(locked);
        int questionCount = quizQuestions.findByQuiz_IdOrderByPositionAsc(quizId).size();
        return QuizResponse.from(quizzes.saveAndFlush(quiz), questionCount);
    }

    @Transactional
    public void delete(UUID quizId) {
        findOrThrow(quizId);
        quizzes.deleteById(quizId);
    }

    Quiz findOrThrow(UUID quizId) {
        return quizzes.findById(quizId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Quiz not found"));
    }

    private Lesson resolveLesson(UUID lessonId) {
        if (lessonId == null) return null;
        return lessons.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Lesson not found"));
    }

    private void applyFields(Quiz quiz, String title, String description, Integer timeLimitSeconds,
                              java.time.Instant deadline, Integer maxAttempts,
                              com.oep.coremodel.enums.QuizGradingPolicy gradingPolicy, Boolean randomizeQuestions,
                              Boolean randomizeAnswers, Boolean showScore, Boolean showCorrectAnswers) {
        quiz.setTitle(title);
        quiz.setDescription(description);
        quiz.setTimeLimitSeconds(timeLimitSeconds);
        quiz.setDeadline(deadline);
        if (maxAttempts != null) quiz.setMaxAttempts(maxAttempts);
        if (gradingPolicy != null) quiz.setGradingPolicy(gradingPolicy);
        if (randomizeQuestions != null) quiz.setRandomizeQuestions(randomizeQuestions);
        if (randomizeAnswers != null) quiz.setRandomizeAnswers(randomizeAnswers);
        if (showScore != null) quiz.setShowScore(showScore);
        if (showCorrectAnswers != null) quiz.setShowCorrectAnswers(showCorrectAnswers);
    }
}
