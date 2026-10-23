package com.oep.corelms.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@RequiredArgsConstructor
public class S3BucketInitializer implements ApplicationRunner {
    private final S3Client client;
    private final StorageProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(properties.getBucket()).build());
        } catch (S3Exception ex) {
            if (ex.statusCode() != 404) throw ex;
            client.createBucket(CreateBucketRequest.builder().bucket(properties.getBucket()).build());
        }
    }
}
