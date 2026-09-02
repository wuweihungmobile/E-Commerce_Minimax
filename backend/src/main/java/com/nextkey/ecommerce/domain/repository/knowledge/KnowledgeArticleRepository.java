package com.nextkey.ecommerce.domain.repository.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.knowledge.KnowledgeArticle;
import com.nextkey.ecommerce.domain.model.knowledge.KnowledgeArticle.ArticleStatus;

@Repository
public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, UUID> {

    Page<KnowledgeArticle> findByTenantIdAndStatus(UUID tenantId, ArticleStatus status, Pageable pageable);

    Page<KnowledgeArticle> findByTenantIdAndCategoryIdAndStatus(UUID tenantId, UUID categoryId, ArticleStatus status, Pageable pageable);

    @Query("SELECT a FROM KnowledgeArticle a WHERE a.tenant.id = :tenantId AND a.status = 'PUBLISHED' " +
           "AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(a.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<KnowledgeArticle> searchByKeyword(@Param("tenantId") UUID tenantId,
                                            @Param("keyword") String keyword,
                                            Pageable pageable);

    Optional<KnowledgeArticle> findByTenantIdAndSlug(UUID tenantId, String slug);

    @Query("SELECT a FROM KnowledgeArticle a WHERE a.id = :id AND a.tenant.id = :tenantId")
    Optional<KnowledgeArticle> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("SELECT a FROM KnowledgeArticle a WHERE a.slug = :slug AND a.status = 'PUBLISHED'")
    Optional<KnowledgeArticle> findPublishedBySlug(@Param("slug") String slug);

    List<KnowledgeArticle> findByTenantIdAndIsPinnedTrueAndStatus(UUID tenantId, ArticleStatus status);

    @Query("SELECT a FROM KnowledgeArticle a WHERE a.category.id = :categoryId AND a.status = 'PUBLISHED' ORDER BY a.viewCount DESC")
    List<KnowledgeArticle> findPopularByCategoryId(@Param("categoryId") UUID categoryId, Pageable pageable);

    Page<KnowledgeArticle> findByCategoryId(UUID categoryId, Pageable pageable);

    /**
     * 原子遞增瀏覽次數（Sprint 106 / DEF-055）
     *
     * <p>取代「{@code findById} → 記憶體 +1 → {@code save()}」的讀後寫。
     * {@code KnowledgeArticle} 沒有 {@code @Version}，該寫法在併發下會靜默丟失更新
     * （紅燈實測：10 執行緒併發瀏覽只存活 1 次），而
     * {@link #findPopularByCategoryId} 以 {@code viewCount DESC} 取熱門文章，
     * 少計會直接讓排名失真。
     *
     * <p>🔴 <b>刻意不帶租戶條件</b>：修復前的 {@code KnowledgeBaseService.incrementViewCount}
     * 用的是無租戶範圍的 {@code findById}（同類別其餘方法都用 {@code findByIdAndTenantId}），
     * 本輪只改併發語意、不改權限語意。該不一致已記錄為 DEF-057。
     *
     * @return 更新筆數；0 表示該文章不存在
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE knowledge_articles SET view_count = COALESCE(view_count, 0) + 1 "
                 + "WHERE id = :articleId",
           nativeQuery = true)
    int incrementViewCount(@Param("articleId") UUID articleId);
}