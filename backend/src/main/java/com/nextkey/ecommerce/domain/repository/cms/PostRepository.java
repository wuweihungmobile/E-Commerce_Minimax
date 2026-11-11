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
     * 前台公開：查詢已發布且屬於指定分類的貼文列表（DEF-358(c)，Sprint 256）
     */
    @Query("SELECT p FROM Post p WHERE p.tenant.id = :tenantId AND p.category.id = :categoryId "
            + "AND p.status = 'PUBLISHED' ORDER BY p.publishedAt DESC")
    Page<Post> findPublishedByTenantIdAndCategoryId(
            @Param("tenantId") UUID tenantId, @Param("categoryId") UUID categoryId, Pageable pageable);

    /**
     * 跨店鋪公開貼文列表（Sprint 339，DEF-495）：所有「已發布」且「所屬店鋪是 ACTIVE」的貼文。
     * 排序由呼叫端的 Pageable 決定（不在這裡寫死，與上面兩個查詢不同，避免 JPQL 與 Pageable 各排一次）。
     *
     * <p>店鋪狀態直接在查詢裡過濾（不是查出來再丟掉）：分頁與總數才會對得上，停權／審核中／被拒／終止的店鋪
     * 一篇都不會出現（與 Sprint 337 的單店鋪公開端點同一個規則）。{@code JOIN FETCH} 只抓 ManyToOne（店鋪、分類），
     * 所以分頁仍在資料庫端完成，也不會因為組卡片而為每篇貼文再查一次店鋪或分類。
     */
    @Query(value = "SELECT p FROM Post p JOIN FETCH p.tenant t LEFT JOIN FETCH p.category "
            + "WHERE p.status = 'PUBLISHED' AND t.status = 'ACTIVE'",
            countQuery = "SELECT COUNT(p) FROM Post p WHERE p.status = 'PUBLISHED' AND p.tenant.status = 'ACTIVE'")
    Page<Post> findPublishedFromActiveTenants(Pageable pageable);

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
     * 檢查本店鋪是否有貼文的封面圖網址含指定片段（Sprint 334，DEF-474）。
     *
     * <p>片段可能是媒體素材編號（M18 的檔案網址是 /api/v2/media/files/{素材編號}），也可能是儲存路徑。
     * 取代原本不分店鋪、用 LIKE 的 existsByFeaturedImageUrlContaining：LIKE 會把儲存路徑裡的 _、% 當萬用字元，
     * 且別家店鋪的貼文也會擋住這家店刪圖。這裡用 LOCATE 做純子字串比對。呼叫端必須保證 fragment 非空
     * （LOCATE('', x) 恆大於 0）。
     */
    @Query("SELECT COUNT(p) > 0 FROM Post p WHERE p.tenant.id = :tenantId AND p.featuredImageUrl IS NOT NULL "
            + "AND LOCATE(:fragment, p.featuredImageUrl) > 0")
    boolean existsByTenantIdAndFeaturedImageUrlReferencing(@Param("tenantId") UUID tenantId,
                                                           @Param("fragment") String fragment);

    /**
     * Sprint 147：MAX_POSTS 數量配額強制執行——計算目前已發布中的貼文數
     */
    long countByTenantIdAndStatus(UUID tenantId, Post.PostStatus status);
}