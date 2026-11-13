package com.oep.corelms;

import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.corelms.service.BlindGradingService;
import com.oep.corelms.service.CourseAnalyticsService;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "OEP_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
        "oep.datasource.routing-enabled=false", "spring.jpa.hibernate.ddl-auto=create"
})
class L3WeekFourPostgresTest {
    private static final String SCHEMA = "l3_week_four_" + UUID.randomUUID().toString().replace("-", "");

    @Autowired BlindGradingService grading;
    @Autowired CourseAnalyticsService analytics;
    @Autowired JdbcTemplate jdbc;
    @Autowired CourseRepository courses;
    @Autowired UserRepository users;

    private UUID course;
    private UUID first;
    private UUID second;
    private UUID student;
    private UUID submission;
    private UUID enrollment;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) throws Exception {
        schemaStatement("CREATE SCHEMA ");
        properties.add("spring.datasource.url", () -> env("OEP_TEST_DB_URL"));
        properties.add("spring.datasource.username", () -> env("OEP_TEST_DB_USER"));
        properties.add("spring.datasource.password", () -> env("OEP_TEST_DB_PASSWORD"));
        properties.add("spring.datasource.hikari.schema", () -> SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }

    @AfterAll
    static void cleanup() throws Exception {
        schemaStatement("DROP SCHEMA ", " CASCADE");
    }

    @BeforeEach
    void fixture() {
        first = user(UserRole.INSTRUCTOR);
        second = user(UserRole.INSTRUCTOR);
        student = user(UserRole.STUDENT);
        Course entity = new Course();
        entity.setTitle("L3 closed cohort");
        entity.setCreatedBy(users.getReferenceById(first));
        course = courses.save(entity).getId();
        for (UUID instructor : List.of(first, second)) {
            jdbc.update("""
                    insert into course_instructors (course_id, instructor_id, course_role, added_at)
                    values (?, ?, 'CO_INSTRUCTOR', now())
                    """, course, instructor);
        }
        enrollment = UUID.randomUUID();
        jdbc.update("""
                insert into enrollments (id, course_id, student_id, status, enrolled_at)
                values (?, ?, ?, 'ACTIVE', now())
                """, enrollment, course, student);
        UUID assignment = UUID.randomUUID();
        jdbc.update("""
                insert into assignments (id, course_id, title, max_submissions, allow_late_submission,
                    allow_text, allow_files, max_score, created_at, updated_at, rubric)
                values (?, ?, 'Essay', 1, false, true, false, 100, now(), now(), cast(? as jsonb))
                """, assignment, course, """
                [{"code":"accuracy","learningOutcome":"LO1","description":"Accuracy","maxScore":100}]
                """);
        submission = UUID.randomUUID();
        jdbc.update("""
                insert into assignment_submissions
                    (id, assignment_id, enrollment_id, text_content, submission_count, status, submitted_at, is_late, version)
                values (?, ?, ?, 'Anonymous essay text', 1, 'SUBMITTED', now(), false, 0)
                """, submission, assignment, enrollment);
    }

    @Test
    void onlyOneInstructorCanClaimAndAnotherCanClaimAfterExpiry() throws Exception {
        assertThat(grading.queue(first, course, 0)).extracting(BlindGradingService.QueueItem::id)
                .containsExactly(submission);
        var ready = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = executor.submit(() -> tryClaim(ready, first));
            var two = executor.submit(() -> tryClaim(ready, second));
            ready.countDown();
            UUID winner = one.get(10, TimeUnit.SECONDS);
            UUID otherWinner = two.get(10, TimeUnit.SECONDS);
            assertThat((winner == null) ^ (otherWinner == null)).isTrue();

            UUID loser = first.equals(winner) ? second : first;
            assertThat(grading.queue(loser, course, 0)).isEmpty();
            jdbc.update("update assignment_submissions set lock_expires_at = now() - interval '1 second' where id = ?",
                    submission);
            assertThat(grading.claim(loser, course, submission).text()).isEqualTo("Anonymous essay text");
        }
    }

    @Test
    void appealExcludesOriginalReviewerAndPublishesFinalGradeAndDiscrepancy() {
        user(UserRole.ADMIN);
        var initial = grading.claim(first, course, submission);
        grading.grade(first, course, submission, initial.token(), Map.of("accuracy", new BigDecimal("60")), "Initial");
        grading.appeal(student, course, submission, "accuracy", "Please review this criterion");

        assertThat(grading.queue(first, course, 0)).isEmpty();
        assertThatThrownBy(() -> grading.claim(first, course, submission)).isInstanceOf(BusinessException.class);
        var appeal = grading.claim(second, course, submission);
        assertThat(appeal.appealCriterion()).isEqualTo("accuracy");
        grading.grade(second, course, submission, appeal.token(), Map.of("accuracy", new BigDecimal("80")), "Final");

        var finalResult = grading.result(student, course, submission);
        assertThat(finalResult.score()).isEqualByComparingTo("80");
        assertThat(finalResult.regraded()).isTrue();
        assertThat(finalResult.appealUntil()).isNull();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where target_id = ? and action = 'REGRADE'",
                Long.class, submission)).isEqualTo(1);
        var eventTypes = jdbc.queryForList("""
                select event_type from outbox_events where aggregate_id = ?
                """, String.class, submission);
        assertThat(eventTypes).contains("ASSIGNMENT_REGRADE_DISCREPANCY");
        assertThat(eventTypes.stream().filter("ASSIGNMENT_GRADED"::equals)).hasSize(2);
    }

    @Test
    void reportsCompletionAndQueuesAnIssueOnlyForCourseInstructor() {
        UUID admin = user(UserRole.ADMIN);
        UUID module = UUID.randomUUID();
        UUID lesson = UUID.randomUUID();
        jdbc.update("""
                insert into course_modules (id, course_id, title, position, is_default, created_at, updated_at)
                values (?, ?, 'Module', 0, false, now(), now())
                """, module, course);
        jdbc.update("""
                insert into lessons (id, module_id, title, position, is_required, created_at, updated_at)
                values (?, ?, '=1+1', 0, true, now(), now())
                """, lesson, module);
        jdbc.update("""
                insert into lesson_progress (id, enrollment_id, lesson_id, status, time_spent_seconds, last_accessed_at)
                values (?, ?, ?, 'COMPLETED', 60, now())
                """, UUID.randomUUID(), enrollment, lesson);
        jdbc.update("update enrollments set status = 'COMPLETED', completed_at = now() where id = ?", enrollment);

        var metrics = analytics.analytics(first, course);
        assertThat(metrics.learners()).isEqualTo(1);
        assertThat(metrics.completed()).isEqualTo(1);
        assertThat(metrics.lessons()).containsExactly(new CourseAnalyticsService.LessonMetrics(lesson, "=1+1", 1, 1));
        assertThat(CourseAnalyticsService.csv(metrics)).contains("\"'=1+1\",1,1,1,100.00");
        assertThatThrownBy(() -> analytics.analytics(student, course)).isInstanceOf(BusinessException.class);

        analytics.flag(first, course, CourseAnalyticsService.IssueCategory.RUBRIC, "Rubric needs review");
        assertThat(jdbc.queryForList("""
                select payload->>'recipientUserId' from outbox_events
                where event_type = 'COURSE_ISSUE_FLAGGED' and payload->>'courseId' = ?
                """, String.class, course.toString())).contains(admin.toString());
    }

    private UUID tryClaim(CountDownLatch ready, UUID instructor) throws InterruptedException {
        ready.await();
        try {
            grading.claim(instructor, course, submission);
            return instructor;
        } catch (BusinessException | OptimisticLockingFailureException conflict) {
            return null;
        }
    }

    private UUID user(UserRole role) {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@test.invalid");
        user.setRole(role);
        return users.save(user).getId();
    }

    private static String env(String key) {
        return Objects.requireNonNull(System.getenv(key), "Set " + key);
    }

    private static void schemaStatement(String command) throws Exception {
        schemaStatement(command, "");
    }

    private static void schemaStatement(String command, String suffix) throws Exception {
        try (var connection = DriverManager.getConnection(
                env("OEP_TEST_DB_URL"), env("OEP_TEST_DB_USER"), env("OEP_TEST_DB_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.execute(command + SCHEMA + suffix);
        }
    }
}
