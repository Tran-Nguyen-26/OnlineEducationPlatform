package com.oep.corelms;

import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.corelms.service.LearnerQuizService;
import com.oep.corelms.service.LessonMasteryService;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "OEP_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {"oep.datasource.routing-enabled=false", "spring.jpa.hibernate.ddl-auto=create",
        "spring.task.scheduling.enabled=false", "oep.progress.flush-ms=3600000"})
class LearnerQuizPostgresTest {
    private static final String SCHEMA = "learner_quiz_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired JdbcTemplate jdbc;
    @Autowired CourseRepository courses;
    @Autowired UserRepository users;
    @Autowired LearnerQuizService quizzes;
    @Autowired LessonMasteryService mastery;
    @Autowired com.oep.corelms.service.GraduationService graduation;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired javax.sql.DataSource datasource;
    @org.springframework.boot.test.mock.mockito.MockBean com.oep.corelms.service.HeartbeatBuffer heartbeat;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) throws Exception {
        schema("create schema " + SCHEMA);
        properties.add("spring.datasource.url", () -> env("OEP_TEST_DB_URL"));
        properties.add("spring.datasource.username", () -> env("OEP_TEST_DB_USER"));
        properties.add("spring.datasource.password", () -> env("OEP_TEST_DB_PASSWORD"));
        properties.add("spring.datasource.hikari.schema", () -> SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }

    @AfterAll
    static void cleanup() throws Exception {
        schema("drop schema " + SCHEMA + " cascade");
    }

    @Test
    void storesServerGradesAndUnlocksSequentialAndFastTrackLessons() throws Exception {
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/migration/V11__lesson_document_reads.sql")).execute(datasource);
        User learner = user(UserRole.STUDENT);
        Course entity = new Course();
        entity.setTitle("Learning path");
        entity.setCreatedBy(user(UserRole.ADMIN));
        entity.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        UUID course = courses.save(entity).getId();
        UUID enrollment = UUID.randomUUID(), module = UUID.randomUUID();
        jdbc.update("insert into enrollments(id, course_id, student_id, status, enrolled_at) values (?,?,?,'ACTIVE',now())",
                enrollment, course, learner.getId());
        jdbc.update("insert into course_modules(id, course_id, title, position, is_default, created_at, updated_at) values (?,?,?,0,false,now(),now())",
                module, course, "Module");
        UUID first = lesson(module, 0), second = lesson(module, 1), third = lesson(module, 2);
        UUID outcome = UUID.randomUUID();
        jdbc.update("insert into learning_outcomes(id, course_id, code, description, bloom_level) values (?,?,?,'Outcome','APPLY')",
                outcome, course, "LO-3");
        UUID document = UUID.randomUUID();
        jdbc.update("""
                insert into lesson_contents(id, lesson_id, lo_id, content_type, upload_status, position, version_number, created_at, updated_at)
                values (?,?,?,'PDF','AVAILABLE',0,1,now(),now())
                """, document, third, outcome);

        var mini = quiz(course, first, "MINI_QUIZ", null);
        var pretest = quiz(course, null, "PRE_TEST", outcome);
        assertThat(states(learner, course)).containsExactly(true, false, false);

        var attempt = quizzes.start(learner.getId(), course, mini.quiz());
        assertThat(json.writeValueAsString(attempt)).doesNotContain("is_correct", "correctOptions", "keyword");
        assertThat(quizzes.start(learner.getId(), course, mini.quiz()).attemptId()).isEqualTo(attempt.attemptId());
        User another = user(UserRole.STUDENT);
        jdbc.update("insert into enrollments(id, course_id, student_id, status, enrolled_at) values (?,?,?,'ACTIVE',now())",
                UUID.randomUUID(), course, another.getId());
        assertThatThrownBy(() -> quizzes.submit(another.getId(), course, attempt.attemptId(), mini.correct()))
                .isInstanceOf(BusinessException.class);
        assertThat(quizzes.submit(learner.getId(), course, attempt.attemptId(), mini.correct()).achieved())
                .isEqualByComparingTo("10");
        assertThat(quizzes.submit(learner.getId(), course, attempt.attemptId(), Map.of()).achieved())
                .isEqualByComparingTo("10");
        assertThat(states(learner, course)).containsExactly(true, true, false);

        var diagnostic = quizzes.start(learner.getId(), course, pretest.quiz());
        quizzes.submit(learner.getId(), course, diagnostic.attemptId(), pretest.correct());
        assertThat(states(learner, course)).containsExactly(true, true, true);
        assertThat(mastery.lessons(learner.getId(), course).stream().filter(item -> item.id().equals(third)).findFirst().orElseThrow().fastTrack()).isTrue();
        assertThatThrownBy(() -> quizzes.start(learner.getId(), course, pretest.quiz()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("một lần");
        assertThat(jdbc.queryForObject("""
                select count(*) from quiz_results r join quiz_attempts a on a.id = r.attempt_id
                where a.enrollment_id = ?
                """, Integer.class, enrollment)).isEqualTo(2);
        assertThat(second).isNotEqualTo(third);
    }

    private List<Boolean> states(User learner, UUID course) {
        return mastery.lessons(learner.getId(), course).stream().map(LessonMasteryService.Lesson::unlocked).toList();
    }

    @Test
    void graduationUsesOfficialGradesAndRequiresSurvey() {
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/migration/V14__graduation_survey.sql")).execute(datasource);
        User learner = user(UserRole.STUDENT);
        Course entity = new Course();
        entity.setTitle("Graduation");
        entity.setCreatedBy(user(UserRole.ADMIN));
        entity.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        entity.setWeightPosttest(60);
        entity.setWeightMassExam(40);
        entity.setWeightAssignment(0);
        UUID course = courses.save(entity).getId();
        UUID enrollment = UUID.randomUUID(), module = UUID.randomUUID();
        jdbc.update("insert into enrollments(id,course_id,student_id,status,enrolled_at) values (?,?,?,'ACTIVE',now())",
                enrollment, course, learner.getId());
        jdbc.update("insert into course_modules(id,course_id,title,position,is_default,created_at,updated_at) values (?,?,?,0,false,now(),now())",
                module, course, "Module");
        UUID lesson = lesson(module, 0);
        var mini = quiz(course, lesson, "MINI_QUIZ", null);
        quizzes.submit(learner.getId(), course, quizzes.start(learner.getId(), course, mini.quiz()).attemptId(), mini.correct());
        var pre = quiz(course, null, "PRE_TEST", null);
        quizzes.submit(learner.getId(), course, quizzes.start(learner.getId(), course, pre.quiz()).attemptId(), pre.correct());
        assertThat(graduation.get(learner.getId(), course).courseComplete()).isTrue();
        assertThat(graduation.get(learner.getId(), course).finalPercent()).isNull();

        var post = quiz(course, lesson, "POST_TEST", null);
        quizzes.submit(learner.getId(), course, quizzes.start(learner.getId(), course, post.quiz()).attemptId(), post.correct());
        var exam = quiz(course, null, "MASS_EXAM", null);
        jdbc.update("update quizzes set exam_starts_at = now() - interval '5 minutes', exam_ends_at = now() + interval '10 minutes' where id = ?",
                exam.quiz());
        UUID device = UUID.randomUUID();
        var started = quizzes.start(learner.getId(), course, exam.quiz(), device);
        quizzes.submit(learner.getId(), course, started.attemptId(), exam.correct(), device);
        var beforeSurvey = graduation.get(learner.getId(), course);
        assertThat(beforeSurvey.finalPercent()).isEqualByComparingTo("100");
        assertThat(beforeSurvey.eligible()).isFalse();
        var survey = new com.oep.corelms.service.GraduationService.Survey(5, 9);
        assertThat(graduation.submitSurvey(learner.getId(), course, survey).eligible()).isTrue();
        graduation.submitSurvey(learner.getId(), course, new com.oep.corelms.service.GraduationService.Survey(1, 0));
        assertThat(jdbc.queryForObject("select csat from course_surveys where enrollment_id = ?", Integer.class, enrollment))
                .isEqualTo(5);
        jdbc.update("update enrollments set access_expires_at = now() - interval '1 second' where id = ?", enrollment);
        assertThatThrownBy(() -> graduation.get(learner.getId(), course)).isInstanceOf(BusinessException.class);
    }

    @Test
    void scheduledExamKeepsServerDraftAtCutoffAndAutomaticallyCollectsAfterGrace() {
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/migration/V13__scheduled_exam.sql")).execute(datasource);
        User learner = user(UserRole.STUDENT);
        Course entity = new Course();
        entity.setTitle("Mass exam");
        entity.setCreatedBy(user(UserRole.ADMIN));
        entity.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        UUID course = courses.save(entity).getId();
        jdbc.update("insert into enrollments(id,course_id,student_id,status,enrolled_at) values (?,?,?,'ACTIVE',now())",
                UUID.randomUUID(), course, learner.getId());
        var scheduled = quiz(course, null, "MASS_EXAM", null);
        UUID device = UUID.randomUUID();
        jdbc.update("update quizzes set exam_starts_at = now() - interval '16 minutes', exam_ends_at = now() + interval '10 minutes' where id = ?",
                scheduled.quiz());
        assertThatThrownBy(() -> quizzes.start(learner.getId(), course, scheduled.quiz(), device)).isInstanceOf(BusinessException.class);
        jdbc.update("update quizzes set exam_starts_at = now() - interval '5 minutes' where id = ?", scheduled.quiz());
        var attempt = quizzes.start(learner.getId(), course, scheduled.quiz(), device);
        assertThatThrownBy(() -> quizzes.start(learner.getId(), course, scheduled.quiz(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
        assertThat(quizzes.sync(learner.getId(), course, attempt.attemptId(), device, 2, scheduled.correct())).isEqualTo(2);
        assertThat(quizzes.sync(learner.getId(), course, attempt.attemptId(), device, 1, Map.of())).isEqualTo(2);
        assertThat(quizzes.start(learner.getId(), course, scheduled.quiz(), device).draft()).isEqualTo(scheduled.correct());
        UUID nextDevice = UUID.randomUUID();
        assertThat(quizzes.transferDevice(learner.getId(), course, scheduled.quiz(), nextDevice,
                "127.0.0.1", "Integration test").draft()).isEqualTo(scheduled.correct());
        quizzes.transferDevice(learner.getId(), course, scheduled.quiz(), nextDevice, "127.0.0.1", "Integration test");
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_logs where target_id = ? and action = 'TRANSFER_EXAM_DEVICE'
                  and metadata ->> 'ip' = '127.0.0.1' and metadata ->> 'userAgent' = 'Integration test'
                """, Integer.class, attempt.attemptId())).isEqualTo(1);
        assertThatThrownBy(() -> quizzes.sync(learner.getId(), course, attempt.attemptId(), device, 3, Map.of()))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode())
                        .isEqualTo(com.oep.coremodel.exception.ErrorCode.EXAM_DEVICE_LOCKED));
        jdbc.update("update quiz_attempts set exam_cutoff_at = now() - interval '31 seconds' where id = ?", attempt.attemptId());
        assertThatThrownBy(() -> quizzes.sync(learner.getId(), course, attempt.attemptId(), nextDevice, 3, Map.of()))
                .isInstanceOf(BusinessException.class);
        quizzes.finalizeExam(attempt.attemptId(), course, learner.getId(), Instant.now());
        assertThat(quizzes.submit(learner.getId(), course, attempt.attemptId(), Map.of(), nextDevice).achieved())
                .isEqualByComparingTo("10");

        var graceExam = quiz(course, null, "MASS_EXAM", null);
        jdbc.update("update quizzes set exam_starts_at = now() - interval '5 minutes', exam_ends_at = now() + interval '10 minutes' where id = ?",
                graceExam.quiz());
        var graceAttempt = quizzes.start(learner.getId(), course, graceExam.quiz(), device);
        quizzes.sync(learner.getId(), course, graceAttempt.attemptId(), device, 1, graceExam.correct());
        jdbc.update("update quiz_attempts set exam_cutoff_at = now() - interval '1 second' where id = ?", graceAttempt.attemptId());
        var changesAfterCutoff = Map.of(graceExam.correct().keySet().iterator().next(), List.<String>of());
        assertThat(quizzes.submit(learner.getId(), course, graceAttempt.attemptId(), changesAfterCutoff, device).achieved())
                .isEqualByComparingTo("10");
    }

    @Test
    void postTestRequiresFreshReviewAndCooldownThenGrantsOnlyOneExtraAttempt() {
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/migration/V12__posttest_remediation.sql")).execute(datasource);
        User learner = user(UserRole.STUDENT);
        Course entity = new Course();
        entity.setTitle("Post-test review");
        entity.setCreatedBy(user(UserRole.ADMIN));
        entity.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        UUID course = courses.save(entity).getId();
        UUID enrollment = UUID.randomUUID(), module = UUID.randomUUID();
        jdbc.update("insert into enrollments(id, course_id, student_id, status, enrolled_at) values (?,?,?,'ACTIVE',now())",
                enrollment, course, learner.getId());
        jdbc.update("insert into course_modules(id, course_id, title, position, is_default, created_at, updated_at) values (?,?,?,0,false,now(),now())",
                module, course, "Module");
        UUID lesson = lesson(module, 0), video = UUID.randomUUID();
        jdbc.update("""
                insert into lesson_contents(id, lesson_id, content_type, upload_status, position, duration_seconds, version_number, created_at, updated_at)
                values (?,?,'VIDEO','AVAILABLE',0,100,1,now(),now())
                """, video, lesson);
        var fixture = quiz(course, lesson, "POST_TEST", null);
        var original = quizzes.start(learner.getId(), course, fixture.quiz());
        quizzes.submit(learner.getId(), course, original.attemptId(), Map.of());
        var repository = new com.oep.corelms.repository.HeartbeatProgressRepository(jdbc);
        assertThat(repository.remedialAttempts(enrollment, video)).containsExactly(original.attemptId());

        org.mockito.Mockito.when(heartbeat.watchedSeconds(enrollment, video)).thenReturn(100L);
        org.mockito.Mockito.when(heartbeat.remedialSeconds(original.attemptId(), video)).thenReturn(100L);
        assertThat(quizzes.list(learner.getId(), course).getFirst().attemptState()).isEqualTo("REMEDIATION_REQUIRED");
        jdbc.update("update quiz_attempts set submitted_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(25 * 3600)), original.attemptId());
        org.mockito.Mockito.when(heartbeat.remedialSeconds(original.attemptId(), video)).thenReturn(99L);
        assertThatThrownBy(() -> quizzes.start(learner.getId(), course, fixture.quiz()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("học bù");
        org.mockito.Mockito.when(heartbeat.remedialSeconds(original.attemptId(), video)).thenReturn(100L);
        assertThat(quizzes.list(learner.getId(), course).getFirst().attemptState()).isEqualTo("REMEDIATION_AVAILABLE");
        var retry = quizzes.start(learner.getId(), course, fixture.quiz());
        assertThat(quizzes.start(learner.getId(), course, fixture.quiz()).attemptId()).isEqualTo(retry.attemptId());
        quizzes.submit(learner.getId(), course, retry.attemptId(), fixture.correct());
        assertThat(quizzes.list(learner.getId(), course).getFirst().effectivePercent()).isEqualByComparingTo("100");
        assertThatThrownBy(() -> quizzes.start(learner.getId(), course, fixture.quiz()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("hết lượt");
    }

    private UUID lesson(UUID module, int position) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into lessons(id,module_id,title,position,is_required,created_at,updated_at) values (?,?,?,?,true,now(),now())",
                id, module, "Lesson " + position, position);
        return id;
    }

    private QuizFixture quiz(UUID course, UUID lesson, String kind, UUID outcome) {
        UUID quiz = UUID.randomUUID(), question = UUID.randomUUID(), option = UUID.randomUUID();
        jdbc.update("""
                insert into quizzes(id,course_id,lesson_id,title,assessment_kind,max_attempts,grading_policy,is_locked,created_at,updated_at)
                values (?,?,?,?,?,1,'HIGHEST',false,now(),now())
                """, quiz, course, lesson, kind, kind);
        jdbc.update("""
                insert into questions(id,lo_id,question_type,question_text,approval_state,created_at,updated_at)
                values (?,?,'SINGLE_CHOICE','Question','APPROVED',now(),now())
                """, question, outcome);
        jdbc.update("insert into quiz_questions(quiz_id,question_id,position,points) values (?,?,0,10)", quiz, question);
        jdbc.update("insert into question_options(id,question_id,option_text,is_correct,position) values (?,?,?,true,0)",
                option, question, "Option");
        return new QuizFixture(quiz, Map.of(question, List.of(option.toString())));
    }

    private User user(UserRole role) {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@test.invalid");
        user.setRole(role);
        return users.save(user);
    }

    private static void schema(String sql) throws Exception {
        try (var connection = DriverManager.getConnection(env("OEP_TEST_DB_URL"), env("OEP_TEST_DB_USER"), env("OEP_TEST_DB_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String env(String name) { return System.getenv(name); }
    private record QuizFixture(UUID quiz, Map<UUID, List<String>> correct) {}
}
