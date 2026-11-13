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
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired javax.sql.DataSource datasource;

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
        assertThat(jdbc.queryForObject("select count(*) from quiz_results", Integer.class)).isEqualTo(2);
        assertThat(second).isNotEqualTo(third);
    }

    private List<Boolean> states(User learner, UUID course) {
        return mastery.lessons(learner.getId(), course).stream().map(LessonMasteryService.Lesson::unlocked).toList();
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
