package com.oep.corelms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

/** Generates presigned URLs so the browser uploads/downloads straight to/from the bucket (see StorageConfig). */
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Presigner presigner;

    @Value("${oep.storage.bucket}")
    private String bucket;

    @Value("${oep.storage.upload-url-ttl-seconds:900}")
    private long uploadUrlTtlSeconds;

    @Value("${oep.storage.view-url-ttl-seconds:600}")
    private long viewUrlTtlSeconds;

    public String newObjectKey(String fileName) {
        String safeName = fileName == null ? "file" : fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return "lesson-contents/" + UUID.randomUUID() + "/" + safeName;
    }

    /** contentType must match exactly what the client sends as the PUT Content-Type header, or MinIO rejects the signature. */
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

    public long viewUrlTtlSeconds() {
        return viewUrlTtlSeconds;
    }

    public String presignView(String objectKey) {
        GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucket).key(objectKey).build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(viewUrlTtlSeconds))
                .getObjectRequest(getRequest)
                .build();
        return presigner.presignGetObject(presignRequest).url().toString();
    }
}
