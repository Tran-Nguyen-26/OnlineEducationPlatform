package com.oep.assessment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.assessment.dto.AttemptResultResponse;
import com.oep.assessment.dto.AttemptResultResponse.QuestionResult;
import com.oep.assessment.dto.AttemptSummaryResponse;
import com.oep.assessment.dto.StartAttemptResponse;
import com.oep.assessment.dto.StartAttemptResponse.OptionForAttempt;
import com.oep.assessment.dto.StartAttemptResponse.QuestionForAttempt;
import com.oep.assessment.dto.StudentQuizResponse;
import com.oep.assessment.dto.SubmitAttemptRequest;
import com.oep.assessment.dto.SubmitAttemptRequest.AnswerInput;
import com.oep.assessment.repository.EnrollmentRepository;
import com.oep.assessment.repository.QuestionOptionRepository;
import com.oep.assessment.repository.QuizAnswerRepository;
import com.oep.assessment.repository.QuizAttemptRepository;
import com.oep.assessment.repository.QuizQuestionRepository;
import com.oep.assessment.repository.QuizRepository;
import com.oep.assessment.repository.QuizResultRepository;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.quiz.Question;
import com.oep.coremodel.entity.quiz.QuestionOption;
import com.oep.coremodel.entity.quiz.Quiz;
import com.oep.coremodel.entity.quiz.QuizAnswer;
import com.oep.coremodel.entity.quiz.QuizAttempt;
import com.oep.coremodel.entity.quiz.QuizQuestion;
import com.oep.coremodel.entity.quiz.QuizResult;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.enums.QuestionType;
import com.oep.coremodel.enums.QuizAttemptStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Starts/submits quiz attempts and auto-grades choice + keyword-fill questions. */
@Service
@RequiredArgsConstructor
public class QuizAttemptService {

    private final QuizRepository quizzes;
    private final QuizQuestionRepository quizQuestions;
    private final QuestionOptionRepository questionOptions;
    private final QuizAttemptRepository attempts;
    private final QuizAnswerRepository quizAnswers;
    private final QuizResultRepository quizResults;
    private final EnrollmentRepository enrollments;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<StudentQuizResponse> listForCourse(UUID courseId, UUID studentId) {
        Enrollment enrollment = requireEnrollment(courseId, studentId);
        return quizzes.findByCourse_IdOrderByCreatedAtDesc(courseId).stream().map(quiz -> toResponse(quiz, enrollment)).toList();
    }

    @Transactional(readOnly = true)
    public List<AttemptSummaryResponse> listMyAttempts(UUID quizId, UUID studentId) {
        Quiz quiz = findQuiz(quizId);
        Enrollment enrollment = requireEnrollment(quiz.getCourse().getId(), studentId);
        boolean showScore = Boolean.TRUE.equals(quiz.getShowScore());
        return attempts.findByQuiz_IdAndEnrollment_IdOrderByAttemptNumberAsc(quizId, enrollment.getId()).stream()
                .map(a -> new AttemptSummaryResponse(a.getId(), a.getAttemptNumber(), a.getStatus(), a.getSubmittedAt(),
                        showScore ? quizResults.findByAttempt_Id(a.getId()).map(QuizResult::getFinalScore).orElse(null) : null,
                        quizAnswers.hasPendingEssay(a.getId())))
                .toList();
    }

    @Transactional
    public StartAttemptResponse startAttempt(UUID quizId, UUID studentId) {
        Quiz quiz = findQuiz(quizId);
        Enrollment enrollment = requireEnrollment(quiz.getCourse().getId(), studentId);
        if (quiz.isLocked()) {
            throw new BusinessException(ErrorCode.QUIZ_LOCKED, "Quiz is locked");
        }
        if (quiz.getDeadline() != null && Instant.now().isAfter(quiz.getDeadline())) {
            throw new BusinessException(ErrorCode.DEADLINE_PASSED, "Quiz deadline has passed");
        }
        CoursePhaseGuard.requireOpen(quiz.getCourse());

        List<QuizAttempt> existing = attempts.findByQuiz_IdAndEnrollment_IdOrderByAttemptNumberAsc(quizId, enrollment.getId());
        Optional<QuizAttempt> inProgress = existing.stream().filter(a -> a.getStatus() == QuizAttemptStatus.IN_PROGRESS).findFirst();
        List<QuizQuestion> pool = quizQuestions.findByQuiz_IdOrderByPositionAsc(quizId);

        if (inProgress.isPresent()) {
            return buildStartResponse(inProgress.get(), questionsFor(inProgress.get(), pool), quiz);
        }
        if (existing.size() >= quiz.getMaxAttempts()) {
            throw new BusinessException(ErrorCode.ATTEMPT_LIMIT_REACHED, "No attempts left for this quiz");
        }

        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setEnrollment(enrollment);
        attempt.setAttemptNumber(existing.size() + 1);
        List<QuizQuestion> drawn = draw(quiz, pool);
        attempt.setSnapshotPayload(toJson(drawn.stream().map(qq -> qq.getQuestion().getId()).toList()));
        attempt = attempts.save(attempt);
        return buildStartResponse(attempt, drawn, quiz);
    }

    @Transactional
    public AttemptResultResponse submitAttempt(UUID attemptId, UUID studentId, SubmitAttemptRequest request) {
        QuizAttempt attempt = attempts.findById(attemptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Attempt not found"));
        if (!attempt.getEnrollment().getStudent().getId().equals(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This attempt does not belong to you");
        }
        if (attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Attempt was already submitted");
        }

        Quiz quiz = attempt.getQuiz();
        List<QuizQuestion> pool = quizQuestions.findByQuiz_IdOrderByPositionAsc(quiz.getId());
        List<QuizQuestion> questions = questionsFor(attempt, pool);
        List<UUID> questionIds = questions.stream().map(qq -> qq.getQuestion().getId()).toList();
        Map<UUID, List<QuestionOption>> optionsByQuestion = questionOptions.findByQuestion_IdIn(questionIds).stream()
                .collect(Collectors.groupingBy(o -> o.getQuestion().getId()));
        Map<UUID, AnswerInput> answerByQuestion = request.answers() == null ? Map.of()
                : request.answers().stream().collect(Collectors.toMap(AnswerInput::questionId, a -> a, (a, b) -> a));

        List<QuestionResult> questionResults = new ArrayList<>();
        int pendingManual = 0;

        for (QuizQuestion qq : questions) {
            Question question = qq.getQuestion();
            AnswerInput input = answerByQuestion.get(question.getId());
            Boolean correct;
            BigDecimal awarded;
            String selectedJson = null;
            String textAnswer = input == null ? null : input.textAnswer();

            if (question.getQuestionType() == QuestionType.ESSAY) {
                // Graded by hand later: no correctness / score yet.
                correct = null;
                awarded = null;
                pendingManual++;
            } else if (question.getQuestionType() == QuestionType.KEYWORD_FILL) {
                String expected = question.getKeywordAnswer() == null ? "" : question.getKeywordAnswer().trim();
                String actual = textAnswer == null ? "" : textAnswer.trim();
                correct = !expected.isEmpty() && expected.equalsIgnoreCase(actual);
                awarded = correct ? qq.getPoints() : BigDecimal.ZERO;
            } else {
                List<UUID> selected = input == null || input.selectedOptionIds() == null ? List.of() : input.selectedOptionIds();
                Set<UUID> correctIds = optionsByQuestion.getOrDefault(question.getId(), List.of()).stream()
                        .filter(QuestionOption::isCorrect).map(QuestionOption::getId).collect(Collectors.toSet());
                correct = !selected.isEmpty() && new HashSet<>(selected).equals(correctIds);
                selectedJson = toJson(selected);
                awarded = correct ? qq.getPoints() : BigDecimal.ZERO;
            }

            QuizAnswer answerEntity = new QuizAnswer();
            answerEntity.setAttempt(attempt);
            answerEntity.setQuestion(question);
            answerEntity.setSelectedOptionData(selectedJson);
            answerEntity.setTextAnswer(textAnswer);
            answerEntity.setCorrect(correct);
            answerEntity.setAwardedScore(awarded);
            quizAnswers.save(answerEntity);

            questionResults.add(new QuestionResult(question.getId(), correct, awarded));
        }
        quizAnswers.flush();

        boolean timedOut = quiz.getTimeLimitSeconds() != null
                && Instant.now().isAfter(attempt.getStartedAt().plusSeconds(quiz.getTimeLimitSeconds()));
        attempt.setStatus(timedOut ? QuizAttemptStatus.TIMED_OUT : QuizAttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(Instant.now());
        attempts.save(attempt);

        QuizResult result = quizResults.findByAttempt_Id(attempt.getId()).orElseGet(() -> {
            QuizResult r = new QuizResult();
            r.setAttempt(attempt);
            return r;
        });
        BigDecimal total = recomputeScore(attempt, pool);
        result.setAutoScore(total);
        result.setFinalScore(total);
        quizResults.save(result);

        BigDecimal shownScore = Boolean.TRUE.equals(quiz.getShowScore()) ? total : null;
        List<QuestionResult> shownDetails = Boolean.TRUE.equals(quiz.getShowCorrectAnswers()) ? questionResults : List.of();
        return new AttemptResultResponse(attempt.getId(), attempt.getAttemptNumber(), attempt.getStatus(),
                attempt.getSubmittedAt(), shownScore, shownDetails, pendingManual);
    }

    /** Questions of this attempt in the order they were drawn (snapshot); legacy attempts fall back to the whole pool. */
    List<QuizQuestion> questionsFor(QuizAttempt attempt, List<QuizQuestion> pool) {
        if (attempt.getSnapshotPayload() == null) {
            return pool;
        }
        try {
            List<UUID> ids = objectMapper.readValue(attempt.getSnapshotPayload(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, UUID.class));
            Map<UUID, QuizQuestion> byId = pool.stream().collect(Collectors.toMap(qq -> qq.getQuestion().getId(), qq -> qq));
            return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        } catch (Exception e) {
            return pool;
        }
    }

    /** Random subset when the quiz is configured to draw N questions, optionally shuffled; otherwise fixed order. */
    private List<QuizQuestion> draw(Quiz quiz, List<QuizQuestion> pool) {
        List<QuizQuestion> chosen = new ArrayList<>(pool);
        Integer n = quiz.getRandomQuestionCount();
        if (n != null && n > 0 && n < chosen.size()) {
            Collections.shuffle(chosen);
            chosen = new ArrayList<>(chosen.subList(0, n));
            chosen.sort(Comparator.comparingInt(QuizQuestion::getPosition));
        }
        if (Boolean.TRUE.equals(quiz.getRandomizeQuestions())) {
            Collections.shuffle(chosen);
        }
        return chosen;
    }

    /**
     * Sum of awarded points of the attempt's questions (pending essays count 0 until graded), scaled to the full pool's
     * maximum when only a random subset was drawn so every attempt is out of the same total.
     */
    BigDecimal recomputeScore(QuizAttempt attempt, List<QuizQuestion> pool) {
        List<QuizQuestion> drawn = questionsFor(attempt, pool);
        BigDecimal earned = quizAnswers.findByAttempt_Id(attempt.getId()).stream()
                .map(a -> a.getAwardedScore() == null ? BigDecimal.ZERO : a.getAwardedScore())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal poolMax = pool.stream().map(QuizQuestion::getPoints).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal drawnMax = drawn.stream().map(QuizQuestion::getPoints).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (drawn.size() < pool.size() && drawnMax.signum() > 0) {
            earned = earned.multiply(poolMax).divide(drawnMax, 2, RoundingMode.HALF_UP);
        }
        return earned.setScale(2, RoundingMode.HALF_UP);
    }

    private StartAttemptResponse buildStartResponse(QuizAttempt attempt, List<QuizQuestion> questions, Quiz quiz) {
        List<UUID> questionIds = questions.stream().map(qq -> qq.getQuestion().getId()).toList();
        Map<UUID, List<QuestionOption>> optionsByQuestion = questionOptions.findByQuestion_IdIn(questionIds).stream()
                .collect(Collectors.groupingBy(o -> o.getQuestion().getId()));
        boolean shuffleOptions = Boolean.TRUE.equals(quiz.getRandomizeAnswers());

        List<QuestionForAttempt> questionDtos = new ArrayList<>();
        int index = 0;
        for (QuizQuestion qq : questions) {
            Question question = qq.getQuestion();
            List<OptionForAttempt> options = new ArrayList<>(optionsByQuestion.getOrDefault(question.getId(), List.of()).stream()
                    .sorted(Comparator.comparingInt(QuestionOption::getPosition))
                    .map(o -> new OptionForAttempt(o.getId(), o.getOptionText(), o.getPosition()))
                    .toList());
            if (shuffleOptions && question.getQuestionType() != QuestionType.TRUE_FALSE) {
                Collections.shuffle(options);
            }
            questionDtos.add(new QuestionForAttempt(question.getId(), question.getQuestionType(), question.getQuestionText(),
                    index++, qq.getPoints(), options));
        }

        return new StartAttemptResponse(attempt.getId(), attempt.getAttemptNumber(), attempt.getStartedAt(),
                attempt.getQuiz().getTimeLimitSeconds(), questionDtos);
    }

    private StudentQuizResponse toResponse(Quiz quiz, Enrollment enrollment) {
        List<QuizAttempt> myAttempts = attempts.findByQuiz_IdAndEnrollment_IdOrderByAttemptNumberAsc(quiz.getId(), enrollment.getId());
        int used = myAttempts.size();
        boolean canAttempt = !quiz.isLocked() && used < quiz.getMaxAttempts()
                && (quiz.getDeadline() == null || Instant.now().isBefore(quiz.getDeadline()));
        BigDecimal best = Boolean.TRUE.equals(quiz.getShowScore()) ? computeBestScore(quiz, myAttempts) : null;
        return new StudentQuizResponse(quiz.getId(), quiz.getCourse().getId(),
                quiz.getLesson() == null ? null : quiz.getLesson().getId(), quiz.getTitle(), quiz.getDescription(),
                quiz.getTimeLimitSeconds(), quiz.getDeadline(), quiz.getMaxAttempts(), quiz.getGradingPolicy(),
                quiz.isLocked(), used, canAttempt, best);
    }

    private BigDecimal computeBestScore(Quiz quiz, List<QuizAttempt> myAttempts) {
        List<BigDecimal> scores = myAttempts.stream()
                .map(a -> quizResults.findByAttempt_Id(a.getId()).map(QuizResult::getFinalScore).orElse(null))
                .filter(Objects::nonNull)
                .toList();
        if (scores.isEmpty()) {
            return null;
        }
        return switch (quiz.getGradingPolicy()) {
            case HIGHEST -> scores.stream().max(Comparator.naturalOrder()).orElse(null);
            case LATEST -> scores.get(scores.size() - 1);
            case AVERAGE -> scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
        };
    }

    private Quiz findQuiz(UUID quizId) {
        return quizzes.findById(quizId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Quiz not found"));
    }

    private Enrollment requireEnrollment(UUID courseId, UUID studentId) {
        Enrollment enrollment = enrollments.findByCourseIdAndStudentId(courseId, studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_ENROLLED, "Not enrolled in this course"));
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_ENROLLED, "Enrollment is not active");
        }
        return enrollment;
    }

    private String toJson(List<UUID> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize selected options", e);
        }
    }
}
