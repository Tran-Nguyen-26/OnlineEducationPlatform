package com.oep.corelms.service;

import com.oep.corelms.repository.AssignmentRepository;
import com.oep.corelms.repository.AssignmentSubmissionRepository;
import com.oep.corelms.repository.CourseSessionRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.QuizAttemptRepository;
import com.oep.corelms.repository.QuizRepository;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.enums.EnrollmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** One chronological feed for a student: class sessions + quiz/assignment deadlines across all their active courses. */
@Service
@RequiredArgsConstructor
public class CalendarService {

    public record Event(String type, UUID itemId, String title, UUID courseId, String courseTitle, Instant at, Instant endsAt,
                        String location, boolean done) {}

    private final EnrollmentRepository enrollments;
    private final CourseSessionRepository sessions;
    private final QuizRepository quizzes;
    private final QuizAttemptRepository attempts;
    private final AssignmentRepository assignments;
    private final AssignmentSubmissionRepository submissions;

    @Transactional(readOnly = true)
    public List<Event> forStudent(UUID studentId, Instant from, Instant to) {
        List<Event> out = new ArrayList<>();
        for (Enrollment e : enrollments.findByStudentIdOrderByEnrolledAtDesc(studentId)) {
            if (e.getStatus() != EnrollmentStatus.ACTIVE) continue;
            UUID courseId = e.getCourse().getId();
            String courseTitle = e.getCourse().getTitle();
            for (var s : sessions.findByCourse_IdOrderByStartsAtAsc(courseId)) {
                if (inRange(s.getStartsAt(), from, to)) {
                    out.add(new Event("SESSION", s.getId(), s.getTitle(), courseId, courseTitle, s.getStartsAt(), s.getEndsAt(), s.getLocation(), false));
                }
            }
            for (var q : quizzes.findByCourse_IdOrderByCreatedAtDesc(courseId)) {
                if (!q.isLocked() && q.getDeadline() != null && inRange(q.getDeadline(), from, to)) {
                    out.add(new Event("QUIZ_DEADLINE", q.getId(), q.getTitle(), courseId, courseTitle, q.getDeadline(), null, null,
                            attempts.existsByQuiz_IdAndEnrollment_Id(q.getId(), e.getId())));
                }
            }
            for (var a : assignments.findByCourse_IdOrderByCreatedAtDesc(courseId)) {
                if (a.getDeadline() != null && inRange(a.getDeadline(), from, to)) {
                    out.add(new Event("ASSIGNMENT_DEADLINE", a.getId(), a.getTitle(), courseId, courseTitle, a.getDeadline(), null, null,
                            submissions.existsByAssignment_IdAndEnrollment_Id(a.getId(), e.getId())));
                }
            }
        }
        out.sort(Comparator.comparing(Event::at));
        return out;
    }

    private static boolean inRange(Instant t, Instant from, Instant to) {
        return t != null && !t.isBefore(from) && !t.isAfter(to);
    }
}
