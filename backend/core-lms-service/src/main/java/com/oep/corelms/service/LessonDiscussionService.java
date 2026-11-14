package com.oep.corelms.service;

import com.oep.coremodel.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LessonDiscussionService {
    private final JdbcTemplate jdbc;
    private final LessonMasteryService mastery;
    private final NotificationPublisher notifications;
    private final AiEmbeddingService embeddings;

    public record Question(UUID id, UUID videoId, int timestampSec, String body, Instant createdAt) {}
    public record Reply(UUID id, String body, Instant createdAt, boolean verified) {}
    public record VerifiedSuggestion(UUID id, UUID questionId, String question, String answer,
                                     UUID videoId, int timestampSec, double similarity) {}
    public record LessonLink(UUID id, String title) {}
    public record ThreadPage(List<Question> questions, boolean canAsk, boolean canReply, boolean canVerify, boolean hasMore) {}
    private record Access(boolean student, boolean archived, boolean instructor) {}

    @Transactional(readOnly = true)
    public List<LessonLink> lessons(UUID actor, UUID course) {
        return jdbc.query("""
                select l.id,l.title from users u join courses c on c.id = ?
                join course_modules m on m.course_id = c.id join lessons l on l.module_id = m.id
                where u.id = ? and u.account_status = 'ACTIVE' and c.lifecycle_status in ('PUBLISHED','ARCHIVED')
                  and (exists (select 1 from course_instructors ci where u.role = 'INSTRUCTOR'
                        and ci.course_id = c.id and ci.instructor_id = u.id)
                    or exists (select 1 from enrollments e where u.role = 'STUDENT'
                        and e.course_id = c.id and e.student_id = u.id and e.status in ('ACTIVE','COMPLETED')
                        and e.access_expires_at < now()))
                order by m.position,m.id,l.position,l.id
                """, (row, index) -> new LessonLink(row.getObject(1, UUID.class), row.getString(2)), course, actor);
    }

    @Transactional(readOnly = true)
    public ThreadPage list(UUID actor, UUID course, UUID lesson, int page) {
        Access access = authorize(actor, course, lesson, false);
        var questions = jdbc.query("""
                select id, video_id, timestamp_sec, body, created_at from lesson_questions where lesson_id = ?
                order by created_at desc, id desc limit 21 offset ?
                """, (row, index) -> new Question(row.getObject(1, UUID.class), row.getObject(2, UUID.class),
                row.getInt(3), row.getString(4), row.getTimestamp(5).toInstant()), lesson, (long) page * 20);
        return new ThreadPage(questions.stream().limit(20).toList(), access.student() && !access.archived(),
                !access.archived(), access.instructor(), questions.size() > 20);
    }

    @Transactional(readOnly = true)
    public List<Reply> replies(UUID actor, UUID course, UUID lesson, UUID question, int page) {
        authorize(actor, course, lesson, false);
        requireQuestion(lesson, question, false);
        return jdbc.query("""
                select id, body, created_at, verified from lesson_replies where question_id = ?
                order by verified desc, created_at, id limit 30 offset ?
                """, (row, index) -> new Reply(row.getObject(1, UUID.class), row.getString(2),
                row.getTimestamp(3).toInstant(), row.getBoolean(4)), question, (long) page * 30);
    }

    @Transactional(readOnly = true)
    public List<VerifiedSuggestion> suggestions(UUID actor, UUID course, UUID lesson, String query) {
        authorize(actor, course, lesson, false);
        String text = query == null ? "" : query.strip();
        if (text.length() > 500) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Câu hỏi gợi ý không được dài quá 500 ký tự");
        if (text.length() < 4) return List.of();
        if (embeddings.isConfigured()) {
            try {
                float[] vector = embeddings.embed(List.of(text)).getFirst();
                List<VerifiedSuggestion> matches = jdbc.query("""
                        select s.id, s.question_id, s.question_text, s.answer_text, q.video_id, q.timestamp_sec,
                               1 - (s.embedding <=> cast(? as vector)) as similarity
                        from ai_verified_qa_sources s join lesson_questions q on q.id = s.question_id
                        where s.course_id = ? and s.lesson_id = ? and s.embedding is not null
                          and (s.embedding <=> cast(? as vector)) <= 0.45
                        order by s.embedding <=> cast(? as vector) limit 3
                        """, (rs, row) -> new VerifiedSuggestion(rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
                        rs.getObject(5, UUID.class), rs.getInt(6), rs.getDouble(7)),
                        AiEmbeddingService.toPgVector(vector), course, lesson,
                        AiEmbeddingService.toPgVector(vector), AiEmbeddingService.toPgVector(vector));
                if (!matches.isEmpty()) return matches;
            } catch (RuntimeException ex) {
                // Keep deflection available while an embedding provider or index is unavailable.
            }
        }
        return jdbc.query("""
                select s.id, s.question_id, s.question_text, s.answer_text, q.video_id, q.timestamp_sec,
                       ts_rank(to_tsvector('simple', s.question_text || ' ' || s.answer_text), plainto_tsquery('simple', ?)) as similarity
                from ai_verified_qa_sources s join lesson_questions q on q.id = s.question_id
                where s.course_id = ? and s.lesson_id = ?
                  and to_tsvector('simple', s.question_text || ' ' || s.answer_text) @@ plainto_tsquery('simple', ?)
                order by similarity desc, s.verified_at desc limit 3
                """, (rs, row) -> new VerifiedSuggestion(rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
                rs.getObject(5, UUID.class), rs.getInt(6), rs.getDouble(7)), text, course, lesson, text);
    }

    @Transactional
    public UUID ask(UUID actor, UUID course, UUID lesson, UUID video, int timestamp, String body) {
        if (!authorize(actor, course, lesson, true).student()) throw forbidden();
        boolean valid = Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from lesson_contents where id = ? and lesson_id = ?
                  and content_type = 'VIDEO' and upload_status = 'AVAILABLE' and duration_seconds >= ?)
                """, Boolean.class, video, lesson, timestamp));
        if (!valid || timestamp < 0) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Mốc video không hợp lệ");
        UUID id = UUID.randomUUID();
        jdbc.update("insert into lesson_questions(id,lesson_id,author_id,video_id,timestamp_sec,body) values (?,?,?,?,?,?)",
                id, lesson, actor, video, timestamp, body.strip());
        return id;
    }

    @Transactional
    public UUID reply(UUID actor, UUID course, UUID lesson, UUID question, String body) {
        authorize(actor, course, lesson, true);
        UUID author = requireQuestion(lesson, question, true);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into lesson_replies(id,question_id,author_id,body) values (?,?,?,?)",
                id, question, actor, body.strip());
        if (!actor.equals(author)) notifications.publishNotificationEvent("QA_REPLY", "LESSON_QUESTION",
                id, author, "Câu hỏi có phản hồi mới", "Mở bài học để xem phản hồi.",
                "/courses/" + course + "?discussion=" + lesson, Map.of("questionId", question));
        return id;
    }

    @Transactional
    public void verify(UUID actor, UUID course, UUID lesson, UUID question, UUID reply) {
        if (!authorize(actor, course, lesson, true).instructor()) throw forbidden();
        requireQuestion(lesson, question, true);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from lesson_replies where id = ? and question_id = ?)",
                Boolean.class, reply, question))) throw missing();
        jdbc.update("update lesson_replies set verified = false where question_id = ? and verified and id <> ?", question, reply);
        jdbc.update("update lesson_replies set verified = true where id = ?", reply);
        jdbc.update("""
                insert into ai_verified_qa_sources(id, course_id, lesson_id, question_id, reply_id, question_text, answer_text, verified_at)
                select gen_random_uuid(), m.course_id, q.lesson_id, q.id, r.id, q.body, r.body, now()
                from lesson_questions q join lessons l on l.id = q.lesson_id
                join course_modules m on m.id = l.module_id join lesson_replies r on r.id = ?
                where q.id = ?
                on conflict (question_id) do update set reply_id = excluded.reply_id,
                  question_text = excluded.question_text, answer_text = excluded.answer_text,
                  embedding = null, verified_at = now()
                """, reply, question);
    }

    private UUID requireQuestion(UUID lesson, UUID question, boolean lock) {
        return jdbc.queryForList("select author_id from lesson_questions where id = ? and lesson_id = ?" + (lock ? " for update" : ""),
                UUID.class, question, lesson).stream().findFirst().orElseThrow(this::missing);
    }

    private Access authorize(UUID actor, UUID course, UUID lesson, boolean write) {
        Access access = jdbc.query("""
                select u.role = 'STUDENT', coalesce(e.access_expires_at < now(), false), u.role = 'INSTRUCTOR'
                from users u join courses c on c.id = ?
                join course_modules m on m.course_id = c.id join lessons l on l.module_id = m.id
                left join enrollments e on e.course_id = c.id and e.student_id = u.id
                where u.id = ? and l.id = ? and u.account_status = 'ACTIVE'
                  and c.lifecycle_status in ('PUBLISHED', 'ARCHIVED')
                  and ((u.role = 'STUDENT' and e.status in ('ACTIVE', 'COMPLETED')) or
                       (u.role = 'INSTRUCTOR' and exists(select 1 from course_instructors ci
                         where ci.course_id = c.id and ci.instructor_id = u.id)))
                """, (row, index) -> new Access(row.getBoolean(1), row.getBoolean(2), row.getBoolean(3)),
                course, actor, lesson).stream().findFirst().orElseThrow(this::forbidden);
        if (access.archived() && write) throw forbidden();
        if (access.student() && !access.archived()) mastery.requireUnlocked(actor, course, lesson);
        return access;
    }

    private BusinessException forbidden() { return new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền thao tác với thảo luận này"); }
    private BusinessException missing() { return new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy thảo luận"); }
}
