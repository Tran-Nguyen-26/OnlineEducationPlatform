package com.oep.corelms.scheduler;

import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.service.AiTutorCourseLifecycleService;
import com.oep.corelms.service.AiTutorIndexReconciliationService;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Repair course access and missing content events without one global transaction. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "oep.ai-tutor", name = "enabled", havingValue = "true")
public class AiTutorIndexReconciliationScheduler {
    private final CourseRepository courses;
    private final AiTutorCourseLifecycleService lifecycle;
    private final AiTutorIndexReconciliationService reconciliation;

    @Scheduled(cron = "${oep.ai-tutor.reconcile-cron:0 0 4 * * *}")
    @SchedulerLock(name = "aiTutorIndexReconciliation", lockAtMostFor = "PT2H")
    public void reconcileCourseState() {
        int total = 0;
        int failures = 0;
        for (var course : courses.findAllByOrderByCreatedAtDesc()) {
            try {
                lifecycle.synchronize(course.getId());
                if (course.getLifecycleStatus() == CourseLifecycleStatus.PUBLISHED) {
                    total += reconciliation.reconcile(course.getId());
                }
            } catch (RuntimeException exception) {
                failures++;
                log.warn("AI Tutor index reconciliation failed for course {}: {}",
                        course.getId(), exception.getClass().getSimpleName());
            }
        }
        log.info("AI Tutor index reconciliation enqueued {} source events; {} courses failed",
                total, failures);
    }
}
