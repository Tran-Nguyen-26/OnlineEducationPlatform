package com.oep.corelms.controller;

import com.oep.corelms.dto.course.CourseDtos;
import com.oep.corelms.dto.course.LessonDtos;
import com.oep.corelms.dto.course.ModuleDtos;
import com.oep.corelms.service.CourseManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CourseManagementController {
    private final CourseManagementService service;

    @GetMapping("/courses")
    public CourseDtos.CoursePage listPublicCourses(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
                                                    @RequestParam(name = "q", required = false) String search) {
        return service.listPublicCourses(page, pageSize, search);
    }

    @GetMapping("/my/courses")
    public CourseDtos.CoursePage listTeachingCourses(@AuthenticationPrincipal Jwt jwt,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return service.listTeachingCourses(userId(jwt), page, pageSize);
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseDtos.CourseResponse createCourse(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody CourseDtos.CreateCourseRequest request) {
        return service.createCourse(userId(jwt), request);
    }

    @GetMapping("/courses/{courseId}")
    public CourseDtos.CourseResponse getCourse(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return service.getCourse(userIdOrNull(jwt), courseId);
    }

    @PatchMapping("/courses/{courseId}")
    public CourseDtos.CourseResponse updateCourse(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                                   @Valid @RequestBody CourseDtos.UpdateCourseRequest request) {
        return service.updateCourse(userId(jwt), courseId, request);
    }

    @GetMapping("/courses/{courseId}/modules")
    public ModuleDtos.PageResponse listModules(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return service.listModules(userId(jwt), courseId, page, pageSize);
    }

    @PostMapping("/courses/{courseId}/modules")
    @ResponseStatus(HttpStatus.CREATED)
    public ModuleDtos.Response createModule(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                            @Valid @RequestBody ModuleDtos.CreateRequest request) {
        return service.createModule(userId(jwt), courseId, request);
    }

    @GetMapping("/courses/{courseId}/modules/{moduleId}")
    public ModuleDtos.Response getModule(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                         @PathVariable UUID moduleId) {
        return service.getModule(userId(jwt), courseId, moduleId);
    }

    @PatchMapping("/courses/{courseId}/modules/{moduleId}")
    public ModuleDtos.Response updateModule(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                            @PathVariable UUID moduleId,
                                            @Valid @RequestBody ModuleDtos.UpdateRequest request) {
        return service.updateModule(userId(jwt), courseId, moduleId, request);
    }

    @PatchMapping("/courses/{courseId}/module-order")
    public ModuleDtos.ReorderResponse reorderModules(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                                       @Valid @RequestBody ModuleDtos.ReorderRequest request) {
        return service.reorderModules(userId(jwt), courseId, request);
    }

    @GetMapping("/courses/{courseId}/modules/{moduleId}/lessons")
    public LessonDtos.PageResponse listLessons(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                               @PathVariable UUID moduleId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return service.listLessons(userId(jwt), courseId, moduleId, page, pageSize);
    }

    @PostMapping("/courses/{courseId}/modules/{moduleId}/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    public LessonDtos.Response createLesson(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                            @PathVariable UUID moduleId,
                                            @Valid @RequestBody LessonDtos.CreateRequest request) {
        return service.createLesson(userId(jwt), courseId, moduleId, request);
    }

    @GetMapping("/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}")
    public LessonDtos.Response getLesson(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                         @PathVariable UUID moduleId, @PathVariable UUID lessonId) {
        return service.getLesson(userId(jwt), courseId, moduleId, lessonId);
    }

    @PatchMapping("/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}")
    public LessonDtos.Response updateLesson(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                            @PathVariable UUID moduleId, @PathVariable UUID lessonId,
                                            @Valid @RequestBody LessonDtos.UpdateRequest request) {
        return service.updateLesson(userId(jwt), courseId, moduleId, lessonId, request);
    }

    @PatchMapping("/courses/{courseId}/modules/{moduleId}/lesson-order")
    public LessonDtos.ReorderResponse reorderLessons(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                                       @PathVariable UUID moduleId,
                                                       @Valid @RequestBody LessonDtos.ReorderRequest request) {
        return service.reorderLessons(userId(jwt), courseId, moduleId, request);
    }

    private UUID userId(Jwt jwt) {
        if (jwt == null) throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        return UUID.fromString(jwt.getSubject());
    }
    private UUID userIdOrNull(Jwt jwt) { return jwt == null ? null : UUID.fromString(jwt.getSubject()); }
}
