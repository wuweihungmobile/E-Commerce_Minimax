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

import com.nextkey.ecommerce.domain.model.cms.post.PostCategory;

/**
 * M15 CMS PostCategory Repository
 */
@Repository
public interface PostCategoryRepository extends JpaRepository<PostCategory, UUID> {

    /**
     * 依 Tenant 分頁查詢分類
     */
    Page<PostCategory> findByTenantId(UUID tenantId, Pageable pageable);

    /**
     * 依 Tenant 查詢（僅啟用）
     */
    List<PostCategory> findByTenantIdAndIsActiveTrue(UUID tenantId);

    /**
     * 依 Tenant 和 Slug 查詢
     */
    Optional<PostCategory> findByTenantIdAndSlug(UUID tenantId, String slug);

    /**
     * 檢查 Slug 是否存在於 Tenant
     */
    boolean existsByTenantIdAndSlug(UUID tenantId, String slug);

    /**
     * 檢查 Slug 是否存在（排除指定 ID）
     */
    @Query("SELECT COUNT(pc) > 0 FROM PostCategory pc WHERE pc.slug = :slug AND pc.id != :excludeId AND pc.tenant.id = :tenantId")
    boolean existsByTenantIdAndSlugAndIdNot(@Param("tenantId") UUID tenantId, @Param("slug") String slug, @Param("excludeId") UUID excludeId);

    /**
     * 依 Tenant 查詢並排序
     */
    List<PostCategory> findByTenantIdOrderBySortOrderAsc(UUID tenantId);

    /**
     * 依 ID 與 Tenant 查詢（DEF-240）：PostService.createPost/updatePost 先前用不分租戶的
     * findById 驗證 categoryId，可把貼文掛到他租戶的分類下（分類名稱因此外洩到本租戶的公開
     * 貼文回應）。比照本模組既有的 findByTenantIdAndSlug 等租戶範圍查詢慣例。
     */
    Optional<PostCategory> findByIdAndTenantId(UUID id, UUID tenantId);
}