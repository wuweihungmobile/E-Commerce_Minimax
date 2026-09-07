package com.nextkey.ecommerce.core.cms.media;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.M15Dto;
import com.nextkey.ecommerce.domain.model.cms.media.MediaAsset;
import com.nextkey.ecommerce.domain.repository.cms.MediaAssetRepository;
import com.nextkey.ecommerce.domain.repository.cms.PostRepository;
import com.nextkey.ecommerce.infrastructure.storage.StorageService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;


/**
 * M15 CMS MediaService (Facade Pattern)
 * 媒體庫服務 - 協調 MediaUploadService 和 MediaValidationService
 *
 * 重構歷史 (Sprint 17):
 * - MediaUploadService: 處理上傳邏輯 (MultipartFile → StorageService → MediaAsset)
 * - MediaValidationService: 處理檔案大小/MIME 類型/格式驗證
 * - MediaService: 協調層，保留既有 API 向後相容
 */
@Slf4j
@Service("cmsMediaService")
public class MediaService {

    private final MediaAssetRepository mediaAssetRepository;
    private final PostRepository postRepository;
    private final StorageService storageService;

    // 委派的子服務（通過 @Autowired 注入）
    private final MediaUploadService mediaUploadService;

    /**
     * 構造函數（支援 Spring 注入）
     */
    @Autowired
    public MediaService(MediaAssetRepository mediaAssetRepository,
                        PostRepository postRepository,
                        StorageService storageService,
                        MediaUploadService mediaUploadService) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.postRepository = postRepository;
        this.storageService = storageService;
        this.mediaUploadService = mediaUploadService;
    }

    /**
     * 上傳媒體（使用 MultipartFile，實際上傳到 S3/MinIO）
     * 委派給 MediaUploadService
     */
    @Transactional
    public M15Dto.MediaUploadResponse uploadMedia(UUID tenantId, UUID uploaderId,
                                                  org.springframework.web.multipart.MultipartFile file) {
        return mediaUploadService.uploadMedia(tenantId, uploaderId, file);
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
        return mediaUploadService.uploadMedia(tenantId, uploaderId,
                fileName, originalName, fileSize, mimeType, filePath);
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
     * 取得媒體檔案內容（供 Controller 串流回應）
     * Sprint 133（DEF-097）：filePath 即 StorageService.uploadFile 回傳的完整物件路徑
     */
    @Transactional(readOnly = true)
    public MediaFile downloadMedia(final UUID mediaId, final UUID tenantId) {
        MediaAsset media = getMediaOrThrow(mediaId);

        if (!media.getTenant().getId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.E_4031);
        }

        // filePath 本身是否真的屬於本租戶（而非僅資料列的 tenantId 相符）需獨立檢查，防止跨租戶 IDOR（DEF-101）
        if (!storageService.belongsToTenant(media.getFilePath(), tenantId)) {
            throw new BusinessException(ErrorCode.E_4103);
        }

        InputStream inputStream = storageService.getObject(media.getFilePath());
        return new MediaFile(inputStream, media.getMimeType(), media.getOriginalName());
    }

    /**
     * 媒體檔案串流內容（InputStream + Content-Type/檔名，供 Controller 組裝 HTTP 回應）
     */
    public record MediaFile(InputStream inputStream, String mimeType, String fileName) {
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
}