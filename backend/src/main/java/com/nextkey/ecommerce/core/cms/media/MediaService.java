package com.nextkey.ecommerce.core.cms.media;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import com.nextkey.ecommerce.domain.repository.cms.PostRepository;
import com.nextkey.ecommerce.infrastructure.storage.StorageService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * M15 CMS MediaService
 * 媒體庫服務，處理上傳、列表、刪除
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaAssetRepository mediaAssetRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final StorageService storageService;

    // 檔案大小限制
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_VIDEO_SIZE = 100 * 1024 * 1024; // 100MB
    private static final long MAX_DOCUMENT_SIZE = 5 * 1024 * 1024; // 5MB

    // 允許的 MIME Types
    private static final List<String> ALLOWED_IMAGE_TYPES = List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");
    private static final List<String> ALLOWED_VIDEO_TYPES = List.of(
            "video/mp4", "video/quicktime", "video/x-msvideo");
    private static final List<String> ALLOWED_DOCUMENT_TYPES = List.of(
            "application/pdf");

    /**
     * 上傳媒體（使用 MultipartFile，實際上傳到 S3/MinIO）
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
        validateFileSize(fileSize, mimeType);

        // 驗證 MIME Type
        MediaAsset.FileType fileType = determineFileType(mimeType);

        // 上傳到 S3/MinIO
        String storedPath;
        try (InputStream inputStream = file.getInputStream()) {
            storedPath = storageService.uploadFile(
                    tenantId, fileName, inputStream, fileSize, mimeType);
        } catch (IOException e) {
            log.error("Failed to read file input stream: {}", fileName, e);
            throw new BusinessException(ErrorCode.E_9000, "Failed to upload file: " + fileName);
        }

        // 建立 MediaAsset
        MediaAsset media = MediaAsset.builder()
                .tenant(tenant)
                .uploader(uploader)
                .fileName(fileName)
                .originalName(fileName)
                .filePath(storedPath)
                .fileSize(fileSize)
                .mimeType(mimeType)
                .fileType(fileType)
                .isActive(true)
                .build();

        media = mediaAssetRepository.save(media);

        log.info("Media uploaded: id={}, tenantId={}, fileName={}, storedPath={}",
                media.getId(), tenantId, fileName, storedPath);

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

    /**
     * 上傳媒體（使用路徑字串，保留向後相容性）
     * 注意：此方法不會實際上傳檔案到 S3/MinIO
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
        validateFileSize(fileSize, mimeType);

        // 驗證 MIME Type
        MediaAsset.FileType fileType = determineFileType(mimeType);

        // 建立 MediaAsset（使用傳入的 filePath）
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

        media = mediaAssetRepository.save(media);

        log.info("Media uploaded (path-based): id={}, tenantId={}, fileName={}",
                media.getId(), tenantId, fileName);

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

    /**
     * 取得媒體列表
     */
    @Transactional(readOnly = true)
    public M15Dto.MediaListResponse getMediaList(UUID tenantId, int page, int size, MediaAsset.FileType fileType) {
        PageRequest pageRequest = PageRequest.of(page, size);

        Page<MediaAsset> mediaPage;
        if (fileType != null) {
            mediaPage = mediaAssetRepository.findByTenantIdAndFileType(tenantId, fileType, pageRequest);
        } else {
            mediaPage = mediaAssetRepository.findByTenantId(tenantId, pageRequest);
        }

        List<M15Dto.MediaResponse> mediaList = mediaPage.getContent().stream()
                .map(M15Dto.MediaResponse::from)
                .toList();

        return M15Dto.MediaListResponse.builder()
                .items(mediaList)
                .totalCount((int) mediaPage.getTotalElements())
                .page(page)
                .size(size)
                .totalPages(mediaPage.getTotalPages())
                .build();
    }

    /**
     * 刪除媒體（不被任何貼文引用時）
     */
    @Transactional
    public void deleteMedia(final UUID mediaId, final UUID tenantId) {
        MediaAsset media = getMediaOrThrow(mediaId);

        // 驗證 Tenant 擁有權
        if (!media.getTenant().getId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.E_4031);
        }

        // 檢查是否被引用（檢查 posts.featured_image_url）
        if (isMediaInUse(mediaId)) {
            throw new BusinessException(ErrorCode.E_9000, "Cannot delete media that is used by posts");
        }

        // 從 S3/MinIO 刪除實際檔案
        try {
            storageService.deleteObject(media.getFilePath());
        } catch (DataAccessException e) {
            log.warn("Failed to delete file from storage: {}, error: {}",
                    media.getFilePath(), e.getMessage());
            // 繼續刪除 DB 記錄
        }

        mediaAssetRepository.delete(media);
        log.info("Media deleted: id={}, tenantId={}", mediaId, tenantId);
    }

    /**
     * 取得媒體詳情
     */
    @Transactional(readOnly = true)
    public M15Dto.MediaResponse getMedia(UUID mediaId, UUID tenantId) {
        MediaAsset media = getMediaOrThrow(mediaId);

        if (!media.getTenant().getId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.E_4031);
        }

        return M15Dto.MediaResponse.from(media);
    }

    /**
     * 檢查媒體是否被使用
     */
    @Transactional(readOnly = true)
    public boolean isMediaInUse(final UUID mediaId) {
        // 檢查 posts.featured_image_url 是否包含此 media 的路徑
        String mediaPath = mediaAssetRepository.findById(mediaId)
                .map(MediaAsset::getFilePath)
                .orElse(null);

        if (mediaPath == null) {
            return false;
        }

        // 檢查是否有 Post 使用此媒體作為 featured_image_url
        return postRepository.existsByFeaturedImageUrlContaining(mediaPath);
    }

    // ========== Helper Methods ==========

    private MediaAsset getMediaOrThrow(final UUID mediaId) {
        return mediaAssetRepository.findById(mediaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4103));
    }

    private void validateFileSize(final Long fileSize, final String mimeType) {
        long maxSize;
        if (mimeType.startsWith("image/")) {
            maxSize = MAX_IMAGE_SIZE;
        } else if (mimeType.startsWith("video/")) {
            maxSize = MAX_VIDEO_SIZE;
        } else if (mimeType.equals("application/pdf")) {
            maxSize = MAX_DOCUMENT_SIZE;
        } else {
            throw new BusinessException(ErrorCode.E_9000, "Unsupported file type: " + mimeType);
        }

        if (fileSize > maxSize) {
            throw new BusinessException(ErrorCode.E_9000,
                    String.format("File size exceeds limit: %d > %d", fileSize, maxSize));
        }
    }

    private MediaAsset.FileType determineFileType(String mimeType) {
        if (ALLOWED_IMAGE_TYPES.contains(mimeType)) {
            return MediaAsset.FileType.IMAGE;
        } else if (ALLOWED_VIDEO_TYPES.contains(mimeType)) {
            return MediaAsset.FileType.VIDEO;
        } else if (ALLOWED_DOCUMENT_TYPES.contains(mimeType)) {
            return MediaAsset.FileType.DOCUMENT;
        } else {
            throw new BusinessException(ErrorCode.E_9000, "Unsupported file type: " + mimeType);
        }
    }
}