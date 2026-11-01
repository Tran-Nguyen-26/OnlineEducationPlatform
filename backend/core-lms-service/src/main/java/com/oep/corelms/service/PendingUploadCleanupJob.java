package com.oep.corelms.service;

import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.storage.ObjectStorageService;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.UploadStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingUploadCleanupJob {
    private final LessonContentRepository contents;
    private final ObjectStorageService storage;

    @Scheduled(fixedDelayString = "${oep.storage.cleanup-interval-ms:3600000}")
    @Transactional
    public void cleanupExpiredUploads() {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        List<LessonContent> expired = contents.findExpiredUploadsForUpdate(
                UploadStatus.PENDING, cutoff, PageRequest.of(0, 100));
        for (LessonContent content : expired) {
            try {
                if (content.getMultipartUploadId() != null && content.getObjectKey() != null) {
                    storage.abortMultipartUpload(content.getObjectKey(), content.getMultipartUploadId());
                }
                if (content.getObjectKey() != null) {
                    storage.delete(content.getObjectKey());
                }
                content.setMultipartUploadId(null);
                content.setObjectKey(null);
                content.setUploadStatus(UploadStatus.FAILED);
            } catch (RuntimeException ex) {
                log.warn("Could not clean expired content upload {}", content.getId(), ex);
            }
        }
        contents.saveAll(expired.stream()
                .filter(content -> content.getUploadStatus() == UploadStatus.FAILED)
                .toList());
    }
}
