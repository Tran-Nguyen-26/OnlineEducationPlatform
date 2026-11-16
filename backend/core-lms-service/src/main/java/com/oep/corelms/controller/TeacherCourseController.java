package com.oep.corelms.controller;

import com.oep.corelms.dto.teacher.CourseContentResponse;
import com.oep.corelms.dto.teacher.InstructorCourseResponse;
import com.oep.corelms.dto.teacher.RosterEntryResponse;
import com.oep.corelms.dto.teacher.TeacherDashboardResponse;
import com.oep.corelms.service.TeacherCourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Teacher-facing, read-only: all content authoring stays under /api/admin/** (ADMIN only). */
@RestController
@RequiredArgsConstructor
public class TeacherCourseController {

    private final TeacherCourseService teacherCourseService;

    @GetMapping("/api/instructors/me/courses")
    public List<InstructorCourseResponse> myCourses(@AuthenticationPrincipal Jwt jwt) {
        return teacherCourseService.listMyCourses(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/api/instructors/me/dashboard")
    public TeacherDashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt) {
        return teacherCourseService.dashboard(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/api/courses/{courseId}/content")
    public CourseContentResponse content(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return teacherCourseService.getContent(courseId, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/api/instructors/courses/{courseId}/students")
    public List<RosterEntryResponse> roster(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return teacherCourseService.roster(courseId, UUID.fromString(jwt.getSubject()));
    }
}
