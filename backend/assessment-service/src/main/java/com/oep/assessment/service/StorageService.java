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

    /** Types that can execute in a browser or on a host: never accepted as uploads, whatever the caller claims. */
    private static final java.util.Set<String> BLOCKED_TYPES = java.util.Set.of(
            "text/html", "application/xhtml+xml", "image/svg+xml", "application/x-msdownload", "application/x-sh",
            "application/x-executable", "application/x-dosexec", "application/javascript", "text/javascript", "application/java-archive");

    public String presignUpload(String objectKey, String contentType) {
        String ct = contentType == null ? "" : contentType.toLowerCase().trim();
        if (BLOCKED_TYPES.contains(ct) || objectKey.toLowerCase().matches(".*\\.(exe|sh|bat|cmd|msi|jar|html?|js|svg)$")) {
            throw new com.oep.coremodel.exception.BusinessException(com.oep.coremodel.exception.ErrorCode.VALIDATION_FAILED,
                    "Loại tệp này không được phép tải lên");
        }
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
