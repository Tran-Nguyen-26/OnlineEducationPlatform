package com.oep.worker.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "oep.minio")
@Data
public class MinioConfig {

    private String endpoint = "http://localhost:9000";
    private String accessKey = "oep";
    private String secretKey = "oep_secret";
    private String bucketRaw = "oep-raw";
    private String bucketHls = "oep-hls";
    private String bucketThumbnails = "oep-thumbnails";

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
