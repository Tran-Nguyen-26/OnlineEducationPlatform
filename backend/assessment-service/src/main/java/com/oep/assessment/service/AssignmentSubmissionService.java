package com.oep.assessment.service;

import com.oep.assessment.dto.CompleteFileUploadRequest;
import com.oep.assessment.dto.MySubmissionResponse;
import com.oep.assessment.dto.RequestFileUploadRequest;
import com.oep.assessment.dto.StudentAssignmentResponse;
import com.oep.assessment.dto.SubmissionFileResponse;
import com.oep.assessment.dto.UploadUrlResponse;
import com.oep.assessment.repository.AssignmentGradeRepository;
import com.oep.assessment.repository.AssignmentRepository;
import com.oep.assessment.repository.AssignmentSubmissionRepository;
import com.oep.assessment.repository.CourseInstructorRepository;
import com.oep.assessment.repository.UserProfileRepository;
import com.oep.coremodel.enums.AssignmentStatus;
import com.oep.assessment.repository.EnrollmentRepository;
import com.oep.assessment.repository.SubmissionFileRepository;
import com.oep.coremodel.entity.assignment.Assignment;
import com.oep.coremodel.entity.assignment.AssignmentGrade;
import com.oep.coremodel.entity.assignment.AssignmentSubmission;
import com.oep.coremodel.entity.assignment.SubmissionFile;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.enums.UploadStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Each student has at most one AssignmentSubmission row per assignment; resubmitting updates it, capped by maxSubmissions. */
@Service
@RequiredArgsConstructor
public class AssignmentSubmissionService {

    private final AuditTrail audit;
    private final AssignmentRepository assignments;
    private final AssignmentSubmissionRepository submissions;
    private final SubmissionFileRepository submissionFiles;
    private final AssignmentGradeRepository assignmentGrades;
    private final EnrollmentRepository enrollments;
    private final StorageService storageService;
    private final NotificationPublisher notifications;
    private final CourseInstructorRepository courseInstructors;
    private final UserProfileRepository userProfiles;

    @Transactional(readOnly = true)
    public List<StudentAssignmentResponse> listForCourse(UUID courseId, UUID studentId) {
        Enrollment enrollment = requireEnrollment(courseId, studentId);
        return assignments.findByCourse_IdOrderByCreatedAtDesc(courseId).stream()
                .map(assignment -> toResponse(assignment, enrollment))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<MySubmissionResponse> getMySubmission(UUID assignmentId, UUID studentId) {
        Assignment assignment = findAssignment(assignmentId);
        Enrollment enrollment = requireEnrollment(assignment.getCourse().getId(), studentId);
        return submissions.findByAssignment_IdAndEnrollment_Id(assignmentId, enrollment.getId()).map(this::toSubmissionResponse);
    }

    @Transactional
    public MySubmissionResponse submitText(UUID assignmentId, UUID studentId, String textContent) {
        Assignment assignment = findAssignment(assignmentId);
        Enrollment enrollment = requireEnrollment(assignment.getCourse().getId(), studentId);
        CoursePhaseGuard.requireOpen(assignment.getCourse());

        if (textContent != null && !textContent.isBlank() && !assignment.isAllowText()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "This assignment does not accept text submissions");
        }

        boolean late = assignment.getDeadline() != null && Instant.now().isAfter(assignment.getDeadline());
        if (late && !assignment.isAllowLateSubmission()) {
            throw new BusinessException(ErrorCode.LATE_SUBMISSION_NOT_ALLOWED, "The deadline for this assignment has passed");
        }

        AssignmentSubmission submission = submissions.findByAssignment_IdAndEnrollment_Id(assignmentId, enrollment.getId())
                .orElse(null);
        if (submission == null) {
            submission = new AssignmentSubmission();
            submission.setAssignment(assignment);
            submission.setEnrollment(enrollment);
            submission.setSubmissionCount(1);
        } else {
            if (submission.getSubmissionCount() >= assignment.getMaxSubmissions()) {
                throw new BusinessException(ErrorCode.MAX_SUBMISSIONS_REACHED, "No submissions left for this assignment");
            }
            submission.setSubmissionCount(submission.getSubmissionCount() + 1);
        }
        boolean regrade = submission.getStatus() == AssignmentStatus.GRADED;
        submission.setTextContent(textContent);
        submission.setSubmittedAt(Instant.now());
        submission.setLate(late);
        submission.setStatus(regrade ? AssignmentStatus.PENDING_REGRADE : AssignmentStatus.SUBMITTED);
        submission = submissions.save(submission);
        audit.record(studentId, regrade ? "ASSIGNMENT_RESUBMIT" : "ASSIGNMENT_SUBMIT", "ASSIGNMENT", assignment.getId(), java.util.Map.of("late", late));
        notifyInstructors(assignment, enrollment, submission);
        return toSubmissionResponse(submission);
    }

    @Transactional
    public UploadUrlResponse requestFileUploadUrl(UUID submissionId, UUID studentId, RequestFileUploadRequest request) {
        AssignmentSubmission submission = findOwnedSubmission(submissionId, studentId);
        if (!submission.getAssignment().isAllowFiles()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "This assignment does not accept file submissions");
        }

        String objectKey = storageService.newObjectKey(request.fileName());
        SubmissionFile file = new SubmissionFile();
        file.setSubmission(submission);
        file.setObjectKey(objectKey);
        file.setFileName(request.fileName());
        file.setFileType(request.mimeType());
        file.setFileSize(0);
        file.setUploadStatus(UploadStatus.PENDING);
        submissionFiles.save(file);

        String uploadUrl = storageService.presignUpload(objectKey, request.mimeType());
        return new UploadUrlResponse(uploadUrl, objectKey, 900);
    }

    @Transactional
    public SubmissionFileResponse completeFileUpload(UUID fileId, UUID studentId, CompleteFileUploadRequest request) {
        SubmissionFile file = submissionFiles.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Submission file not found"));
        if (!file.getSubmission().getEnrollment().getStudent().getId().equals(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This file does not belong to you");
        }
        file.setFileSize(request.fileSize());
        file.setUploadStatus(UploadStatus.AVAILABLE);
        return SubmissionFileResponse.from(submissionFiles.save(file));
    }

    private AssignmentSubmission findOwnedSubmission(UUID submissionId, UUID studentId) {
        AssignmentSubmission submission = submissions.findById(submissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Submission not found"));
        if (!submission.getEnrollment().getStudent().getId().equals(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This submission does not belong to you");
        }
        return submission;
    }

    private MySubmissionResponse toSubmissionResponse(AssignmentSubmission submission) {
        List<SubmissionFileResponse> files = submissionFiles.findBySubmission_Id(submission.getId()).stream()
                .map(SubmissionFileResponse::from)
                .toList();
        Optional<AssignmentGrade> grade = assignmentGrades.findBySubmission_Id(submission.getId());
        return new MySubmissionResponse(
                submission.getId(),
                submission.getTextContent(),
                submission.getSubmissionCount(),
                submission.getStatus(),
                submission.getSubmittedAt(),
                submission.isLate(),
                files,
                grade.map(AssignmentGrade::getScore).orElse(null),
                grade.map(AssignmentGrade::getFeedback).orElse(null));
    }

    private StudentAssignmentResponse toResponse(Assignment assignment, Enrollment enrollment) {
        Optional<AssignmentSubmission> submission = submissions.findByAssignment_IdAndEnrollment_Id(assignment.getId(), enrollment.getId());
        int submissionCount = submission.map(AssignmentSubmission::getSubmissionCount).orElse(0);
        boolean canSubmit = submissionCount < assignment.getMaxSubmissions()
                && (assignment.getDeadline() == null || Instant.now().isBefore(assignment.getDeadline()) || assignment.isAllowLateSubmission());
        Optional<AssignmentGrade> grade = submission.flatMap(s -> assignmentGrades.findBySubmission_Id(s.getId()));
        return new StudentAssignmentResponse(
                assignment.getId(),
                assignment.getCourse().getId(),
                assignment.getLesson() == null ? null : assignment.getLesson().getId(),
                assignment.getTitle(),
                assignment.getInstructions(),
                assignment.getDeadline(),
                assignment.getMaxSubmissions(),
                assignment.isAllowLateSubmission(),
                assignment.isAllowText(),
                assignment.isAllowFiles(),
                assignment.getMaxScore(),
                submissionCount,
                canSubmit,
                grade.isPresent(),
                grade.map(AssignmentGrade::getScore).orElse(null));
    }

    private Assignment findAssignment(UUID assignmentId) {
        return assignments.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Assignment not found"));
    }

    private Enrollment requireEnrollment(UUID courseId, UUID studentId) {
        Enrollment enrollment = enrollments.findByCourseIdAndStudentId(courseId, studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_ENROLLED, "Not enrolled in this course"));
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_ENROLLED, "Enrollment is not active");
        }
        return enrollment;
    }

    /** Tells every instructor assigned to the course that new work is waiting (idempotent per submission + count). */
    private void notifyInstructors(Assignment assignment, Enrollment enrollment, AssignmentSubmission submission) {
        UUID courseId = assignment.getCourse().getId();
        String who = userProfiles.findById(enrollment.getStudent().getId()).map(p -> p.getFullName()).orElse(enrollment.getStudent().getEmail());
        for (var ci : courseInstructors.findAll()) {
            if (ci.getCourse().getId().equals(courseId)) {
                notifications.send(ci.getInstructor().getId(), "ASSIGNMENT_SUBMITTED",
                        "Bài nộp mới — " + assignment.getCourse().getTitle(),
                        who + " vừa nộp \"" + assignment.getTitle() + "\"" + (submission.isLate() ? " (nộp muộn)" : ""),
                        "submission:" + submission.getId() + ":" + submission.getSubmissionCount() + ":" + ci.getInstructor().getId(), courseId);
            }
        }
    }
}
