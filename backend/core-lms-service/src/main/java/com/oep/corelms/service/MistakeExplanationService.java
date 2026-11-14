package com.oep.corelms.service;

import com.oep.corelms.dto.ai.MistakeExplanationResponse;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MistakeExplanationService {
    private static final String PROMPT_VERSION = "mistake-v1";
    private final JdbcTemplate jdbc;
    private final AiTutorService aiTutor;

    private record AttemptAnswer(String question, String selectedText, String correctTexts,
                                 boolean selectedCorrect, String attemptStatus, String assessmentKind,
                                 boolean massExamStillOpen, boolean expired, String enrollmentStatus,
                                 String courseStatus, String accountStatus) {}

    @Transactional
    public MistakeExplanationResponse explain(UUID actor, UUID course, UUID attemptId, UUID questionId, UUID optionId) {
        AttemptAnswer answer = jdbc.query("""
                select q.question_text, selected.option_text, selected.is_correct,
                       coalesce((select string_agg(correct.option_text, ' / ' order by correct.position)
                           from question_options correct where correct.question_id = q.id and correct.is_correct), ''),
                       a.status, z.assessment_kind, coalesce(z.exam_ends_at > now(), true),
                       coalesce(e.access_expires_at < now(), false), e.status, c.lifecycle_status, u.account_status
                from quiz_attempts a join quizzes z on z.id = a.quiz_id join courses c on c.id = z.course_id
                join enrollments e on e.id = a.enrollment_id join users u on u.id = e.student_id
                join quiz_answers qa on qa.attempt_id = a.id and qa.question_id = ?
                join questions q on q.id = qa.question_id
                join question_options selected on selected.id = ? and selected.question_id = q.id
                where a.id = ? and z.course_id = ? and e.student_id = ?
                  and qa.selected_option_data @> jsonb_build_array(cast(? as text))
                """, rs -> rs.next() ? new AttemptAnswer(rs.getString(1), rs.getString(2), rs.getString(4),
                rs.getBoolean(3), rs.getString(5), rs.getString(6), rs.getBoolean(7), rs.getBoolean(8),
                rs.getString(9), rs.getString(10), rs.getString(11)) : null,
                questionId, optionId, attemptId, course, actor, optionId.toString());
        if (answer == null) throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy lựa chọn trong bài làm của bạn");
        if ("IN_PROGRESS".equals(answer.attemptStatus())) {
            throw new BusinessException(ErrorCode.AI_EXAM_LOCKED, "Không thể xem giải thích trong khi đang làm bài");
        }
        boolean activeExam = Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from quiz_attempts a join enrollments e on e.id = a.enrollment_id
                    join quizzes z on z.id = a.quiz_id
                    where e.student_id = ? and a.status = 'IN_PROGRESS'
                      and z.assessment_kind in ('PRE_TEST','POST_TEST','MASS_EXAM'))
                """, Boolean.class, actor));
        if (activeExam) throw new BusinessException(ErrorCode.AI_EXAM_LOCKED, "Trợ lý AI bị khóa trong thời gian làm bài thi");
        if (!"GRADED".equals(answer.attemptStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ có thể xem giải thích sau khi bài làm đã được chấm");
        }
        if ("MASS_EXAM".equals(answer.assessmentKind()) && answer.massExamStillOpen()) {
            throw new BusinessException(ErrorCode.AI_EXAM_LOCKED, "Chỉ có thể xem giải thích sau khi ca thi kết thúc");
        }
        boolean learnerActive = "ACTIVE".equals(answer.accountStatus());
        boolean currentAccess = learnerActive && "ACTIVE".equals(answer.enrollmentStatus())
                && "PUBLISHED".equals(answer.courseStatus()) && !answer.expired();
        boolean archivedRead = learnerActive && answer.expired()
                && ("ACTIVE".equals(answer.enrollmentStatus()) || "COMPLETED".equals(answer.enrollmentStatus()))
                && ("PUBLISHED".equals(answer.courseStatus()) || "ARCHIVED".equals(answer.courseStatus()));
        if (!currentAccess && !archivedRead) throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không còn quyền truy cập kết quả này");

        if (answer.selectedCorrect()) {
            return new MistakeExplanationResponse(questionId, optionId, true,
                    "Lựa chọn này đúng. Nếu câu hỏi cho phép chọn nhiều đáp án, hãy kiểm tra xem còn thiếu lựa chọn đúng nào không.", true);
        }

        if (currentAccess) aiTutor.consumeQuestionQuota(actor);

        String cached = findCached(questionId, optionId);
        if (cached != null) {
            recordCacheAccess(questionId, optionId, true);
            return new MistakeExplanationResponse(questionId, optionId, false, cached, true);
        }
        if (archivedRead) throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ có giải thích đã lưu trước khi kết thúc quyền truy cập mới được xem ở chế độ lưu trữ");

        recordCacheAccess(questionId, optionId, false);
        AiTutorService.GeneratedExplanation generated = aiTutor.explainWrongOption(
                course, answer.question(), answer.selectedText(), answer.correctTexts());
        jdbc.update("""
                insert into ai_mistake_explanation_cache
                  (question_id, selected_option_id, prompt_version, explanation, prompt_tokens, completion_tokens, latency_ms, created_at)
                values (?, ?, ?, ?, ?, ?, ?, now())
                on conflict (question_id, selected_option_id, prompt_version) do nothing
                """, questionId, optionId, PROMPT_VERSION, generated.text(), generated.promptTokens(),
                generated.completionTokens(), generated.latencyMs());
        String persisted = findCached(questionId, optionId);
        return new MistakeExplanationResponse(questionId, optionId, false,
                persisted == null ? generated.text() : persisted, false);
    }

    private String findCached(UUID question, UUID option) {
        return jdbc.query("""
                select explanation from ai_mistake_explanation_cache
                where question_id = ? and selected_option_id = ? and prompt_version = ?
                """, rs -> rs.next() ? rs.getString(1) : null, question, option, PROMPT_VERSION);
    }

    private void recordCacheAccess(UUID question, UUID option, boolean hit) {
        jdbc.update("insert into ai_mistake_cache_events (question_id, selected_option_id, cache_hit) values (?, ?, ?)",
                question, option, hit);
    }
}
