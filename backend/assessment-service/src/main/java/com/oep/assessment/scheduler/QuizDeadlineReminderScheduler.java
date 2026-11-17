package com.oep.assessment.scheduler;

import com.oep.assessment.service.NotificationPublisher;
import com.oep.assessment.repository.EnrollmentRepository;
import com.oep.assessment.repository.QuizAttemptRepository;
import com.oep.assessment.repository.QuizRepository;
import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.quiz.Quiz;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.event.NotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Reminds any actively-enrolled student who hasn't started a quiz yet, shortly before its deadline. */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuizDeadlineReminderScheduler {

    private static final long REMIND_WINDOW_HOURS = 24;

    private final QuizRepository quizzes;
    private final EnrollmentRepository enrollments;
    private final QuizAttemptRepository attempts;
    private final NotificationPublisher notificationPublisher;

    @Scheduled(cron = "0 0 * * * *")
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(name = "quizDeadlineReminder", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    
    @Transactional
    public void remindUpcomingDeadlines() {
        Instant now = Instant.now();
        List<Quiz> dueSoon = quizzes.findByLockedFalseAndDeadlineBetween(now, now.plus(REMIND_WINDOW_HOURS, ChronoUnit.HOURS));
        int queued = 0;
        for (Quiz quiz : dueSoon) {
            List<Enrollment> activeStudents = enrollments.findByCourse_IdAndStatus(quiz.getCourse().getId(), EnrollmentStatus.ACTIVE);
            for (Enrollment enrollment : activeStudents) {
                if (attempts.countByQuiz_IdAndEnrollment_Id(quiz.getId(), enrollment.getId()) > 0) continue;

                NotificationRequestedEvent event = new NotificationRequestedEvent(
                        enrollment.getStudent().getId(), "QUIZ_DEADLINE_SOON",
                        "Quiz sắp đến hạn — " + quiz.getCourse().getTitle(),
                        "Quiz \"" + quiz.getTitle() + "\" sẽ đóng vào " + quiz.getDeadline() + ", bạn chưa làm bài.",
                        "quiz-deadline:" + quiz.getId() + ":" + enrollment.getStudent().getId(),
                        "COURSE", quiz.getCourse().getId(), Instant.now());
                notificationPublisher.publish(event);
                queued++;
            }
        }
        if (queued > 0) {
            log.info("Queued {} quiz-deadline reminder(s)", queued);
        }
    }
}
