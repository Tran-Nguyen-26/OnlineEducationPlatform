package com.oep.corelms.controller;

import com.oep.corelms.dto.student.CourseOutlineResponse;
import com.oep.corelms.dto.student.StudentCourseResponse;
import com.oep.corelms.service.StudentCourseContentService;
import com.oep.corelms.service.StudentCourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Any logged-in user can browse the public catalog; the outline requires an active enrollment. */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class StudentCourseController {

    private final StudentCourseService courseService;
    private final StudentCourseContentService contentService;

    @GetMapping
    public List<StudentCourseResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return courseService.listCatalog(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/{courseId}")
    public StudentCourseResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return courseService.getCourse(courseId, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/{courseId}/outline")
    public CourseOutlineResponse outline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return contentService.getOutline(courseId, UUID.fromString(jwt.getSubject()));
    }
}
