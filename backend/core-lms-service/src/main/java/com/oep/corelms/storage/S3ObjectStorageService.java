package com.oep.corelms.storage;

import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class S3ObjectStorageService implements ObjectStorageService {
    private final S3Client client;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    @Override
    public String presignPut(String objectKey, String contentType, Duration expiry) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .contentType(contentType)
                    .build();
            return presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(expiry)
                    .putObjectRequest(request)
                    .build()).url().toString();
        } catch (RuntimeException ex) {
            throw unavailable();
        }
    }

    @Override
    public String presignGet(String objectKey, Duration expiry) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build();
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(expiry)
                    .getObjectRequest(request)
                    .build()).url().toString();
        } catch (RuntimeException ex) {
            throw unavailable();
        }
    }

    @Override
    public ObjectMetadata stat(String objectKey) {
        try {
            var head = client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build());
            return new ObjectMetadata(head.contentLength(), head.contentType());
        } catch (NoSuchKeyException ex) {
            throw new BusinessException(ErrorCode.UPLOAD_OBJECT_NOT_FOUND, "Uploaded object was not found");
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new BusinessException(ErrorCode.UPLOAD_OBJECT_NOT_FOUND, "Uploaded object was not found");
            }
            throw unavailable();
        } catch (RuntimeException ex) {
            throw unavailable();
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build());
        } catch (RuntimeException ex) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.CONTENT_STORAGE_UNAVAILABLE, "Object storage is unavailable");
    }
}
