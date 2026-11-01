package com.oep.corelms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.corelms.repository.*;
import com.oep.corelms.service.*;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.sql.DataSource;
import java.net.URI;
import java.net.http.*;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in, real PostgreSQL + real HTTP/JWT. Never runs against the development schema. */
@EnabledIfEnvironmentVariable(named = "OEP_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "oep.datasource.routing-enabled=false", "spring.jpa.hibernate.ddl-auto=create",
        "spring.datasource.hikari.maximum-pool-size=20"})
class EnrollmentPostgresTest {
    static final String SCHEMA = "enroll_test_" + UUID.randomUUID().toString().replace("-", "");
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired EnrollmentRepository enrollments;
    @Autowired EnrollmentService service;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource datasource;

    static String env(String key) { return Objects.requireNonNull(System.getenv(key), "Set " + key); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) throws Exception {
        try (var connection = DriverManager.getConnection(env("OEP_TEST_DB_URL"), env("OEP_TEST_DB_USER"), env("OEP_TEST_DB_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }
        properties.add("spring.datasource.url", () -> env("OEP_TEST_DB_URL"));
        properties.add("spring.datasource.username", () -> env("OEP_TEST_DB_USER"));
        properties.add("spring.datasource.password", () -> env("OEP_TEST_DB_PASSWORD"));
        properties.add("spring.datasource.hikari.schema", () -> SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }
    @AfterAll static void cleanup() throws Exception {
        try (var connection = DriverManager.getConnection(env("OEP_TEST_DB_URL"), env("OEP_TEST_DB_USER"), env("OEP_TEST_DB_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
        }
    }
    User user(UserRole role) {
        User u = new User(); u.setEmail(UUID.randomUUID() + "@test.invalid"); u.setRole(role); return users.save(u);
    }
    Course course(int capacity) {
        Course c = new Course(); c.setTitle("Enrollment integration test"); c.setCapacity(capacity);
        c.setCreatedBy(user(UserRole.INSTRUCTOR)); c.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        return courses.save(c);
    }
    List<HttpResponse<String>> concurrent(Course c, List<User> students) throws Exception {
        var requests = students.stream().map(s -> HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/enrollments"))
                .header("Authorization", "Bearer " + jwt.generateAccessToken(s)).header("Content-Type", "application/json")
                .timeout(java.time.Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString("{\"courseId\":\"" + c.getId() + "\"}")).build()).toList();
        var start = new CountDownLatch(1);
        var ready = new CountDownLatch(requests.size());
        try (var client = HttpClient.newHttpClient(); var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = requests.stream().map(request -> pool.submit(() -> {
                ready.countDown(); start.await(); return client.send(request, HttpResponse.BodyHandlers.ofString());
            })).toList();
            boolean allReady = ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            assertThat(allReady).isTrue();
            var responses = new ArrayList<HttpResponse<String>>();
            for (var future : futures) responses.add(future.get(65, TimeUnit.SECONDS));
            return responses;
        } finally { start.countDown(); }
    }
    @Test void twoHundredRequestsExactlyFiftySuccessAndOneHundredFifty409() throws Exception {
        Course c = course(50);
        List<User> students = new ArrayList<>(); for (int i = 0; i < 200; i++) students.add(user(UserRole.STUDENT));
        var responses = concurrent(c, students);
        assertThat(responses.stream().filter(r -> r.statusCode() == 200).count()).isEqualTo(50);
        assertThat(responses.stream().filter(r -> r.statusCode() == 409).count()).isEqualTo(150);
        for (var response : responses) if (response.statusCode() == 409)
            assertThat(json.readTree(response.body()).get("error").asText()).isEqualTo("COURSE_FULL");
        assertThat(enrollments.countByCourseIdAndStatusIn(c.getId(), List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED))).isEqualTo(50);
        assertThat(jdbc.queryForObject("select count(*) from enrollments where course_id = ?", Long.class, c.getId())).isEqualTo(50);
    }
    @Test void concurrentRetriesReturnOneEnrollment() throws Exception {
        Course c = course(1); User student = user(UserRole.STUDENT);
        var responses = concurrent(c, Collections.nCopies(20, student));
        var ids = new HashSet<String>();
        for (var response : responses) { assertThat(response.statusCode()).isEqualTo(200); ids.add(json.readTree(response.body()).get("id").asText()); }
        assertThat(ids).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from enrollments where course_id = ?", Long.class, c.getId())).isEqualTo(1);
    }
    @Test void withdrawAndRejoinPreservesProgress() {
        Course c = course(1); User student = user(UserRole.STUDENT);
        var enrolled = service.enroll(c.getId(), student.getId());
        UUID module = UUID.randomUUID(), lesson = UUID.randomUUID(), progress = UUID.randomUUID();
        jdbc.update("insert into course_modules(id,course_id,title,position,is_default,created_at,updated_at) values (?,?,?,0,false,now(),now())", module, c.getId(), "Module");
        jdbc.update("insert into lessons(id,module_id,title,position,is_required,created_at,updated_at) values (?,?,?,0,true,now(),now())", lesson, module, "Lesson");
        jdbc.update("insert into lesson_progress(id,enrollment_id,lesson_id,status,time_spent_seconds,last_accessed_at) values (?,?,?,'IN_PROGRESS',120,now())", progress, enrolled.id(), lesson);
        service.withdraw(enrolled.id(), student.getId());
        assertThat(service.enroll(c.getId(), student.getId()).id()).isEqualTo(enrolled.id());
        assertThat(jdbc.queryForObject("select time_spent_seconds from lesson_progress where id = ?", Integer.class, progress)).isEqualTo(120);
    }
    @Test void sqlMigrationCanBeAppliedTwiceAfterHibernate() {
        var script = new ResourceDatabasePopulator(new ClassPathResource("db/migration/V3__enrollments_and_lesson_progress.sql"));
        script.execute(datasource); script.execute(datasource);
        assertThat(jdbc.queryForObject("select count(*) from pg_indexes where schemaname = ? and indexname = 'idx_enrollments_course_id_status'", Long.class, SCHEMA)).isEqualTo(1);
    }
}
