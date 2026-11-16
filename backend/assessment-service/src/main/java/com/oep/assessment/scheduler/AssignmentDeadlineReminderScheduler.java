package com.oep.assessment.scheduler;

import com.oep.assessment.repository.AssignmentRepository;
import com.oep.assessment.repository.AssignmentSubmissionRepository;
import com.oep.assessment.repository.EnrollmentRepository;
import com.oep.assessment.service.NotificationPublisher;
import com.oep.coremodel.entity.assignment.Assignment;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.enums.EnrollmentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Reminds active students who haven't submitted an assignment yet, within 24h of its deadline. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssignmentDeadlineReminderScheduler {

    private final AssignmentRepository assignments;
    private final EnrollmentRepository enrollments;
    private final AssignmentSubmissionRepository submissions;
    private final NotificationPublisher notifications;

    @Scheduled(cron = "0 5 * * * *")
    @Transactional(readOnly = true)
    public void remindUpcomingDeadlines() {
        Instant now = Instant.now();
        int queued = 0;
        for (Assignment a : assignments.findByDeadlineBetween(now, now.plus(24, ChronoUnit.HOURS))) {
            List<Enrollment> active = enrollments.findByCourse_IdAndStatus(a.getCourse().getId(), EnrollmentStatus.ACTIVE);
            for (Enrollment e : active) {
                if (submissions.findByAssignment_IdAndEnrollment_Id(a.getId(), e.getId()).isPresent()) continue;
                notifications.send(e.getStudent().getId(), "ASSIGNMENT_DEADLINE_SOON", "Bài tập sắp đến hạn — " + a.getCourse().getTitle(),
                        "Bài tập \"" + a.getTitle() + "\" sẽ đóng vào " + a.getDeadline() + ", bạn chưa nộp bài.",
                        "assignment-deadline:" + a.getId() + ":" + e.getStudent().getId(), a.getCourse().getId());
                queued++;
            }
        }
        if (queued > 0) log.info("Queued {} assignment-deadline reminder(s)", queued);
    }
}
