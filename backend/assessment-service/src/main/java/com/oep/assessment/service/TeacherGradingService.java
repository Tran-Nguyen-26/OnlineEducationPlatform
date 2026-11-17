package com.oep.assessment.service;

import com.oep.assessment.service.NotificationPublisher;
import com.oep.assessment.dto.GradeSubmissionRequest;
import com.oep.assessment.dto.SubmissionForGradingResponse;
import com.oep.assessment.dto.SubmissionFileResponse;
import com.oep.assessment.dto.TeacherAssignmentResponse;
import com.oep.assessment.repository.AssignmentGradeRepository;
import com.oep.assessment.repository.AssignmentRepository;
import com.oep.assessment.repository.AssignmentSubmissionRepository;
import com.oep.assessment.repository.CourseInstructorRepository;
import com.oep.assessment.repository.SubmissionFileRepository;
import com.oep.assessment.repository.UserProfileRepository;
import com.oep.assessment.repository.UserRepository;
import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.entity.assignment.Assignment;
import com.oep.coremodel.entity.assignment.AssignmentGrade;
import com.oep.coremodel.entity.assignment.AssignmentSubmission;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.enums.AssignmentStatus;
import com.oep.coremodel.event.NotificationRequestedEvent;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Only a teacher assigned to the assignment's course may see/grade its submissions. */
@Service
@RequiredArgsConstructor
public class TeacherGradingService {

    private final AssignmentRepository assignments;
    private final AssignmentSubmissionRepository submissions;
    private final SubmissionFileRepository submissionFiles;
    private final AssignmentGradeRepository assignmentGrades;
    private final CourseInstructorRepository courseInstructors;
    private final UserRepository users;
    private final UserProfileRepository userProfiles;
    private final NotificationPublisher notificationPublisher;

    @Transactional(readOnly = true)
    public List<TeacherAssignmentResponse> listAssignmentsForCourse(UUID courseId, UUID instructorId) {
        requireAssignedInstructor(courseId, instructorId);
        return assignments.findByCourse_IdOrderByCreatedAtDesc(courseId).stream()
                .map(a -> new TeacherAssignmentResponse(a.getId(), a.getTitle(), a.getInstructions(), a.getDeadline(),
                        a.getMaxScore(), submissions.findByAssignment_IdOrderBySubmittedAtDesc(a.getId()).size()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubmissionForGradingResponse> listSubmissions(UUID assignmentId, UUID instructorId) {
        Assignment assignment = findAssignment(assignmentId);
        requireAssignedInstructor(assignment.getCourse().getId(), instructorId);
        return submissions.findByAssignment_IdOrderBySubmittedAtDesc(assignmentId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SubmissionForGradingResponse grade(UUID submissionId, UUID instructorId, GradeSubmissionRequest request) {
        AssignmentSubmission submission = submissions.findById(submissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Submission not found"));
        requireAssignedInstructor(submission.getAssignment().getCourse().getId(), instructorId);

        Optional<AssignmentGrade> existing = assignmentGrades.findBySubmission_Id(submissionId);
        AssignmentGrade grade = existing.orElseGet(AssignmentGrade::new);
        if (existing.isPresent()) {
            grade.setPreviousScore(existing.get().getScore());
            grade.setRegradedAt(Instant.now());
        }
        grade.setSubmission(submission);
        grade.setScore(request.score());
        grade.setFeedback(request.feedback());
        grade.setGradedBy(users.getReferenceById(instructorId));
        grade.setGradedAt(Instant.now());
        AssignmentGrade saved = assignmentGrades.saveAndFlush(grade);

        submission.setStatus(AssignmentStatus.GRADED);
        submissions.save(submission);

        notifyStudent(submission, saved);
        return toResponse(submission);
    }

    private void notifyStudent(AssignmentSubmission submission, AssignmentGrade grade) {
        Assignment assignment = submission.getAssignment();
        UUID studentId = submission.getEnrollment().getStudent().getId();
        String courseTitle = assignment.getCourse().getTitle();
        String message = "Bài tập \"" + assignment.getTitle() + "\" đã được chấm: "
                + grade.getScore().stripTrailingZeros().toPlainString() + "/" + assignment.getMaxScore().stripTrailingZeros().toPlainString() + " điểm";

        NotificationRequestedEvent event = new NotificationRequestedEvent(
                studentId, "ASSIGNMENT_GRADED", "Bài tập đã được chấm — " + courseTitle, message,
                "assignment-graded:" + grade.getId() + ":" + grade.getGradedAt().toEpochMilli(),
                "COURSE", assignment.getCourse().getId(), Instant.now());
        notificationPublisher.publish(event);
    }

    private void requireAssignedInstructor(UUID courseId, UUID instructorId) {
        if (!courseInstructors.existsByCourse_IdAndInstructor_Id(courseId, instructorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You are not assigned to this course");
        }
    }

    private Assignment findAssignment(UUID assignmentId) {
        return assignments.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Assignment not found"));
    }

    private SubmissionForGradingResponse toResponse(AssignmentSubmission submission) {
        User student = submission.getEnrollment().getStudent();
        String name = userProfiles.findById(student.getId()).map(UserProfile::getFullName).orElse(student.getEmail());
        List<SubmissionFileResponse> files = submissionFiles.findBySubmission_Id(submission.getId()).stream()
                .map(SubmissionFileResponse::from)
                .toList();
        Optional<AssignmentGrade> grade = assignmentGrades.findBySubmission_Id(submission.getId());
        return new SubmissionForGradingResponse(
                submission.getId(), student.getId(), name, student.getEmail(), submission.getTextContent(),
                submission.getSubmissionCount(), submission.getStatus(), submission.getSubmittedAt(), submission.isLate(),
                files, grade.map(AssignmentGrade::getScore).orElse(null), grade.map(AssignmentGrade::getFeedback).orElse(null));
    }
}
