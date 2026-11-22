package com.oep.corelms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseModule;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.UploadStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiTutorIndexReconciliationServiceTest {
    private final UUID courseId = UUID.randomUUID();
    private final AiTutorAdminIndexService index = mock(AiTutorAdminIndexService.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final LessonContentRepository contents = mock(LessonContentRepository.class);
    private final AiTutorIndexOutbox outbox = mock(AiTutorIndexOutbox.class);
    private final AiTutorIndexReconciliationService service =
            new AiTutorIndexReconciliationService(index, courses, contents, outbox);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void resendsOnlyMissingOrOlderCurrentVersions() throws Exception {
        Course course = course(CourseLifecycleStatus.PUBLISHED);
        LessonContent absent = source(course, ContentType.TEXT, UploadStatus.AVAILABLE, 1);
        LessonContent older = source(course, ContentType.PDF, UploadStatus.AVAILABLE, 3);
        LessonContent current = source(course, ContentType.TEXT, UploadStatus.AVAILABLE, 2);
        LessonContent pending = source(course, ContentType.FILE, UploadStatus.PENDING, 1);
        when(index.status(courseId)).thenReturn(mapper.readTree("{\"items\":["
                + item(older, 2) + "," + item(current, 2) + "]}"));
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(contents.findByLesson_Module_Course_Id(courseId))
                .thenReturn(List.of(absent, older, current, pending));
        when(outbox.publishRecoveryIfEligible(absent)).thenReturn(true);
        when(outbox.publishRecoveryIfEligible(older)).thenReturn(true);
        when(outbox.publishRecoveryIfEligible(pending)).thenReturn(false);

        assertEquals(2, service.reconcile(courseId));
        verify(outbox).publishRecoveryIfEligible(absent);
        verify(outbox).publishRecoveryIfEligible(older);
        verify(outbox).publishRecoveryIfEligible(pending);
        verify(outbox, never()).publishRecoveryIfEligible(current);
    }

    @Test
    void archivedCourseCannotEnqueueAndInvalidRemoteStateFailsClosed() throws Exception {
        Course course = course(CourseLifecycleStatus.ARCHIVED);
        when(index.status(courseId)).thenReturn(mapper.readTree("{\"items\":[]}"));
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        assertEquals(0, service.reconcile(courseId));
        verifyNoInteractions(contents, outbox);

        when(index.status(courseId)).thenReturn(mapper.readTree("{\"items\":["
                + "{\"content_id\":\"wrong\",\"version_number\":1}]}"));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.reconcile(courseId));
        assertEquals(ErrorCode.AI_TUTOR_UNAVAILABLE, error.getCode());
        verifyNoInteractions(outbox);
    }

    private Course course(CourseLifecycleStatus state) {
        Course course = new Course();
        course.setId(courseId);
        course.setLifecycleStatus(state);
        return course;
    }

    private static LessonContent source(Course course, ContentType type,
                                        UploadStatus status, int version) {
        CourseModule module = new CourseModule();
        module.setCourse(course);
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setModule(module);
        LessonContent content = new LessonContent();
        content.setId(UUID.randomUUID());
        content.setLesson(lesson);
        content.setContentType(type);
        content.setUploadStatus(status);
        content.setVersionNumber(version);
        return content;
    }

    private static String item(LessonContent content, int version) {
        return "{\"content_id\":\"" + content.getId() + "\",\"version_number\":"
                + version + "}";
    }
}
