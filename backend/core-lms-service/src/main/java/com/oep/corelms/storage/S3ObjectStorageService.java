package com.oep.corelms.storage;

import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.time.Duration;
import java.util.List;

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
    public String createMultipartUpload(String objectKey, String contentType) {
        try {
            return client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .contentType(contentType)
                    .build()).uploadId();
        } catch (RuntimeException ex) {
            throw unavailable();
        }
    }

    @Override
    public String presignUploadPart(String objectKey, String uploadId, int partNumber, Duration expiry) {
        try {
            UploadPartRequest request = UploadPartRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .build();
            return presigner.presignUploadPart(UploadPartPresignRequest.builder()
                    .signatureDuration(expiry)
                    .uploadPartRequest(request)
                    .build()).url().toString();
        } catch (RuntimeException ex) {
            throw unavailable();
        }
    }

    @Override
    public void completeMultipartUpload(String objectKey, String uploadId, List<CompletedPartData> parts) {
        try {
            List<CompletedPart> completedParts = parts.stream()
                    .map(part -> CompletedPart.builder().partNumber(part.partNumber()).eTag(part.eTag()).build())
                    .toList();
            client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                    .build());
        } catch (RuntimeException ex) {
            throw unavailable();
        }
    }

    @Override
    public void abortMultipartUpload(String objectKey, String uploadId) {
        try {
            client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .uploadId(uploadId)
                    .build());
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
