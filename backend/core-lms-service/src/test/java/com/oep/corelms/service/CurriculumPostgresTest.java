package com.oep.corelms.service;

import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "OEP_TEST_DB_URL", matches = ".+")
class CurriculumPostgresTest {
    @Test
    void migratesLegacySchemaAndCountsOnlyApprovedDistinctQuestionsForEachOutcome() {
        String url = System.getenv("OEP_TEST_DB_URL");
        String user = System.getenv("OEP_TEST_DB_USER");
        String password = System.getenv("OEP_TEST_DB_PASSWORD");
        String schema = "curriculum_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        admin.execute("create schema " + schema);
        var source = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema, user, password);
        var jdbc = new JdbcTemplate(source);
        try {
            jdbc.execute("""
                    create table courses (id uuid primary key);
                    create table course_modules (id uuid primary key, course_id uuid references courses);
                    create table lessons (id uuid primary key, module_id uuid references course_modules);
                    create table lesson_contents (id uuid primary key, lesson_id uuid references lessons, upload_status text, content_type text);
                    create table questions (id uuid primary key);
                    create table quizzes (id uuid primary key, course_id uuid references courses);
                    create table quiz_questions (quiz_id uuid references quizzes, question_id uuid references questions,
                        primary key (quiz_id, question_id));
                    """);
            UUID id = UUID.randomUUID();
            jdbc.update("insert into courses values (?)", id);
            var migration = new ResourceDatabasePopulator(new ClassPathResource("db/migration/V10__curriculum_alignment.sql"));
            migration.execute(source);
            migration.execute(source);

            Course course = new Course();
            course.setId(id);
            course.setWeightPosttest(30);
            course.setWeightMassExam(50);
            course.setWeightAssignment(20);
            UUID outcome = UUID.randomUUID();
            UUID module = UUID.randomUUID();
            UUID lesson = UUID.randomUUID();
            UUID mini = UUID.randomUUID();
            UUID anotherMini = UUID.randomUUID();
            UUID official = UUID.randomUUID();
            jdbc.update("insert into learning_outcomes values (?, ?, 'LO1', 'Outcome', 'APPLY')", outcome, id);
            jdbc.update("insert into course_modules values (?, ?)", module, id);
            jdbc.update("insert into lessons values (?, ?)", lesson, module);
            jdbc.update("insert into lesson_contents values (?, ?, 'AVAILABLE', 'VIDEO', ?)", UUID.randomUUID(), lesson, outcome);
            jdbc.update("insert into quizzes values (?, ?, 'MINI_QUIZ'), (?, ?, 'MINI_QUIZ'), (?, ?, 'POST_TEST')",
                    mini, id, anotherMini, id, official, id);

            UUID first = question(jdbc, outcome, mini, "APPROVED");
            question(jdbc, outcome, mini, "APPROVED");
            UUID draft = question(jdbc, outcome, mini, "DRAFT_AI");
            question(jdbc, outcome, official, "APPROVED");
            jdbc.update("insert into quiz_questions values (?, ?)", anotherMini, first);

            var validator = new CoursePublishValidator(jdbc);
            assertThatThrownBy(() -> validator.validate(course))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("LO1", "Mini-quiz 2/3");
            jdbc.update("update questions set approval_state = 'APPROVED' where id = ?", draft);
            validator.validate(course);
        } finally {
            admin.execute("drop schema " + schema + " cascade");
        }
    }

    private UUID question(JdbcTemplate jdbc, UUID outcome, UUID quiz, String approval) {
        UUID question = UUID.randomUUID();
        jdbc.update("insert into questions values (?, ?, ?)", question, outcome, approval);
        jdbc.update("insert into quiz_questions values (?, ?)", quiz, question);
        return question;
    }
}
