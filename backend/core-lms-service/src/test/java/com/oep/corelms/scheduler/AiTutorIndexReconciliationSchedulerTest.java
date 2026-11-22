package com.oep.corelms.scheduler;

import com.oep.corelms.repository.CourseRepository;
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
        AiTutorIndexReconciliationService reconciliation =
                mock(AiTutorIndexReconciliationService.class);
        Course first = new Course();
        first.setId(UUID.randomUUID());
        Course second = new Course();
        second.setId(UUID.randomUUID());
        when(courses.findByLifecycleStatusOrderByCreatedAtDesc(CourseLifecycleStatus.PUBLISHED))
                .thenReturn(List.of(first, second));
        when(reconciliation.reconcile(first.getId()))
                .thenThrow(new IllegalStateException("M7 unavailable"));
        when(reconciliation.reconcile(second.getId())).thenReturn(2);

        new AiTutorIndexReconciliationScheduler(courses, reconciliation)
                .reconcilePublishedCourses();

        verify(reconciliation).reconcile(first.getId());
        verify(reconciliation).reconcile(second.getId());
    }
}
