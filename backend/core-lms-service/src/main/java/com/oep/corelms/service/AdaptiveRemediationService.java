package com.oep.corelms.service;

import com.oep.corelms.service.OutcomeMasteryService.Outcome;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdaptiveRemediationService {
    private static final double FSRS_FACTOR = 0.2345679012345679; // Makes R(S) = 0.9 for the 90% target.
    private static final double FSRS_DECAY = -0.5;
    private static final double[] FSRS_WEIGHTS = {0.40255, 1.18385, 3.173, 15.69105, 7.1949,
            0.5345, 1.4604, 0.0046, 1.54575, 0.1192, 1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898};
    private static final double TARGET_RETENTION = 0.9;
    private final JdbcTemplate jdbc;
    private final OutcomeMasteryService outcomes;
    private final LessonMasteryService mastery;
    private final ExamPanicCircuitBreaker examPanicCircuitBreaker;

    public enum Rating { AGAIN, HARD, GOOD, EASY }
    public record Video(UUID id, UUID lessonId, String lessonTitle, String title, int durationSeconds) {}
    public record Option(UUID id, String text) {}
    public record Drill(UUID questionId, String question, String learningOutcome, Instant dueAt, List<Option> options) {}
    public record OutcomePlan(String code, String description, double percent, List<Video> videos, List<Drill> drills) {}
    public record Plan(List<OutcomePlan> outcomes) {}
    public record ReviewResult(UUID questionId, boolean correct, List<UUID> correctOptionIds,
                               Rating appliedRating, Instant nextDueAt, double stabilityDays, double difficulty) {}
    private record Card(UUID questionId, String code, String question, Instant dueAt) {}
    private record State(String outcomeCode, double stability, double difficulty, int repetitions, int lapses, Instant lastReviewedAt,
                         Instant dueAt) {}

    @Transactional
    public Plan plan(UUID actor, UUID course) {
        examPanicCircuitBreaker.requireAncillaryServicesAvailable();
        requireNoActiveExam(actor);
        UUID enrollment = requireEnrollment(actor, course);
        List<UUID> unlockedLessons = mastery.lessons(actor, course).stream().filter(LessonMasteryService.Lesson::unlocked)
                .map(LessonMasteryService.Lesson::id).toList();
        var weak = outcomes.forLearner(actor, course).stream()
                .filter(item -> item.percent() != null && item.percent().doubleValue() < 70)
                .toList();
        List<OutcomePlan> result = new ArrayList<>();
        for (Outcome outcome : weak) {
            UUID outcomeId = jdbc.queryForObject("select id from learning_outcomes where course_id = ? and code = ?", UUID.class, course, outcome.code());
            seedCards(enrollment, course, outcomeId);
            List<Video> videos = jdbc.query("""
                    select c.id, l.id, l.title, coalesce(c.title, c.file_name, 'Video học liệu'), coalesce(c.duration_seconds, 0)
                    from lesson_contents c join lessons l on l.id = c.lesson_id
                    join course_modules m on m.id = l.module_id
                    where m.course_id = ? and c.lo_id = ? and c.content_type = 'VIDEO' and c.upload_status = 'AVAILABLE'
                    order by m.position, l.position, c.position, c.id
                    """, (rs, row) -> new Video(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getInt(5)),
                    course, outcomeId).stream().filter(item -> unlockedLessons.contains(item.lessonId())).toList();
            List<Drill> drills = dueDrills(enrollment, outcomeId);
            result.add(new OutcomePlan(outcome.code(), outcome.description(), outcome.percent().doubleValue(), videos, drills));
        }
        return new Plan(List.copyOf(result));
    }

    @Transactional
    public ReviewResult answer(UUID actor, UUID course, UUID questionId, UUID selectedOption, Rating requestedRating) {
        examPanicCircuitBreaker.requireAncillaryServicesAvailable();
        UUID enrollment = requireEnrollment(actor, course);
        requireNoActiveExam(actor);
        List<Boolean> selected = jdbc.query("""
                select o.is_correct from question_options o join questions q on q.id = o.question_id
                join question_banks b on b.id = q.question_bank_id
                where q.id = ? and b.course_id = ? and o.id = ? and q.question_type in ('SINGLE_CHOICE','TRUE_FALSE')
                  and (q.approval_state is null or q.approval_state = 'APPROVED')
                """, (rs, row) -> rs.getBoolean(1), questionId, course, selectedOption);
        if (selected.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy câu hỏi ôn tập");

        List<State> rows = jdbc.query("""
                select lo.code, c.stability_days, c.difficulty, c.repetitions, c.lapses, c.last_reviewed_at, c.due_at
                from learner_micro_drill_cards c join learning_outcomes lo on lo.id = c.outcome_id
                where c.enrollment_id = ? and c.course_id = ? and c.question_id = ?
                for update
                """, (rs, row) -> new State(rs.getString(1), rs.getDouble(2), rs.getDouble(3), rs.getInt(4), rs.getInt(5),
                rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant(), rs.getTimestamp(7).toInstant()),
                enrollment, course, questionId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.FORBIDDEN, "Câu hỏi này chưa nằm trong lộ trình ôn tập của bạn");
        State state = rows.getFirst();
        boolean stillWeak = outcomes.forLearner(actor, course).stream().anyMatch(item -> item.code().equals(state.outcomeCode())
                && item.percent() != null && item.percent().doubleValue() < 70);
        if (!stillWeak) throw new BusinessException(ErrorCode.FORBIDDEN, "Chuẩn đầu ra này hiện không còn trong lộ trình học bù");
        Instant now = Instant.now();
        if (state.dueAt().isAfter(now)) throw new BusinessException(ErrorCode.GRADING_STATE_CONFLICT, "Câu hỏi này chưa đến lịch ôn tiếp theo");
        boolean correct = selected.getFirst();
        Rating rating = correct ? requestedRating : Rating.AGAIN;
        double elapsed = state.lastReviewedAt() == null ? 0 : Math.max(0, Duration.between(state.lastReviewedAt(), now).toSeconds() / 86_400.0);
        int grade = ordinalValue(rating);
        double retrievability = Math.pow(1 + FSRS_FACTOR * elapsed / Math.max(0.1, state.stability()), FSRS_DECAY);
        double initialDifficulty = FSRS_WEIGHTS[4] - Math.exp(FSRS_WEIGHTS[5] * (grade - 1)) + 1;
        double difficulty = state.lastReviewedAt() == null ? initialDifficulty
                : FSRS_WEIGHTS[7] * (FSRS_WEIGHTS[4] - Math.exp(FSRS_WEIGHTS[5] * 3) + 1)
                + (1 - FSRS_WEIGHTS[7]) * (state.difficulty() - FSRS_WEIGHTS[6] * (grade - 3));
        difficulty = clamp(difficulty, 1, 10);
        double stability;
        Instant due;
        if (state.lastReviewedAt() == null) {
            stability = FSRS_WEIGHTS[grade - 1];
            if (rating == Rating.AGAIN) {
                due = now.plus(Duration.ofMinutes(10));
            } else {
                double intervalDays = stability * (Math.pow(TARGET_RETENTION, 1 / FSRS_DECAY) - 1) / FSRS_FACTOR;
                due = now.plus(Duration.ofSeconds((long) Math.ceil(intervalDays * 86_400)));
            }
        } else if (rating == Rating.AGAIN) {
            stability = FSRS_WEIGHTS[11] * Math.pow(difficulty, -FSRS_WEIGHTS[12])
                    * (Math.pow(state.stability() + 1, FSRS_WEIGHTS[13]) - 1)
                    * Math.exp(FSRS_WEIGHTS[14] * (1 - retrievability));
            stability = clamp(stability, 0.1, 365);
            due = now.plus(Duration.ofMinutes(10));
        } else {
            double hardPenalty = rating == Rating.HARD ? FSRS_WEIGHTS[15] : 1;
            double easyBonus = rating == Rating.EASY ? FSRS_WEIGHTS[16] : 1;
            double growth = 1 + Math.exp(FSRS_WEIGHTS[8]) * (11 - difficulty)
                    * Math.pow(state.stability(), -FSRS_WEIGHTS[9])
                    * (Math.exp(FSRS_WEIGHTS[10] * (1 - retrievability)) - 1) * hardPenalty * easyBonus;
            stability = clamp(state.stability() * growth, 0.1, 365);
            double intervalDays = stability * (Math.pow(TARGET_RETENTION, 1 / FSRS_DECAY) - 1) / FSRS_FACTOR;
            due = now.plus(Duration.ofSeconds((long) Math.ceil(intervalDays * 86_400)));
        }
        int repetitions = rating == Rating.AGAIN ? 0 : state.repetitions() + 1;
        int lapses = state.lapses() + (rating == Rating.AGAIN ? 1 : 0);
        jdbc.update("""
                update learner_micro_drill_cards set stability_days = ?, difficulty = ?, repetitions = ?, lapses = ?,
                    last_reviewed_at = ?, due_at = ?, updated_at = now()
                where enrollment_id = ? and course_id = ? and question_id = ?
                """, stability, difficulty, repetitions, lapses, now, due, enrollment, course, questionId);
        List<UUID> correctIds = jdbc.queryForList("select id from question_options where question_id = ? and is_correct order by position, id", UUID.class, questionId);
        return new ReviewResult(questionId, correct, correctIds, rating, due, stability, difficulty);
    }

    private UUID requireEnrollment(UUID actor, UUID course) {
        List<UUID> rows = jdbc.queryForList("""
                select e.id from enrollments e join users u on u.id = e.student_id join courses c on c.id = e.course_id
                where e.student_id = ? and e.course_id = ? and e.status = 'ACTIVE'
                  and u.role = 'STUDENT' and u.account_status = 'ACTIVE' and c.lifecycle_status = 'PUBLISHED'
                  and (e.access_expires_at is null or e.access_expires_at >= now())
                """, UUID.class, actor, course);
        return rows.stream().findFirst().orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Cần quyền học còn hiệu lực"));
    }

    private void requireNoActiveExam(UUID actor) {
        boolean active = Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from quiz_attempts a join enrollments e on e.id = a.enrollment_id
                    join quizzes q on q.id = a.quiz_id where e.student_id = ? and a.status = 'IN_PROGRESS'
                    and q.assessment_kind in ('PRE_TEST','POST_TEST','MASS_EXAM'))
                """, Boolean.class, actor));
        if (active) throw new BusinessException(ErrorCode.AI_EXAM_LOCKED, "Không thể ôn tập trong khi đang làm bài thi");
    }

    private void seedCards(UUID enrollment, UUID course, UUID outcome) {
        jdbc.update("""
                insert into learner_micro_drill_cards(enrollment_id, course_id, outcome_id, question_id)
                select ?, ?, ?, q.id from questions q join question_banks b on b.id = q.question_bank_id
                where b.course_id = ? and q.lo_id = ? and q.question_type in ('SINGLE_CHOICE','TRUE_FALSE')
                  and (q.approval_state is null or q.approval_state = 'APPROVED')
                  and (select count(*) from question_options o where o.question_id = q.id) >= 2
                  and (select count(*) from question_options o where o.question_id = q.id and o.is_correct) = 1
                on conflict (enrollment_id, question_id) do nothing
                """, enrollment, course, outcome, course, outcome);
    }

    private List<Drill> dueDrills(UUID enrollment, UUID outcome) {
        List<Card> cards = jdbc.query("""
                select q.id, lo.code, q.question_text, c.due_at
                from learner_micro_drill_cards c join questions q on q.id = c.question_id
                join learning_outcomes lo on lo.id = c.outcome_id
                where c.enrollment_id = ? and c.outcome_id = ? and c.due_at <= now()
                order by c.due_at, c.id limit 5
                """, (rs, row) -> new Card(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()), enrollment, outcome);
        List<Drill> result = new ArrayList<>();
        for (Card card : cards) {
            List<Option> options = jdbc.query("select id, option_text from question_options where question_id = ? order by position, id",
                    (rs, row) -> new Option(rs.getObject(1, UUID.class), rs.getString(2)), card.questionId());
            result.add(new Drill(card.questionId(), card.question(), card.code(), card.dueAt(), options));
        }
        return List.copyOf(result);
    }

    private int ordinalValue(Rating rating) {
        return switch (rating) { case AGAIN -> 1; case HARD -> 2; case GOOD -> 3; case EASY -> 4; };
    }

    private double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
