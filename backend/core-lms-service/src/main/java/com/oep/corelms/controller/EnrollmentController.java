package com.oep.corelms.controller;

import com.oep.corelms.dto.student.EnrollRequest;
import com.oep.corelms.dto.student.MyEnrollmentResponse;
import com.oep.corelms.dto.student.MyProgressResponse;
import com.oep.corelms.service.EnrollmentService;
import com.oep.corelms.service.StudentCourseContentService;
import com.oep.corelms.service.StudentCourseService;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final StudentCourseService studentCourseService;
    private final StudentCourseContentService studentCourseContentService;

    @PostMapping
    public MyEnrollmentResponse enroll(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EnrollRequest request) {
        requireStudent(jwt);
        return enrollmentService.enroll(request.courseId(), UUID.fromString(jwt.getSubject()), request.inviteCode());
    }

    @GetMapping
    public List<MyEnrollmentResponse> myEnrollments(@AuthenticationPrincipal Jwt jwt) {
        return studentCourseService.myEnrollments(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/progress")
    public List<MyProgressResponse> myProgress(@AuthenticationPrincipal Jwt jwt) {
        return studentCourseContentService.myProgress(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/{enrollmentId}/withdraw")
    public MyEnrollmentResponse withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID enrollmentId) {
        return enrollmentService.withdraw(enrollmentId, UUID.fromString(jwt.getSubject()));
    }

    private void requireStudent(Jwt jwt) {
        if (!"STUDENT".equals(jwt.getClaimAsString("role"))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only students can enroll in courses");
        }
    }
}
