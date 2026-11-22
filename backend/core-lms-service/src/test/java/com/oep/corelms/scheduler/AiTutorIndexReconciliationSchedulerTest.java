package com.oep.corelms.scheduler;

import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.service.AiTutorCourseLifecycleService;
import com.oep.corelms.service.AiTutorIndexReconciliationService;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class AiTutorIndexReconciliationSchedulerTest {
    @Test
    void continuesAfterOneCourseFails() {
        CourseRepository courses = mock(CourseRepository.class);
        AiTutorCourseLifecycleService lifecycle = mock(AiTutorCourseLifecycleService.class);
        AiTutorIndexReconciliationService reconciliation =
                mock(AiTutorIndexReconciliationService.class);
        Course first = new Course();
        first.setId(UUID.randomUUID());
        first.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        Course second = new Course();
        second.setId(UUID.randomUUID());
        second.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        Course archived = new Course();
        archived.setId(UUID.randomUUID());
        archived.setLifecycleStatus(CourseLifecycleStatus.ARCHIVED);
        when(courses.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(first, second, archived));
        doThrow(new IllegalStateException("M7 unavailable"))
                .when(lifecycle).synchronize(first.getId());
        when(reconciliation.reconcile(second.getId())).thenReturn(2);

        new AiTutorIndexReconciliationScheduler(courses, lifecycle, reconciliation)
                .reconcileCourseState();

        verify(lifecycle).synchronize(first.getId());
        verify(lifecycle).synchronize(second.getId());
        verify(lifecycle).synchronize(archived.getId());
        verify(reconciliation, never()).reconcile(first.getId());
        verify(reconciliation).reconcile(second.getId());
        verify(reconciliation, never()).reconcile(archived.getId());
    }
}
