package com.oep.corelms.storage;

import java.time.Duration;
import java.util.List;

public interface ObjectStorageService {
    String presignPut(String objectKey, String contentType, Duration expiry);

    String presignGet(String objectKey, Duration expiry);

    String createMultipartUpload(String objectKey, String contentType);

    String presignUploadPart(String objectKey, String uploadId, int partNumber, Duration expiry);

    void completeMultipartUpload(String objectKey, String uploadId, List<CompletedPartData> parts);

    void abortMultipartUpload(String objectKey, String uploadId);

    ObjectMetadata stat(String objectKey);

    void delete(String objectKey);

    record ObjectMetadata(long size, String contentType) {}

    record CompletedPartData(int partNumber, String eTag) {}
}
