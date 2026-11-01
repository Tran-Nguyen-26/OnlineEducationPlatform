package com.oep.corelms.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "oep.storage")
public class StorageProperties {
    private String endpoint = "http://localhost:9000";
    private String accessKey = "oep";
    private String secretKey = "oep-minio-secret";
    private String bucket = "oep-course-content";
    private String region = "us-east-1";
    private int uploadUrlTtlSeconds = 900;
    private int accessUrlTtlSeconds = 300;
    private List<String> browserAllowedOrigins = List.of("http://localhost:5173", "http://127.0.0.1:5173");
}
