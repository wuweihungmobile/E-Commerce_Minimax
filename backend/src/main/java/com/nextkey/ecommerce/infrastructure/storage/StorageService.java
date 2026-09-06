package com.nextkey.ecommerce.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import lombok.extern.slf4j.Slf4j;

/**
 * StorageService - S3/MinIO 儲存服務
 * 處理檔案上傳、下載、刪除到 S3/MinIO
 */
@Slf4j
@Service
public class StorageService {

    private final MinioClient minioClient;
    private final String bucket;
    @SuppressWarnings("unused")
    private final int presignedExpiry;

    public StorageService(
            @Value("${storage.endpoint}") String endpoint,
            @Value("${storage.access-key}") String accessKey,
            @Value("${storage.secret-key}") String secretKey,
            @Value("${storage.bucket}") String bucket,
            @Value("${storage.region}") String region,
            @Value("${storage.presigned-expiry}") int presignedExpiry) {

        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .region(region)
                .build();
        this.bucket = bucket;
        this.presignedExpiry = presignedExpiry;

        initializeBucket();
    }

    /**
     * 初始化 bucket（如果不存在則建立）
     */
    private void initializeBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created bucket: {}", bucket);
            }
        } catch (ErrorResponseException | InsufficientDataException | InternalException
                | InvalidResponseException | ServerException | IOException
                | java.security.InvalidKeyException | java.security.NoSuchAlgorithmException
                | XmlParserException e) {
            log.warn("Could not initialize bucket {}: {}", bucket, e.getMessage());
        }
    }

    /**
     * 上傳檔案到 S3/MinIO（使用多租戶前綴）
     * @param tenantId 租戶 ID
     * @param fileName 檔案名稱
     * @param inputStream 檔案輸入流
     * @param size 檔案大小
     * @param contentType MIME type
     * @return 儲存路徑（包含 tenant 前綴）
     */
    public String uploadFile(UUID tenantId, String fileName,
                             InputStream inputStream, long size,
                             String contentType) {
        String objectName = buildObjectName(tenantId, fileName);

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build());
            log.info("Uploaded file: {} to bucket: {}", objectName, bucket);
            return objectName;
        } catch (ErrorResponseException | InsufficientDataException | InternalException
                | InvalidResponseException | ServerException | IOException
                | java.security.InvalidKeyException | java.security.NoSuchAlgorithmException
                | XmlParserException e) {
            log.error("[E_9906] Failed to upload file: {}", objectName, e);
            throw new BusinessException(ErrorCode.E_9906, "File upload failed: " + objectName, e);
        }
    }

    /**
     * 檢查物件是否存在
     */
    public boolean objectExists(final UUID tenantId, final String fileName) {
        String objectName = buildObjectName(tenantId, fileName);
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build());
            return true;
        } catch (ErrorResponseException | InsufficientDataException | InternalException
                | InvalidResponseException | ServerException | IOException
                | java.security.InvalidKeyException | java.security.NoSuchAlgorithmException
                | XmlParserException e) {
            return false;
        }
    }

    /**
     * 取得物件輸入流
     * 注意: objectName 應該是完整路徑（包含 tenantId 前綴），即 uploadFile 的回傳值。
     * （Sprint 132 修正：原簽章 (tenantId, fileName) 透過 buildObjectName 重新產生亂數 UUID，
     * 與實際儲存路徑不符，本方法在此修正前無任何呼叫端，從未被正確使用過）
     */
    public InputStream getObject(final String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build());
        } catch (ErrorResponseException | InsufficientDataException | InternalException
                | InvalidResponseException | ServerException | IOException
                | java.security.InvalidKeyException | java.security.NoSuchAlgorithmException
                | XmlParserException e) {
            log.error("[E_9906] Failed to get object: {}", objectName, e);
            throw new BusinessException(ErrorCode.E_9906, "Failed to get object: " + objectName, e);
        }
    }

    /**
     * 刪除物件
     * 注意: objectName 應該是完整路徑（包含 tenantId 前綴）
     */
    public void deleteObject(final String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build());
            log.info("Deleted object: {} from bucket: {}", objectName, bucket);
        } catch (ErrorResponseException | InsufficientDataException | InternalException
                | InvalidResponseException | ServerException | IOException
                | java.security.InvalidKeyException | java.security.NoSuchAlgorithmException
                | XmlParserException e) {
            log.error("[E_9906] Failed to delete object: {}", objectName, e);
            throw new BusinessException(ErrorCode.E_9906, "Failed to delete object: " + objectName, e);
        }
    }

    /**
     * 建立多租戶隔離的物件名稱
     * 格式: {tenantId}/{uuid}-{originalFileName}
     */
    private String buildObjectName(final UUID tenantId, final String fileName) {
        String uuid = UUID.randomUUID().toString();
        return String.format("%s/%s-%s", tenantId.toString(), uuid, fileName);
    }
}