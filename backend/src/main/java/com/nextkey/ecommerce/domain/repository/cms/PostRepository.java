package com.nextkey.ecommerce.domain.repository.cms;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.cms.post.Post;

/**
 * M15 CMS Post Repository
 */
@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    /**
     * 依 Tenant 分頁查詢貼文
     */
    Page<Post> findByTenantId(UUID tenantId, Pageable pageable);

    /**
     * 依 Tenant 和 Status 分頁查詢貼文
     */
    Page<Post> findByTenantIdAndStatus(UUID tenantId, Post.PostStatus status, Pageable pageable);

    /**
     * 依 Slug 查詢（前台公開）
     */
    Optional<Post> findBySlug(String slug);

    /**
     * 依 Tenant 和 Slug 查詢
     */
    Optional<Post> findByTenantIdAndSlug(UUID tenantId, String slug);

    /**
     * 前台公開：查詢已發布的貼文列表
     */
    @Query("SELECT p FROM Post p WHERE p.tenant.id = :tenantId AND p.status = 'PUBLISHED' ORDER BY p.publishedAt DESC")
    Page<Post> findPublishedByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * 依作者查詢
     */
    Page<Post> findByAuthorId(UUID authorId, Pageable pageable);

    /**
     * 依分類查詢
     */
    Page<Post> findByTenantIdAndCategoryId(UUID tenantId, UUID categoryId, Pageable pageable);

    /**
     * 檢查 Slug 是否已存在
     */
    boolean existsBySlug(String slug);

    /**
     * 檢查 Slug 是否存在（排除指定 ID）
     */
    @Query("SELECT COUNT(p) > 0 FROM Post p WHERE p.slug = :slug AND p.id != :excludeId")
    boolean existsBySlugAndIdNot(@Param("slug") String slug, @Param("excludeId") UUID excludeId);

    /**
     * 搜尋貼文標題
     */
    @Query("SELECT p FROM Post p WHERE p.tenant.id = :tenantId AND LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Post> searchByTitle(@Param("tenantId") UUID tenantId, @Param("keyword") String keyword, Pageable pageable);

    /**
     * 檢查是否有貼文使用指定的路徑作為封面圖
     */
    @Query("SELECT COUNT(p) > 0 FROM Post p WHERE p.featuredImageUrl LIKE %:filePath%")
    boolean existsByFeaturedImageUrlContaining(@Param("filePath") String filePath);
}