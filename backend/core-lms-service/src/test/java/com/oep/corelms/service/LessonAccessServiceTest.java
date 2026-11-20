package com.oep.corelms.service;

import com.oep.corelms.repository.CourseModuleRepository;
import com.oep.corelms.repository.LessonProgressRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseModule;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.enrollment.LessonProgress;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.enums.LearningMode;
import com.oep.coremodel.enums.LessonProgressStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LessonAccessServiceTest {

    private CourseModuleRepository modules;
    private LessonRepository lessons;
    private LessonProgressRepository progress;
    private LessonAccessService access;
    private Course course;
    private Enrollment enrollment;
    private Lesson first;
    private Lesson optional;
    private Lesson laterRequired;
    private Lesson last;

    @BeforeEach
    void setUp() {
        modules = mock(CourseModuleRepository.class);
        lessons = mock(LessonRepository.class);
        progress = mock(LessonProgressRepository.class);
        access = new LessonAccessService(modules, lessons, progress);
        course = new Course();
        course.setId(UUID.randomUUID());
        course.setLearningMode(LearningMode.SEQUENTIAL);
        enrollment = new Enrollment();
        enrollment.setId(UUID.randomUUID());
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        CourseModule firstModule = module();
        CourseModule secondModule = module();
        first = lesson(firstModule, "First required", true);
        optional = lesson(firstModule, "Optional", false);
        laterRequired = lesson(secondModule, "Later required", true);
        last = lesson(secondModule, "Last", false);
        when(modules.findByCourse_IdOrderByPositionAsc(course.getId()))
                .thenReturn(List.of(firstModule, secondModule));
        when(lessons.findByModule_IdOrderByPositionAsc(firstModule.getId()))
                .thenReturn(List.of(first, optional));
        when(lessons.findByModule_IdOrderByPositionAsc(secondModule.getId()))
                .thenReturn(List.of(laterRequired, last));
    }

    @Test
    void sequentialCourseExposesCurrentLessonButNotLaterLessons() {
        assertEquals(List.of(first.getId()), access.readableLessonIds(enrollment));
        BusinessException blocked = assertThrows(BusinessException.class,
                () -> access.requireUnlocked(optional, enrollment));
        assertEquals(ErrorCode.COMPLETION_RULE_NOT_MET, blocked.getCode());
        assertTrue(blocked.getMessage().contains("First required"));
        access.requireUnlocked(first, enrollment);
    }

    @Test
    void completingRequiredLessonsUnlocksLaterModulesInOrder() {
        when(progress.findByEnrollmentId(enrollment.getId())).thenReturn(List.of(completed(first)));
        assertEquals(List.of(first.getId(), optional.getId(), laterRequired.getId()),
                access.readableLessonIds(enrollment));
        BusinessException blocked = assertThrows(BusinessException.class,
                () -> access.requireUnlocked(last, enrollment));
        assertTrue(blocked.getMessage().contains("Later required"));

        when(progress.findByEnrollmentId(enrollment.getId()))
                .thenReturn(List.of(completed(first), completed(laterRequired)));
        assertEquals(List.of(first.getId(), optional.getId(), laterRequired.getId(), last.getId()),
                access.readableLessonIds(enrollment));
        access.requireUnlocked(last, enrollment);
    }

    @Test
    void freeNavigationAndCompletedEnrollmentRetainExistingM2ReadAccess() {
        course.setLearningMode(LearningMode.FREE_NAVIGATION);
        assertEquals(4, access.readableLessonIds(enrollment).size());
        access.requireUnlocked(last, enrollment);

        course.setLearningMode(LearningMode.SEQUENTIAL);
        enrollment.setStatus(EnrollmentStatus.COMPLETED);
        assertEquals(4, access.readableLessonIds(enrollment).size());
        access.requireUnlocked(last, enrollment);
    }

    private CourseModule module() {
        CourseModule result = new CourseModule();
        result.setId(UUID.randomUUID());
        result.setCourse(course);
        return result;
    }

    private Lesson lesson(CourseModule module, String title, boolean required) {
        Lesson result = new Lesson();
        result.setId(UUID.randomUUID());
        result.setModule(module);
        result.setTitle(title);
        result.setRequired(required);
        return result;
    }

    private LessonProgress completed(Lesson lesson) {
        LessonProgress result = new LessonProgress();
        result.setLesson(lesson);
        result.setStatus(LessonProgressStatus.COMPLETED);
        return result;
    }
}
