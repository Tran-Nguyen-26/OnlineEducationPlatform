package com.oep.corelms.config;

import com.oep.corelms.service.media.CustomMinioClient;
import io.minio.MinioClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "oep.minio")
@Data
public class MinioConfig {

    private String endpoint  = "http://localhost:9000";
    private String accessKey = "oep";
    private String secretKey = "oep_secret";
    private String bucketRaw          = "oep-raw";
    private String bucketHls          = "oep-hls";
    private String bucketThumbnails   = "oep-thumbnails";
    /** TTL Presigned PUT URL (phút) */
    private int presignedPutTtlMinutes = 15;
    /** TTL Presigned GET URL cho streaming (giây) */
    private int presignedGetTtlSeconds = 3600;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    public CustomMinioClient customMinioClient() {
        io.minio.MinioAsyncClient asyncClient = io.minio.MinioAsyncClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        return new CustomMinioClient(asyncClient);
    }

    @Bean
    public org.springframework.boot.ApplicationRunner minioBucketInitializer(MinioClient minioClient) {
        return args -> {
            java.util.List<String> buckets = java.util.List.of(bucketRaw, bucketHls, bucketThumbnails);
            for (String bucket : buckets) {
                try {
                    boolean exists = minioClient.bucketExists(
                            io.minio.BucketExistsArgs.builder().bucket(bucket).build()
                    );
                    if (!exists) {
                        minioClient.makeBucket(
                                io.minio.MakeBucketArgs.builder().bucket(bucket).build()
                        );
                        log.info("Đã tự động tạo bucket MinIO: {}", bucket);
                    }
                } catch (Exception e) {
                    log.warn("Không thể kiểm tra/tạo MinIO bucket {}: {}", bucket, e.getMessage());
                }
            }
        };
    }
}
