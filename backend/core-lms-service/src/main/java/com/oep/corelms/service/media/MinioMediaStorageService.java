package com.oep.corelms.service.media;

import com.oep.corelms.config.MinioConfig;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioMediaStorageService implements MediaStorageService {

    private final MinioClient minioClient;
    private final CustomMinioClient customMinioClient;
    private final MinioConfig minioConfig;

    @Override
    public String generatePresignedPutUrl(String bucket, String objectKey, int ttlMinutes) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(ttlMinutes, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            log.error("Lỗi tạo Presigned PUT URL — bucket={}, key={}", bucket, objectKey, e);
            throw new RuntimeException("Không thể tạo Presigned PUT URL", e);
        }
    }

    @Override
    public String generatePresignedGetUrl(String bucket, String objectKey, int ttlSeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(ttlSeconds, TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            log.error("Lỗi tạo Presigned GET URL — bucket={}, key={}", bucket, objectKey, e);
            throw new RuntimeException("Không thể tạo Presigned GET URL", e);
        }
    }

    @Override
    public boolean objectExists(String bucket, String objectKey) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(objectKey).build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public ObjectMetadata getObjectMetadata(String bucket, String objectKey) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(objectKey).build()
            );
            return new ObjectMetadata(stat.contentType(), stat.size());
        } catch (Exception e) {
            log.error("Lỗi getObjectMetadata — bucket={}, key={}", bucket, objectKey, e);
            throw new RuntimeException("Không thể đọc metadata object", e);
        }
    }

    @Override
    public void deleteObject(String bucket, String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build()
            );
            log.info("Đã xóa object: {}/{}", bucket, objectKey);
        } catch (Exception e) {
            log.warn("Lỗi xóa object {}/{}: {}", bucket, objectKey, e.getMessage());
        }
    }

    @Override
    public void deleteObjectsByPrefix(String bucket, String prefix) {
        try {
            Iterable<Result<io.minio.messages.Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucket).prefix(prefix).recursive(true).build()
            );
            for (Result<io.minio.messages.Item> result : results) {
                String key = result.get().objectName();
                deleteObject(bucket, key);
            }
            log.info("Đã xóa tất cả object với prefix: {}/{}", bucket, prefix);
        } catch (Exception e) {
            log.warn("Lỗi xóa objects với prefix {}/{}: {}", bucket, prefix, e.getMessage());
        }
    }

    // ---- Multipart Upload ----

    @Override
    public String initiateMultipartUpload(String bucket, String objectKey, String contentType) {
        try {
            return customMinioClient.initMultipartUpload(bucket, objectKey, contentType);
        } catch (Exception e) {
            log.error("Lỗi initiateMultipartUpload — bucket={}, key={}", bucket, objectKey, e);
            throw new RuntimeException("Không thể khởi tạo multipart upload", e);
        }
    }

    @Override
    public String generatePresignedPartUrl(String bucket, String objectKey,
                                           String uploadId, int partNumber, int ttlMinutes) {
        try {
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("partNumber", String.valueOf(partNumber));
            queryParams.put("uploadId", uploadId);
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(ttlMinutes, TimeUnit.MINUTES)
                            .extraQueryParams(queryParams)
                            .build()
            );
        } catch (Exception e) {
            log.error("Lỗi generatePresignedPartUrl — partNumber={}", partNumber, e);
            throw new RuntimeException("Không thể tạo Presigned Part URL", e);
        }
    }

    @Override
    public void completeMultipartUpload(String bucket, String objectKey,
                                        String uploadId, List<PartETag> parts) {
        try {
            Part[] minioParts = parts.stream()
                    .map(p -> new Part(p.partNumber(), p.etag()))
                    .toArray(Part[]::new);
            customMinioClient.completeMultipart(bucket, objectKey, uploadId, minioParts);
            log.info("Multipart upload hoàn thành: {}/{}", bucket, objectKey);
        } catch (Exception e) {
            log.error("Lỗi completeMultipartUpload — bucket={}, key={}", bucket, objectKey, e);
            throw new RuntimeException("Không thể hoàn tất multipart upload", e);
        }
    }
}
