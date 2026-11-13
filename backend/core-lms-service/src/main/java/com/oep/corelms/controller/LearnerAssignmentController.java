package com.oep.corelms.controller;

import com.oep.corelms.service.LearnerAssignmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{course}/learner-assignments")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class LearnerAssignmentController {
    private final LearnerAssignmentService assignments;
    public record TextSubmission(@NotBlank @Size(max = 100000) String text) {}

    @GetMapping
    public List<LearnerAssignmentService.Item> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course) {
        return assignments.list(UUID.fromString(jwt.getSubject()), course);
    }

    @PutMapping(value = "/{id}/submission", consumes = MediaType.APPLICATION_JSON_VALUE)
    public LearnerAssignmentService.Item text(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course,
            @PathVariable UUID id, @Valid @RequestBody TextSubmission submission) {
        return assignments.submitText(UUID.fromString(jwt.getSubject()), course, id, submission.text());
    }

    @PutMapping(value = "/{id}/submission", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LearnerAssignmentService.Item file(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course,
            @PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        UUID actor = UUID.fromString(jwt.getSubject());
        assignments.checkFileAccess(actor, course, id);
        return assignments.submit(actor, course, id, LearnerAssignmentService.extract(file), true);
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.CONFLICT)
    public java.util.Map<String, String> concurrentSubmission() {
        return java.util.Map.of("message", "Trạng thái bài đã thay đổi. Hãy tải lại trước khi nộp.");
    }
}
