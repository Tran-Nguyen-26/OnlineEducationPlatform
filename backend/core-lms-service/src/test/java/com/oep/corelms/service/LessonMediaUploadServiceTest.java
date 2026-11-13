package com.oep.corelms.service;

import com.oep.coremodel.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LessonMediaUploadServiceTest {
    @Test
    void rejectsUnsupportedFormatsAndOversizedFilesBeforeIssuingUpload() {
        var storage = mock(software.amazon.awssdk.services.s3.S3Client.class);
        var signer = mock(software.amazon.awssdk.services.s3.presigner.S3Presigner.class);
        var service = new LessonMediaUploadService(null, null, null, null, null, storage, signer, null);
        UUID id = UUID.randomUUID();
        for (var request : new LessonMediaUploadService.UploadRequest[] {
                new LessonMediaUploadService.UploadRequest("video.exe", 12, id),
                new LessonMediaUploadService.UploadRequest("video.mp4", 2L * 1024 * 1024 * 1024 + 1, id),
                new LessonMediaUploadService.UploadRequest("lesson.docx", 20L * 1024 * 1024 + 1, id),
                new LessonMediaUploadService.UploadRequest("lesson.srt", 5L * 1024 * 1024 + 1, id)
        }) {
            assertThatThrownBy(() -> service.initiate(id, id, id, request)).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(storage, signer);
    }

    @Test
    void recognizesSupportedExtensionsCaseInsensitively() {
        assertThat(LessonMediaUploadService.Format.of("Week.01.MP4")).isEqualTo(LessonMediaUploadService.Format.MP4);
        assertThat(LessonMediaUploadService.Format.of("captions.VTT")).isEqualTo(LessonMediaUploadService.Format.VTT);
    }
}
