package com.oep.corelms.service;

import com.oep.corelms.dto.admin.CompleteUploadRequest;
import com.oep.corelms.dto.admin.RequestUploadUrlRequest;
import com.oep.corelms.repository.ContentChangeLogRepository;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.enums.UploadStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiTutorLearningUploadTest {

    private final TranscodeRequester transcode = mock(TranscodeRequester.class);
    private final LessonRepository lessons = mock(LessonRepository.class);
    private final LessonContentRepository contents = mock(LessonContentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private final ContentChangeLogRepository changes = mock(ContentChangeLogRepository.class);
    private final AdminLessonContentService service = new AdminLessonContentService(
            transcode, lessons, contents, users, storage, changes);
    private LessonContent content;

    @BeforeEach
    void setUp() {
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        content = new LessonContent();
        content.setId(UUID.randomUUID());
        content.setLesson(lesson);
        content.setContentType(ContentType.FILE);
        content.setFileName("notes.md");
        content.setMimeType("text/markdown");
        content.setObjectKey("lesson-contents/test/notes.md");
        content.setUploadStatus(UploadStatus.PENDING);
        when(contents.findByIdForUpdate(content.getId())).thenReturn(Optional.of(content));
    }

    @Test
    void rejectsMismatchedMetadataBeforeSigningAnUploadUrl() {
        BusinessException error = assertThrows(BusinessException.class, () ->
                service.requestUploadUrl(content.getId(),
                        new RequestUploadUrlRequest("notes.md", "application/json")));
        assertEquals(ErrorCode.VALIDATION_FAILED, error.getCode());
        verify(storage, never()).newObjectKey("notes.md");
    }

    @Test
    void checksStoredObjectSizeInsteadOfTrustingBrowserConfirmation() {
        long limit = AiTutorLearningFilePolicy.MARKDOWN_JSON_MAX_BYTES;
        when(storage.inspectObject(content.getObjectKey()))
                .thenReturn(new StorageService.StoredObjectInfo(limit + 1, "text/markdown"));

        BusinessException oversized = assertThrows(BusinessException.class, () ->
                service.completeUpload(content.getId(), new CompleteUploadRequest(limit + 1, null)));
        assertEquals(ErrorCode.VALIDATION_FAILED, oversized.getCode());
        assertEquals(UploadStatus.PENDING, content.getUploadStatus());
        verify(contents, never()).saveAndFlush(content);

        BusinessException spoofed = assertThrows(BusinessException.class, () ->
                service.completeUpload(content.getId(), new CompleteUploadRequest(1L, null)));
        assertEquals(ErrorCode.VALIDATION_FAILED, spoofed.getCode());
    }

    @Test
    void rejectsStoredObjectWithDifferentMimeType() {
        when(storage.inspectObject(content.getObjectKey()))
                .thenReturn(new StorageService.StoredObjectInfo(100, "application/octet-stream"));

        BusinessException error = assertThrows(BusinessException.class, () ->
                service.completeUpload(content.getId(), new CompleteUploadRequest(100L, null)));
        assertEquals(ErrorCode.VALIDATION_FAILED, error.getCode());
        assertEquals(UploadStatus.PENDING, content.getUploadStatus());
        verify(contents, never()).saveAndFlush(content);
    }

    @Test
    void acceptsAnEligibleFileAtTheLimitAndPreservesOtherFileBehavior() {
        long limit = AiTutorLearningFilePolicy.MARKDOWN_JSON_MAX_BYTES;
        when(storage.inspectObject(content.getObjectKey()))
                .thenReturn(new StorageService.StoredObjectInfo(limit, "text/markdown"));
        when(contents.saveAndFlush(content)).thenReturn(content);

        var accepted = service.completeUpload(content.getId(), new CompleteUploadRequest(limit, null));
        assertEquals(UploadStatus.AVAILABLE, accepted.uploadStatus());
        assertEquals(limit, accepted.fileSize());

        content.setFileName("worksheet.txt");
        content.setMimeType("text/plain");
        content.setUploadStatus(UploadStatus.PENDING);
        service.completeUpload(content.getId(), new CompleteUploadRequest(50L * 1024 * 1024, null));
        verify(storage).inspectObject(content.getObjectKey());
    }
}
