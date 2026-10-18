package com.oep.corelms.scheduler;

import com.oep.corelms.config.MinioConfig;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.service.media.MediaStorageService;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.UploadStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UploadCleanupScheduler {

    private final LessonContentRepository lessonContentRepository;
    private final MediaStorageService mediaStorageService;
    private final MinioConfig minioConfig;

    /**
     * Chạy định kỳ mỗi 15 phút để quét các bản ghi upload ở trạng thái PENDING quá 20 phút.
     * Đánh dấu FAILED và dọn dẹp file rác trên storage nếu có.
     */
    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void cleanExpiredPendingUploads() {
        Instant threshold = Instant.now().minus(20, ChronoUnit.MINUTES);
        List<LessonContent> expiredContents = lessonContentRepository.findByUploadStatusAndCreatedAtBefore(
                UploadStatus.PENDING, threshold
        );

        if (expiredContents.isEmpty()) {
            return;
        }

        log.info("Phát hiện {} bản ghi upload PENDING quá hạn (trước {}), bắt đầu dọn dẹp...",
                expiredContents.size(), threshold);

        for (LessonContent content : expiredContents) {
            try {
                if (content.getObjectKey() != null
                        && mediaStorageService.objectExists(minioConfig.getBucketRaw(), content.getObjectKey())) {
                    mediaStorageService.deleteObject(minioConfig.getBucketRaw(), content.getObjectKey());
                    log.info("Đã xóa file dở dang trên MinIO: {}", content.getObjectKey());
                }

                content.setUploadStatus(UploadStatus.FAILED);
                content.setProcessingError("Upload timeout (quá 20 phút không xác nhận)");
                lessonContentRepository.save(content);
            } catch (Exception e) {
                log.error("Lỗi khi dọn dẹp expired upload contentId={}: {}", content.getId(), e.getMessage());
            }
        }
    }
}
