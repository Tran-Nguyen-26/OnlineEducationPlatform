package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.CourseResponse;
import com.oep.corelms.dto.admin.CreateCourseRequest;
import com.oep.corelms.dto.admin.UpdateCourseRequest;
import com.oep.corelms.dto.admin.UpdateCourseStatusRequest;
import com.oep.corelms.service.AdminCourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/courses")
@RequiredArgsConstructor
public class AdminCourseController {

    private final AdminCourseService courseService;

    @PostMapping
    public CourseResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateCourseRequest request) {
        return courseService.create(UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping
    public List<CourseResponse> list() {
        return courseService.list();
    }

    @GetMapping("/{id}")
    public CourseResponse get(@PathVariable UUID id) {
        return courseService.get(id);
    }

    @PatchMapping("/{id}")
    public CourseResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCourseRequest request) {
        return courseService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public CourseResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateCourseStatusRequest request) {
        return courseService.updateStatus(id, request.status());
    }
}
