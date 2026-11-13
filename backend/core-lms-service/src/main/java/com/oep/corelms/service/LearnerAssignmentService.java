package com.oep.corelms.service;

import com.oep.corelms.repository.*;
import com.oep.coremodel.entity.assignment.Assignment;
import com.oep.coremodel.entity.assignment.AssignmentSubmission;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.enums.*;
import com.oep.coremodel.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LearnerAssignmentService {
    private final CourseAssignmentRepository assignments;
    private final EnrollmentRepository enrollments;
    private final BlindGradingRepository submissions;
    private final LessonMasteryService mastery;

    public record Item(UUID id, String title, String instructions, Instant deadline, boolean allowText, boolean allowFiles,
                       UUID submissionId, AssignmentStatus status, String submittedText, boolean canSubmit) {}

    @Transactional(readOnly = true)
    public List<Item> list(UUID actor, UUID course) {
        var enrollment = enrollments.findByCourseIdAndStudentId(course, actor).filter(this::eligible).orElseThrow(this::forbidden);
        var unlocked = mastery.lessons(actor, course).stream().filter(LessonMasteryService.Lesson::unlocked)
                .map(LessonMasteryService.Lesson::id).collect(java.util.stream.Collectors.toSet());
        return assignments.findByCourseIdOrderByCreatedAtAsc(course).stream()
                .filter(item -> item.getLesson() == null || unlocked.contains(item.getLesson().getId()))
                .map(item -> view(item, submissions.findByAssignmentIdAndEnrollmentId(item.getId(), enrollment.getId()).orElse(null), Instant.now()))
                .toList();
    }

    @Transactional
    public Item submit(UUID actor, UUID course, UUID assignmentId, String text, boolean file) {
        Enrollment enrollment = enrollments.lockForAssessment(course, actor).filter(this::eligible).orElseThrow(this::forbidden);
        Assignment assignment = assignments.findByIdAndCourseId(assignmentId, course)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy bài tự luận"));
        if (assignment.getLesson() != null) mastery.requireUnlocked(actor, course, assignment.getLesson().getId());
        if (file ? !assignment.isAllowFiles() : !assignment.isAllowText()) throw forbidden();
        Instant now = Instant.now();
        var submission = submissions.findByAssignmentIdAndEnrollmentId(assignmentId, enrollment.getId()).orElse(null);
        if (submission != null && Objects.equals(submission.getTextContent(), text)) return view(assignment, submission, now);
        if (!canSubmit(assignment, submission, now))
            throw new BusinessException(ErrorCode.GRADING_STATE_CONFLICT, "Đã hết hạn, hết lượt hoặc bài đã được nhận chấm");
        if (submission == null) {
            submission = new AssignmentSubmission();
            submission.setAssignment(assignment);
            submission.setEnrollment(enrollment);
        } else {
            submission.setSubmissionCount(submission.getSubmissionCount() + 1);
            submission.setClaimedBy(null);
            submission.setLockExpiresAt(null);
            submission.setLockToken(null);
        }
        submission.setTextContent(text);
        submission.setSubmittedAt(now);
        submission.setLate(assignment.getDeadline() != null && now.isAfter(assignment.getDeadline()));
        return view(assignment, submissions.saveAndFlush(submission), now);
    }

    private Item view(Assignment assignment, AssignmentSubmission submission, Instant now) {
        return new Item(assignment.getId(), assignment.getTitle(), assignment.getInstructions(), assignment.getDeadline(),
                assignment.isAllowText(), assignment.isAllowFiles(), submission == null ? null : submission.getId(),
                submission == null ? null : submission.getStatus(), submission == null ? null : submission.getTextContent(),
                canSubmit(assignment, submission, now));
    }

    private boolean canSubmit(Assignment assignment, AssignmentSubmission submission, Instant now) {
        boolean beforeDeadline = assignment.getDeadline() == null || !now.isAfter(assignment.getDeadline());
        if (submission == null) return beforeDeadline || assignment.isAllowLateSubmission();
        return beforeDeadline && submission.getStatus() == AssignmentStatus.SUBMITTED
                && submission.getSubmissionCount() < assignment.getMaxSubmissions()
                && submission.getClaimedBy() == null;
    }

    private boolean eligible(Enrollment item) {
        return item.getStatus() == EnrollmentStatus.ACTIVE && item.getStudent().getRole() == UserRole.STUDENT
                && item.getStudent().getAccountStatus() == AccountStatus.ACTIVE
                && item.getCourse().getLifecycleStatus() == CourseLifecycleStatus.PUBLISHED
                && (item.getAccessExpiresAt() == null || !Instant.now().isAfter(item.getAccessExpiresAt()));
    }

    private BusinessException forbidden() { return new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền nộp bài"); }

    @Transactional
    public Item submitText(UUID actor, UUID course, UUID id, String text) {
        return submit(actor, course, id, AssignmentText.validate(text), false);
    }

    public static String extract(org.springframework.web.multipart.MultipartFile file) {
        return AssignmentText.read(file);
    }

    @Transactional(readOnly = true)
    public void checkFileAccess(UUID actor, UUID course, UUID id) {
        enrollments.findByCourseIdAndStudentId(course, actor).filter(this::eligible).orElseThrow(this::forbidden);
        Assignment assignment = assignments.findByIdAndCourseId(id, course).filter(Assignment::isAllowFiles)
                .orElseThrow(this::forbidden);
        if (assignment.getLesson() != null) mastery.requireUnlocked(actor, course, assignment.getLesson().getId());
    }
}
