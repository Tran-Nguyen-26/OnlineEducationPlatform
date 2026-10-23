package com.oep.corelms.storage;

import java.time.Duration;

public interface ObjectStorageService {
    String presignPut(String objectKey, String contentType, Duration expiry);

    String presignGet(String objectKey, Duration expiry);

    ObjectMetadata stat(String objectKey);

    void delete(String objectKey);

    record ObjectMetadata(long size, String contentType) {}
}
