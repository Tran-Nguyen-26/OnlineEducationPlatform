package com.oep.assessment.service;

import com.oep.assessment.dto.AppealDtos;
import com.oep.assessment.repository.AssignmentGradeRepository;
import com.oep.assessment.repository.AssignmentSubmissionRepository;
import com.oep.assessment.repository.CourseInstructorRepository;
import com.oep.assessment.repository.EnrollmentRepository;
import com.oep.assessment.repository.GradeAppealRepository;
import com.oep.assessment.repository.QuizAttemptRepository;
import com.oep.assessment.repository.QuizQuestionRepository;
import com.oep.assessment.repository.QuizResultRepository;
import com.oep.assessment.repository.UserProfileRepository;
import com.oep.assessment.repository.UserRepository;
import com.oep.coremodel.entity.assignment.Assignment;
import com.oep.coremodel.entity.assignment.AssignmentGrade;
import com.oep.coremodel.entity.assignment.AssignmentSubmission;
import com.oep.coremodel.entity.course.CourseInstructor;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.enrollment.GradeAppeal;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.entity.quiz.Quiz;
import com.oep.coremodel.entity.quiz.QuizAttempt;
import com.oep.coremodel.entity.quiz.QuizQuestion;
import com.oep.coremodel.entity.quiz.QuizResult;
import com.oep.coremodel.enums.AssignmentStatus;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.enums.QuizAttemptStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Grade appeals (phúc khảo): a student contests a graded assignment or a quiz attempt; staff accept (optionally re-scoring) or reject. */
@Service
@RequiredArgsConstructor
public class AppealService {

    private static final String SUBMISSION = "ASSIGNMENT_SUBMISSION";
    private static final String ATTEMPT = "QUIZ_ATTEMPT";

    private final GradeAppealRepository appeals;
    private final EnrollmentRepository enrollments;
    private final AssignmentSubmissionRepository submissions;
    private final AssignmentGradeRepository grades;
    private final QuizAttemptRepository attempts;
    private final QuizResultRepository quizResults;
    private final QuizQuestionRepository quizQuestions;
    private final CourseInstructorRepository courseInstructors;
    private final UserRepository users;
    private final UserProfileRepository userProfiles;
    private final NotificationPublisher notifications;

    @Transactional
    public AppealDtos.AppealResponse create(UUID studentId, AppealDtos.CreateRequest req) {
        Enrollment enrollment;
        String title;
        if (SUBMISSION.equals(req.targetType())) {
            AssignmentSubmission s = submissions.findById(req.targetId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Submission not found"));
            enrollment = s.getEnrollment();
            if (s.getStatus() != AssignmentStatus.GRADED) {
                throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Chỉ có thể phúc khảo bài đã được chấm");
            }
            title = s.getAssignment().getTitle();
        } else if (ATTEMPT.equals(req.targetType())) {
            QuizAttempt a = attempts.findById(req.targetId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Attempt not found"));
            enrollment = a.getEnrollment();
            if (a.getStatus() == QuizAttemptStatus.IN_PROGRESS || quizResults.findByAttempt_Id(a.getId()).isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Chỉ có thể phúc khảo lượt làm đã nộp");
            }
            title = a.getQuiz().getTitle() + " (lần " + a.getAttemptNumber() + ")";
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown targetType");
        }
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This is not your work");
        }
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE && enrollment.getStatus() != EnrollmentStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.NOT_ENROLLED, "Enrollment is not active");
        }
        if (appeals.existsByTargetTypeAndTargetIdAndEnrollment_IdAndStatus(req.targetType(), req.targetId(), enrollment.getId(), "OPEN")) {
            throw new BusinessException(ErrorCode.ALREADY_ASSIGNED, "Bạn đã gửi yêu cầu phúc khảo cho mục này và đang chờ xử lý");
        }
        GradeAppeal ap = new GradeAppeal();
        ap.setEnrollment(enrollment);
        ap.setCourseId(enrollment.getCourse().getId());
        ap.setTargetType(req.targetType());
        ap.setTargetId(req.targetId());
        ap.setTargetTitle(title);
        ap.setReason(req.reason().trim());
        GradeAppeal saved = appeals.save(ap);

        String studentName = nameOf(enrollment.getStudent());
        for (CourseInstructor ci : courseInstructors.findAll()) {
            if (ci.getCourse().getId().equals(ap.getCourseId())) {
                notifications.send(ci.getInstructor().getId(), "APPEAL_CREATED", "Yêu cầu phúc khảo mới — " + enrollment.getCourse().getTitle(),
                        studentName + " đề nghị xem lại điểm \"" + title + "\"", "appeal-new:" + saved.getId() + ":" + ci.getInstructor().getId(),
                        ap.getCourseId());
            }
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AppealDtos.AppealResponse> mine(UUID studentId) {
        return appeals.findByEnrollment_Student_IdOrderByCreatedAtDesc(studentId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AppealDtos.AppealResponse> forStaff(UUID userId, String role) {
        if ("ADMIN".equals(role)) {
            return appeals.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
        }
        List<UUID> courseIds = courseInstructors.findByInstructor_Id(userId).stream().map(ci -> ci.getCourse().getId()).toList();
        if (courseIds.isEmpty()) {
            return List.of();
        }
        return appeals.findByCourseIdInOrderByCreatedAtDesc(courseIds).stream().map(this::toResponse).toList();
    }

    @Transactional
    public AppealDtos.AppealResponse resolve(UUID appealId, UUID userId, String role, AppealDtos.ResolveRequest req) {
        GradeAppeal ap = appeals.findById(appealId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Appeal not found"));
        boolean allowed = "ADMIN".equals(role)
                || ("INSTRUCTOR".equals(role) && courseInstructors.existsByCourse_IdAndInstructor_Id(ap.getCourseId(), userId));
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You are not assigned to this course");
        }
        if (!"OPEN".equals(ap.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Appeal was already resolved");
        }
        if (req.response() == null || req.response().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Vui lòng nhập phản hồi cho học sinh");
        }
        String scoreNote = "";
        if (req.accepted() && req.newScore() != null) {
            scoreNote = applyNewScore(ap, req.newScore(), userId);
        }
        ap.setStatus(req.accepted() ? "ACCEPTED" : "REJECTED");
        ap.setResponse(req.response().trim());
        ap.setResolvedBy(users.getReferenceById(userId));
        ap.setResolvedAt(Instant.now());
        appeals.save(ap);

        notifications.send(ap.getEnrollment().getStudent().getId(), "APPEAL_RESOLVED", "Kết quả phúc khảo — " + ap.getTargetTitle(),
                (req.accepted() ? "Được chấp nhận" : "Không được chấp nhận") + scoreNote + ". Phản hồi: " + ap.getResponse(),
                "appeal-resolved:" + ap.getId(), ap.getCourseId());
        return toResponse(ap);
    }

    private String applyNewScore(GradeAppeal ap, BigDecimal newScore, UUID userId) {
        if (SUBMISSION.equals(ap.getTargetType())) {
            AssignmentSubmission s = submissions.findById(ap.getTargetId()).orElseThrow();
            Assignment a = s.getAssignment();
            if (newScore.compareTo(a.getMaxScore()) > 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Score exceeds the maximum (" + a.getMaxScore() + ")");
            }
            AssignmentGrade g = grades.findBySubmission_Id(s.getId()).orElseThrow();
            g.setPreviousScore(g.getScore());
            g.setRegradedAt(Instant.now());
            g.setRegradeReason("Phúc khảo");
            g.setScore(newScore);
            g.setGradedBy(users.getReferenceById(userId));
            grades.save(g);
            return " — điểm mới " + newScore.stripTrailingZeros().toPlainString() + "/" + a.getMaxScore().stripTrailingZeros().toPlainString();
        }
        QuizAttempt at = attempts.findById(ap.getTargetId()).orElseThrow();
        Quiz quiz = at.getQuiz();
        BigDecimal max = quizQuestions.findByQuiz_IdOrderByPositionAsc(quiz.getId()).stream()
                .map(QuizQuestion::getPoints).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (newScore.compareTo(max) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Score exceeds the quiz maximum (" + max + ")");
        }
        QuizResult r = quizResults.findByAttempt_Id(at.getId()).orElseThrow();
        r.setFinalScore(newScore);
        r.setAdjustedBy(users.getReferenceById(userId));
        r.setAdjustmentReason("Phúc khảo");
        quizResults.save(r);
        return " — điểm mới " + newScore.stripTrailingZeros().toPlainString() + "/" + max.stripTrailingZeros().toPlainString();
    }

    private AppealDtos.AppealResponse toResponse(GradeAppeal a) {
        User st = a.getEnrollment().getStudent();
        return new AppealDtos.AppealResponse(a.getId(), a.getTargetType(), a.getTargetId(), a.getTargetTitle(), a.getCourseId(),
                a.getEnrollment().getCourse().getTitle(), nameOf(st), st.getEmail(), a.getReason(), a.getStatus(), a.getResponse(),
                a.getCreatedAt(), a.getResolvedAt());
    }

    private String nameOf(User u) {
        return userProfiles.findById(u.getId()).map(UserProfile::getFullName).orElse(u.getEmail());
    }
}
