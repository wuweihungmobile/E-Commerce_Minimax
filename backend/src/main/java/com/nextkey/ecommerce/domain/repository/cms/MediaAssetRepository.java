package com.nextkey.ecommerce.domain.repository.cms;

import java.util.List;
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
 */
@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    /**
     * 依 Tenant 分頁查詢媒體
     */
    Page<MediaAsset> findByTenantId(UUID tenantId, Pageable pageable);

    /**
     * 依 Tenant 和 FileType 分頁查詢
     */
    Page<MediaAsset> findByTenantIdAndFileType(UUID tenantId, MediaAsset.FileType fileType, Pageable pageable);

    /**
     * 依 Tenant 查詢（按時間倒序）
     */
    List<MediaAsset> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /**
     * 依上傳者查詢
     */
    Page<MediaAsset> findByUploaderId(UUID uploaderId, Pageable pageable);

    /**
     * 檢查檔案是否被引用
     */
    @Query("SELECT COUNT(m) > 0 FROM MediaAsset m WHERE m.filePath LIKE %:filePath%")
    boolean isFileUsedInMediaAssets(@Param("filePath") String filePath);

    /**
     * 依 MIME Type 搜尋
     */
    Page<MediaAsset> findByTenantIdAndMimeTypeContaining(UUID tenantId, String mimeType, Pageable pageable);
}