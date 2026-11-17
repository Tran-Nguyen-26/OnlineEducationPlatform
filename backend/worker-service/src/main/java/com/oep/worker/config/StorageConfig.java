package com.oep.worker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

/** Same two modes as the other services: MinIO (endpoint + keys) or AWS S3 (default credential chain / IAM role). */
@Configuration
public class StorageConfig {

    @Value("${oep.storage.endpoint:}") private String endpoint;
    @Value("${oep.storage.access-key:}") private String accessKey;
    @Value("${oep.storage.secret-key:}") private String secretKey;
    @Value("${oep.storage.region:us-east-1}") private String region;

    @Bean
    S3Client s3Client() {
        S3ClientBuilder b = S3Client.builder().region(Region.of(region)).credentialsProvider(credentials());
        if (!endpoint.isBlank()) b.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        return b.build();
    }

    private AwsCredentialsProvider credentials() {
        if (!accessKey.isBlank() && !secretKey.isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        return DefaultCredentialsProvider.create();
    }
}
