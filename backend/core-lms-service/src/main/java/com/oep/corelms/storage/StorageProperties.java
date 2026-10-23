package com.oep.corelms.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "oep.storage")
public class StorageProperties {
    private String endpoint = "http://localhost:9000";
    private String accessKey = "oep";
    private String secretKey = "oep-minio-secret";
    private String bucket = "oep-course-content";
    private String region = "us-east-1";
    private int uploadUrlTtlSeconds = 600;
    private int accessUrlTtlSeconds = 300;
}
