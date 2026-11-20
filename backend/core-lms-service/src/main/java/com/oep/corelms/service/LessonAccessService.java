package com.oep.corelms.service;

import com.oep.corelms.repository.CourseModuleRepository;
import com.oep.corelms.repository.LessonProgressRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.enums.LearningMode;
import com.oep.coremodel.enums.LessonProgressStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** One source of truth for per-student readable lessons, including sequential learning locks. */
@Service
@RequiredArgsConstructor
public class LessonAccessService {

    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final LessonProgressRepository progress;

    public List<UUID> readableLessonIds(Enrollment enrollment) {
        return visibility(enrollment).stream()
                .filter(LessonVisibility::readable)
                .map(LessonVisibility::id)
                .toList();
    }

    public void requireUnlocked(Lesson lesson, Enrollment enrollment) {
        if (enrollment.getCourse().getLearningMode() != LearningMode.SEQUENTIAL
                || enrollment.getStatus() != EnrollmentStatus.ACTIVE) {
            return;
        }
        LessonVisibility target = visibility(enrollment).stream()
                .filter(item -> item.id().equals(lesson.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Lesson not found in course"));
        if (!target.readable()) {
            throw new BusinessException(ErrorCode.COMPLETION_RULE_NOT_MET,
                    "Khóa học học tuần tự: hãy hoàn thành \"" + target.blockingLessonTitle() + "\" trước");
        }
    }

    private List<LessonVisibility> visibility(Enrollment enrollment) {
        boolean sequential = enrollment.getCourse().getLearningMode() == LearningMode.SEQUENTIAL
                && enrollment.getStatus() == EnrollmentStatus.ACTIVE;
        Set<UUID> completed = sequential
                ? progress.findByEnrollmentId(enrollment.getId()).stream()
                        .filter(item -> item.getStatus() == LessonProgressStatus.COMPLETED)
                        .map(item -> item.getLesson().getId())
                        .collect(Collectors.toSet())
                : Set.of();
        List<Lesson> orderedLessons = modules.findByCourse_IdOrderByPositionAsc(enrollment.getCourse().getId()).stream()
                .flatMap(module -> lessons.findByModule_IdOrderByPositionAsc(module.getId()).stream())
                .toList();
        java.util.ArrayList<LessonVisibility> result = new java.util.ArrayList<>(orderedLessons.size());
        String blockingTitle = null;
        for (Lesson lesson : orderedLessons) {
            result.add(new LessonVisibility(lesson.getId(), blockingTitle == null, blockingTitle));
            if (sequential && blockingTitle == null && lesson.isRequired() && !completed.contains(lesson.getId())) {
                blockingTitle = lesson.getTitle();
            }
        }
        return List.copyOf(result);
    }

    private record LessonVisibility(UUID id, boolean readable, String blockingLessonTitle) {
    }
}
