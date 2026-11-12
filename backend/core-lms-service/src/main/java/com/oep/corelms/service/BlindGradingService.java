package com.oep.corelms.service;

import com.oep.corelms.repository.AssignmentGradeRepository;
import com.oep.corelms.repository.AuditLogRepository;
import com.oep.corelms.repository.BlindGradingRepository;
import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.assignment.Assignment.RubricCriterion;
import com.oep.coremodel.entity.assignment.AssignmentGrade;
import com.oep.coremodel.entity.assignment.AssignmentSubmission;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.system.AuditLog;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.AssignmentStatus;
import com.oep.coremodel.enums.AuditAction;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BlindGradingService {
    private static final Duration LEASE_DURATION = Duration.ofMinutes(30);
    private static final Duration APPEAL_WINDOW = Duration.ofDays(7);

    private final BlindGradingRepository submissions;
    private final AssignmentGradeRepository grades;
    private final CourseInstructorRepository instructors;
    private final UserRepository users;
    private final AuditLogRepository auditLogs;
    private final NotificationPublisher notifications;
    private final ObjectMapper json;

    public record QueueItem(UUID id, String code, String title, boolean appeal) {}
    public record Claim(QueueItem item, UUID token, Instant expiresAt, String text,
                        List<RubricCriterion> rubric, String appealCriterion, String appealReason) {}
    public record Result(BigDecimal score, Map<String, BigDecimal> scores, String feedback,
                         Instant gradedAt, boolean regraded, Instant appealUntil) {}

    @Transactional(readOnly = true)
    public List<QueueItem> queue(UUID actor, UUID course, int page) {
        requireInstructor(actor, course);
        if (page < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid page");
        }
        return submissions.queue(course, actor, Instant.now(), PageRequest.of(page, 50))
                .stream().map(this::item).toList();
    }

    public Claim claim(UUID actor, UUID course, UUID id) {
        requireInstructor(actor, course);
        AssignmentSubmission submission = get(course, id);
        requireGradable(submission, actor);
        Instant now = Instant.now();
        if (leaseActive(submission, now) && !actor.equals(submission.getClaimedBy())) {
            throw conflict("Submission is already claimed");
        }
        if (!leaseActive(submission, now)) {
            submission.setClaimedBy(actor);
            submission.setLockToken(UUID.randomUUID());
        }
        return extend(submission, now);
    }

    public Claim renew(UUID actor, UUID course, UUID id, UUID token) {
        requireInstructor(actor, course);
        AssignmentSubmission submission = get(course, id);
        Instant now = Instant.now();
        requireLease(submission, actor, token, now);
        return extend(submission, now);
    }

    public void grade(UUID actor, UUID course, UUID id, UUID token,
                      Map<String, BigDecimal> scores, String feedback) {
        User reviewer = requireInstructor(actor, course);
        AssignmentSubmission submission = get(course, id);
        Instant now = Instant.now();
        requireLease(submission, actor, token, now);
        requireGradable(submission, actor);

        BigDecimal score = score(submission, scores);
        boolean appeal = submission.getStatus() == AssignmentStatus.PENDING_REGRADE;
        AssignmentGrade grade = grades.findBySubmissionId(id).orElseGet(AssignmentGrade::new);
        BigDecimal discrepancy = BigDecimal.ZERO;
        if (appeal) {
            discrepancy = score.subtract(grade.getScore()).abs();
            recordRegrade(submission, grade, reviewer, score, scores, feedback);
            grade.setPreviousScore(grade.getScore());
            grade.setRegradeReason(submission.getAppealReason());
            grade.setRegradedAt(now);
            grade.setRegradeReviewerId(actor);
        } else {
            grade.setSubmission(submission);
            grade.setGradedAt(now);
            grade.setGradedBy(reviewer);
        }
        grade.setScore(score);
        grade.setRubricScores(scores);
        grade.setFeedback(feedback);
        submission.setStatus(AssignmentStatus.GRADED);
        submission.setClaimedBy(null);
        submission.setLockToken(null);
        submission.setLockExpiresAt(null);

        submissions.flush();
        grades.save(grade);
        submissions.flush();

        UUID learner = submission.getEnrollment().getStudent().getId();
        notifications.publish("ASSIGNMENT_SUBMISSION", id, "ASSIGNMENT_GRADED", Map.of(
                "eventType", "ASSIGNMENT_GRADED", "targetType", "ASSIGNMENT_SUBMISSION", "targetId", id,
                "recipientUserId", learner, "title", "Bài tập đã được chấm",
                "message", "Bạn có thể xem kết quả bài tập.", "regraded", appeal,
                "dedupeKey", notifications.computeDedupeKey("ASSIGNMENT_GRADED", id, learner) + ":" + appeal));
        if (appeal && discrepancy.compareTo(submission.getAssignment().getMaxScore()
                .multiply(new BigDecimal("0.15"))) > 0) {
            for (User admin : users.findByRoleAndAccountStatus(UserRole.ADMIN, AccountStatus.ACTIVE)) {
                notifications.publishNotificationEvent("ASSIGNMENT_REGRADE_DISCREPANCY",
                        "ASSIGNMENT_SUBMISSION", id, admin.getId(), "Chênh lệch điểm phúc khảo",
                        "Điểm phúc khảo chênh hơn 15% thang điểm.", null,
                        Map.of("difference", discrepancy));
            }
        }
    }

    @Transactional(readOnly = true)
    public Result result(UUID actor, UUID course, UUID id) {
        AssignmentSubmission submission = get(course, id);
        requireOwner(submission, actor);
        AssignmentGrade grade = grades.findBySubmissionId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Grade is unavailable"));
        Instant until = submission.getStatus() == AssignmentStatus.GRADED && grade.getRegradedAt() == null
                ? grade.getGradedAt().plus(APPEAL_WINDOW) : null;
        return new Result(grade.getScore(), grade.getRubricScores(), grade.getFeedback(),
                grade.getRegradedAt() == null ? grade.getGradedAt() : grade.getRegradedAt(),
                grade.getRegradedAt() != null, until);
    }

    public void appeal(UUID actor, UUID course, UUID id, String criterion, String reason) {
        AssignmentSubmission submission = get(course, id);
        requireOwner(submission, actor);
        AssignmentGrade grade = grades.findBySubmissionId(id)
                .orElseThrow(() -> conflict("Submission has no grade"));
        if (submission.getStatus() != AssignmentStatus.GRADED || grade.getRegradedAt() != null
                || !Instant.now().isBefore(grade.getGradedAt().plus(APPEAL_WINDOW))) {
            throw conflict("Submission is outside the appeal window or has already been appealed");
        }
        if (grade.getRubricScores() == null || !grade.getRubricScores().containsKey(criterion)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown rubric criterion");
        }
        submission.setAppealCriterion(criterion);
        submission.setAppealReason(reason);
        submission.setStatus(AssignmentStatus.PENDING_REGRADE);
        submissions.flush();
    }

    private User requireInstructor(UUID actor, UUID course) {
        User instructor = users.findById(actor)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Instructor access required"));
        if (instructor.getRole() != UserRole.INSTRUCTOR || instructor.getAccountStatus() != AccountStatus.ACTIVE
                || instructors.findByIdCourseIdAndIdInstructorId(course, actor).isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Course instructor access required");
        }
        return instructor;
    }

    private AssignmentSubmission get(UUID course, UUID id) {
        return submissions.findByIdAndAssignmentCourseId(id, course)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Submission not found"));
    }

    private void requireOwner(AssignmentSubmission submission, UUID actor) {
        if (!submission.getEnrollment().getStudent().getId().equals(actor)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Submission not found");
        }
    }

    private void requireGradable(AssignmentSubmission submission, UUID actor) {
        if (submission.getTextContent() == null || submission.getTextContent().isBlank()) {
            throw conflict("Submission has no reviewable text");
        }
        if (submission.getStatus() == AssignmentStatus.PENDING_REGRADE) {
            AssignmentGrade firstGrade = grades.findBySubmissionId(submission.getId())
                    .orElseThrow(() -> conflict("Missing original grade"));
            if (firstGrade.getGradedBy().getId().equals(actor)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Appeals require an independent reviewer");
            }
        } else if (submission.getStatus() != AssignmentStatus.SUBMITTED) {
            throw conflict("Submission is unavailable for grading");
        }
    }

    private Claim extend(AssignmentSubmission submission, Instant now) {
        submission.setLockExpiresAt(now.plus(LEASE_DURATION));
        submissions.flush();
        return new Claim(item(submission), submission.getLockToken(), submission.getLockExpiresAt(),
                submission.getTextContent(), submission.getAssignment().getRubric(),
                submission.getAppealCriterion(), submission.getAppealReason());
    }

    private boolean leaseActive(AssignmentSubmission submission, Instant now) {
        return submission.getLockExpiresAt() != null && now.isBefore(submission.getLockExpiresAt());
    }

    private void requireLease(AssignmentSubmission submission, UUID actor, UUID token, Instant now) {
        if (!leaseActive(submission, now) || !actor.equals(submission.getClaimedBy())
                || !Objects.equals(token, submission.getLockToken())) {
            throw conflict("Claim lease has expired or is no longer valid");
        }
    }

    private QueueItem item(AssignmentSubmission submission) {
        UUID id = submission.getId();
        return new QueueItem(id, "#SUB-" + id, submission.getAssignment().getTitle(),
                submission.getStatus() == AssignmentStatus.PENDING_REGRADE);
    }

    private BigDecimal score(AssignmentSubmission submission, Map<String, BigDecimal> scores) {
        List<RubricCriterion> rubric = submission.getAssignment().getRubric();
        if (rubric == null || rubric.isEmpty()) {
            throw conflict("Assignment has no grading rubric");
        }
        Map<String, BigDecimal> maxima = rubric.stream()
                .collect(Collectors.toMap(RubricCriterion::code, RubricCriterion::maxScore));
        if (!maxima.keySet().equals(scores.keySet()) || scores.entrySet().stream().anyMatch(entry ->
                entry.getValue() == null || entry.getValue().signum() < 0 || entry.getValue().scale() > 2
                        || entry.getValue().compareTo(maxima.get(entry.getKey())) > 0)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid rubric scores");
        }
        BigDecimal total = scores.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(submission.getAssignment().getMaxScore()) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Score exceeds assignment maximum");
        }
        return total;
    }

    private void recordRegrade(AssignmentSubmission submission, AssignmentGrade grade, User reviewer,
                               BigDecimal score, Map<String, BigDecimal> scores, String feedback) {
        var metadata = json.createObjectNode();
        metadata.put("originalScore", grade.getScore());
        metadata.set("originalRubricScores", json.valueToTree(grade.getRubricScores()));
        metadata.put("originalFeedback", grade.getFeedback());
        metadata.put("originalReviewerId", grade.getGradedBy().getId().toString());
        metadata.put("criterion", submission.getAppealCriterion());
        metadata.put("reason", submission.getAppealReason());
        metadata.put("finalScore", score);
        metadata.set("finalRubricScores", json.valueToTree(scores));
        metadata.put("finalFeedback", feedback);

        AuditLog entry = new AuditLog();
        entry.setActor(reviewer);
        entry.setAction(AuditAction.REGRADE);
        entry.setTargetType("ASSIGNMENT_SUBMISSION");
        entry.setTargetId(submission.getId());
        entry.setMetadata(metadata.toString());
        auditLogs.save(entry);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.GRADING_STATE_CONFLICT, message);
    }
}
