package com.nextkey.ecommerce.domain.repository.cms;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
     * 原子遞增瀏覽次數（Sprint 106 / DEF-055）
     *
     * <p>取代「載入 → {@code post.incrementViewCount()} → {@code save()}」的讀後寫。
     * {@code Post} 沒有 {@code @Version}，該寫法在併發下會靜默丟失更新。
     *
     * <p>刻意不加 {@code clearAutomatically}：呼叫端 {@code getPublishedPostBySlug}
     * 在本方法之後仍要用同一個 {@code Post} 實體組回應，清空持久化上下文會把它 detach。
     *
     * <p>本敘述不會觸發 {@code @PreUpdate}，因此瀏覽不再更動 {@code updated_at}
     * ——瀏覽不是內容修改，且無任何排序或業務邏輯依賴該欄位。
     *
     * @return 更新筆數；0 表示該貼文不存在
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE posts SET view_count = COALESCE(view_count, 0) + 1 WHERE id = :postId",
           nativeQuery = true)
    int incrementViewCount(@Param("postId") UUID postId);

    /**
     * 檢查是否有貼文使用指定的路徑作為封面圖
     */
    @Query("SELECT COUNT(p) > 0 FROM Post p WHERE p.featuredImageUrl LIKE %:filePath%")
    boolean existsByFeaturedImageUrlContaining(@Param("filePath") String filePath);
}