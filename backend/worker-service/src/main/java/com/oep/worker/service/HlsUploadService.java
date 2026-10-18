package com.oep.worker.service;

import com.oep.worker.config.MinioConfig;
import io.minio.MinioClient;
import io.minio.UploadObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class HlsUploadService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final ExecutorService uploadExecutor = Executors.newFixedThreadPool(8);

    public record UploadResult(
            String hlsMasterKey,
            String thumbnailKey,
            String spriteSheetKey
    ) {}

    /**
     * Upload song song các rendition HLS, master playlist, thumbnail và sprite sheet lên MinIO.
     *
     * @param contentId ID của LessonContent
     * @param hlsDir    Thư mục chứa các folder rendition và master.m3u8
     * @param thumbPath Đường dẫn file thumb.jpg
     * @param spritePath Đường dẫn file sprite.jpg
     */
    public UploadResult uploadHlsPackage(
            UUID contentId,
            Path hlsDir,
            Path thumbPath,
            Path spritePath
    ) {
        log.info("Bắt đầu upload gói HLS cho contentId: {}", contentId);
        String basePrefix = "videos/" + contentId + "/";

        List<CompletableFuture<Void>> uploadTasks = new ArrayList<>();

        // 1. Quét tất cả file trong thư mục HLS (m3u8 và ts)
        try (Stream<Path> stream = Files.walk(hlsDir)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();

            for (Path file : files) {
                String relative = hlsDir.relativize(file).toString();
                String objectKey = basePrefix + relative;
                String contentType = determineContentType(file.getFileName().toString());

                CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                    uploadSingleFile(minioConfig.getBucketHls(), objectKey, file, contentType);
                }, uploadExecutor);

                uploadTasks.add(task);
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi duyệt thư mục HLS: " + e.getMessage(), e);
        }

        // 2. Upload thumbnail nếu có
        String thumbKey = null;
        if (thumbPath != null && Files.exists(thumbPath)) {
            thumbKey = basePrefix + "thumb.jpg";
            String finalThumbKey = thumbKey;
            uploadTasks.add(CompletableFuture.runAsync(() -> {
                uploadSingleFile(minioConfig.getBucketThumbnails(), finalThumbKey, thumbPath, "image/jpeg");
            }, uploadExecutor));
        }

        // 3. Upload sprite sheet nếu có
        String spriteKey = null;
        if (spritePath != null && Files.exists(spritePath)) {
            spriteKey = basePrefix + "sprite.jpg";
            String finalSpriteKey = spriteKey;
            uploadTasks.add(CompletableFuture.runAsync(() -> {
                uploadSingleFile(minioConfig.getBucketThumbnails(), finalSpriteKey, spritePath, "image/jpeg");
            }, uploadExecutor));
        }

        // Đợi tất cả task upload hoàn thành
        CompletableFuture.allOf(uploadTasks.toArray(new CompletableFuture[0])).join();

        log.info("Đã upload xong toàn bộ gói HLS cho contentId: {}", contentId);

        String masterKey = basePrefix + "master.m3u8";
        return new UploadResult(masterKey, thumbKey, spriteKey);
    }

    private void uploadSingleFile(String bucket, String objectKey, Path filePath, String contentType) {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                minioClient.uploadObject(
                        UploadObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectKey)
                                .filename(filePath.toAbsolutePath().toString())
                                .contentType(contentType)
                                .build()
                );
                return;
            } catch (Exception e) {
                log.warn("Lỗi upload file {} lên {}/{} (lần {}): {}",
                        filePath, bucket, objectKey, attempt, e.getMessage());
                if (attempt == maxRetries) {
                    throw new RuntimeException("Upload thất bại sau 3 lần: " + objectKey, e);
                }
                try {
                    Thread.sleep(500L * attempt);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    private String determineContentType(String fileName) {
        if (fileName.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        } else if (fileName.endsWith(".ts")) {
            return "video/mp2t";
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }
}
