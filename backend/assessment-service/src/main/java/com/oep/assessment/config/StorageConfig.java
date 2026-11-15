package com.oep.assessment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/** Same S3-compatible bucket (MinIO) as core-lms-service's StorageConfig — see that one for the full note. */
@Configuration
public class StorageConfig {

    @Value("${oep.storage.endpoint}")
    private String endpoint;

    @Value("${oep.storage.access-key}")
    private String accessKey;

    @Value("${oep.storage.secret-key}")
    private String secretKey;

    @Value("${oep.storage.region:us-east-1}")
    private String region;

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
