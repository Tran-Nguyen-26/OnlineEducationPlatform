package com.oep.assessment.service;

import com.oep.assessment.dto.GradebookResponse;
import com.oep.assessment.dto.PendingGradingResponse;
import com.oep.assessment.repository.AssignmentGradeRepository;
import com.oep.assessment.repository.AssignmentRepository;
import com.oep.assessment.repository.AssignmentSubmissionRepository;
import com.oep.assessment.repository.CourseInstructorRepository;
import com.oep.assessment.repository.EnrollmentRepository;
import com.oep.assessment.repository.QuizAttemptRepository;
import com.oep.assessment.repository.QuizQuestionRepository;
import com.oep.assessment.repository.QuizRepository;
import com.oep.assessment.repository.QuizResultRepository;
import com.oep.assessment.repository.UserProfileRepository;
import com.oep.coremodel.entity.assignment.Assignment;
import com.oep.coremodel.entity.assignment.AssignmentGrade;
import com.oep.coremodel.entity.assignment.AssignmentSubmission;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseInstructor;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.entity.quiz.Quiz;
import com.oep.coremodel.entity.quiz.QuizAttempt;
import com.oep.coremodel.entity.quiz.QuizQuestion;
import com.oep.coremodel.entity.quiz.QuizResult;
import com.oep.coremodel.enums.AssignmentStatus;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Course gradebook (quizzes + assignments × students) and the cross-course "needs grading" queue, for assigned instructors and admins. */
@Service
@RequiredArgsConstructor
public class GradebookService {

    private static final Set<EnrollmentStatus> GRADED_ROSTER = EnumSet.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED);

    private final QuizRepository quizzes;
    private final QuizQuestionRepository quizQuestions;
    private final QuizAttemptRepository attempts;
    private final QuizResultRepository quizResults;
    private final AssignmentRepository assignments;
    private final AssignmentSubmissionRepository submissions;
    private final AssignmentGradeRepository grades;
    private final EnrollmentRepository enrollments;
    private final CourseInstructorRepository courseInstructors;
    private final UserProfileRepository userProfiles;

    @Transactional(readOnly = true)
    public GradebookResponse gradebook(UUID courseId, UUID userId, String role) {
        requireAccess(courseId, userId, role);
        List<Quiz> courseQuizzes = quizzes.findByCourse_IdOrderByCreatedAtDesc(courseId);
        List<Assignment> courseAssignments = assignments.findByCourse_IdOrderByCreatedAtDesc(courseId);

        List<GradebookResponse.Column> columns = new ArrayList<>();
        Map<UUID, BigDecimal> maxByColumn = new LinkedHashMap<>();
        for (Quiz q : courseQuizzes) {
            BigDecimal max = quizQuestions.findByQuiz_IdOrderByPositionAsc(q.getId()).stream()
                    .map(QuizQuestion::getPoints).reduce(BigDecimal.ZERO, BigDecimal::add);
            columns.add(new GradebookResponse.Column(q.getId(), "QUIZ", q.getTitle(), max, q.getDeadline()));
            maxByColumn.put(q.getId(), max);
        }
        for (Assignment a : courseAssignments) {
            columns.add(new GradebookResponse.Column(a.getId(), "ASSIGNMENT", a.getTitle(), a.getMaxScore(), a.getDeadline()));
            maxByColumn.put(a.getId(), a.getMaxScore());
        }

        String courseTitle = courseQuizzes.isEmpty() && courseAssignments.isEmpty() ? null
                : (courseQuizzes.isEmpty() ? courseAssignments.get(0).getCourse() : courseQuizzes.get(0).getCourse()).getTitle();

        List<GradebookResponse.Row> rows = new ArrayList<>();
        for (Enrollment e : enrollments.findByCourse_IdAndStatusIn(courseId, GRADED_ROSTER)) {
            Map<UUID, GradebookResponse.Cell> cells = new LinkedHashMap<>();
            List<BigDecimal> percents = new ArrayList<>();

            for (Quiz q : courseQuizzes) {
                List<QuizAttempt> mine = attempts.findByQuiz_IdAndEnrollment_IdOrderByAttemptNumberAsc(q.getId(), e.getId());
                BigDecimal best = bestScore(q, mine);
                cells.put(q.getId(), new GradebookResponse.Cell(best, mine.size(), mine.isEmpty() ? "NONE" : best == null ? "SUBMITTED" : "GRADED"));
                addPercent(percents, best, maxByColumn.get(q.getId()));
            }
            for (Assignment a : courseAssignments) {
                AssignmentSubmission sub = submissions.findByAssignment_IdAndEnrollment_Id(a.getId(), e.getId()).orElse(null);
                if (sub == null) {
                    cells.put(a.getId(), new GradebookResponse.Cell(null, 0, "NONE"));
                    continue;
                }
                BigDecimal score = grades.findBySubmission_Id(sub.getId()).map(AssignmentGrade::getScore).orElse(null);
                cells.put(a.getId(), new GradebookResponse.Cell(score, sub.getSubmissionCount(), score == null ? "SUBMITTED" : "GRADED"));
                addPercent(percents, score, a.getMaxScore());
            }

            User student = e.getStudent();
            String name = userProfiles.findById(student.getId()).map(UserProfile::getFullName).orElse(student.getEmail());
            BigDecimal avg = percents.isEmpty() ? null
                    : percents.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(percents.size()), 1, RoundingMode.HALF_UP);
            rows.add(new GradebookResponse.Row(e.getId(), student.getId(), name, student.getEmail(), e.getStatus().name(), cells, avg));
        }
        rows.sort(Comparator.comparing(GradebookResponse.Row::studentName, String.CASE_INSENSITIVE_ORDER));
        return new GradebookResponse(courseId, courseTitle, columns, rows);
    }

    /** Submissions still waiting for a first grade or a regrade; instructors see only their courses, admins see all. */
    @Transactional(readOnly = true)
    public List<PendingGradingResponse> pendingGrading(UUID userId, String role) {
        Set<AssignmentStatus> waiting = EnumSet.of(AssignmentStatus.SUBMITTED, AssignmentStatus.PENDING_REGRADE);
        List<AssignmentSubmission> found;
        if ("ADMIN".equals(role)) {
            found = submissions.findByStatusInOrderBySubmittedAtAsc(waiting);
        } else {
            List<UUID> courseIds = courseInstructors.findByInstructor_Id(userId).stream()
                    .map(CourseInstructor::getCourse).map(Course::getId).toList();
            if (courseIds.isEmpty()) {
                return List.of();
            }
            found = submissions.findByAssignment_Course_IdInAndStatusInOrderBySubmittedAtAsc(courseIds, waiting);
        }
        return found.stream().map(s -> {
            User student = s.getEnrollment().getStudent();
            String name = userProfiles.findById(student.getId()).map(UserProfile::getFullName).orElse(student.getEmail());
            Assignment a = s.getAssignment();
            return new PendingGradingResponse(s.getId(), a.getId(), a.getTitle(), a.getCourse().getId(), a.getCourse().getTitle(),
                    name, student.getEmail(), s.getStatus().name(), s.isLate(), s.getSubmittedAt());
        }).toList();
    }

    private void addPercent(List<BigDecimal> percents, BigDecimal score, BigDecimal max) {
        if (score != null && max != null && max.signum() > 0) {
            percents.add(score.multiply(BigDecimal.valueOf(100)).divide(max, 1, RoundingMode.HALF_UP));
        }
    }

    private BigDecimal bestScore(Quiz quiz, List<QuizAttempt> mine) {
        List<BigDecimal> scores = mine.stream()
                .map(a -> quizResults.findByAttempt_Id(a.getId()).map(QuizResult::getFinalScore).orElse(null))
                .filter(Objects::nonNull).toList();
        if (scores.isEmpty()) {
            return null;
        }
        return switch (quiz.getGradingPolicy()) {
            case HIGHEST -> scores.stream().max(Comparator.naturalOrder()).orElse(null);
            case LATEST -> scores.get(scores.size() - 1);
            case AVERAGE -> scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
        };
    }

    private void requireAccess(UUID courseId, UUID userId, String role) {
        if ("ADMIN".equals(role)) {
            return;
        }
        if (!"INSTRUCTOR".equals(role) || !courseInstructors.existsByCourse_IdAndInstructor_Id(courseId, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You are not assigned to this course");
        }
    }
}
