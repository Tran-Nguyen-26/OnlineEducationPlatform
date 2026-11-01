package com.oep.corelms.service;

import com.oep.corelms.dto.course.LessonContentDtos;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.storage.ObjectStorageService;
import com.oep.corelms.storage.StorageProperties;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseModule;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.enums.UploadStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonContentStorageServiceTest {
    @Mock private LessonContentRepository contents;
    @Mock private CourseManagementService courses;
    @Mock private ObjectStorageService storage;

    private final UUID actorId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final LessonContent content = new LessonContent();
    private LessonContentStorageService service;

    @BeforeEach
    void setUp() {
        service = new LessonContentStorageService(contents, courses, storage, new StorageProperties());
        Course course = new Course();
        course.setId(courseId);
        CourseModule module = new CourseModule();
        module.setCourse(course);
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setModule(module);
        content.setId(UUID.randomUUID());
        content.setLesson(lesson);
        content.setContentType(ContentType.PDF);
        content.setFileName("lecture.pdf");
        content.setMimeType("application/pdf");
        content.setFileSize(4096L);
        content.setUploadStatus(UploadStatus.PENDING);
    }

    @Test
    void generatesServerOwnedKeyAndFifteenMinutePutUrl() {
        when(contents.findByIdForUpdate(content.getId())).thenReturn(Optional.of(content));
        when(contents.saveAndFlush(content)).thenReturn(content);
        when(storage.presignPut(any(), any(), any())).thenReturn("http://localhost:9000/signed-upload");

        LessonContentDtos.UploadUrlResponse response = service.issueUploadUrl(actorId, content.getId());

        assertEquals("PUT", response.method());
        assertEquals("application/pdf", response.requiredHeaders().get("Content-Type"));
        assertTrue(response.expiresAt().isAfter(java.time.Instant.now().plusSeconds(890)));
        assertTrue(response.expiresAt().isBefore(java.time.Instant.now().plusSeconds(910)));
        assertEquals(UploadStatus.PENDING, content.getUploadStatus());
        assertNotNull(content.getObjectKey());
        assertFalse(content.getObjectKey().contains(content.getId().toString()));
        assertFalse(response.uploadUrl().contains(content.getId().toString()));
        verify(storage).presignPut(content.getObjectKey(), "application/pdf", java.time.Duration.ofMinutes(15));
    }

    @Test
    void verifiesStoredObjectBeforeMarkingContentAvailable() {
        content.setObjectKey("server-generated/key");
        when(contents.findByIdForUpdate(content.getId())).thenReturn(Optional.of(content));
        when(storage.stat("server-generated/key"))
                .thenReturn(new ObjectStorageService.ObjectMetadata(4096L, "application/pdf"));
        when(contents.saveAndFlush(content)).thenReturn(content);

        LessonContentDtos.Response response = service.completeUpload(actorId, content.getId());

        assertEquals(UploadStatus.AVAILABLE, response.uploadStatus());
        verify(storage).stat("server-generated/key");
    }

    @Test
    void rejectsUploadWhenStoredMetadataDoesNotMatchDeclaration() {
        content.setObjectKey("server-generated/key");
        when(contents.findByIdForUpdate(content.getId())).thenReturn(Optional.of(content));
        when(storage.stat("server-generated/key"))
                .thenReturn(new ObjectStorageService.ObjectMetadata(4095L, "application/pdf"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.completeUpload(actorId, content.getId()));

        assertEquals(ErrorCode.UPLOAD_METADATA_MISMATCH, error.getCode());
        verify(storage).delete("server-generated/key");
        verify(contents, never()).saveAndFlush(any(LessonContent.class));
    }

    @Test
    void rejectsCompletionWhenObjectWasNeverUploaded() {
        content.setObjectKey("server-generated/key");
        when(contents.findByIdForUpdate(content.getId())).thenReturn(Optional.of(content));
        when(storage.stat("server-generated/key"))
                .thenThrow(new BusinessException(ErrorCode.UPLOAD_OBJECT_NOT_FOUND, "missing"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.completeUpload(actorId, content.getId()));

        assertEquals(ErrorCode.UPLOAD_OBJECT_NOT_FOUND, error.getCode());
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, error.getCode().status());
        verify(contents, never()).saveAndFlush(any(LessonContent.class));
    }

    @Test
    void returnsFiveMinuteDownloadUrlOnlyForAvailableContent() {
        content.setObjectKey("server-generated/key");
        content.setUploadStatus(UploadStatus.AVAILABLE);
        when(contents.findById(content.getId())).thenReturn(Optional.of(content));
        when(storage.presignGet("server-generated/key", java.time.Duration.ofMinutes(5)))
                .thenReturn("http://localhost:9000/signed-download");

        LessonContentDtos.AccessUrlResponse response = service.getAccessUrl(actorId, content.getId());

        assertEquals("GET", response.method());
        assertEquals("lecture.pdf", response.fileName());
        verify(courses).assertCourseReadAccess(courseId, actorId);
        verify(storage).presignGet("server-generated/key", java.time.Duration.ofMinutes(5));
    }

    @Test
    void deniesDownloadBeforeUploadCompletes() {
        when(contents.findById(content.getId())).thenReturn(Optional.of(content));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getAccessUrl(actorId, content.getId()));

        assertEquals(ErrorCode.INVALID_UPLOAD_STATE, error.getCode());
        verify(storage, never()).presignGet(any(), any());
    }

    @Test
    void multipartVideoReturnsFiftyTenMiBPartsForFiveHundredMiBFile() {
        content.setContentType(ContentType.VIDEO);
        content.setMimeType("video/mp4");
        content.setFileSize(500L * 1024 * 1024);
        when(contents.findByIdForUpdate(content.getId())).thenReturn(Optional.of(content));
        when(contents.saveAndFlush(content)).thenReturn(content);
        when(storage.createMultipartUpload(any(), any())).thenReturn("multipart-id");
        when(storage.presignUploadPart(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenAnswer(invocation -> "https://storage/part/" + invocation.getArgument(2));

        var response = service.issueMultipartUploadUrls(actorId, content.getId());

        assertEquals(50, response.parts().size());
        assertEquals(10 * 1024 * 1024, response.partSizeBytes());
        assertEquals("multipart-id", response.uploadId());
        assertEquals("multipart-id", content.getMultipartUploadId());
        assertTrue(response.expiresAt().isAfter(java.time.Instant.now().plusSeconds(890)));
        assertTrue(response.expiresAt().isBefore(java.time.Instant.now().plusSeconds(910)));
        verify(storage).createMultipartUpload(content.getObjectKey(), "video/mp4");
    }

    @Test
    void rejectsSinglePutForLargeVideo() {
        content.setContentType(ContentType.VIDEO);
        content.setMimeType("video/mp4");
        content.setFileSize(500L * 1024 * 1024);
        when(contents.findByIdForUpdate(content.getId())).thenReturn(Optional.of(content));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.issueUploadUrl(actorId, content.getId()));

        assertEquals(ErrorCode.INVALID_UPLOAD_STATE, error.getCode());
        verify(storage, never()).presignPut(any(), any(), any());
    }

    @Test
    void rejectsMultipartCompletionWhenPartIsMissing() {
        content.setContentType(ContentType.VIDEO);
        content.setFileSize(500L * 1024 * 1024);
        content.setObjectKey("content/server-key");
        content.setMultipartUploadId("multipart-id");
        when(contents.findByIdForUpdate(content.getId())).thenReturn(Optional.of(content));
        var request = new LessonContentDtos.CompleteMultipartRequest(java.util.List.of(
                new LessonContentDtos.MultipartPart(1, "etag-1")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.completeMultipartUpload(actorId, content.getId(), request));

        assertEquals(ErrorCode.INVALID_CONTENT_METADATA, error.getCode());
        verify(storage, never()).completeMultipartUpload(any(), any(), any());
    }

    @Test
    void completesMultipartPartsInAscendingOrderAndVerifiesObjectMetadata() {
        content.setContentType(ContentType.VIDEO);
        content.setMimeType("video/mp4");
        content.setFileSize(2L * LessonContentStorageService.MULTIPART_PART_SIZE_BYTES);
        content.setObjectKey("content/server-key");
        content.setMultipartUploadId("multipart-id");
        when(contents.findByIdForUpdate(content.getId())).thenReturn(Optional.of(content));
        when(storage.stat("content/server-key")).thenReturn(new ObjectStorageService.ObjectMetadata(
                content.getFileSize(), "video/mp4"));
        when(contents.saveAndFlush(content)).thenReturn(content);
        var request = new LessonContentDtos.CompleteMultipartRequest(java.util.List.of(
                new LessonContentDtos.MultipartPart(2, "etag-2"),
                new LessonContentDtos.MultipartPart(1, "etag-1")));

        var response = service.completeMultipartUpload(actorId, content.getId(), request);

        assertEquals(UploadStatus.AVAILABLE, response.uploadStatus());
        assertNull(content.getMultipartUploadId());
        org.mockito.ArgumentCaptor<java.util.List<ObjectStorageService.CompletedPartData>> partsCaptor =
                org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(storage).completeMultipartUpload(org.mockito.ArgumentMatchers.eq("content/server-key"),
                org.mockito.ArgumentMatchers.eq("multipart-id"), partsCaptor.capture());
        assertEquals(java.util.List.of(1, 2), partsCaptor.getValue().stream()
                .map(ObjectStorageService.CompletedPartData::partNumber).toList());
    }

    @Test
    void cancelMultipartUploadAbortsStorageAndMarksContentFailed() {
        content.setObjectKey("content/server-key");
        content.setMultipartUploadId("multipart-id");
        when(contents.findByIdForUpdate(content.getId())).thenReturn(Optional.of(content));
        when(contents.saveAndFlush(content)).thenReturn(content);

        service.cancelMultipartUpload(actorId, content.getId());

        verify(storage).abortMultipartUpload("content/server-key", "multipart-id");
        assertEquals(UploadStatus.FAILED, content.getUploadStatus());
        assertNull(content.getMultipartUploadId());
    }

    @Test
    void failedSinglePutClearsItsObjectAndCanBeRetried() {
        content.setObjectKey("content/partial-key");
        when(contents.findByIdForUpdate(content.getId())).thenReturn(Optional.of(content));
        when(contents.saveAndFlush(content)).thenReturn(content);

        service.failUpload(actorId, content.getId());

        verify(storage).delete("content/partial-key");
        assertEquals(UploadStatus.FAILED, content.getUploadStatus());
        assertNull(content.getObjectKey());
        assertNull(content.getMultipartUploadId());
    }

    @Test
    void failedMultipartUploadAbortsAndDeletesPartialParts() {
        content.setContentType(ContentType.VIDEO);
        content.setMimeType("video/mp4");
        content.setObjectKey("content/partial-video");
        content.setMultipartUploadId("multipart-id");
        when(contents.findByIdForUpdate(content.getId())).thenReturn(Optional.of(content));
        when(contents.saveAndFlush(content)).thenReturn(content);

        service.failUpload(actorId, content.getId());

        verify(storage).abortMultipartUpload("content/partial-video", "multipart-id");
        verify(storage).delete("content/partial-video");
        assertEquals(UploadStatus.FAILED, content.getUploadStatus());
    }
}
