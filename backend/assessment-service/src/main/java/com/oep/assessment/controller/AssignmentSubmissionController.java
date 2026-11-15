package com.oep.assessment.controller;

import com.oep.assessment.dto.CompleteFileUploadRequest;
import com.oep.assessment.dto.MySubmissionResponse;
import com.oep.assessment.dto.RequestFileUploadRequest;
import com.oep.assessment.dto.StudentAssignmentResponse;
import com.oep.assessment.dto.SubmissionFileResponse;
import com.oep.assessment.dto.SubmitTextRequest;
import com.oep.assessment.dto.UploadUrlResponse;
import com.oep.assessment.service.AssignmentSubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AssignmentSubmissionController {

    private final AssignmentSubmissionService submissionService;

    @GetMapping("/api/assignments")
    public List<StudentAssignmentResponse> listForCourse(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID courseId) {
        return submissionService.listForCourse(courseId, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/api/assignments/{assignmentId}/my-submission")
    public ResponseEntity<MySubmissionResponse> mySubmission(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID assignmentId) {
        return submissionService.getMySubmission(assignmentId, UUID.fromString(jwt.getSubject()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/assignments/{assignmentId}/submissions")
    public MySubmissionResponse submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID assignmentId,
                                        @RequestBody SubmitTextRequest request) {
        return submissionService.submitText(assignmentId, UUID.fromString(jwt.getSubject()), request.textContent());
    }

    @PostMapping("/api/submissions/{submissionId}/files/upload-url")
    public UploadUrlResponse requestUploadUrl(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID submissionId,
                                               @Valid @RequestBody RequestFileUploadRequest request) {
        return submissionService.requestFileUploadUrl(submissionId, UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping("/api/submission-files/{fileId}/complete-upload")
    public SubmissionFileResponse completeUpload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID fileId,
                                                  @Valid @RequestBody CompleteFileUploadRequest request) {
        return submissionService.completeFileUpload(fileId, UUID.fromString(jwt.getSubject()), request);
    }
}
