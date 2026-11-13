package com.oep.corelms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.corelms.repository.*;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.quiz.*;
import com.oep.coremodel.enums.*;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LearnerQuizService {
    private final EnrollmentRepository enrollments;
    private final QuizRepository quizzes;
    private final QuizQuestionRepository questions;
    private final QuestionOptionRepository options;
    private final QuizAttemptRepository attempts;
    private final QuizAnswerRepository answers;
    private final QuestionRepository questionRepository;
    private final QuizResultRepository results;
    private final LessonMasteryService mastery;
    private final ObjectMapper json;

    public record Option(UUID id, String option_text, int position) {}
    public record Prompt(UUID id, String question_type, String question_text, BigDecimal points, List<Option> options) {}
    public record Start(UUID attemptId, Instant expiresAt, List<Prompt> questions) {}
    public record Score(BigDecimal achieved, BigDecimal total, int correctCount) {}
    @com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AvailableQuiz(UUID id, UUID courseId, UUID lessonId, String title, String description,
                                Integer timeLimitSeconds, Instant deadline, int maxAttempts, String gradingPolicy,
                                boolean locked, Boolean showScore) {
        @com.fasterxml.jackson.annotation.JsonProperty("is_locked")
        public boolean locked() { return locked; }
    }
    public record Item(Prompt prompt, Set<UUID> correctOptions, String keyword, UUID outcomeId) {}
    public record Snapshot(Instant expiresAt, List<Item> items) {}

    @Transactional(readOnly = true)
    public List<AvailableQuiz> list(UUID actor, UUID course) {
        Enrollment enrollment = enrollments.findByCourseIdAndStudentId(course, actor)
                .filter(this::eligible).orElseThrow(this::unavailable);
        var unlocked = mastery.lessons(actor, course).stream().filter(LessonMasteryService.Lesson::unlocked)
                .map(LessonMasteryService.Lesson::id).collect(java.util.stream.Collectors.toSet());
        Instant now = Instant.now();
        return quizzes.findByCourseIdAndAssessmentKindInOrderByTitle(course, List.of(Quiz.Kind.PRE_TEST, Quiz.Kind.MINI_QUIZ))
                .stream().map(quiz -> {
                    UUID lesson = quiz.getLesson() == null ? null : quiz.getLesson().getId();
                    boolean pretest = quiz.getAssessmentKind() == Quiz.Kind.PRE_TEST;
                    var history = pretest ? attempts.findByQuizIdAndEnrollmentIdOrderByAttemptNumberDesc(quiz.getId(), enrollment.getId())
                            : List.<QuizAttempt>of();
                    boolean exhausted = !history.isEmpty() && (history.getFirst().getStatus() != QuizAttemptStatus.IN_PROGRESS
                            || history.getFirst().getSnapshotPayload() == null || !now.isBefore(read(history.getFirst()).expiresAt()));
                    boolean locked = quiz.isLocked() || exhausted || (quiz.getDeadline() != null && !now.isBefore(quiz.getDeadline()))
                            || (!pretest && (lesson == null || !unlocked.contains(lesson)));
                    return new AvailableQuiz(quiz.getId(), course, lesson, quiz.getTitle(), quiz.getDescription(),
                            quiz.getTimeLimitSeconds(), quiz.getDeadline(), pretest ? 1 : 0, quiz.getGradingPolicy().name(),
                            locked, quiz.getShowScore());
                }).toList();
    }

    @Transactional
    public Start start(UUID actor, UUID course, UUID quizId) {
        Enrollment enrollment = access(actor, course);
        Quiz quiz = quizzes.findByIdAndCourseIdForUpdate(quizId, course).orElseThrow(this::unavailable);
        if (quiz.getAssessmentKind() != Quiz.Kind.MINI_QUIZ && quiz.getAssessmentKind() != Quiz.Kind.PRE_TEST) throw unavailable();
        if (quiz.isLocked()) throw unavailable();
        if (quiz.getAssessmentKind() == Quiz.Kind.MINI_QUIZ) {
            if (quiz.getLesson() == null) throw unavailable();
            mastery.requireUnlocked(actor, course, quiz.getLesson().getId());
        }
        Instant now = Instant.now();
        if (quiz.getDeadline() != null && !now.isBefore(quiz.getDeadline())) throw unavailable();
        var history = attempts.findByQuizIdAndEnrollmentIdOrderByAttemptNumberDesc(quizId, enrollment.getId());
        QuizAttempt latest = history.isEmpty() ? null : history.getFirst();
        if (latest != null && latest.getStatus() == QuizAttemptStatus.IN_PROGRESS) {
            Snapshot previous = read(latest);
            if (now.isBefore(previous.expiresAt())) return response(latest, previous);
            if (quiz.getAssessmentKind() == Quiz.Kind.MINI_QUIZ) finish(latest, previous, Map.of(), now);
        }
        int number = latest == null ? 1 : latest.getAttemptNumber() + 1;
        if (quiz.getAssessmentKind() == Quiz.Kind.PRE_TEST && number > 1) {
            throw new BusinessException(ErrorCode.ATTEMPT_LIMIT_REACHED, "Pre-test chỉ được làm một lần");
        }
        List<Item> items = new ArrayList<>(questions.findByQuizIdOrderByPositionAsc(quizId).stream()
                .filter(item -> "APPROVED".equals(item.getQuestion().getApprovalState()))
                .map(this::snapshotItem).toList());
        BigDecimal total = items.stream().map(item -> item.prompt().points()).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (items.isEmpty() || items.size() > 500 || total.signum() <= 0 || total.compareTo(new BigDecimal("999.99")) > 0)
            throw unavailable();
        if (quiz.getAssessmentKind() == Quiz.Kind.MINI_QUIZ || Boolean.TRUE.equals(quiz.getRandomizeQuestions()))
            Collections.shuffle(items);
        int timeLimit = quiz.getTimeLimitSeconds() == null ? 1800 : quiz.getTimeLimitSeconds();
        if (timeLimit <= 0) throw unavailable();
        Instant expires = now.plusSeconds(timeLimit);
        if (quiz.getDeadline() != null && quiz.getDeadline().isBefore(expires)) expires = quiz.getDeadline();
        Snapshot snapshot = new Snapshot(expires, items);
        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setEnrollment(enrollment);
        attempt.setAttemptNumber(number);
        attempt.setStartedAt(now);
        attempt.setSnapshotPayload(write(snapshot));
        return response(attempts.save(attempt), snapshot);
    }

    @Transactional
    public Score submit(UUID actor, UUID course, UUID attemptId, Map<UUID, List<String>> submitted) {
        Enrollment enrollment = access(actor, course);
        QuizAttempt attempt = attempts.findById(attemptId)
                .filter(item -> item.getEnrollment().getId().equals(enrollment.getId()))
                .filter(item -> item.getQuiz().getAssessmentKind() == Quiz.Kind.MINI_QUIZ
                        || item.getQuiz().getAssessmentKind() == Quiz.Kind.PRE_TEST).orElseThrow(this::unavailable);
        Snapshot snapshot = read(attempt);
        Instant now = Instant.now();
        return finish(attempt, snapshot, now.isBefore(snapshot.expiresAt()) ? submitted : Map.of(), now);
    }

    private Score finish(QuizAttempt attempt, Snapshot snapshot, Map<UUID, List<String>> submitted, Instant now) {
        BigDecimal total = snapshot.items().stream().map(item -> item.prompt().points()).reduce(BigDecimal.ZERO, BigDecimal::add);
        var existing = results.findByAttemptId(attempt.getId());
        if (existing.isPresent()) {
            return new Score(existing.get().getFinalScore(), total, (int) answers.countByAttemptIdAndCorrectTrue(attempt.getId()));
        }
        if (attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS) throw unavailable();
        int count = 0;
        BigDecimal achieved = BigDecimal.ZERO;
        for (Item item : snapshot.items()) {
            List<String> given = submitted.getOrDefault(item.prompt().id(), List.of());
            boolean correct = matches(item, given);
            BigDecimal points = correct ? item.prompt().points() : BigDecimal.ZERO;
            var answer = new QuizAnswer();
            answer.setAttempt(attempt);
            answer.setQuestion(questionRepository.getReferenceById(item.prompt().id()));
            answer.setSelectedOptionData(write(given));
            answer.setAwardedScore(points);
            answer.setCorrect(correct);
            answers.save(answer);
            if (correct) count++;
            achieved = achieved.add(points);
        }
        QuizResult result = new QuizResult();
        result.setAttempt(attempt);
        result.setAutoScore(achieved);
        result.setFinalScore(achieved);
        results.save(result);
        attempt.setSubmittedAt(now);
        attempt.setStatus(QuizAttemptStatus.GRADED);
        return new Score(achieved, total, count);
    }

    static boolean matches(Item item, List<String> given) {
        if (given == null || given.stream().anyMatch(Objects::isNull)) return false;
        if ("KEYWORD_FILL".equals(item.prompt().question_type())) {
            return given.size() == 1 && item.keyword() != null && item.keyword().strip().equalsIgnoreCase(given.getFirst().strip());
        }
        return !item.correctOptions().isEmpty() && given.size() == item.correctOptions().size()
                && new HashSet<>(given).equals(item.correctOptions().stream().map(UUID::toString).collect(java.util.stream.Collectors.toSet()));
    }

    private Item snapshotItem(QuizQuestion item) {
        var question = item.getQuestion();
        var choices = options.findByQuestionIdOrderByPositionAsc(question.getId());
        var prompt = new Prompt(question.getId(), question.getQuestionType().name(), question.getQuestionText(), item.getPoints(),
                choices.stream().map(choice -> new Option(choice.getId(), choice.getOptionText(), choice.getPosition())).toList());
        return new Item(prompt, choices.stream().filter(QuestionOption::isCorrect).map(QuestionOption::getId)
                .collect(java.util.stream.Collectors.toSet()), question.getKeywordAnswer(),
                question.getLearningOutcome() == null ? null : question.getLearningOutcome().getId());
    }

    private Enrollment access(UUID actor, UUID course) {
        return enrollments.lockForAssessment(course, actor).filter(this::eligible).orElseThrow(this::unavailable);
    }

    private boolean eligible(Enrollment item) {
        return item.getStatus() == EnrollmentStatus.ACTIVE
                && item.getStudent().getAccountStatus() == AccountStatus.ACTIVE
                && item.getStudent().getRole() == UserRole.STUDENT
                && item.getCourse().getLifecycleStatus() == CourseLifecycleStatus.PUBLISHED
                && (item.getAccessExpiresAt() == null || !Instant.now().isAfter(item.getAccessExpiresAt()));
    }

    private Start response(QuizAttempt attempt, Snapshot snapshot) {
        return new Start(attempt.getId(), snapshot.expiresAt(), snapshot.items().stream().map(Item::prompt).toList());
    }

    private Snapshot read(QuizAttempt attempt) {
        if (attempt.getSnapshotPayload() == null) throw unavailable();
        try {
            return json.readValue(attempt.getSnapshotPayload(), Snapshot.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid assessment snapshot", ex);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not save assessment", ex);
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.FORBIDDEN, "Bài kiểm tra chưa khả dụng");
    }
}
