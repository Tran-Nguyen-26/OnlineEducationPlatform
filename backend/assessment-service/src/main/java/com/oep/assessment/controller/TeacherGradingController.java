package com.oep.assessment.controller;

import com.oep.assessment.dto.GradeSubmissionRequest;
import com.oep.assessment.dto.SubmissionForGradingResponse;
import com.oep.assessment.dto.TeacherAssignmentResponse;
import com.oep.assessment.service.TeacherGradingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TeacherGradingController {

    private final TeacherGradingService gradingService;

    @GetMapping("/api/assignments/by-course/{courseId}")
    public List<TeacherAssignmentResponse> listForCourse(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return gradingService.listAssignmentsForCourse(courseId, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/api/assignments/{assignmentId}/submissions")
    public List<SubmissionForGradingResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID assignmentId) {
        return gradingService.listSubmissions(assignmentId, UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/api/submissions/{submissionId}/grade")
    public SubmissionForGradingResponse grade(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID submissionId,
                                               @Valid @RequestBody GradeSubmissionRequest request) {
        return gradingService.grade(submissionId, UUID.fromString(jwt.getSubject()), request);
    }
}
