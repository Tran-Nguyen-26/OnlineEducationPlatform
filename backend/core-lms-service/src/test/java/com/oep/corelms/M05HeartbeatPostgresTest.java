package com.oep.corelms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.HeartbeatProgressRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.corelms.service.HeartbeatBuffer;
import com.oep.corelms.service.JwtService;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.UserRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OEP_M05_REDIS_PORT", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "oep.datasource.routing-enabled=false", "spring.jpa.hibernate.ddl-auto=create",
        "spring.datasource.hikari.maximum-pool-size=20"
})
class M05HeartbeatPostgresTest {
    private static final String SCHEMA = "m05_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String PREFIX = "oep:{m05}:";

    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired CourseRepository courses;
    @Autowired UserRepository users;
    @Autowired HeartbeatBuffer buffer;
    @Autowired HeartbeatProgressRepository repository;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;
    @TempDir Path temporary;

    record Video(UUID course, UUID lesson, UUID content) {}
    record Learner(UUID id, UUID enrollment, String token) {}

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry properties) throws Exception {
        schema("CREATE SCHEMA ", "");
        properties.add("spring.datasource.url", () -> required("OEP_TEST_DB_URL"));
        properties.add("spring.datasource.username", () -> required("OEP_TEST_DB_USER"));
        properties.add("spring.datasource.password", () -> required("OEP_TEST_DB_PASSWORD"));
        properties.add("spring.datasource.hikari.schema", () -> SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        properties.add("spring.data.redis.port", () -> required("OEP_M05_REDIS_PORT"));
        properties.add("spring.data.redis.database", () -> required("OEP_M05_REDIS_DB"));
    }

    @AfterAll
    static void cleanup() throws Exception {
        schema("DROP SCHEMA ", " CASCADE");
    }

    @Test
    void authenticatesStreamsRejectsStaleDeviceAndFlushesDeduplicatedProgress() throws Exception {
        Video video = video(3600);
        Learner learner = learners(video, 1).getFirst();
        try (var client = HttpClient.newHttpClient()) {
            String first = open(client, video, learner);
            var initial = heartbeat(video, learner, first, 1);
            initial.put("watched_delta_sec", 0);
            assertThat(send(client, "/heartbeat", learner, initial).statusCode())
                    .isEqualTo(204);
            String second = open(client, video, learner);
            var superseded = send(client, "/heartbeat", learner, heartbeat(video, learner, first, 2));
            assertThat(superseded.statusCode()).isEqualTo(409);
            assertThat(json.readTree(superseded.body()).get("error").asText()).isEqualTo("STREAM_SUPERSEDED");

            // Advance Redis's recorded last server tick, keeping the actual test short.
            String streamKey = PREFIX + "stream:lock:" + learner.id();
            redis.opsForHash().put(streamKey, "at", Long.toString(System.currentTimeMillis() - 31_000));
            var sample = heartbeat(video, learner, second, 1);
            assertThat(send(client, "/heartbeat", learner, sample).statusCode()).isEqualTo(204);
            assertThat(send(client, "/heartbeat", learner, sample).statusCode()).isEqualTo(204);
            assertThat(progress(learner)).isNull();
            buffer.flush();
            buffer.flush();
            assertThat(progress(learner)).isEqualTo(30);
            assertThat(jdbc.queryForObject("""
                    select video_position_seconds from lesson_progress where enrollment_id = ?
                    """, Integer.class, learner.enrollment())).isEqualTo(30);
            redis.expire(streamKey, Duration.ZERO);
            assertThat(send(client, "/heartbeat", learner, heartbeat(video, learner, second, 2)).statusCode())
                    .isEqualTo(409);
            jdbc.update("update enrollments set status = 'WITHDRAWN' where id = ?", learner.enrollment());
            assertThat(send(client, "/streams", learner, openBody(video, learner)).statusCode()).isEqualTo(403);
        }
    }

    @Test
    void flushesBeforeThePeriodicIntervalWhenVideoReachesEightyPercent() throws Exception {
        Video video = video(30);
        Learner learner = learners(video, 1).getFirst();
        try (var client = HttpClient.newHttpClient()) {
            String token = open(client, video, learner);
            String streamKey = PREFIX + "stream:lock:" + learner.id();
            redis.opsForHash().put(streamKey, "at", Long.toString(System.currentTimeMillis() - 25_000));
            var sample = heartbeat(video, learner, token, 1);
            sample.put("watched_delta_sec", 24);
            assertThat(send(client, "/heartbeat", learner, sample).statusCode()).isEqualTo(204);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (progress(learner) == null && System.nanoTime() < deadline) {
                Thread.sleep(100);
            }
            assertThat(progress(learner)).isEqualTo(24);
        }
    }

    @Test
    void retriesCommittedBatchWithoutAddingSecondsTwice() throws Exception {
        Video video = video(3600);
        Learner learner = learners(video, 1).getFirst();
        UUID batch = UUID.randomUUID();
        long now = System.currentTimeMillis();
        String rows = json.writeValueAsString(List.of(Map.of(
                "enrollment", learner.enrollment(), "lesson", video.lesson(), "content", video.content(),
                "seconds", 30, "events", 1, "position", 30, "first_ms", now, "last_ms", now)));
        assertThat(repository.persist(batch, rows)).isEqualTo(1);
        assertThat(repository.persist(batch, rows)).isZero();
        assertThat(progress(learner)).isEqualTo(30);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OEP_M05_K6", matches = ".+")
    void k6VerifiesSecondStreamTakesOver() throws Exception {
        Video video = video(3600);
        Learner learner = learners(video, 1).getFirst();
        Path fixture = temporary.resolve("takeover-fixture.json");
        json.writeValue(fixture.toFile(), List.of(Map.of("token", learner.token(),
                "enrollment_id", learner.enrollment(), "lesson_id", video.lesson(), "video_id", video.content())));
        Path output = Path.of(required("OEP_M05_REPORT_DIR")).toAbsolutePath();
        Files.createDirectories(output);
        var process = new ProcessBuilder(required("OEP_M05_K6"), "run", "--quiet", required("OEP_M05_K6_SCRIPT"));
        process.environment().putAll(Map.of("TARGET_URL", "http://127.0.0.1:" + port,
                "HEARTBEAT_FIXTURE", fixture.toString(), "STREAM_CASE", "1"));
        var run = process.redirectErrorStream(true).redirectOutput(output.resolve("k6-takeover.log").toFile()).start();
        try {
            assertThat(run.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(run.exitValue()).isZero();
        } finally {
            if (run.isAlive()) run.destroyForcibly();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {300, 1500})
    @EnabledIfEnvironmentVariable(named = "OEP_M05_K6", matches = ".+")
    void measuresK6AgainstRealApiRedisAndPostgres(int count) throws Exception {
        int samples = Integer.parseInt(required("OEP_M05_SAMPLES"));
        Video video = video(3600);
        var fixtures = learners(video, count).stream().map(learner -> Map.of(
                "token", learner.token(), "enrollment_id", learner.enrollment(),
                "lesson_id", video.lesson(), "video_id", video.content())).toList();
        Path fixture = temporary.resolve("learner-fixture.json");
        json.writeValue(fixture.toFile(), fixtures);

        buffer.flush();
        var before = writeCounts();
        Path output = Path.of(required("OEP_M05_REPORT_DIR")).toAbsolutePath();
        Files.createDirectories(output);
        var process = new ProcessBuilder(required("OEP_M05_K6"), "run", "--quiet",
                required("OEP_M05_K6_SCRIPT"));
        process.environment().putAll(Map.of(
                "TARGET_URL", "http://127.0.0.1:" + port,
                "HEARTBEAT_FIXTURE", fixture.toString(),
                "LEARNERS", Integer.toString(count),
                "HEARTBEATS_PER_LEARNER", Integer.toString(samples),
                "STREAM_CASE", "0",
                "M05_SUMMARY_PATH", output.resolve("k6-" + count + ".json").toString()));
        var run = process.redirectErrorStream(true).redirectOutput(output.resolve("k6-" + count + ".log").toFile())
                .start();
        try {
            assertThat(run.waitFor(samples * 30L + 100, TimeUnit.SECONDS)).isTrue();
            buffer.flush();
            var after = writeCounts();
            var measured = Map.of(
                    "learners", count, "samplesPerLearner", samples,
                    "batchStatements", after.get("statements") - before.get("statements"),
                    "progressRows", after.get("rows") - before.get("rows"),
                    "acceptedHeartbeats", after.get("events") - before.get("events"));
            json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("postgres-" + count + ".json").toFile(),
                    measured);
            assertThat(run.exitValue()).isZero();
            assertThat(measured.get("acceptedHeartbeats").longValue()).isEqualTo((long) count * samples);
            assertThat(measured.get("batchStatements").longValue()).isLessThan((long) count * samples / 5);
        } finally {
            if (run.isAlive()) run.destroyForcibly();
        }
    }

    private Map<String, Long> writeCounts() {
        return jdbc.queryForObject("""
                select count(*), coalesce(sum(progress_rows), 0), coalesce(sum(heartbeat_count), 0)
                from heartbeat_flush_batches
                """, (rs, index) -> Map.of("statements", rs.getLong(1), "rows", rs.getLong(2), "events", rs.getLong(3)));
    }

    private Integer progress(Learner learner) {
        return jdbc.query("select time_spent_seconds from lesson_progress where enrollment_id = ?",
                (rs, index) -> rs.getInt(1), learner.enrollment()).stream().findFirst().orElse(null);
    }

    private Video video(int duration) {
        User instructor = new User();
        instructor.setRole(UserRole.INSTRUCTOR);
        instructor.setEmail(UUID.randomUUID() + "@test.invalid");
        Course course = new Course();
        course.setCreatedBy(users.save(instructor));
        course.setTitle("Heartbeat benchmark course");
        UUID courseId = courses.save(course).getId();
        UUID module = UUID.randomUUID();
        UUID lesson = UUID.randomUUID();
        UUID content = UUID.randomUUID();
        jdbc.update("""
                insert into course_modules (id, course_id, title, position, is_default, created_at, updated_at)
                values (?, ?, 'Module', 0, false, now(), now())
                """, module, courseId);
        jdbc.update("""
                insert into lessons (id, module_id, title, position, is_required, created_at, updated_at)
                values (?, ?, 'Lesson', 0, true, now(), now())
                """, lesson, module);
        jdbc.update("""
                insert into lesson_contents (id, lesson_id, content_type, upload_status, position, version_number,
                    duration_seconds, created_at, updated_at)
                values (?, ?, 'VIDEO', 'AVAILABLE', 0, 1, ?, now(), now())
                """, content, lesson, duration);
        return new Video(courseId, lesson, content);
    }

    private List<Learner> learners(Video video, int count) {
        var userRows = new ArrayList<Object[]>(count);
        var enrollmentRows = new ArrayList<Object[]>(count);
        var fixtures = new ArrayList<Learner>(count);
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            UUID enrollment = UUID.randomUUID();
            User student = new User();
            student.setId(id);
            student.setEmail(id + "@test.invalid");
            userRows.add(new Object[]{id, student.getEmail()});
            enrollmentRows.add(new Object[]{enrollment, video.course(), id});
            fixtures.add(new Learner(id, enrollment, jwt.generateAccessToken(student)));
        }
        jdbc.batchUpdate("""
                insert into users (id, email, role, account_status, created_at, updated_at)
                values (?, ?, 'STUDENT', 'ACTIVE', now(), now())
                """, userRows);
        jdbc.batchUpdate("""
                insert into enrollments (id, course_id, student_id, status, enrolled_at)
                values (?, ?, ?, 'ACTIVE', now())
                """, enrollmentRows);
        return fixtures;
    }

    private String open(HttpClient client, Video video, Learner learner) throws Exception {
        var response = send(client, "/streams", learner, openBody(video, learner));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json.readTree(response.body()).get("stream_token").asText();
    }

    private Map<String, Object> openBody(Video video, Learner learner) {
        return Map.of("enrollment_id", learner.enrollment(), "lesson_id", video.lesson(), "video_id", video.content());
    }

    private Map<String, Object> heartbeat(Video video, Learner learner, String token, int sequence) {
        return new java.util.HashMap<>(Map.of("enrollment_id", learner.enrollment(), "lesson_id", video.lesson(),
                "stream_token", token, "sequence", sequence, "current_playback_sec", 30, "watched_delta_sec", 30));
    }

    private HttpResponse<String> send(HttpClient client, String path, Learner learner, Map<String, Object> body)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/progress" + path))
                .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + learner.token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void schema(String statement, String suffix) throws Exception {
        try (var connection = DriverManager.getConnection(required("OEP_TEST_DB_URL"),
                required("OEP_TEST_DB_USER"), required("OEP_TEST_DB_PASSWORD"));
             var sql = connection.createStatement()) {
            sql.execute(statement + SCHEMA + suffix);
        }
    }

    private static String required(String key) {
        return java.util.Objects.requireNonNull(System.getenv(key), "Set " + key);
    }
}
