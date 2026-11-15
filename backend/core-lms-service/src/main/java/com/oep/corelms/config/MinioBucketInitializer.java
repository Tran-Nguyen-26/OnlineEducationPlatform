package com.oep.corelms.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/**
 * Creates the content bucket on startup. CORS does not need configuring here: this MinIO build
 * answers every preflight with a permissive, origin-reflecting Access-Control-Allow-Origin by
 * default and rejects the PutBucketCors API call entirely ("A header you provided implies
 * functionality that is not implemented") — confirmed both via the AWS SDK and `mc cors set`.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class MinioBucketInitializer implements ApplicationRunner {

    private final S3Client s3Client;

    @Value("${oep.storage.bucket}")
    private String bucket;

    @Override
    public void run(ApplicationArguments args) {
        boolean exists = s3Client.listBuckets().buckets().stream().anyMatch(b -> b.name().equals(bucket));
        if (!exists) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created storage bucket '{}'", bucket);
        }
    }
}
