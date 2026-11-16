package com.oep.corelms.service;

import com.oep.corelms.dto.admin.LessonCompletionRuleRequest;
import com.oep.corelms.dto.admin.LessonCompletionRuleResponse;
import com.oep.corelms.repository.LessonCompletionRuleRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.course.LessonCompletionRule;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/** Admin-configured gate on marking a lesson COMPLETED (GAPS.md G2); enforced in StudentCourseContentService. */
@Service
@RequiredArgsConstructor
public class AdminLessonCompletionRuleService {

    private final LessonRepository lessons;
    private final LessonCompletionRuleRepository rules;

    @Transactional(readOnly = true)
    public LessonCompletionRuleResponse get(UUID lessonId) {
        return rules.findByLesson_Id(lessonId)
                .map(this::toResponse)
                .orElseGet(() -> new LessonCompletionRuleResponse(lessonId, false, 0, BigDecimal.ZERO, false, false));
    }

    @Transactional
    public LessonCompletionRuleResponse upsert(UUID lessonId, LessonCompletionRuleRequest request) {
        Lesson lesson = lessons.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Lesson not found"));
        LessonCompletionRule rule = rules.findByLesson_Id(lessonId).orElseGet(() -> {
            LessonCompletionRule r = new LessonCompletionRule();
            r.setLesson(lesson);
            return r;
        });
        rule.setMinimumTimeSeconds(request.minimumTimeSeconds());
        rule.setMinVideoPercentage(request.minVideoPercentage());
        rule.setRequireQuiz(request.requireQuiz());
        rule.setRequireAssignment(request.requireAssignment());
        return toResponse(rules.saveAndFlush(rule));
    }

    @Transactional
    public void delete(UUID lessonId) {
        rules.findByLesson_Id(lessonId).ifPresent(rules::delete);
    }

    private LessonCompletionRuleResponse toResponse(LessonCompletionRule r) {
        return new LessonCompletionRuleResponse(r.getLesson().getId(), true, r.getMinimumTimeSeconds(),
                r.getMinVideoPercentage(), r.isRequireQuiz(), r.isRequireAssignment());
    }
}
