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
    private final HeartbeatBuffer heartbeat;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final ObjectMapper json;

    public record Option(UUID id, String option_text, int position) {}
    public record Prompt(UUID id, String question_type, String question_text, BigDecimal points, List<Option> options) {}
    public record Start(UUID attemptId, Instant expiresAt, List<Prompt> questions) {}
    public record Score(BigDecimal achieved, BigDecimal total, int correctCount) {}
    @com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AvailableQuiz(UUID id, UUID courseId, UUID lessonId, String title, String description,
                                Integer timeLimitSeconds, Instant deadline, int maxAttempts, String gradingPolicy,
                                boolean locked, Boolean showScore, Quiz.Kind assessmentKind,
                                String attemptState, Instant retryAfter, BigDecimal effectivePercent) {
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
        return quizzes.findByCourseIdAndAssessmentKindInOrderByTitle(course, List.of(Quiz.Kind.PRE_TEST, Quiz.Kind.MINI_QUIZ, Quiz.Kind.POST_TEST))
                .stream().map(quiz -> {
                    UUID lesson = quiz.getLesson() == null ? null : quiz.getLesson().getId();
                    boolean pretest = quiz.getAssessmentKind() == Quiz.Kind.PRE_TEST;
                    boolean posttest = quiz.getAssessmentKind() == Quiz.Kind.POST_TEST;
                    var history = pretest ? attempts.findByQuizIdAndEnrollmentIdOrderByAttemptNumberDesc(quiz.getId(), enrollment.getId())
                            : List.<QuizAttempt>of();
                    boolean exhausted = !history.isEmpty() && (history.getFirst().getStatus() != QuizAttemptStatus.IN_PROGRESS
                            || history.getFirst().getSnapshotPayload() == null || !now.isBefore(read(history.getFirst()).expiresAt()));
                    PostTestAccess post = posttest ? postTestAccess(quiz, enrollment, now) : null;
                    boolean locked = quiz.isLocked() || exhausted || (quiz.getDeadline() != null && !now.isBefore(quiz.getDeadline()))
                            || (!pretest && (lesson == null || !unlocked.contains(lesson)));
                    if (post != null && !post.canStart()) locked = true;
                    return new AvailableQuiz(quiz.getId(), course, lesson, quiz.getTitle(), quiz.getDescription(),
                            quiz.getTimeLimitSeconds(), quiz.getDeadline(), pretest ? 1 : posttest ? Math.max(1, quiz.getMaxAttempts()) : 0,
                            quiz.getGradingPolicy().name(), locked, quiz.getShowScore(), quiz.getAssessmentKind(),
                            post == null ? null : post.state(), post == null ? null : post.retryAfter(),
                            post == null || Boolean.FALSE.equals(quiz.getShowScore()) ? null : post.effectivePercent());
                }).toList();
    }

    @Transactional
    public Start start(UUID actor, UUID course, UUID quizId) {
        Enrollment enrollment = access(actor, course);
        Quiz quiz = quizzes.findByIdAndCourseIdForUpdate(quizId, course).orElseThrow(this::unavailable);
        if (!supported(quiz)) throw unavailable();
        if (quiz.isLocked()) throw unavailable();
        if (quiz.getAssessmentKind() != Quiz.Kind.PRE_TEST) {
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
            if (quiz.getAssessmentKind() != Quiz.Kind.PRE_TEST) finish(latest, previous, Map.of(), previous.expiresAt());
        }
        int number = latest == null ? 1 : latest.getAttemptNumber() + 1;
        if (quiz.getAssessmentKind() == Quiz.Kind.PRE_TEST && number > 1) {
            throw new BusinessException(ErrorCode.ATTEMPT_LIMIT_REACHED, "Pre-test chỉ được làm một lần");
        }
        PostTestAccess post = quiz.getAssessmentKind() == Quiz.Kind.POST_TEST ? postTestAccess(quiz, enrollment, now) : null;
        if (post != null && !post.canStart()) {
            throw new BusinessException(ErrorCode.ATTEMPT_LIMIT_REACHED, "REMEDIATION_REQUIRED".equals(post.state())
                    ? "Xem lại toàn bộ video của chương và chờ đủ 24 giờ để mở lượt học bù" : "Đã hết lượt Post-test");
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
        attempt.setRemediation(post != null && "REMEDIATION_AVAILABLE".equals(post.state()));
        attempt.setStartedAt(now);
        attempt.setSnapshotPayload(write(snapshot));
        return response(attempts.save(attempt), snapshot);
    }

    @Transactional
    public Score submit(UUID actor, UUID course, UUID attemptId, Map<UUID, List<String>> submitted) {
        Enrollment enrollment = access(actor, course);
        QuizAttempt attempt = attempts.findById(attemptId)
                .filter(item -> item.getEnrollment().getId().equals(enrollment.getId()))
                .filter(item -> supported(item.getQuiz())).orElseThrow(this::unavailable);
        Snapshot snapshot = read(attempt);
        Instant now = Instant.now();
        return now.isBefore(snapshot.expiresAt()) ? finish(attempt, snapshot, submitted, now)
                : finish(attempt, snapshot, Map.of(), snapshot.expiresAt());
    }

    private boolean supported(Quiz quiz) {
        return quiz.getAssessmentKind() == Quiz.Kind.MINI_QUIZ || quiz.getAssessmentKind() == Quiz.Kind.PRE_TEST
                || quiz.getAssessmentKind() == Quiz.Kind.POST_TEST;
    }

    private PostTestAccess postTestAccess(Quiz quiz, Enrollment enrollment, Instant now) {
        var history = attempts.findByQuizIdAndEnrollmentIdOrderByAttemptNumberDesc(quiz.getId(), enrollment.getId());
        QuizAttempt last = history.isEmpty() ? null : history.getFirst();
        var grades = new ArrayList<BigDecimal>();
        for (QuizAttempt item : history) {
            if (item.getSnapshotPayload() == null) continue;
            BigDecimal total = read(item).items().stream().map(question -> question.prompt().points())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.signum() > 0) results.findByAttemptId(item.getId()).ifPresent(result -> grades.add(
                    result.getFinalScore().multiply(BigDecimal.valueOf(100)).divide(total, 6, java.math.RoundingMode.DOWN)));
        }
        BigDecimal effective = PostTestPolicy.aggregate(grades, quiz.getGradingPolicy().name());
        if (last != null && last.getStatus() == QuizAttemptStatus.IN_PROGRESS && last.getSnapshotPayload() != null
                && now.isBefore(read(last).expiresAt())) {
            return new PostTestAccess("IN_PROGRESS", null, effective, true);
        }
        int limit = Math.max(1, quiz.getMaxAttempts());
        boolean used = history.stream().anyMatch(QuizAttempt::isRemediation);
        boolean passed = grades.stream().anyMatch(score -> score.compareTo(BigDecimal.valueOf(70)) >= 0);
        int count = (int) history.stream().filter(item -> !item.isRemediation()).count();
        Instant lastSubmission = last == null ? null : last.getSubmittedAt();
        if (lastSubmission == null && last != null && last.getSnapshotPayload() != null)
            lastSubmission = read(last).expiresAt();
        boolean ready = lastSubmission != null && count >= limit && !passed && !used
                && !now.isBefore(lastSubmission.plus(PostTestPolicy.COOLDOWN)) && reviewedModule(quiz, last.getId());
        var state = PostTestPolicy.evaluate(count, limit, passed, used, lastSubmission, now, ready);
        Instant retry = state == PostTestPolicy.State.REMEDIATION_REQUIRED ? lastSubmission.plus(PostTestPolicy.COOLDOWN) : null;
        return new PostTestAccess(state.name(), retry, effective,
                state == PostTestPolicy.State.AVAILABLE || state == PostTestPolicy.State.REMEDIATION_AVAILABLE);
    }

    private boolean reviewedModule(Quiz quiz, UUID attempt) {
        if (quiz.getLesson() == null) return false;
        var videos = jdbc.query("""
                select v.id, case when v.upload_status = 'AVAILABLE' then coalesce(v.duration_seconds, 0) else 0 end
                from lessons l join lesson_contents v on v.lesson_id = l.id
                where l.module_id = ? and v.content_type = 'VIDEO'
                """, (row, index) -> new ReviewVideo(row.getObject(1, UUID.class), row.getInt(2)), quiz.getLesson().getModule().getId());
        return videos.stream().allMatch(video -> video.duration() > 0 && heartbeat.remedialSeconds(attempt, video.id()) >= video.duration());
    }

    private record PostTestAccess(String state, Instant retryAfter, BigDecimal effectivePercent, boolean canStart) {}
    private record ReviewVideo(UUID id, int duration) {}

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
