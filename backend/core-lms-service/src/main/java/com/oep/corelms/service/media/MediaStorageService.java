package com.oep.corelms.service.media;

import java.io.InputStream;

/**
 * Abstraction cho object storage. Impl hiện tại: MinIO (local dev).
 * Production: swap sang GcsMediaStorageService / S3MediaStorageService — không sửa business logic.
 */
public interface MediaStorageService {

    /**
     * Tạo Presigned PUT URL để browser upload thẳng lên storage.
     *
     * @param bucket     tên bucket
     * @param objectKey  đường dẫn object trong bucket
     * @param ttlMinutes thời gian hiệu lực URL (phút)
     * @return URL có chữ ký, browser dùng HTTP PUT để upload
     */
    String generatePresignedPutUrl(String bucket, String objectKey, int ttlMinutes);

    /**
     * Tạo Presigned GET URL để client đọc object (stream video HLS, xem thumbnail).
     *
     * @param bucket     tên bucket
     * @param objectKey  đường dẫn object
     * @param ttlSeconds thời gian hiệu lực URL (giây)
     */
    String generatePresignedGetUrl(String bucket, String objectKey, int ttlSeconds);

    /** Kiểm tra object có tồn tại trong bucket không (dùng khi confirm upload). */
    boolean objectExists(String bucket, String objectKey);

    /** Lấy content-type và size của object. */
    ObjectMetadata getObjectMetadata(String bucket, String objectKey);

    /** Xóa 1 object (dùng khi xóa content hoặc dọn raw video sau transcode). */
    void deleteObject(String bucket, String objectKey);

    /** Xóa tất cả object có prefix (dùng để xóa toàn bộ HLS folder của 1 video). */
    void deleteObjectsByPrefix(String bucket, String prefix);

    // ---- Multipart Upload ----

    /** Khởi tạo multipart upload, trả về uploadId. */
    String initiateMultipartUpload(String bucket, String objectKey, String contentType);

    /**
     * Tạo Presigned URL cho 1 part trong multipart upload.
     *
     * @param partNumber 1-based part number
     */
    String generatePresignedPartUrl(String bucket, String objectKey, String uploadId, int partNumber, int ttlMinutes);

    /** Hoàn tất multipart upload sau khi tất cả part đã PUT xong. */
    void completeMultipartUpload(String bucket, String objectKey, String uploadId,
                                 java.util.List<PartETag> parts);

    record ObjectMetadata(String contentType, long size) {}
    record PartETag(int partNumber, String etag) {}
}
