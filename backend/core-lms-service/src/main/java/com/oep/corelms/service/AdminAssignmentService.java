package com.oep.corelms.service;

import com.oep.corelms.dto.admin.AssignmentResponse;
import com.oep.corelms.dto.admin.CreateAssignmentRequest;
import com.oep.corelms.dto.admin.UpdateAssignmentRequest;
import com.oep.corelms.repository.AssignmentRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.assignment.Assignment;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAssignmentService {

    private final CourseRepository courses;
    private final LessonRepository lessons;
    private final AssignmentRepository assignments;
    private final UserRepository users;

    @Transactional
    public AssignmentResponse create(UUID courseId, UUID adminId, CreateAssignmentRequest request) {
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));

        Assignment assignment = new Assignment();
        assignment.setCourse(course);
        assignment.setLesson(resolveLesson(request.lessonId()));
        assignment.setCreatedBy(users.getReferenceById(adminId));
        applyFields(assignment, request.title(), request.instructions(), request.deadline(), request.maxSubmissions(),
                request.allowLateSubmission(), request.allowText(), request.allowFiles(), request.maxScore());

        return AssignmentResponse.from(assignments.saveAndFlush(assignment));
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> list(UUID courseId) {
        return assignments.findByCourse_IdOrderByCreatedAtDesc(courseId).stream().map(AssignmentResponse::from).toList();
    }

    @Transactional
    public AssignmentResponse update(UUID assignmentId, UpdateAssignmentRequest request) {
        Assignment assignment = findOrThrow(assignmentId);
        assignment.setLesson(resolveLesson(request.lessonId()));
        applyFields(assignment, request.title(), request.instructions(), request.deadline(), request.maxSubmissions(),
                request.allowLateSubmission(), request.allowText(), request.allowFiles(), request.maxScore());
        return AssignmentResponse.from(assignments.saveAndFlush(assignment));
    }

    @Transactional
    public void delete(UUID assignmentId) {
        findOrThrow(assignmentId);
        assignments.deleteById(assignmentId);
    }

    private Assignment findOrThrow(UUID assignmentId) {
        return assignments.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Assignment not found"));
    }

    private Lesson resolveLesson(UUID lessonId) {
        if (lessonId == null) return null;
        return lessons.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Lesson not found"));
    }

    private void applyFields(Assignment assignment, String title, String instructions, java.time.Instant deadline,
                              Integer maxSubmissions, Boolean allowLateSubmission, Boolean allowText, Boolean allowFiles,
                              java.math.BigDecimal maxScore) {
        assignment.setTitle(title);
        assignment.setInstructions(instructions);
        assignment.setDeadline(deadline);
        if (maxSubmissions != null) assignment.setMaxSubmissions(maxSubmissions);
        if (allowLateSubmission != null) assignment.setAllowLateSubmission(allowLateSubmission);
        if (allowText != null) assignment.setAllowText(allowText);
        if (allowFiles != null) assignment.setAllowFiles(allowFiles);
        if (maxScore != null) assignment.setMaxScore(maxScore);
    }
}
