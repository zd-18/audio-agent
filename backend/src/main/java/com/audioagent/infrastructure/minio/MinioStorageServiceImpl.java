package com.audioagent.infrastructure.minio;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageServiceImpl implements MinioStorageService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Override
    public void upload(
            String objectKey,
            InputStream inputStream,
            long size,
            String contentType
    ) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectKey)
                            .stream(inputStream, size, -1)
                            .contentType(
                                    contentType == null
                                            ? "application/octet-stream"
                                            : contentType
                            )
                            .build()
            );

            log.info(
                    "MinIO object uploaded successfully, bucket={}, objectKey={}",
                    minioProperties.getBucketName(),
                    objectKey
            );
        } catch (Exception e) {
            log.error(
                    "Failed to upload object to MinIO, objectKey={}",
                    objectKey,
                    e
            );

            throw new BusinessException(
                   ErrorCode.MINIO_UPLOAD_FAILED,
                    "文件上传至对象存储失败"
            );
        }
    }

    @Override
    public InputStream getObject(String objectKey) {
        return getObject(minioProperties.getBucketName(), objectKey);
    }

    @Override
    public InputStream getObject(String bucketName, String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
        } catch (ErrorResponseException e) {
            String errorCode = e.errorResponse().code();
            if ("NoSuchKey".equals(errorCode)
                    || "NoSuchObject".equals(errorCode)) {
                throw new BusinessException(
                        ErrorCode.MINIO_OBJECT_NOT_FOUND,
                        "MinIO object does not exist"
                );
            }
            log.error(
                    "Failed to get MinIO object, bucket={}, objectKey={}",
                    bucketName,
                    objectKey,
                    e
            );
            throw new BusinessException(
                    ErrorCode.MINIO_DOWNLOAD_FAILED,
                    "Object storage is temporarily unavailable"
            );
        } catch (Exception e) {
            log.error(
                    "Failed to get MinIO object, objectKey={}",
                    objectKey,
                    e
            );

            throw new BusinessException(
                    ErrorCode.MINIO_DOWNLOAD_FAILED,
                    "获取文件失败"
            );
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectKey)
                            .build()
            );

            log.info("MinIO object deleted successfully, objectKey={}", objectKey);
        } catch (Exception e) {
            log.error(
                    "Failed to delete MinIO object, objectKey={}",
                    objectKey,
                    e
            );

            throw new BusinessException(
                     ErrorCode.MINIO_DELETE_FAILED,
                    "对象存储文件删除失败"
            );
        }
    }

    @Override
    public String generatePresignedUrl(String objectKey) {
        return generatePresignedUrl(
                minioProperties.getBucketName(),
                objectKey,
                minioProperties.getPresignedUrlExpireSeconds()
        );
    }

    @Override
    public String generatePresignedUrl(
            String bucketName,
            String objectKey,
            int expirySeconds
    ) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to generate MinIO presigned URL", e);

            throw new BusinessException(
                   ErrorCode.MINIO_PRESIGNED_URL_FAILED,
                    "生成文件临时访问地址失败"
            );
        }
    }

    @Override
    public boolean exists(String objectKey) {
        return exists(minioProperties.getBucketName(), objectKey);
    }

    @Override
    public boolean exists(String bucketName, String objectKey) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            String errorCode = e.errorResponse().code();
            if ("NoSuchKey".equals(errorCode)
                    || "NoSuchObject".equals(errorCode)) {
                return false;
            }

            log.error(
                    "Failed to check MinIO object, objectKey={}",
                    objectKey,
                    e
            );

            throw new BusinessException(
                    ErrorCode.MINIO_OBJECT_CHECK_FAILED,
                    "检查对象存储文件失败"
            );
        } catch (Exception e) {
            log.error(
                    "Failed to check MinIO object, objectKey={}",
                    objectKey,
                    e
            );

            throw new BusinessException(
                   ErrorCode.MINIO_OBJECT_CHECK_FAILED,
                    "检查对象存储文件失败"
            );
        }
    }
}
