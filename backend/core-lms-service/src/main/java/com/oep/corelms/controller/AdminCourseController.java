package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.CourseResponse;
import com.oep.corelms.dto.admin.CreateCourseRequest;
import com.oep.corelms.dto.admin.UpdateCourseRequest;
import com.oep.corelms.dto.admin.UpdateCourseStatusRequest;
import com.oep.corelms.service.AdminCourseService;
import com.oep.corelms.service.AiTutorCourseLifecycleService;
import com.oep.corelms.service.AuditLogService;
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

import java.util.Map;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/courses")
@RequiredArgsConstructor
public class AdminCourseController {

    private final AdminCourseService courseService;
    private final AuditLogService auditLogService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AiTutorCourseLifecycleService aiTutorLifecycle;

    @PostMapping
    public CourseResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateCourseRequest request) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        CourseResponse response = courseService.create(adminId, request);
        auditLogService.record(adminId, "CREATE_COURSE", "COURSE", response.id(), Map.of("title", response.title()));
        return response;
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
    public CourseResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                  @Valid @RequestBody UpdateCourseRequest request) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        CourseResponse response = courseService.update(id, request);
        auditLogService.record(adminId, "UPDATE_COURSE", "COURSE", id, Map.of("title", response.title()));
        return response;
    }

    @PatchMapping("/{id}/status")
    public CourseResponse updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @Valid @RequestBody UpdateCourseStatusRequest request) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        CourseResponse response = aiTutorLifecycle == null
                ? courseService.updateStatus(id, request.status())
                : aiTutorLifecycle.updateStatus(id, request.status());
        auditLogService.record(adminId, "UPDATE_COURSE_STATUS", "COURSE", id, Map.of("status", request.status().name()));
        return response;
    }
}
