package com.oep.worker.service;

import com.oep.worker.config.MinioConfig;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioRawStorageService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * Tải file video gốc từ MinIO bucket oep-raw vào đường dẫn cục bộ (hỗ trợ thử lại tối đa 3 lần).
     */
    public void downloadRawVideo(String rawObjectKey, Path targetPath) {
        int maxRetries = 3;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("Bắt đầu tải file raw: bucket={}, key={}, attempt={}",
                        minioConfig.getBucketRaw(), rawObjectKey, attempt);

                if (targetPath.getParent() != null) {
                    Files.createDirectories(targetPath.getParent());
                }

                try (InputStream stream = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(minioConfig.getBucketRaw())
                                .object(rawObjectKey)
                                .build()
                )) {
                    Files.copy(stream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }

                log.info("Tải raw video thành công: {} (kích thước: {} bytes)",
                        targetPath, Files.size(targetPath));
                return;
            } catch (Exception e) {
                lastException = e;
                log.warn("Lỗi tải video từ MinIO (lần {}): {}", attempt, e.getMessage());
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ignored) {}
            }
        }

        throw new RuntimeException("Không thể tải raw video sau 3 lần thử: " + rawObjectKey, lastException);
    }

    /**
     * Xóa video gốc sau khi đã hoàn tất transcode HLS để tiết kiệm storage.
     */
    public void deleteRawVideo(String rawObjectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioConfig.getBucketRaw())
                            .object(rawObjectKey)
                            .build()
            );
            log.info("Đã xóa raw video khỏi {}: {}", minioConfig.getBucketRaw(), rawObjectKey);
        } catch (Exception e) {
            log.warn("Lỗi xóa file raw video {}: {}", rawObjectKey, e.getMessage());
        }
    }
}
