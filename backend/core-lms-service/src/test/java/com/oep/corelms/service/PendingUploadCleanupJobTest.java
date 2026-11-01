package com.oep.corelms.service;

import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.storage.ObjectStorageService;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.UploadStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingUploadCleanupJobTest {
    @Mock private LessonContentRepository contents;
    @Mock private ObjectStorageService storage;

    @Test
    void removesExpiredSinglePutObjectsAndMarksRowsFailed() {
        LessonContent content = pending("content/object-key", null);
        when(contents.findExpiredUploadsForUpdate(eq(UploadStatus.PENDING), any(), any())).thenReturn(List.of(content));
        PendingUploadCleanupJob job = new PendingUploadCleanupJob(contents, storage);

        job.cleanupExpiredUploads();

        verify(storage).delete("content/object-key");
        verify(storage, never()).abortMultipartUpload(any(), any());
        verify(contents).saveAll(List.of(content));
        assertEquals(UploadStatus.FAILED, content.getUploadStatus());
        assertNull(content.getObjectKey());
    }

    @Test
    void abortsExpiredMultipartAndRemovesItsObject() {
        LessonContent content = pending("content/multipart-key", "multipart-id");
        when(contents.findExpiredUploadsForUpdate(eq(UploadStatus.PENDING), any(), any())).thenReturn(List.of(content));
        PendingUploadCleanupJob job = new PendingUploadCleanupJob(contents, storage);

        job.cleanupExpiredUploads();

        verify(storage).abortMultipartUpload("content/multipart-key", "multipart-id");
        verify(storage).delete("content/multipart-key");
        verify(contents).saveAll(List.of(content));
        assertEquals(UploadStatus.FAILED, content.getUploadStatus());
        assertNull(content.getMultipartUploadId());
    }

    @Test
    void leavesRowPendingWhenStorageCleanupFailsForRetry() {
        LessonContent content = pending("content/object-key", null);
        when(contents.findExpiredUploadsForUpdate(eq(UploadStatus.PENDING), any(), any())).thenReturn(List.of(content));
        org.mockito.Mockito.doThrow(new IllegalStateException("storage down")).when(storage).delete("content/object-key");
        PendingUploadCleanupJob job = new PendingUploadCleanupJob(contents, storage);

        job.cleanupExpiredUploads();

        verify(contents).saveAll(List.of());
        assertEquals(UploadStatus.PENDING, content.getUploadStatus());
    }

    private LessonContent pending(String key, String uploadId) {
        LessonContent content = new LessonContent();
        content.setId(UUID.randomUUID());
        content.setObjectKey(key);
        content.setMultipartUploadId(uploadId);
        content.setUploadStatus(UploadStatus.PENDING);
        return content;
    }
}
