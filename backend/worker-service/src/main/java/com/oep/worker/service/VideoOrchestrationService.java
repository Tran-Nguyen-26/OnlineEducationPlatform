package com.oep.worker.service;

import com.oep.coremodel.enums.VideoJobStatus;
import com.oep.coremodel.event.VideoUploadedEvent;
import com.oep.worker.config.FfmpegConfig;
import com.oep.worker.model.VideoMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoOrchestrationService {

    private final FfmpegConfig ffmpegConfig;
    private final MinioRawStorageService minioRawStorageService;
    private final VideoProbeService videoProbeService;
    private final VideoTranscodingService videoTranscodingService;
    private final ThumbnailService thumbnailService;
    private final HlsUploadService hlsUploadService;
    private final JobStatusService jobStatusService;

    /**
     * Điều phối toàn bộ pipeline xử lý video qua 5 phase độc lập.
     */
    public void process(VideoUploadedEvent event) {
        UUID jobId = event.jobId();
        UUID contentId = event.contentId();
        String rawKey = event.rawObjectKey();

        log.info("Bắt đầu xử lý video pipeline: jobId={}, contentId={}, rawKey={}", jobId, contentId, rawKey);

        Path jobTempDir = Path.of(ffmpegConfig.getTempDir(), jobId.toString());
        Path inputVideoPath = jobTempDir.resolve("input.mp4");
        Path hlsOutputDir = jobTempDir.resolve("hls");
        Path thumbPath = jobTempDir.resolve("thumb.jpg");
        Path spritePath = jobTempDir.resolve("sprite.jpg");

        try {
            Files.createDirectories(jobTempDir);

            // Phase A: DOWNLOADING
            jobStatusService.startJob(jobId);
            minioRawStorageService.downloadRawVideo(rawKey, inputVideoPath);

            // Phase B: PROBING
            jobStatusService.updateProgress(jobId, VideoJobStatus.PROBING, 10, null);
            VideoMetadata metadata = videoProbeService.probe(inputVideoPath);

            // Phase C: TRANSCODING & THUMBNAILS
            List<String> renditionsDone = videoTranscodingService.transcodeHls(
                    inputVideoPath,
                    hlsOutputDir,
                    metadata,
                    (pct, rendition) -> jobStatusService.updateProgress(jobId, VideoJobStatus.TRANSCODING, pct, null)
            );

            try {
                thumbnailService.generateThumbnail(inputVideoPath, thumbPath, metadata.durationSeconds());
            } catch (Exception e) {
                log.warn("Lỗi tạo thumbnail (tiếp tục luồng): {}", e.getMessage());
            }

            try {
                thumbnailService.generateSpriteSheet(inputVideoPath, spritePath, metadata.durationSeconds());
            } catch (Exception e) {
                log.warn("Lỗi tạo sprite sheet (tiếp tục luồng): {}", e.getMessage());
            }

            // Phase D: UPLOADING
            jobStatusService.updateProgress(jobId, VideoJobStatus.UPLOADING, 88, renditionsDone);
            HlsUploadService.UploadResult uploadResult = hlsUploadService.uploadHlsPackage(
                    contentId,
                    hlsOutputDir,
                    thumbPath,
                    spritePath
            );

            // Phase E: FINALIZE
            jobStatusService.markDone(
                    jobId,
                    contentId,
                    uploadResult.hlsMasterKey(),
                    uploadResult.thumbnailKey(),
                    uploadResult.spriteSheetKey(),
                    (int) Math.round(metadata.durationSeconds())
            );

            // Xóa file gốc khỏi bucket oep-raw để tiết kiệm dung lượng
            minioRawStorageService.deleteRawVideo(rawKey);

            log.info("Pipeline xử lý video hoàn tất thành công cho jobId={}", jobId);

        } catch (Exception e) {
            log.error("Pipeline xử lý video thất bại cho jobId={}: {}", jobId, e.getMessage(), e);
            jobStatusService.markFailed(jobId, contentId, e.getMessage());
            throw new RuntimeException("Xử lý video thất bại: " + e.getMessage(), e);
        } finally {
            // Dọn dẹp toàn bộ file tạm trong /tmp/oep/{jobId}
            cleanupDirectory(jobTempDir);
        }
    }

    private void cleanupDirectory(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {}
                    });
            log.info("Đã dọn dẹp thư mục tạm: {}", dir);
        } catch (Exception e) {
            log.warn("Lỗi khi dọn dẹp thư mục tạm {}: {}", dir, e.getMessage());
        }
    }
}
