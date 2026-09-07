package com.nextkey.ecommerce.core.media;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.nextkey.ecommerce.api.dto.media.CreateMediaCategoryRequest;
import com.nextkey.ecommerce.api.dto.media.MediaAssetDto;
import com.nextkey.ecommerce.api.dto.media.MediaCategoryDto;
import com.nextkey.ecommerce.api.dto.media.UpdateMediaCategoryRequest;
import com.nextkey.ecommerce.api.dto.media.UpdateMediaRequest;
import com.nextkey.ecommerce.api.dto.media.UploadMediaRequest;
import com.nextkey.ecommerce.core.cms.media.MediaValidationService;
import com.nextkey.ecommerce.domain.model.cms.media.MediaAsset;
import com.nextkey.ecommerce.domain.model.media.MediaCategory;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.cms.MediaAssetRepository;
import com.nextkey.ecommerce.domain.repository.media.MediaCategoryRepository;
import com.nextkey.ecommerce.infrastructure.storage.StorageService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import static com.nextkey.ecommerce.shared.tenant.TenantContext.getCurrentTenant;
import static com.nextkey.ecommerce.shared.tenant.TenantContext.getCurrentUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaCategoryRepository mediaCategoryRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final MediaValidationService mediaValidationService;

    // ========== Media Category Operations ==========

    @Transactional(readOnly = true)
    public List<MediaCategoryDto> getCategories() {
        UUID tenantId = getCurrentTenant();
        List<MediaCategory> categories = mediaCategoryRepository.findByTenantIdOrderBySortOrderAsc(tenantId);
        return categories.stream()
                .map(this::toMediaCategoryDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MediaCategoryDto> getRootCategories() {
        UUID tenantId = getCurrentTenant();
        List<MediaCategory> categories = mediaCategoryRepository.findByTenantIdAndParentIsNullOrderBySortOrderAsc(tenantId);
        return categories.stream()
                .map(this::toMediaCategoryDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MediaCategoryDto getCategory(UUID categoryId) {
        UUID tenantId = getCurrentTenant();
        MediaCategory category = mediaCategoryRepository.findByIdAndTenantId(categoryId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Category not found"));
        return toMediaCategoryDto(category);
    }

    @Transactional
    public MediaCategoryDto createCategory(CreateMediaCategoryRequest request) {
        UUID tenantId = getCurrentTenant();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000, "Tenant not found"));

        // 驗證分類名稱唯一性
        if (request.getParentId() == null) {
            if (mediaCategoryRepository.existsByTenantIdAndNameAndParentIsNull(tenantId, request.getName())) {
                throw new BusinessException(ErrorCode.E_3001, "Category name already exists at root level");
            }
        } else {
            if (mediaCategoryRepository.existsByTenantIdAndNameAndParentId(tenantId, request.getName(), request.getParentId())) {
                throw new BusinessException(ErrorCode.E_3001, "Category name already exists under this parent");
            }
            // 驗證父分類存在
            mediaCategoryRepository.findByIdAndTenantId(request.getParentId(), tenantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Parent category not found"));
        }

        MediaCategory parent = null;
        if (request.getParentId() != null) {
            parent = mediaCategoryRepository.findByIdAndTenantId(request.getParentId(), tenantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Parent category not found"));
        }

        MediaCategory category = MediaCategory.builder()
                .tenant(tenant)
                .name(request.getName())
                .description(request.getDescription())
                .parent(parent)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();

        category = mediaCategoryRepository.save(category);
        log.info("Created media category: id={}, name={}, tenantId={}", category.getId(), category.getName(), tenantId);

        return toMediaCategoryDto(category);
    }

    @Transactional
    public MediaCategoryDto updateCategory(UUID categoryId, UpdateMediaCategoryRequest request) {
        UUID tenantId = getCurrentTenant();
        MediaCategory category = mediaCategoryRepository.findByIdAndTenantId(categoryId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Category not found"));

        if (request.getName() != null) {
            // 驗證名稱唯一性
            boolean duplicate;
            if (request.getParentId() != null) {
                duplicate = mediaCategoryRepository.existsByTenantIdAndNameAndParentId(
                        tenantId, request.getName(), request.getParentId());
            } else if (category.getParent() != null) {
                duplicate = mediaCategoryRepository.existsByTenantIdAndNameAndParentId(
                        tenantId, request.getName(), category.getParent().getId());
            } else {
                duplicate = mediaCategoryRepository.existsByTenantIdAndNameAndParentIsNull(tenantId, request.getName());
            }
            if (duplicate && !category.getName().equals(request.getName())) {
                throw new BusinessException(ErrorCode.E_3001, "Category name already exists");
            }
            category.setName(request.getName());
        }

        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }

        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }

        category = mediaCategoryRepository.save(category);
        log.info("Updated media category: id={}", categoryId);

        return toMediaCategoryDto(category);
    }

    @Transactional
    public void deleteCategory(UUID categoryId) {
        UUID tenantId = getCurrentTenant();
        MediaCategory category = mediaCategoryRepository.findByIdAndTenantId(categoryId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Category not found"));

        // 檢查是否有子分類
        List<MediaCategory> children = mediaCategoryRepository.findByTenantIdAndParentIdOrderBySortOrderAsc(tenantId, categoryId);
        if (!children.isEmpty()) {
            throw new BusinessException(ErrorCode.E_3001, "Cannot delete category with children");
        }

        // 檢查是否有媒體資產使用此分類
        Page<MediaAsset> assets = mediaAssetRepository.findByTenantIdAndCategoryIdAndIsDeletedFalse(
                tenantId, categoryId, PageRequest.of(0, 1));
        if (assets.hasContent()) {
            throw new BusinessException(ErrorCode.E_3001, "Cannot delete category with media assets");
        }

        mediaCategoryRepository.delete(category);
        log.info("Deleted media category: id={}", categoryId);
    }

    // ========== Media Asset Operations ==========

    @Transactional(readOnly = true)
    public Page<MediaAssetDto> getAssets(int page, int size, UUID categoryId, String mimeType, String keyword) {
        UUID tenantId = getCurrentTenant();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<MediaAsset> assets;
        if (keyword != null && !keyword.isBlank()) {
            assets = mediaAssetRepository.searchByKeyword(tenantId, keyword.trim(), pageable);
        } else if (mimeType != null && !mimeType.isBlank()) {
            assets = mediaAssetRepository.findByTenantIdAndMimeTypeAndIsDeletedFalse(tenantId, mimeType, pageable);
        } else if (categoryId != null) {
            assets = mediaAssetRepository.findByTenantIdAndCategoryIdAndIsDeletedFalse(tenantId, categoryId, pageable);
        } else {
            assets = mediaAssetRepository.findByTenantIdAndIsDeletedFalse(tenantId, pageable);
        }

        return assets.map(this::toMediaAssetDto);
    }

    @Transactional(readOnly = true)
    public MediaAssetDto getAsset(UUID assetId) {
        UUID tenantId = getCurrentTenant();
        MediaAsset asset = mediaAssetRepository.findActiveByIdAndTenantId(assetId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Media asset not found"));
        return toMediaAssetDto(asset);
    }

    @Transactional
    public MediaAssetDto uploadAsset(UploadMediaRequest request) {
        UUID tenantId = getCurrentTenant();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000, "Tenant not found"));

        MediaCategory category = null;
        if (request.getCategoryId() != null) {
            category = mediaCategoryRepository.findByIdAndTenantId(request.getCategoryId(), tenantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Category not found"));
        }

        // 從 mimeType 推斷 fileType
        MediaAsset.FileType fileType = inferFileType(request.getMimeType());

        // 驗證 filePath 確實屬於本租戶且物件真實存在於儲存層，防止跨租戶 IDOR（DEF-101）
        if (!storageService.belongsToTenant(request.getFilePath(), tenantId)
                || !storageService.objectExists(request.getFilePath())) {
            throw new BusinessException(ErrorCode.E_9000, "Invalid file path");
        }

        MediaAsset asset = MediaAsset.builder()
                .tenant(tenant)
                .category(category)
                .fileName(request.getFileName())
                .originalName(request.getOriginalName())
                .filePath(request.getFilePath())
                .fileSize(request.getFileSize())
                .mimeType(request.getMimeType())
                .fileType(fileType)
                .tags(request.getTags() != null ? request.getTags() : List.of())
                .altText(request.getAltText())
                .title(request.getTitle())
                .usageCount(0)
                .isDeleted(false)
                .build();

        asset = mediaAssetRepository.save(asset);
        log.info("Uploaded media asset: id={}, fileName={}, tenantId={}", asset.getId(), asset.getFileName(), tenantId);

        return toMediaAssetDto(asset);
    }

    /**
     * 上傳媒體（MultipartFile，實際上傳到 S3/MinIO）
     * Sprint 132（DEF-096）：分類/標籤/替代文字/標題皆為選填，上傳當下可先不分類
     */
    @Transactional
    public MediaAssetDto uploadAssetMultipart(MultipartFile file, UUID categoryId,
                                              List<String> tags, String altText, String title) {
        UUID tenantId = getCurrentTenant();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000, "Tenant not found"));

        User uploader = null;
        UUID uploaderId = getCurrentUser();
        if (uploaderId != null) {
            uploader = userRepository.findById(uploaderId).orElse(null);
        }

        MediaCategory category = null;
        if (categoryId != null) {
            category = mediaCategoryRepository.findByIdAndTenantId(categoryId, tenantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Category not found"));
        }

        String originalName = file.getOriginalFilename();
        String mimeType = file.getContentType();
        Long fileSize = file.getSize();

        mediaValidationService.validateFileSize(fileSize, mimeType);
        MediaAsset.FileType fileType = mediaValidationService.determineFileType(mimeType);
        mediaValidationService.validateActualContent(file, mimeType);

        String storedPath;
        try (InputStream inputStream = file.getInputStream()) {
            storedPath = storageService.uploadFile(tenantId, originalName, inputStream, fileSize, mimeType);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.E_9000, "Failed to upload file: " + originalName);
        }

        MediaAsset asset = MediaAsset.builder()
                .tenant(tenant)
                .uploader(uploader)
                .category(category)
                .fileName(originalName)
                .originalName(originalName)
                .filePath(storedPath)
                .fileSize(fileSize)
                .mimeType(mimeType)
                .fileType(fileType)
                .tags(tags != null ? tags : List.of())
                .altText(altText)
                .title(title)
                .usageCount(0)
                .isDeleted(false)
                .build();

        asset = mediaAssetRepository.save(asset);
        log.info("Uploaded media asset (multipart): id={}, fileName={}, tenantId={}",
                asset.getId(), asset.getFileName(), tenantId);

        return toMediaAssetDto(asset);
    }

    /**
     * 取得媒體檔案內容（供 Controller 串流回應）
     * Sprint 132（DEF-096）：filePath 即 StorageService.uploadFile 回傳的完整物件路徑
     */
    @Transactional(readOnly = true)
    public AssetFile downloadAsset(UUID assetId) {
        UUID tenantId = getCurrentTenant();
        MediaAsset asset = mediaAssetRepository.findActiveByIdAndTenantId(assetId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Media asset not found"));
        // filePath 本身是否真的屬於本租戶（而非僅資料列的 tenantId 相符）需獨立檢查，防止跨租戶 IDOR（DEF-101）
        if (!storageService.belongsToTenant(asset.getFilePath(), tenantId)) {
            throw new BusinessException(ErrorCode.E_4000, "Media asset not found");
        }
        InputStream inputStream = storageService.getObject(asset.getFilePath());
        return new AssetFile(inputStream, asset.getMimeType(), asset.getOriginalName());
    }

    /**
     * 媒體檔案串流內容（InputStream + Content-Type/檔名，供 Controller 組裝 HTTP 回應）
     */
    public record AssetFile(InputStream inputStream, String mimeType, String fileName) {
    }

    @Transactional
    public MediaAssetDto updateAsset(UUID assetId, UpdateMediaRequest request) {
        UUID tenantId = getCurrentTenant();
        MediaAsset asset = mediaAssetRepository.findActiveByIdAndTenantId(assetId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Media asset not found"));

        if (request.getCategoryId() != null) {
            MediaCategory category = mediaCategoryRepository.findByIdAndTenantId(request.getCategoryId(), tenantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Category not found"));
            asset.setCategory(category);
        }

        if (request.getTags() != null) {
            asset.setTags(request.getTags());
        }

        if (request.getAltText() != null) {
            asset.setAltText(request.getAltText());
        }

        if (request.getTitle() != null) {
            asset.setTitle(request.getTitle());
        }

        asset = mediaAssetRepository.save(asset);
        log.info("Updated media asset: id={}", assetId);

        return toMediaAssetDto(asset);
    }

    @Transactional
    public void deleteAsset(UUID assetId) {
        UUID tenantId = getCurrentTenant();
        MediaAsset asset = mediaAssetRepository.findActiveByIdAndTenantId(assetId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Media asset not found"));

        // 軟刪除
        asset.setIsDeleted(true);
        mediaAssetRepository.save(asset);
        log.info("Soft deleted media asset: id={}", assetId);
    }

    @Transactional
    public void incrementUsageCount(UUID assetId) {
        UUID tenantId = getCurrentTenant();
        MediaAsset asset = mediaAssetRepository.findActiveByIdAndTenantId(assetId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Media asset not found"));
        asset.incrementUsageCount();
        mediaAssetRepository.save(asset);
    }

    @Transactional(readOnly = true)
    public Long getAssetCount() {
        UUID tenantId = getCurrentTenant();
        return mediaAssetRepository.countByTenantId(tenantId);
    }

    // ========== Helper Methods ==========

    private MediaCategoryDto toMediaCategoryDto(MediaCategory category) {
        MediaCategoryDto.MediaCategoryDtoBuilder builder = MediaCategoryDto.builder()
                .id(category.getId())
                .tenantId(category.getTenantId())
                .name(category.getName())
                .description(category.getDescription())
                .sortOrder(category.getSortOrder())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt());

        if (category.getParent() != null) {
            builder.parentId(category.getParent().getId());
        }

        // 載入子分類
        List<MediaCategory> children = mediaCategoryRepository
                .findByTenantIdAndParentIdOrderBySortOrderAsc(category.getTenantId(), category.getId());
        if (!children.isEmpty()) {
            builder.children(children.stream()
                    .map(this::toMediaCategoryDto)
                    .collect(Collectors.toList()));
        }

        return builder.build();
    }

    private MediaAssetDto toMediaAssetDto(MediaAsset asset) {
        MediaAssetDto.MediaAssetDtoBuilder builder = MediaAssetDto.builder()
                .id(asset.getId())
                .tenantId(asset.getTenantId())
                .fileName(asset.getFileName())
                .filePath(asset.getFilePath())
                .fileSize(asset.getFileSize())
                .mimeType(asset.getMimeType())
                .tags(asset.getTags())
                .usageCount(asset.getUsageCount())
                .altText(asset.getAltText())
                .title(asset.getTitle())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt());

        if (asset.getCategory() != null) {
            builder.categoryId(asset.getCategory().getId())
                   .categoryName(asset.getCategory().getName());
        }

        // 生成 URL（基於檔案路徑）
        if (asset.getFilePath() != null) {
            builder.url("/api/v2/media/files/" + asset.getId());
        }

        return builder.build();
    }

    // ========== 圖片驗證方法（Sprint 16 US-005） ==========

    /**
     * 檢查媒體是否存在（Spring Data 命名風格）
     *
     * 用於評價上傳時檢查每個 imageUrl 對應的 mediaId 是否真實存在於 M15 Media Library。
     *
     * @param mediaId 媒體 ID（可為 null 或空字串）
     * @return true 表示存在且有效；false 表示不存在
     */
    @Transactional(readOnly = true)
    public boolean existsMediaById(String mediaId) {
        if (mediaId == null || mediaId.isBlank()) {
            return false;
        }
        try {
            UUID id = UUID.fromString(mediaId);
            return mediaAssetRepository.existsById(id);
        } catch (IllegalArgumentException e) {
            // mediaId 不是合法的 UUID 格式
            return false;
        }
    }

    /**
     * 批次檢查多個媒體是否存在
     *
     * @param mediaIds 媒體 ID 列表
     * @return 第一個無效的 mediaId，若全部有效則回傳 null
     */
    @Transactional(readOnly = true)
    public String findFirstInvalidMediaId(List<String> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return null;
        }
        for (String mediaId : mediaIds) {
            if (!existsMediaById(mediaId)) {
                return mediaId;
            }
        }
        return null;
    }

    /**
     * 從 MIME Type 推斷檔案類型
     */
    private MediaAsset.FileType inferFileType(String mimeType) {
        if (mimeType == null) {
            return MediaAsset.FileType.DOCUMENT;
        }
        if (mimeType.startsWith("image/")) {
            return MediaAsset.FileType.IMAGE;
        }
        if (mimeType.startsWith("video/")) {
            return MediaAsset.FileType.VIDEO;
        }
        return MediaAsset.FileType.DOCUMENT;
    }

}