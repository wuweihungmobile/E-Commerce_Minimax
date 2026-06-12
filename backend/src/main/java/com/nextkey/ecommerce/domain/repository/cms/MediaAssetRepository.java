package com.nextkey.ecommerce.domain.repository.cms;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.cms.media.MediaAsset;

/**
 * M15 CMS MediaAsset Repository
 *
 * 同時支援 M15 CMS 媒體管理 (uploader, fileType 等) 與 Sprint 16 US-005/006 多圖評價整合
 * (category, tags, usageCount, isDeleted 等)。
 *
 * 🔴 注意：所有依 tenantId 查詢的方法都必須使用 @Query + m.tenant.id，因為 MediaAsset
 * 的 tenant 是 ManyToOne 關聯，不是直接屬性。
 */
@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    /**
     * 依 Tenant 分頁查詢媒體
     * 🔴 修正：使用 @Query 因為 tenant 是關聯屬性
     */
    @Query("SELECT m FROM CmsMediaAsset m WHERE m.tenant.id = :tenantId")
    Page<MediaAsset> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * 依 Tenant 和 FileType 分頁查詢
     * 🔴 修正：使用 @Query
     */
    @Query("SELECT m FROM CmsMediaAsset m WHERE m.tenant.id = :tenantId AND m.fileType = :fileType")
    Page<MediaAsset> findByTenantIdAndFileType(@Param("tenantId") UUID tenantId, @Param("fileType") MediaAsset.FileType fileType, Pageable pageable);

    /**
     * 依 Tenant 查詢（按時間倒序）
     */
    @Query("SELECT m FROM CmsMediaAsset m WHERE m.tenant.id = :tenantId ORDER BY m.createdAt DESC")
    List<MediaAsset> findByTenantIdOrderByCreatedAtDesc(@Param("tenantId") UUID tenantId);

    /**
     * 依上傳者查詢
     */
    Page<MediaAsset> findByUploaderId(UUID uploaderId, Pageable pageable);

    /**
     * 檢查檔案是否被引用
     */
    @Query("SELECT COUNT(m) > 0 FROM CmsMediaAsset m WHERE m.filePath LIKE %:filePath%")
    boolean isFileUsedInMediaAssets(@Param("filePath") String filePath);

    /**
     * 依 MIME Type 搜尋
     * 🔴 修正：使用 @Query
     */
    @Query("SELECT m FROM CmsMediaAsset m WHERE m.tenant.id = :tenantId AND m.mimeType LIKE %:mimeType%")
    Page<MediaAsset> findByTenantIdAndMimeTypeContaining(@Param("tenantId") UUID tenantId, @Param("mimeType") String mimeType, Pageable pageable);

    // ========== Sprint 16 US-005/006 多圖評價整合方法 ==========

    /**
     * 依 Tenant 查詢未刪除的媒體 (Sprint 16)
     * 🔴 修正：使用 @Query
     */
    @Query("SELECT m FROM CmsMediaAsset m WHERE m.tenant.id = :tenantId AND m.isDeleted = false")
    Page<MediaAsset> findByTenantIdAndIsDeletedFalse(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * 依 Tenant 與 Category 查詢未刪除的媒體 (Sprint 16)
     * 🔴 修正：使用 @Query
     */
    @Query("SELECT m FROM CmsMediaAsset m WHERE m.tenant.id = :tenantId AND m.category.id = :categoryId AND m.isDeleted = false")
    Page<MediaAsset> findByTenantIdAndCategoryIdAndIsDeletedFalse(
            @Param("tenantId") UUID tenantId, @Param("categoryId") UUID categoryId, Pageable pageable);

    /**
     * 依 Tenant 與精確 MIME Type 查詢未刪除的媒體 (Sprint 16)
     * 🔴 修正：使用 @Query
     */
    @Query("SELECT m FROM CmsMediaAsset m WHERE m.tenant.id = :tenantId AND m.mimeType = :mimeType AND m.isDeleted = false")
    Page<MediaAsset> findByTenantIdAndMimeTypeAndIsDeletedFalse(
            @Param("tenantId") UUID tenantId, @Param("mimeType") String mimeType, Pageable pageable);

    /**
     * 依關鍵字搜尋未刪除的媒體 (檔名 / altText) (Sprint 16)
     */
    @Query("SELECT m FROM CmsMediaAsset m WHERE m.tenant.id = :tenantId AND m.isDeleted = false "
            + "AND (LOWER(m.fileName) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(m.altText) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<MediaAsset> searchByKeyword(
            @Param("tenantId") UUID tenantId,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * 查詢單一未刪除的媒體 (Sprint 16)
     */
    @Query("SELECT m FROM CmsMediaAsset m WHERE m.id = :id AND m.tenant.id = :tenantId AND m.isDeleted = false")
    Optional<MediaAsset> findActiveByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    /**
     * 統計 Tenant 的未刪除媒體數 (Sprint 16)
     * 🔴 修正：使用 @Query
     */
    @Query("SELECT COUNT(m) FROM CmsMediaAsset m WHERE m.tenant.id = :tenantId AND m.isDeleted = false")
    long countByTenantIdAndIsDeletedFalse(@Param("tenantId") UUID tenantId);

    /**
     * 統計 Tenant 的媒體數 (相容舊 API, 含已刪除)
     * 🔴 修正：使用 @Query
     */
    @Query("SELECT COUNT(m) FROM CmsMediaAsset m WHERE m.tenant.id = :tenantId")
    long countByTenantId(@Param("tenantId") UUID tenantId);
}