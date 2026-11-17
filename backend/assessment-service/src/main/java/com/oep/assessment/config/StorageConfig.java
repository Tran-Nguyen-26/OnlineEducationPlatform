package com.oep.assessment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/** Same storage modes as core-lms-service's StorageConfig (MinIO via endpoint, or AWS S3 via the default credential chain). */
@Configuration
public class StorageConfig {

    @Value("${oep.storage.endpoint:}") private String endpoint;
    @Value("${oep.storage.public-endpoint:}") private String publicEndpoint;
    @Value("${oep.storage.access-key:}") private String accessKey;
    @Value("${oep.storage.secret-key:}") private String secretKey;
    @Value("${oep.storage.region:us-east-1}") private String region;

    @Bean
    public S3Presigner s3Presigner() {
        S3Presigner.Builder b = S3Presigner.builder().region(Region.of(region)).credentialsProvider(credentials());
        String signingEndpoint = !publicEndpoint.isBlank() ? publicEndpoint : endpoint;
        if (!signingEndpoint.isBlank()) {
            b.endpointOverride(URI.create(signingEndpoint))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return b.build();
    }

    private AwsCredentialsProvider credentials() {
        if (!accessKey.isBlank() && !secretKey.isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        return DefaultCredentialsProvider.create();
    }
}
