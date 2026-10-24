package com.oep.corelms.service;

import com.oep.corelms.dto.course.LessonContentDtos;
import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.AuditLogRepository;
import com.oep.corelms.repository.CourseModuleRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.LessonContentReferenceRepository;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.corelms.storage.ObjectStorageService;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseInstructor;
import com.oep.coremodel.entity.course.CourseModule;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.enums.CourseInstructorRole;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.UploadStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonContentManagementTest {
    @Mock private CourseRepository courses;
    @Mock private CourseModuleRepository modules;
    @Mock private LessonRepository lessons;
    @Mock private LessonContentRepository contents;
    @Mock private LessonContentReferenceRepository contentReferences;
    @Mock private CourseInstructorRepository courseInstructors;
    @Mock private AuditLogRepository auditLogs;
    @Mock private UserRepository users;
    @Mock private EnrollmentRepository enrollments;
    @Mock private ObjectStorageService objectStorage;

    private final UUID actorId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID moduleId = UUID.randomUUID();
    private final UUID lessonId = UUID.randomUUID();
    private final Course course = new Course();
    private final CourseModule module = new CourseModule();
    private final Lesson lesson = new Lesson();
    private final User actor = new User();
    private CourseManagementService service;

    @BeforeEach
    void setUp() {
        service = new CourseManagementService(courses, modules, lessons, contents, contentReferences, courseInstructors, auditLogs, users, enrollments, objectStorage);
        course.setId(courseId);
        course.setLifecycleStatus(CourseLifecycleStatus.DRAFT);
        module.setId(moduleId);
        module.setCourse(course);
        lesson.setId(lessonId);
        lesson.setModule(module);
        actor.setId(actorId);

        CourseInstructor owner = new CourseInstructor();
        owner.setCourseRole(CourseInstructorRole.OWNER);
        lenient().when(courses.findById(courseId)).thenReturn(Optional.of(course));
        lenient().when(courseInstructors.findByIdCourseIdAndIdInstructorId(courseId, actorId)).thenReturn(Optional.of(owner));
        lenient().when(modules.findByIdAndCourseId(moduleId, courseId)).thenReturn(Optional.of(module));
        lenient().when(lessons.findByIdAndModuleIdForUpdate(lessonId, moduleId)).thenReturn(Optional.of(lesson));
        lenient().when(lessons.findByIdAndModuleId(lessonId, moduleId)).thenReturn(Optional.of(lesson));
        lenient().when(users.findById(actorId)).thenReturn(Optional.of(actor));
    }

    @Test
    void createsTextAsAvailableAndAppendsAfterLastPosition() {
        LessonContent existing = content(ContentType.TEXT, 4);
        when(contents.findByLessonIdOrderByPositionAsc(lessonId)).thenReturn(List.of(existing));
        when(contents.saveAndFlush(any(LessonContent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createLessonContent(actorId, courseId, moduleId, lessonId,
                new LessonContentDtos.CreateRequest(ContentType.TEXT, "Intro", "Hello", null, null, null, null, null));

        assertEquals(ContentType.TEXT, response.contentType());
        assertEquals(UploadStatus.AVAILABLE, response.uploadStatus());
        assertEquals(5, response.position());
        assertEquals("Hello", response.textContent());
        assertNull(response.externalUrl());
    }

    @Test
    void sanitizesTextBeforePersistingAndReturningIt() {
        when(contents.findByLessonIdOrderByPositionAsc(lessonId)).thenReturn(List.of());
        when(contents.saveAndFlush(any(LessonContent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createLessonContent(actorId, courseId, moduleId, lessonId,
                new LessonContentDtos.CreateRequest(ContentType.TEXT, "Intro",
                        "<p>Safe</p><script>alert(1)</script>", null, null, null, null, null));

        assertTrue(response.textContent().contains("<p>Safe</p>"));
        assertFalse(response.textContent().contains("<script"));
        assertFalse(response.textContent().contains("alert(1)"));
        verify(contents).saveAndFlush(argThat(saved -> saved.getTextContent().contains("<p>Safe</p>")
                && !saved.getTextContent().contains("<script") && !saved.getTextContent().contains("alert(1)")));
    }

    @Test
    void sanitizesLegacyTextWhenReturningIt() {
        LessonContent legacy = content(ContentType.TEXT, 0);
        legacy.setTextContent("<p>Safe</p><img src=x onerror=alert(1)>");
        when(contents.findByIdAndLessonId(legacy.getId(), lessonId)).thenReturn(Optional.of(legacy));

        var response = service.getLessonContent(actorId, courseId, moduleId, lessonId, legacy.getId());

        assertTrue(response.textContent().contains("<p>Safe</p>"));
        assertFalse(response.textContent().contains("<img"));
        assertFalse(response.textContent().contains("onerror"));
        assertFalse(response.textContent().contains("alert(1)"));
    }

    @Test
    void insertingContentAtUsedPositionShiftsExistingContentWithoutChangingTheirOrder() {
        LessonContent first = content(ContentType.TEXT, 0);
        LessonContent second = content(ContentType.TEXT, 1);
        when(contents.findByLessonIdOrderByPositionAsc(lessonId)).thenReturn(List.of(first, second));
        when(contents.saveAndFlush(any(LessonContent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var inserted = service.createLessonContent(actorId, courseId, moduleId, lessonId,
                new LessonContentDtos.CreateRequest(ContentType.TEXT, "Inserted", "Body", null, null, null, null, 0));

        assertEquals(0, inserted.position());
        assertEquals(1, first.getPosition());
        assertEquals(2, second.getPosition());
        verify(contents, times(2)).saveAllAndFlush(anyIterable());
    }

    @Test
    void createsHttpLinkAndRejectsNonHttpSchemes() {
        when(contents.findByLessonIdOrderByPositionAsc(lessonId)).thenReturn(List.of());
        when(contents.saveAndFlush(any(LessonContent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createLessonContent(actorId, courseId, moduleId, lessonId,
                new LessonContentDtos.CreateRequest(ContentType.LINK, "Docs", null, "https://example.org/guide", null, null, null, null));

        assertEquals(ContentType.LINK, response.contentType());
        assertEquals("https://example.org/guide", response.externalUrl());

        BusinessException error = assertThrows(BusinessException.class, () -> service.createLessonContent(actorId, courseId, moduleId,
                lessonId, new LessonContentDtos.CreateRequest(ContentType.LINK, "Bad", null, "javascript:alert(1)", null, null, null, null)));
        assertEquals(ErrorCode.INVALID_CONTENT_METADATA, error.getCode());
    }

    @Test
    void createsFileContentAsPendingAndRejectsUnsupportedMetadata() {
        when(contents.findByLessonIdOrderByPositionAsc(lessonId)).thenReturn(List.of());
        when(contents.saveAndFlush(any(LessonContent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createLessonContent(actorId, courseId, moduleId, lessonId,
                new LessonContentDtos.CreateRequest(ContentType.PDF, "PDF", null, null, "a.pdf", "application/pdf", 50L, null));

        assertEquals(ContentType.PDF, response.contentType());
        assertEquals(UploadStatus.PENDING, response.uploadStatus());
        assertEquals("a.pdf", response.fileName());
        verify(contents).saveAndFlush(argThat(content -> content.getObjectKey() == null));

        BusinessException error = assertThrows(BusinessException.class, () -> service.createLessonContent(actorId, courseId, moduleId,
                lessonId, new LessonContentDtos.CreateRequest(ContentType.PDF, "Bad", null, null, "a.pdf", "text/plain", 50L, 1)));
        assertEquals(ErrorCode.UNSUPPORTED_MEDIA_TYPE, error.getCode());
    }

    @Test
    void rejectsFileMetadataAboveTheLimit() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.createLessonContent(actorId, courseId, moduleId,
                lessonId, new LessonContentDtos.CreateRequest(ContentType.PDF, "Too large", null, null,
                        "large.pdf", "application/pdf", FileUploadPolicy.MAX_FILE_SIZE_BYTES + 1, null)));

        assertEquals(ErrorCode.UPLOAD_TOO_LARGE, error.getCode());
        verify(contents, never()).saveAndFlush(any(LessonContent.class));
    }

    @Test
    void updatesTextBodyAndRejectsFieldsForAnotherType() {
        LessonContent text = content(ContentType.TEXT, 0);
        when(contents.findByIdAndLessonId(text.getId(), lessonId)).thenReturn(Optional.of(text));
        when(contents.saveAndFlush(text)).thenReturn(text);

        var response = service.updateLessonContent(actorId, courseId, moduleId, lessonId, text.getId(),
                new LessonContentDtos.UpdateRequest("Updated", "New body", null));
        assertEquals("Updated", response.title());
        assertEquals("New body", response.textContent());

        BusinessException error = assertThrows(BusinessException.class, () -> service.updateLessonContent(actorId, courseId, moduleId,
                lessonId, text.getId(), new LessonContentDtos.UpdateRequest(null, null, "https://example.org")));
        assertEquals(ErrorCode.INVALID_CONTENT_METADATA, error.getCode());
    }

    @Test
    void rejectsDeletionWhenContentHasReferences() {
        LessonContent text = content(ContentType.TEXT, 0);
        when(contents.findByIdAndLessonIdForUpdate(text.getId(), lessonId)).thenReturn(Optional.of(text));
        when(contentReferences.isReferenced(text.getId())).thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.deleteLessonContent(actorId, courseId, moduleId, lessonId, text.getId()));

        assertEquals(ErrorCode.CONTENT_IN_USE, error.getCode());
        verify(contents, never()).delete(text);
    }

    @Test
    void deletesUnreferencedText() {
        LessonContent text = content(ContentType.TEXT, 0);
        when(contents.findByIdAndLessonIdForUpdate(text.getId(), lessonId)).thenReturn(Optional.of(text));
        when(contentReferences.isReferenced(text.getId())).thenReturn(false);

        var response = service.deleteLessonContent(actorId, courseId, moduleId, lessonId, text.getId());

        assertEquals(true, response.success());
        assertEquals(text.getId(), response.deletedId());
        verify(contents).delete(text);
    }

    @Test
    void deletesUnreferencedFileAndItsStoredObject() {
        LessonContent file = content(ContentType.PDF, 0);
        file.setObjectKey("opaque/object-key");
        when(contents.findByIdAndLessonIdForUpdate(file.getId(), lessonId)).thenReturn(Optional.of(file));
        when(contentReferences.isReferenced(file.getId())).thenReturn(false);

        var response = service.deleteLessonContent(actorId, courseId, moduleId, lessonId, file.getId());

        assertEquals(true, response.success());
        verify(objectStorage).delete("opaque/object-key");
        verify(contents).delete(file);
    }

    private LessonContent content(ContentType type, int position) {
        LessonContent content = new LessonContent();
        content.setId(UUID.randomUUID());
        content.setLesson(lesson);
        content.setContentType(type);
        content.setPosition(position);
        content.setUploadStatus(UploadStatus.AVAILABLE);
        return content;
    }
}
