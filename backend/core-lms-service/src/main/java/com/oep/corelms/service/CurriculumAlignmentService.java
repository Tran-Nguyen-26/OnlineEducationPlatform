package com.oep.corelms.service;

import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.LearningOutcomeRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.LearningOutcome;
import com.oep.coremodel.entity.quiz.Quiz;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CurriculumAlignmentService {
    private final CourseRepository courses;
    private final LearningOutcomeRepository outcomes;
    private final JdbcTemplate jdbc;

    public record Weights(@NotNull @Min(0) @Max(100) Integer postTest,
                          @NotNull @Min(0) @Max(100) Integer massExam,
                          @NotNull @Min(0) @Max(100) Integer assignment) {}
    public record OutcomeInput(@NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,39}") String code,
                               @NotBlank @Size(max = 500) String description, @NotNull LearningOutcome.Bloom bloom) {}
    public record Outcome(UUID id, String code, String description, LearningOutcome.Bloom bloom) {
        static Outcome from(LearningOutcome outcome) {
            return new Outcome(outcome.getId(), outcome.getCode(), outcome.getDescription(), outcome.getBloom());
        }
    }
    public record OutcomeLink(@NotNull UUID outcomeId) {}
    public record QuizClassification(@NotNull Quiz.Kind kind) {}
    public record Curriculum(Weights weights, List<Outcome> outcomes) {}

    @Transactional(readOnly = true)
    public Curriculum get(UUID courseId) {
        Course course = courses.findById(courseId).orElseThrow(this::missing);
        return new Curriculum(new Weights(course.getWeightPosttest(), course.getWeightMassExam(), course.getWeightAssignment()),
                outcomes.findByCourseIdOrderByCode(courseId).stream().map(Outcome::from).toList());
    }

    public Weights setWeights(UUID courseId, Weights weights) {
        Course course = lockDraft(courseId);
        if (!CoursePublishValidator.validWeights(weights.postTest(), weights.massExam(), weights.assignment())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tổng trọng số phải bằng 100%");
        }
        course.setWeightPosttest(weights.postTest());
        course.setWeightMassExam(weights.massExam());
        course.setWeightAssignment(weights.assignment());
        return weights;
    }

    public Outcome saveOutcome(UUID courseId, UUID outcomeId, OutcomeInput input) {
        lockDraft(courseId);
        LearningOutcome outcome = outcomeId == null ? new LearningOutcome() : requireOutcome(courseId, outcomeId);
        String code = input.code().toUpperCase(Locale.ROOT);
        if (!code.equals(outcome.getCode()) && outcomes.existsByCourseIdAndCode(courseId, code)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Mã chuẩn đầu ra đã tồn tại");
        }
        outcome.setCourseId(courseId);
        outcome.setCode(code);
        outcome.setDescription(input.description().strip());
        outcome.setBloom(input.bloom());
        return Outcome.from(outcomes.save(outcome));
    }

    public void tagMaterial(UUID courseId, UUID materialId, UUID outcomeId) {
        lockDraft(courseId);
        requireOutcome(courseId, outcomeId);
        checkUpdated(jdbc.update("""
                update lesson_contents lc set lo_id = ?
                from lessons l join course_modules m on m.id = l.module_id
                where lc.id = ? and lc.lesson_id = l.id and m.course_id = ?
                """, outcomeId, materialId, courseId));
    }

    public void classifyQuiz(UUID courseId, UUID quizId, Quiz.Kind kind) {
        lockDraft(courseId);
        checkUpdated(jdbc.update("update quizzes set assessment_kind = ? where id = ? and course_id = ?",
                kind.name(), quizId, courseId));
    }

    public void tagQuestion(UUID courseId, UUID quizId, UUID questionId, UUID outcomeId) {
        lockDraft(courseId);
        requireOutcome(courseId, outcomeId);
        checkUpdated(jdbc.update("""
                update questions q set lo_id = ?, approval_state = 'APPROVED'
                where q.id = ? and (q.approval_state is null or q.approval_state = 'APPROVED') and exists (
                    select 1 from quiz_questions qq join quizzes z on z.id = qq.quiz_id
                    where qq.question_id = q.id and z.id = ? and z.course_id = ?
                ) and not exists (
                    select 1 from quiz_questions qq join quizzes z on z.id = qq.quiz_id
                    where qq.question_id = q.id and z.course_id <> ?
                )
                """, outcomeId, questionId, quizId, courseId, courseId));
    }

    private Course lockDraft(UUID courseId) {
        Course course = courses.findByIdForUpdate(courseId).orElseThrow(this::missing);
        if (course.getLifecycleStatus() != CourseLifecycleStatus.DRAFT) {
            throw new BusinessException(ErrorCode.COURSE_NOT_EDITABLE, "Chuyển khóa học về bản nháp để chỉnh chuẩn đầu ra");
        }
        return course;
    }

    private LearningOutcome requireOutcome(UUID courseId, UUID outcomeId) {
        return outcomes.findByIdAndCourseId(outcomeId, courseId).orElseThrow(this::missing);
    }

    private void checkUpdated(int count) {
        if (count != 1) throw missing();
    }

    private BusinessException missing() {
        return new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy mục trong khóa học");
    }
}
