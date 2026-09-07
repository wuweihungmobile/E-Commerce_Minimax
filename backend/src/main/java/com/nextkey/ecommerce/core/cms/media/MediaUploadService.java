package com.nextkey.ecommerce.core.cms.media;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.nextkey.ecommerce.api.dto.M15Dto;
import com.nextkey.ecommerce.domain.model.cms.media.MediaAsset;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.cms.MediaAssetRepository;
import com.nextkey.ecommerce.infrastructure.storage.StorageService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * M15 CMS MediaUploadService
 * 媒體上傳服務，處理檔案上傳到 S3/MinIO + 建立 MediaAsset 記錄
 */
@Slf4j
@Service("mediaUploadService")
@RequiredArgsConstructor
public class MediaUploadService {

    private final MediaAssetRepository mediaAssetRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final MediaValidationService mediaValidationService;

    /**
     * 上傳媒體（使用 MultipartFile，實際上傳到 S3/MinIO）
     *
     * @param tenantId 租戶 ID
     * @param uploaderId 上傳者 ID
     * @param file MultipartFile
     * @return M15Dto.MediaUploadResponse
     */
    @Transactional
    public M15Dto.MediaUploadResponse uploadMedia(UUID tenantId, UUID uploaderId,
                                                  MultipartFile file) {
        // 驗證 Tenant
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));

        // 驗證 User
        User uploader = userRepository.findById(uploaderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1006));

        String fileName = file.getOriginalFilename();
        String mimeType = file.getContentType();
        Long fileSize = file.getSize();

        // 驗證檔案大小
        mediaValidationService.validateFileSize(fileSize, mimeType);

        // 驗證 MIME Type 並取得檔案類型
        MediaAsset.FileType fileType = mediaValidationService.determineFileType(mimeType);

        // 驗證檔案實際內容（magic bytes）與宣稱的 mimeType 相符（DEF-099）
        mediaValidationService.validateActualContent(file, mimeType);

        // 上傳到 S3/MinIO
        String storedPath = uploadToStorage(tenantId, fileName, file, mimeType);

        // 建立 MediaAsset
        MediaAsset media = createMediaAsset(tenant, uploader, fileName, fileName,
                storedPath, fileSize, mimeType, fileType);

        log.info("Media uploaded via MediaUploadService: id={}, tenantId={}, fileName={}",
                media.getId(), tenantId, fileName);

        return buildUploadResponse(media);
    }

    /**
     * 上傳媒體（使用路徑字串，保留向後相容性）
     * 注意：此方法不會實際上傳檔案到 S3/MinIO
     *
     * @param tenantId 租戶 ID
     * @param uploaderId 上傳者 ID
     * @param fileName 檔案名稱
     * @param originalName 原始檔案名稱
     * @param fileSize 檔案大小
     * @param mimeType MIME 類型
     * @param filePath 檔案路徑
     * @return M15Dto.MediaUploadResponse
     */
    @Transactional
    public M15Dto.MediaUploadResponse uploadMedia(UUID tenantId, UUID uploaderId,
                                                  String fileName, String originalName,
                                                  Long fileSize, String mimeType,
                                                  String filePath) {
        // 驗證 Tenant
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));

        // 驗證 User
        User uploader = userRepository.findById(uploaderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1006));

        // 驗證檔案大小
        mediaValidationService.validateFileSize(fileSize, mimeType);

        // 驗證 MIME Type 並取得檔案類型
        MediaAsset.FileType fileType = mediaValidationService.determineFileType(mimeType);

        // 驗證 filePath 確實屬於本租戶且物件真實存在於儲存層，防止跨租戶 IDOR（DEF-101）
        if (!storageService.belongsToTenant(filePath, tenantId) || !storageService.objectExists(filePath)) {
            throw new BusinessException(ErrorCode.E_9000, "Invalid file path");
        }

        // 建立 MediaAsset（使用傳入的 filePath）
        MediaAsset media = createMediaAsset(tenant, uploader, fileName, originalName,
                filePath, fileSize, mimeType, fileType);

        log.info("Media uploaded (path-based) via MediaUploadService: id={}, tenantId={}, fileName={}",
                media.getId(), tenantId, fileName);

        return buildUploadResponse(media);
    }

    // ========== Helper Methods ==========

    /**
     * 上傳檔案到 S3/MinIO
     */
    private String uploadToStorage(UUID tenantId, String fileName,
                                   MultipartFile file, String mimeType) {
        try (InputStream inputStream = file.getInputStream()) {
            return storageService.uploadFile(
                    tenantId, fileName, inputStream, file.getSize(), mimeType);
        } catch (IOException e) {
            log.error("Failed to read file input stream: {}", fileName, e);
            throw new BusinessException(ErrorCode.E_9000, "Failed to upload file: " + fileName);
        }
    }

    /**
     * 建立 MediaAsset 實體
     */
    private MediaAsset createMediaAsset(Tenant tenant, User uploader,
                                        String fileName, String originalName,
                                        String filePath, Long fileSize,
                                        String mimeType, MediaAsset.FileType fileType) {
        MediaAsset media = MediaAsset.builder()
                .tenant(tenant)
                .uploader(uploader)
                .fileName(fileName)
                .originalName(originalName)
                .filePath(filePath)
                .fileSize(fileSize)
                .mimeType(mimeType)
                .fileType(fileType)
                .isActive(true)
                .build();

        return mediaAssetRepository.save(media);
    }

    /**
     * 建立上傳回應 DTO
     */
    private M15Dto.MediaUploadResponse buildUploadResponse(MediaAsset media) {
        return M15Dto.MediaUploadResponse.builder()
                .id(media.getId())
                .fileName(media.getFileName())
                .filePath(media.getFilePath())
                .fileSize(media.getFileSize())
                .mimeType(media.getMimeType())
                .fileType(media.getFileType().name())
                .uploadedAt(media.getCreatedAt())
                .build();
    }
}