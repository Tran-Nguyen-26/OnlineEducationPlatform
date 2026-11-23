package com.oep.corelms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
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

    public record StoredObjectInfo(long sizeBytes, String mimeType) {
    }

    private final S3Presigner presigner;
    private final software.amazon.awssdk.services.s3.S3Client s3;

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

    /** Real size of an uploaded object (null if it cannot be read) — the client-declared size is never trusted alone. */
    public Long objectSize(String objectKey) {
        try {
            return s3.headObject(software.amazon.awssdk.services.s3.model.HeadObjectRequest.builder().bucket(bucket).key(objectKey).build()).contentLength();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void deleteObject(String objectKey) {
        try {
            s3.deleteObject(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (RuntimeException ignored) {
            // best effort: an orphan object is harmless, the content row is not marked AVAILABLE
        }
    }

    public long viewUrlTtlSeconds() {
        return viewUrlTtlSeconds;
    }

    /** Small text objects (HLS playlists). */
    public String readText(String objectKey) {
        return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(objectKey).build())
                .asUtf8String();
    }

    public String presignView(String objectKey) {
        GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucket).key(objectKey).build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(viewUrlTtlSeconds))
                .getObjectRequest(getRequest)
                .build();
        return presigner.presignGetObject(presignRequest).url().toString();
    }

    /** Verify the object actually uploaded before accepting an AI-eligible learning file. */
    public StoredObjectInfo inspectObject(String objectKey) {
        var response = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
        return new StoredObjectInfo(response.contentLength(), response.contentType());
    }
}
