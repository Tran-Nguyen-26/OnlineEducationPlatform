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
    private final AuditLogRepository auditLogs;
    private final LessonMasteryService mastery;
    private final HeartbeatBuffer heartbeat;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ExamPanicCircuitBreaker examPanicCircuitBreaker;
    private final NotificationPublisher notifications;

    public record Option(UUID id, String option_text, int position) {}
    public record Prompt(UUID id, String question_type, String question_text, BigDecimal points, List<Option> options) {}
    public record Start(UUID attemptId, Instant expiresAt, List<Prompt> questions, Instant serverTime,
                        Map<UUID, List<String>> draft, long draftSequence) {}
    public record Score(BigDecimal achieved, BigDecimal total, int correctCount) {}
    @com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AvailableQuiz(UUID id, UUID courseId, UUID lessonId, String title, String description,
                                Integer timeLimitSeconds, Instant deadline, int maxAttempts, String gradingPolicy,
                                boolean locked, Boolean showScore, Quiz.Kind assessmentKind,
                                String attemptState, Instant retryAfter, BigDecimal effectivePercent,
                                String lockReason) {
        @com.fasterxml.jackson.annotation.JsonProperty("is_locked")
        public boolean locked() { return locked; }
    }
    public record Item(Prompt prompt, Set<UUID> correctOptions, String keyword, UUID outcomeId) {}
    public record Snapshot(Instant expiresAt, List<Item> items) {}

    @Transactional
    public List<AvailableQuiz> list(UUID actor, UUID course) {
        Enrollment enrollment = enrollments.findByCourseIdAndStudentId(course, actor)
                .filter(this::eligible).orElseThrow(this::unavailable);
        var unlocked = mastery.lessons(actor, course).stream().filter(LessonMasteryService.Lesson::unlocked)
                .map(LessonMasteryService.Lesson::id).collect(java.util.stream.Collectors.toSet());
        Instant now = Instant.now();
        boolean panicEnabled = examPanicCircuitBreaker.isEnabled();
        return quizzes.findLearnerAssessments(course, List.of(Quiz.Kind.PRE_TEST, Quiz.Kind.MINI_QUIZ, Quiz.Kind.POST_TEST, Quiz.Kind.MASS_EXAM))
                .stream().map(quiz -> {
                    Quiz.Kind kind = quiz.getAssessmentKind() == null ? Quiz.Kind.MINI_QUIZ : quiz.getAssessmentKind();
                    UUID lesson = quiz.getLesson() == null ? null : quiz.getLesson().getId();
                    boolean pretest = kind == Quiz.Kind.PRE_TEST;
                    boolean posttest = kind == Quiz.Kind.POST_TEST;
                    boolean exam = kind == Quiz.Kind.MASS_EXAM;
                    var history = pretest || exam ? attempts.findByQuizIdAndEnrollmentIdOrderByAttemptNumberDesc(quiz.getId(), enrollment.getId())
                            : List.<QuizAttempt>of();
                    boolean exhausted = !history.isEmpty() && (history.getFirst().getStatus() != QuizAttemptStatus.IN_PROGRESS
                            || history.getFirst().getSnapshotPayload() == null || !now.isBefore(read(history.getFirst()).expiresAt()));
                    PostTestAccess post = posttest ? postTestAccess(quiz, enrollment, now) : null;
                    if (post != null && "REMEDIATION_AVAILABLE".equals(post.state())) {
                        notifyRemediationAvailable(actor, quiz, course);
                    }
                    boolean locked = quiz.isLocked() || exhausted || (quiz.getDeadline() != null && !now.isBefore(quiz.getDeadline()))
                            || (!pretest && !exam && (lesson == null || !unlocked.contains(lesson)));
                    if (exam) {
                        locked = quiz.isLocked() || (history.isEmpty() ? !mayEnter(quiz, now)
                                : history.getFirst().getStatus() != QuizAttemptStatus.IN_PROGRESS
                                    || history.getFirst().getExamCutoffAt() == null
                                    || ExamWindow.shouldFinalize(history.getFirst().getExamCutoffAt(), now));
                        if (history.isEmpty() && panicEnabled) locked = true;
                    }
                    if (post != null && !post.canStart()) locked = true;
                    String lockReason = exam && panicEnabled && history.isEmpty()
                            ? "Kỳ thi đang tạm dừng nhận lượt mới theo công tắc an toàn của Admin" : null;
                    return new AvailableQuiz(quiz.getId(), course, lesson, quiz.getTitle(), quiz.getDescription(),
                            quiz.getTimeLimitSeconds(), exam ? quiz.getExamEndsAt() : quiz.getDeadline(), pretest || exam ? 1 : posttest ? Math.max(1, quiz.getMaxAttempts()) : 0,
                            quiz.getGradingPolicy().name(), locked, quiz.getShowScore(), kind,
                            post == null ? null : post.state(), post == null ? null : post.retryAfter(),
                            post == null || Boolean.FALSE.equals(quiz.getShowScore()) ? null : post.effectivePercent(), lockReason);
                }).toList();
    }

    private void notifyRemediationAvailable(UUID actor, Quiz quiz, UUID course) {
        String dedupeKey = notifications.computeDedupeKey("POST_TEST_RETAKE_OPENED", quiz.getId(), actor);
        boolean alreadyQueued = Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from outbox_events where payload ->> 'dedupeKey' = ?)
                """, Boolean.class, dedupeKey));
        if (alreadyQueued) return;
        notifications.publishNotificationEvent("POST_TEST_RETAKE_OPENED", "QUIZ", quiz.getId(), actor,
                "Lượt học bù Post-test đã mở", "Bạn đã xem lại nội dung bắt buộc và đủ thời gian chờ. Có thể làm lượt Post-test học bù cho khóa học “"
                        + quiz.getCourse().getTitle() + "”.",
                "/assessment", Map.of("courseId", course, "quizId", quiz.getId()));
    }

    @Transactional
    public Start start(UUID actor, UUID course, UUID quizId) {
        return start(actor, course, quizId, null);
    }

    @Transactional
    public Start start(UUID actor, UUID course, UUID quizId, UUID device) {
        Enrollment enrollment = access(actor, course);
        Quiz quiz = quizzes.findByIdAndCourseIdForUpdate(quizId, course).orElseThrow(this::unavailable);
        if (!supported(quiz)) throw unavailable();
        if (quiz.isLocked()) throw unavailable();
        boolean exam = quiz.getAssessmentKind() == Quiz.Kind.MASS_EXAM;
        if (exam && device == null) throw unavailable();
        if (quiz.getAssessmentKind() != Quiz.Kind.PRE_TEST && !exam) {
            if (quiz.getLesson() == null) throw unavailable();
            mastery.requireUnlocked(actor, course, quiz.getLesson().getId());
        }
        Instant now = Instant.now();
        if (!exam && quiz.getDeadline() != null && !now.isBefore(quiz.getDeadline())) throw unavailable();
        var history = attempts.findByQuizIdAndEnrollmentIdOrderByAttemptNumberDesc(quizId, enrollment.getId());
        QuizAttempt latest = history.isEmpty() ? null : history.getFirst();
        if (exam && latest != null) {
            requireDevice(latest, device);
            if (latest.getStatus() == QuizAttemptStatus.IN_PROGRESS && latest.getExamCutoffAt() != null
                    && !ExamWindow.shouldFinalize(latest.getExamCutoffAt(), now)) return response(latest, read(latest));
            throw new BusinessException(ErrorCode.ATTEMPT_LIMIT_REACHED, "Ca thi chỉ có một lượt");
        }
        if (exam && examPanicCircuitBreaker.isEnabled()) {
            throw new BusinessException(ErrorCode.EXAM_PANIC_ACTIVE, "Ca thi đang tạm dừng nhận lượt làm bài mới theo công tắc an toàn");
        }
        if (exam && !mayEnter(quiz, now)) throw unavailable();
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
        Instant expires = exam ? quiz.getExamEndsAt() : now.plusSeconds(timeLimit);
        if (!exam && quiz.getDeadline() != null && quiz.getDeadline().isBefore(expires)) expires = quiz.getDeadline();
        Snapshot snapshot = new Snapshot(expires, items);
        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setEnrollment(enrollment);
        attempt.setAttemptNumber(number);
        attempt.setRemediation(post != null && "REMEDIATION_AVAILABLE".equals(post.state()));
        attempt.setStartedAt(now);
        if (exam) {
            attempt.setExamCutoffAt(expires);
            attempt.setExamDeviceId(device);
        }
        attempt.setSnapshotPayload(write(snapshot));
        return response(attempts.save(attempt), snapshot);
    }

    @Transactional
    public Score submit(UUID actor, UUID course, UUID attemptId, Map<UUID, List<String>> submitted) {
        return submit(actor, course, attemptId, submitted, null);
    }

    @Transactional
    public Score submit(UUID actor, UUID course, UUID attemptId, Map<UUID, List<String>> submitted, UUID device) {
        Enrollment enrollment = access(actor, course);
        QuizAttempt attempt = attempts.findById(attemptId)
                .filter(item -> item.getEnrollment().getId().equals(enrollment.getId()))
                .filter(item -> supported(item.getQuiz())).orElseThrow(this::unavailable);
        Snapshot snapshot = read(attempt);
        Instant now = Instant.now();
        if (attempt.getExamCutoffAt() != null) {
            requireDevice(attempt, device);
            // Grace acknowledges only answers received before the server cutoff.
            return finish(attempt, snapshot, ExamWindow.mayEdit(attempt.getExamCutoffAt(), now)
                    ? mergeDraft(attempt, snapshot, submitted) : draft(attempt), now);
        }
        return now.isBefore(snapshot.expiresAt()) ? finish(attempt, snapshot, submitted, now)
                : finish(attempt, snapshot, Map.of(), snapshot.expiresAt());
    }

    @Transactional
    public long sync(UUID actor, UUID course, UUID id, UUID device, long sequence, Map<UUID, List<String>> delta) {
        Enrollment enrollment = access(actor, course);
        QuizAttempt attempt = attempts.findById(id).filter(item -> item.getEnrollment().getId().equals(enrollment.getId())
                && item.getExamCutoffAt() != null).orElseThrow(this::unavailable);
        requireDevice(attempt, device);
        if (attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS || !ExamWindow.mayEdit(attempt.getExamCutoffAt(), Instant.now()))
            throw new BusinessException(ErrorCode.DEADLINE_PASSED, "Đã khóa đáp án");
        if (sequence > sequence(attempt)) {
            attempt.setExamDraft(write(mergeDraft(attempt, read(attempt), delta)));
            attempt.setExamDraftSequence(sequence);
        }
        return sequence(attempt);
    }

    @Transactional
    public Start transferDevice(UUID actor, UUID course, UUID quizId, UUID device, String ip, String userAgent) {
        Enrollment enrollment = access(actor, course);
        Quiz quiz = quizzes.findByIdAndCourseIdForUpdate(quizId, course).orElseThrow(this::unavailable);
        if (quiz.getAssessmentKind() != Quiz.Kind.MASS_EXAM || device == null) throw unavailable();
        QuizAttempt attempt = attempts.findByQuizIdAndEnrollmentIdOrderByAttemptNumberDesc(quizId, enrollment.getId())
                .stream().findFirst().orElseThrow(this::unavailable);
        Instant now = Instant.now();
        if (attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS || attempt.getExamCutoffAt() == null
                || ExamWindow.shouldFinalize(attempt.getExamCutoffAt(), now)) throw unavailable();
        Snapshot snapshot = read(attempt);
        if (!device.equals(attempt.getExamDeviceId())) {
            var entry = new com.oep.coremodel.entity.system.AuditLog();
            entry.setActor(enrollment.getStudent());
            entry.setAction(AuditAction.TRANSFER_EXAM_DEVICE);
            entry.setTargetType("QUIZ_ATTEMPT");
            entry.setTargetId(attempt.getId());
            entry.setMetadata(write(Map.of(
                    "ip", Objects.toString(ip, ""),
                    "userAgent", userAgent == null ? "" : userAgent.substring(0, Math.min(512, userAgent.length())),
                    "at", now.toString())));
            auditLogs.save(entry);
            attempt.setExamDeviceId(device);
        }
        return response(attempt, snapshot);
    }

    @Transactional
    public void finalizeExam(UUID attemptId, UUID courseId, UUID learnerId, Instant now) {
        var enrollment = enrollments.lockForAssessment(courseId, learnerId).orElseThrow(this::unavailable);
        attempts.findById(attemptId).filter(item -> item.getEnrollment().getId().equals(enrollment.getId()))
                .filter(item -> item.getStatus() == QuizAttemptStatus.IN_PROGRESS && item.getExamCutoffAt() != null
                        && ExamWindow.shouldFinalize(item.getExamCutoffAt(), now))
                .ifPresent(item -> finish(item, read(item), draft(item), now));
    }

    private boolean mayEnter(Quiz quiz, Instant now) {
        return quiz.getExamStartsAt() != null && quiz.getExamEndsAt() != null && ExamWindow.mayEnter(
                quiz.getExamStartsAt(), quiz.getExamEndsAt(), quiz.getLateEntryMinutes() == null ? 15 : quiz.getLateEntryMinutes(), now);
    }

    private void requireDevice(QuizAttempt attempt, UUID device) {
        if (device == null || !device.equals(attempt.getExamDeviceId()))
            throw new BusinessException(ErrorCode.EXAM_DEVICE_LOCKED, "Ca thi đang được giữ trên thiết bị khác");
    }

    private long sequence(QuizAttempt attempt) {
        return attempt.getExamDraftSequence() == null ? 0 : attempt.getExamDraftSequence();
    }

    private Map<UUID, List<String>> mergeDraft(QuizAttempt attempt, Snapshot snapshot, Map<UUID, List<String>> delta) {
        var merged = new HashMap<>(draft(attempt));
        for (Item item : snapshot.items()) {
            UUID question = item.prompt().id();
            if (delta.containsKey(question)) merged.put(question, delta.get(question));
        }
        return merged;
    }

    private Map<UUID, List<String>> draft(QuizAttempt attempt) {
        if (attempt.getExamDraft() == null) return Map.of();
        try {
            return json.readValue(attempt.getExamDraft(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid exam draft", ex);
        }
    }

    private boolean supported(Quiz quiz) {
        return quiz.getAssessmentKind() == Quiz.Kind.MINI_QUIZ || quiz.getAssessmentKind() == Quiz.Kind.PRE_TEST
                || quiz.getAssessmentKind() == Quiz.Kind.POST_TEST || quiz.getAssessmentKind() == Quiz.Kind.MASS_EXAM;
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
        return new Start(attempt.getId(), snapshot.expiresAt(), snapshot.items().stream().map(Item::prompt).toList(),
                Instant.now(), draft(attempt), sequence(attempt));
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
