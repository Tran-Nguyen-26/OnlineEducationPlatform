package com.oep.corelms.service.media;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.minio.CreateMultipartUploadResponse;
import io.minio.MinioAsyncClient;
import io.minio.messages.Part;

/**
 * Custom MinIO client kế thừa MinioAsyncClient để truy cập các phương thức protected
 * liên quan đến S3 Multipart Upload (createMultipartUpload, completeMultipartUpload).
 */
public class CustomMinioClient extends MinioAsyncClient {

    public CustomMinioClient(MinioAsyncClient client) {
        super(client);
    }

    public String initMultipartUpload(String bucket, String objectKey, String contentType) throws Exception {
        Multimap<String, String> headers = HashMultimap.create();
        if (contentType != null && !contentType.isBlank()) {
            headers.put("Content-Type", contentType);
        }
        CreateMultipartUploadResponse response = createMultipartUpload(
                bucket, null, objectKey, headers, null
        );
        return response.result().uploadId();
    }

    public void completeMultipart(String bucket, String objectKey, String uploadId, Part[] parts) throws Exception {
        completeMultipartUpload(bucket, null, objectKey, uploadId, parts, null, null);
    }
}
