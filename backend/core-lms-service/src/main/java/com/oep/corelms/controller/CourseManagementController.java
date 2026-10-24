package com.oep.corelms.controller;

import com.oep.corelms.dto.course.CourseDtos;
import com.oep.corelms.dto.course.LessonDtos;
import com.oep.corelms.dto.course.LessonContentDtos;
import com.oep.corelms.dto.course.ModuleDtos;
import com.oep.corelms.service.CourseManagementService;
import com.oep.coremodel.enums.UploadStatus;
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

    @GetMapping("/courses/{courseId}/outline")
    public CourseDtos.CourseOutline getCourseOutline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return service.getCourseOutline(userIdOrNull(jwt), courseId);
    }

    @PatchMapping("/courses/{courseId}")
    public CourseDtos.CourseResponse updateCourse(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                                   @Valid @RequestBody CourseDtos.UpdateCourseRequest request) {
        return service.updateCourse(userId(jwt), courseId, request);
    }

    @DeleteMapping("/courses/{courseId}")
    public CourseDtos.CourseResponse deleteDraftCourse(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable UUID courseId) {
        return service.deleteDraftCourse(userId(jwt), courseId);
    }

    @PostMapping("/courses/{courseId}/publish")
    public CourseDtos.CourseResponse publishCourse(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return service.publishCourse(userId(jwt), courseId);
    }

    @PostMapping("/courses/{courseId}/unpublish")
    public CourseDtos.CourseResponse unpublishCourse(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return service.unpublishCourse(userId(jwt), courseId);
    }

    @PostMapping("/courses/{courseId}/archive")
    public CourseDtos.CourseResponse archiveCourse(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return service.archiveCourse(userId(jwt), courseId);
    }

    @PostMapping("/courses/{courseId}/restore")
    public CourseDtos.CourseResponse restoreCourse(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return service.restoreCourse(userId(jwt), courseId);
    }

    @PostMapping("/courses/{courseId}/disable")
    public CourseDtos.CourseResponse disableCourse(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return service.disableCourse(userId(jwt), courseId);
    }

    @PostMapping("/courses/{courseId}/enable")
    public CourseDtos.CourseResponse enableCourse(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return service.enableCourse(userId(jwt), courseId);
    }

    @GetMapping("/courses/{courseId}/instructors")
    public CourseDtos.InstructorList listCourseInstructors(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return service.listCourseInstructors(userId(jwt), courseId);
    }

    @PostMapping("/courses/{courseId}/instructors")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseDtos.InstructorResponse addCoInstructor(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                                         @Valid @RequestBody CourseDtos.AddInstructorRequest request) {
        return service.addCoInstructor(userId(jwt), courseId, request);
    }

    @DeleteMapping("/courses/{courseId}/instructors/{instructorId}")
    public CourseDtos.DeleteInstructorResponse removeCoInstructor(@AuthenticationPrincipal Jwt jwt,
                                                                  @PathVariable UUID courseId,
                                                                  @PathVariable UUID instructorId) {
        return service.removeCoInstructor(userId(jwt), courseId, instructorId);
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

    @DeleteMapping("/courses/{courseId}/modules/{moduleId}")
    public ModuleDtos.DeleteResponse deleteModule(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable UUID courseId,
                                                  @PathVariable UUID moduleId) {
        return service.deleteModule(userId(jwt), courseId, moduleId);
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

    @DeleteMapping("/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}")
    public LessonDtos.DeleteResponse deleteLesson(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable UUID courseId,
                                                  @PathVariable UUID moduleId,
                                                  @PathVariable UUID lessonId) {
        return service.deleteLesson(userId(jwt), courseId, moduleId, lessonId);
    }

    @PatchMapping("/courses/{courseId}/modules/{moduleId}/lesson-order")
    public LessonDtos.ReorderResponse reorderLessons(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                                       @PathVariable UUID moduleId,
                                                       @Valid @RequestBody LessonDtos.ReorderRequest request) {
        return service.reorderLessons(userId(jwt), courseId, moduleId, request);
    }

    @GetMapping("/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}/contents")
    public LessonContentDtos.PageResponse listLessonContents(@AuthenticationPrincipal Jwt jwt,
                                                              @PathVariable UUID courseId,
                                                              @PathVariable UUID moduleId,
                                                              @PathVariable UUID lessonId,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
                                                              @RequestParam(name = "upload_status", required = false) UploadStatus status) {
        return service.listLessonContents(userId(jwt), courseId, moduleId, lessonId, page, pageSize, status);
    }

    @PostMapping("/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}/contents")
    @ResponseStatus(HttpStatus.CREATED)
    public LessonContentDtos.Response createLessonContent(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable UUID courseId,
                                                           @PathVariable UUID moduleId,
                                                           @PathVariable UUID lessonId,
                                                           @Valid @RequestBody LessonContentDtos.CreateRequest request) {
        return service.createLessonContent(userId(jwt), courseId, moduleId, lessonId, request);
    }

    @GetMapping("/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}/contents/{contentId}")
    public LessonContentDtos.Response getLessonContent(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable UUID courseId,
                                                        @PathVariable UUID moduleId,
                                                        @PathVariable UUID lessonId,
                                                        @PathVariable UUID contentId) {
        return service.getLessonContent(userId(jwt), courseId, moduleId, lessonId, contentId);
    }

    @PatchMapping("/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}/contents/{contentId}")
    public LessonContentDtos.Response updateLessonContent(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable UUID courseId,
                                                           @PathVariable UUID moduleId,
                                                           @PathVariable UUID lessonId,
                                                           @PathVariable UUID contentId,
                                                           @Valid @RequestBody LessonContentDtos.UpdateRequest request) {
        return service.updateLessonContent(userId(jwt), courseId, moduleId, lessonId, contentId, request);
    }

    @DeleteMapping("/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}/contents/{contentId}")
    public LessonContentDtos.DeleteResponse deleteLessonContent(@AuthenticationPrincipal Jwt jwt,
                                                                  @PathVariable UUID courseId,
                                                                  @PathVariable UUID moduleId,
                                                                  @PathVariable UUID lessonId,
                                                                  @PathVariable UUID contentId) {
        return service.deleteLessonContent(userId(jwt), courseId, moduleId, lessonId, contentId);
    }

    private UUID userId(Jwt jwt) {
        if (jwt == null) throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        return UUID.fromString(jwt.getSubject());
    }
    private UUID userIdOrNull(Jwt jwt) { return jwt == null ? null : UUID.fromString(jwt.getSubject()); }
}
