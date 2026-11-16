package com.oep.assessment.service;

import com.oep.assessment.dto.QuizGradingDtos;
import com.oep.assessment.repository.CourseInstructorRepository;
import com.oep.assessment.repository.QuizAnswerRepository;
import com.oep.assessment.repository.QuizAttemptRepository;
import com.oep.assessment.repository.QuizQuestionRepository;
import com.oep.assessment.repository.QuizResultRepository;
import com.oep.assessment.repository.UserProfileRepository;
import com.oep.assessment.repository.UserRepository;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.entity.quiz.Quiz;
import com.oep.coremodel.entity.quiz.QuizAnswer;
import com.oep.coremodel.entity.quiz.QuizAttempt;
import com.oep.coremodel.entity.quiz.QuizQuestion;
import com.oep.coremodel.entity.quiz.QuizResult;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Manual grading of essay answers and instructor/admin overrides of a quiz attempt's final score. */
@Service
@RequiredArgsConstructor
public class QuizGradingService {

    private final QuizAnswerRepository quizAnswers;
    private final QuizAttemptRepository attempts;
    private final QuizResultRepository quizResults;
    private final QuizQuestionRepository quizQuestions;
    private final CourseInstructorRepository courseInstructors;
    private final UserRepository users;
    private final UserProfileRepository userProfiles;
    private final QuizAttemptService quizAttemptService;
    private final NotificationPublisher notifications;

    @Transactional(readOnly = true)
    public List<QuizGradingDtos.PendingEssay> pendingEssays(UUID userId, String role) {
        return quizAnswers.findPendingEssays().stream()
                .filter(a -> canView(a.getAttempt().getQuiz().getCourse().getId(), userId, role))
                .map(a -> {
                    QuizAttempt at = a.getAttempt();
                    Quiz quiz = at.getQuiz();
                    User st = at.getEnrollment().getStudent();
                    String name = userProfiles.findById(st.getId()).map(UserProfile::getFullName).orElse(st.getEmail());
                    BigDecimal points = quizQuestions.findByQuiz_IdOrderByPositionAsc(quiz.getId()).stream()
                            .filter(qq -> qq.getQuestion().getId().equals(a.getQuestion().getId()))
                            .map(QuizQuestion::getPoints).findFirst().orElse(BigDecimal.ONE);
                    return new QuizGradingDtos.PendingEssay(a.getId(), at.getId(), quiz.getId(), quiz.getTitle(), quiz.getCourse().getId(),
                            quiz.getCourse().getTitle(), name, st.getEmail(), a.getQuestion().getQuestionText(), points,
                            a.getTextAnswer(), at.getSubmittedAt());
                }).toList();
    }

    @Transactional
    public void gradeEssay(UUID answerId, UUID userId, String role, QuizGradingDtos.GradeEssayRequest req) {
        QuizAnswer answer = quizAnswers.findById(answerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Answer not found"));
        QuizAttempt attempt = answer.getAttempt();
        Quiz quiz = attempt.getQuiz();
        requireGrader(quiz.getCourse().getId(), userId, role);

        BigDecimal max = quizQuestions.findByQuiz_IdOrderByPositionAsc(quiz.getId()).stream()
                .filter(qq -> qq.getQuestion().getId().equals(answer.getQuestion().getId()))
                .map(QuizQuestion::getPoints).findFirst().orElse(BigDecimal.ONE);
        if (req.score().compareTo(max) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Score exceeds the question's maximum (" + max + ")");
        }
        answer.setAwardedScore(req.score());
        answer.setCorrect(req.score().compareTo(max) >= 0);
        answer.setFeedback(req.feedback());
        quizAnswers.saveAndFlush(answer);

        QuizResult result = quizResults.findByAttempt_Id(attempt.getId()).orElse(null);
        if (result != null && result.getAdjustedBy() == null) {
            BigDecimal total = quizAttemptService.recomputeScore(attempt, quizQuestions.findByQuiz_IdOrderByPositionAsc(quiz.getId()));
            result.setAutoScore(total);
            result.setFinalScore(total);
            quizResults.save(result);
        }
        if (!quizAnswers.hasPendingEssay(attempt.getId())) {
            UUID studentId = attempt.getEnrollment().getStudent().getId();
            BigDecimal fin = result == null ? null : result.getFinalScore();
            notifications.send(studentId, "QUIZ_GRADED", "Quiz đã được chấm — " + quiz.getCourse().getTitle(),
                    "Quiz \"" + quiz.getTitle() + "\" (lần " + attempt.getAttemptNumber() + ") đã chấm xong"
                            + (fin != null ? ": " + fin.stripTrailingZeros().toPlainString() + " điểm" : ""),
                    "quiz-graded:" + attempt.getId(), quiz.getCourse().getId());
        }
    }

    @Transactional
    public void adjustScore(UUID attemptId, UUID userId, String role, QuizGradingDtos.AdjustScoreRequest req) {
        QuizAttempt attempt = attempts.findById(attemptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Attempt not found"));
        Quiz quiz = attempt.getQuiz();
        requireGrader(quiz.getCourse().getId(), userId, role);
        QuizResult result = quizResults.findByAttempt_Id(attemptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Attempt has no result yet"));
        BigDecimal max = quizQuestions.findByQuiz_IdOrderByPositionAsc(quiz.getId()).stream()
                .map(QuizQuestion::getPoints).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (req.finalScore().compareTo(max) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Score exceeds the quiz maximum (" + max + ")");
        }
        if (req.reason() == null || req.reason().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A reason is required when adjusting a score");
        }
        result.setFinalScore(req.finalScore());
        result.setAdjustedBy(users.getReferenceById(userId));
        result.setAdjustmentReason(req.reason().trim());
        quizResults.save(result);
        notifications.send(attempt.getEnrollment().getStudent().getId(), "QUIZ_SCORE_ADJUSTED",
                "Điểm quiz được điều chỉnh — " + quiz.getCourse().getTitle(),
                "Quiz \"" + quiz.getTitle() + "\" (lần " + attempt.getAttemptNumber() + "): "
                        + req.finalScore().stripTrailingZeros().toPlainString() + " điểm. Lý do: " + req.reason().trim(),
                "quiz-adjust:" + attemptId + ":" + System.currentTimeMillis(), quiz.getCourse().getId());
    }

    /** Admins can look at the queues (oversight) but grading is the assigned instructor's job. */
    private boolean canView(UUID courseId, UUID userId, String role) {
        return "ADMIN".equals(role) || canGrade(courseId, userId, role);
    }

    private boolean canGrade(UUID courseId, UUID userId, String role) {
        return "INSTRUCTOR".equals(role) && courseInstructors.existsByCourse_IdAndInstructor_Id(courseId, userId);
    }

    private void requireGrader(UUID courseId, UUID userId, String role) {
        if (!canGrade(courseId, userId, role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ giáo viên phụ trách khóa học mới được chấm bài");
        }
    }
}
