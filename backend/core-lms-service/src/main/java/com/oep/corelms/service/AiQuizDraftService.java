package com.oep.corelms.service;

import com.oep.corelms.dto.ai.AiQuizDtos;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.LearningOutcomeRepository;
import com.oep.corelms.repository.QuestionBankRepository;
import com.oep.corelms.repository.QuestionOptionRepository;
import com.oep.corelms.repository.QuestionRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.course.LearningOutcome;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.quiz.Question;
import com.oep.coremodel.entity.quiz.QuestionBank;
import com.oep.coremodel.entity.quiz.QuestionOption;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiQuizDraftService {
    private static final String PROMPT_VERSION = "mistake-v1";
    private final JdbcTemplate jdbc;
    private final AiTutorService aiTutor;
    private final CourseRepository courses;
    private final LearningOutcomeRepository outcomes;
    private final QuestionBankRepository banks;
    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final UserRepository users;

    @Transactional
    public AiQuizDtos.GeneratedBatch generate(UUID actor, AiQuizDtos.GenerateRequest request) {
        User admin = requireAdmin(actor);
        var course = courses.findById(request.courseId()).orElseThrow(this::notFound);
        LearningOutcome outcome = outcomes.findByIdAndCourseId(request.learningOutcomeId(), request.courseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy chuẩn đầu ra trong khóa học"));
        boolean lessonBelongs = Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from lessons l join course_modules m on m.id = l.module_id
                    where l.id = ? and m.course_id = ?)
                """, Boolean.class, request.lessonId(), request.courseId()));
        if (!lessonBelongs) throw new BusinessException(ErrorCode.INVALID_COURSE_LESSON, "Bài học không thuộc khóa học đã chọn");

        QuestionBank bank;
        if (request.questionBankId() != null) {
            bank = banks.findByIdAndCourseId(request.questionBankId(), request.courseId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_BANK_NOT_FOUND, "Ngân hàng câu hỏi không thuộc khóa học"));
        } else {
            bank = new QuestionBank();
            bank.setCourse(course);
            bank.setName("Câu hỏi AI nháp");
            bank.setCreatedBy(admin);
            bank = banks.save(bank);
        }

        String source = sourceMaterial(request.courseId(), request.lessonId());
        List<AiTutorService.GeneratedQuestionDraft> generated = aiTutor.generateQuizDrafts(
                request.courseId(), outcome.getCode() + " — " + outcome.getDescription(), request.bloomLevel().name(), source, request.count());
        List<UUID> draftIds = new ArrayList<>();
        for (AiTutorService.GeneratedQuestionDraft draft : generated) {
            Question question = new Question();
            question.setQuestionBank(bank);
            question.setLearningOutcome(outcome);
            question.setBloomLevel(request.bloomLevel());
            question.setSourceLessonId(request.lessonId());
            question.setQuestionType(com.oep.coremodel.enums.QuestionType.SINGLE_CHOICE);
            question.setQuestionText(draft.question());
            question.setApprovalState("DRAFT_AI");
            question.setCreatedBy(admin);
            question = questions.saveAndFlush(question);
            draftIds.add(question.getId());

            List<QuestionOption> savedOptions = new ArrayList<>();
            for (int index = 0; index < draft.options().size(); index++) {
                AiTutorService.GeneratedOptionDraft generatedOption = draft.options().get(index);
                QuestionOption option = new QuestionOption();
                option.setQuestion(question);
                option.setOptionText(generatedOption.text());
                option.setCorrect(generatedOption.correct());
                option.setPosition(index);
                savedOptions.add(options.save(option));
                if (!generatedOption.correct()) cacheExplanation(question.getId(), option.getId(),
                        generatedOption.explanation().isBlank()
                                ? "Đây là phương án nhiễu. Hãy đối chiếu với chuẩn đầu ra và nội dung bài học để tìm điểm chưa phù hợp."
                                : generatedOption.explanation());
            }
        }
        return new AiQuizDtos.GeneratedBatch(bank.getId(), draftIds.size(), listByIds(draftIds));
    }

    @Transactional(readOnly = true)
    public List<AiQuizDtos.DraftQuestion> listDrafts(UUID actor, UUID courseId) {
        requireAdmin(actor);
        List<UUID> ids = courseId == null
                ? jdbc.queryForList("select q.id from questions q join question_banks b on b.id = q.question_bank_id where q.approval_state = 'DRAFT_AI' order by q.created_at desc", UUID.class)
                : jdbc.queryForList("select q.id from questions q join question_banks b on b.id = q.question_bank_id where q.approval_state = 'DRAFT_AI' and b.course_id = ? order by q.created_at desc", UUID.class, courseId);
        return listByIds(ids);
    }

    @Transactional
    public AiQuizDtos.DraftQuestion approve(UUID actor, UUID questionId) {
        requireAdmin(actor);
        int changed = jdbc.update("update questions set approval_state = 'APPROVED' where id = ? and approval_state = 'DRAFT_AI'", questionId);
        if (changed != 1) throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND, "Câu hỏi AI nháp không tồn tại hoặc đã được xử lý");
        return listByIds(List.of(questionId)).getFirst();
    }

    @Transactional
    public AiQuizDtos.DraftQuestion editAndApprove(UUID actor, UUID questionId, AiQuizDtos.ReviewRequest request) {
        requireAdmin(actor);
        Question question = questions.findById(questionId).filter(item -> "DRAFT_AI".equals(item.getApprovalState()))
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND, "Câu hỏi AI nháp không tồn tại hoặc đã được xử lý"));
        if (request.options().stream().filter(AiQuizDtos.EditOption::correct).count() != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Câu hỏi phải có đúng một đáp án đúng");
        }
        question.setQuestionText(request.question().trim());
        question.setApprovalState("APPROVED");
        questions.save(question);
        options.deleteByQuestionId(questionId);
        for (int index = 0; index < request.options().size(); index++) {
            AiQuizDtos.EditOption input = request.options().get(index);
            QuestionOption option = new QuestionOption();
            option.setQuestion(question);
            option.setOptionText(input.text().trim());
            option.setCorrect(input.correct());
            option.setPosition(index);
            option = options.save(option);
            if (!input.correct()) cacheExplanation(questionId, option.getId(),
                    input.explanation() == null || input.explanation().isBlank()
                            ? "Đây là phương án nhiễu. Hãy đối chiếu với chuẩn đầu ra và nội dung bài học để tìm điểm chưa phù hợp."
                            : input.explanation().trim());
        }
        return listByIds(List.of(questionId)).getFirst();
    }

    @Transactional
    public void reject(UUID actor, UUID questionId) {
        requireAdmin(actor);
        int changed = jdbc.update("update questions set approval_state = 'REJECTED' where id = ? and approval_state = 'DRAFT_AI'", questionId);
        if (changed != 1) throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND, "Câu hỏi AI nháp không tồn tại hoặc đã được xử lý");
    }

    private String sourceMaterial(UUID course, UUID lesson) {
        List<String> chunks = jdbc.queryForList("""
                select d.chunk_text from ai_document_chunks d join lesson_contents c on c.id = d.content_id
                where d.course_id = ? and d.lesson_id = ? and c.upload_status = 'AVAILABLE'
                order by d.chunk_index limit 20
                """, String.class, course, lesson);
        if (chunks.isEmpty()) chunks = jdbc.queryForList("""
                select text_content from lesson_contents where lesson_id = ? and content_type = 'TEXT'
                  and upload_status = 'AVAILABLE' and text_content is not null order by position limit 10
                """, String.class, lesson);
        String source = String.join("\n\n", chunks);
        if (source.isBlank()) throw new BusinessException(ErrorCode.AI_CONTEXT_UNAVAILABLE, "Bài học chưa có nội dung văn bản đã được lập chỉ mục để sinh câu hỏi");
        return source.substring(0, Math.min(source.length(), 12_000));
    }

    private void cacheExplanation(UUID questionId, UUID optionId, String explanation) {
        jdbc.update("""
                insert into ai_mistake_explanation_cache
                  (question_id, selected_option_id, prompt_version, explanation, prompt_tokens, completion_tokens, latency_ms, created_at)
                values (?, ?, ?, ?, 0, 0, 0, now())
                on conflict (question_id, selected_option_id, prompt_version) do update set explanation = excluded.explanation
                """, questionId, optionId, PROMPT_VERSION, explanation);
    }

    private List<AiQuizDtos.DraftQuestion> listByIds(List<UUID> ids) {
        List<AiQuizDtos.DraftQuestion> drafts = new ArrayList<>();
        for (UUID id : ids) {
            List<Object[]> rows = jdbc.query("""
                    select q.id, b.course_id, q.source_lesson_id, lo.id, lo.code, q.bloom_level, q.question_text
                    from questions q join question_banks b on b.id = q.question_bank_id
                    left join learning_outcomes lo on lo.id = q.lo_id
                    where q.id = ?
                    """, (rs, row) -> new Object[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                    rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6), rs.getString(7)}, id);
            if (rows.isEmpty()) continue;
            Object[] row = rows.getFirst();
            List<AiQuizDtos.DraftOption> draftOptions = options.findByQuestionIdOrderByPositionAsc(id).stream().map(option -> {
                String explanation = jdbc.query("""
                        select explanation from ai_mistake_explanation_cache
                        where question_id = ? and selected_option_id = ? and prompt_version = ?
                        """, rs -> rs.next() ? rs.getString(1) : "", id, option.getId(), PROMPT_VERSION);
                return new AiQuizDtos.DraftOption(option.getId(), option.getOptionText(), option.isCorrect(), explanation);
            }).toList();
            drafts.add(new AiQuizDtos.DraftQuestion((UUID) row[0], (UUID) row[1], (UUID) row[2],
                    (UUID) row[3], (String) row[4], (String) row[5], (String) row[6], draftOptions));
        }
        return List.copyOf(drafts);
    }

    private User requireAdmin(UUID actor) {
        User user = users.findById(actor).orElseThrow(this::notFound);
        if (user.getRole() != UserRole.ADMIN) throw new BusinessException(ErrorCode.ADMIN_ROLE_REQUIRED, "Chỉ Admin được duyệt câu hỏi AI");
        return user;
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy dữ liệu");
    }
}
