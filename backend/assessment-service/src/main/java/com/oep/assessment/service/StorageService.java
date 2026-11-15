package com.oep.assessment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

/** Presigned upload URLs for assignment submission files — same bucket core-lms-service created/owns. */
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Presigner presigner;

    @Value("${oep.storage.bucket}")
    private String bucket;

    @Value("${oep.storage.upload-url-ttl-seconds:900}")
    private long uploadUrlTtlSeconds;

    public String newObjectKey(String fileName) {
        String safeName = fileName == null ? "file" : fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return "submissions/" + UUID.randomUUID() + "/" + safeName;
    }

    public String presignUpload(String objectKey, String contentType) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(uploadUrlTtlSeconds))
                .putObjectRequest(putRequest)
                .build();
        return presigner.presignPutObject(presignRequest).url().toString();
    }
}
