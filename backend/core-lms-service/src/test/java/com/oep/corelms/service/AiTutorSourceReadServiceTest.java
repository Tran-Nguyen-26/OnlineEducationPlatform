package com.oep.corelms.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiTutorSourceReadServiceTest {
    private final LessonContentRepository contents = mock(LessonContentRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private final AiTutorSourceReadService service = new AiTutorSourceReadService(contents, storage);
    private LessonContent content;
    private UUID courseId;
    private UUID lessonId;

    @BeforeEach
    void setUp() {
        Course course = new Course();
        courseId = UUID.randomUUID();
        course.setId(courseId);
        course.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        CourseModule module = new CourseModule();
        module.setCourse(course);
        Lesson lesson = new Lesson();
        lessonId = UUID.randomUUID();
        lesson.setId(lessonId);
        lesson.setModule(module);
        content = new LessonContent();
        content.setId(UUID.randomUUID());
        content.setLesson(lesson);
        content.setContentType(ContentType.TEXT);
        content.setTextContent("Private lesson text");
        content.setUploadStatus(UploadStatus.AVAILABLE);
        content.setVersionNumber(4);
        when(contents.findById(content.getId())).thenReturn(Optional.of(content));
    }

    @Test
    void servesCurrentInlineTextWithoutStorageGrant() {
        var source = read();
        assertEquals("Private lesson text", source.textContent());
        assertEquals(4, source.versionNumber());
        assertEquals("Lesson content", source.sourceTitle());
        assertNull(source.downloadUrl());
        verifyNoInteractions(storage);
    }

    @Test
    void servesFileOnlyWithShortLivedStorageGrant() {
        content.setContentType(ContentType.PDF);
        content.setTitle("Lecture");
        content.setFileName("lecture.pdf");
        content.setMimeType("application/pdf");
        content.setFileSize(100L);
        content.setObjectKey("private/lecture.pdf");
        when(storage.presignView("private/lecture.pdf"))
                .thenReturn("https://storage.test/short-lived-grant");
        var source = read();
        assertEquals("Lecture", source.sourceTitle());
        assertNull(source.textContent());
        assertEquals("lecture.pdf", source.fileName());
        assertEquals("https://storage.test/short-lived-grant", source.downloadUrl());
    }

    @Test
    void rejectsForeignScopeStaleVersionAndUnavailableContent() {
        assertNotFound(() -> service.read(content.getId(), UUID.randomUUID(), lessonId, 4));
        assertNotFound(() -> service.read(content.getId(), courseId, UUID.randomUUID(), 4));
        assertNotFound(() -> service.read(content.getId(), courseId, lessonId, 3));
        content.setUploadStatus(UploadStatus.INVALIDATING);
        assertNotFound(this::read);
        content.setUploadStatus(UploadStatus.AVAILABLE);
        content.getLesson().getModule().getCourse().setLifecycleStatus(CourseLifecycleStatus.ARCHIVED);
        assertNotFound(this::read);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsUnsupportedOrMissingFile() {
        content.setContentType(ContentType.FILE);
        content.setFileName("private.exe");
        content.setMimeType("application/octet-stream");
        assertNotFound(this::read);
        content.setFileName("notes.md");
        content.setMimeType("text/markdown");
        assertNotFound(this::read);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsOversizedLegacyFileAndHandlesStorageFailure() {
        content.setContentType(ContentType.PDF);
        content.setFileName("lecture.pdf");
        content.setMimeType("application/pdf");
        content.setObjectKey("private/lecture.pdf");
        content.setFileSize(AiTutorLearningFilePolicy.PDF_MAX_BYTES + 1);
        assertNotFound(this::read);
        verifyNoInteractions(storage);

        content.setFileSize(100L);
        when(storage.presignView("private/lecture.pdf"))
                .thenThrow(new IllegalStateException("storage down"));
        BusinessException error = assertThrows(BusinessException.class, this::read);
        assertEquals(ErrorCode.AI_TUTOR_UNAVAILABLE, error.getCode());
    }

    private AiTutorSourceReadService.Source read() {
        return service.read(content.getId(), courseId, lessonId, 4);
    }

    private static void assertNotFound(Runnable action) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
    }
}
