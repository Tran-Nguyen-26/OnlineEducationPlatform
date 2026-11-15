package com.oep.corelms.service;

import com.oep.corelms.dto.teacher.CourseContentResponse;
import com.oep.corelms.dto.teacher.CourseContentResponse.ContentItem;
import com.oep.corelms.dto.teacher.CourseContentResponse.ContentLesson;
import com.oep.corelms.dto.teacher.CourseContentResponse.ContentModule;
import com.oep.corelms.dto.teacher.InstructorCourseResponse;
import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.CourseModuleRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Teachers can only ever read course content — all authoring stays admin-only (see AdminModuleService etc). */
@Service
@RequiredArgsConstructor
public class TeacherCourseService {

    private final CourseRepository courses;
    private final CourseInstructorRepository courseInstructors;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final LessonContentRepository lessonContents;
    private final TeacherAccessService accessService;

    @Transactional(readOnly = true)
    public List<InstructorCourseResponse> listMyCourses(UUID instructorId) {
        return courseInstructors.findByInstructor_IdOrderByAddedAtDesc(instructorId).stream()
                .map(link -> InstructorCourseResponse.from(link.getCourse()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseContentResponse getContent(UUID courseId, UUID instructorId) {
        accessService.requireAssignedInstructor(courseId, instructorId);
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));

        List<ContentModule> moduleDtos = modules.findByCourse_IdOrderByPositionAsc(courseId).stream()
                .map(module -> new ContentModule(
                        module.getId(), module.getTitle(), module.getDescription(), module.getPosition(),
                        lessons.findByModule_IdOrderByPositionAsc(module.getId()).stream()
                                .map(lesson -> new ContentLesson(
                                        lesson.getId(), lesson.getTitle(), lesson.getDescription(), lesson.getPosition(),
                                        lesson.isRequired(),
                                        lessonContents.findByLesson_IdOrderByPositionAsc(lesson.getId()).stream()
                                                .map(c -> new ContentItem(c.getId(), c.getContentType(), c.getTitle(),
                                                        c.getTextContent(), c.getExternalUrl(), c.getFileName(), c.getUploadStatus()))
                                                .toList()))
                                .toList()))
                .toList();

        return new CourseContentResponse(courseId, course.getTitle(), moduleDtos);
    }
}
