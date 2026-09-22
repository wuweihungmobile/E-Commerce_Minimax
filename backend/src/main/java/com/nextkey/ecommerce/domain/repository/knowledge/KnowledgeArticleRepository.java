package com.nextkey.ecommerce.domain.repository.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * 併發防護（DEF-120，KnowledgeBaseService.createVersionSnapshot）：以
     * {@code SELECT ... FOR UPDATE} 鎖住文章列，序列化「讀目前最大版本號 → +1 → INSERT」
     * 這段複合操作。{@code article_versions} 表沒有 {@code (article_id, version_number)}
     * 唯一約束，兩個併發請求各自算出相同的 {@code newVersion} 並各自成功 INSERT 會造成
     * {@link com.nextkey.ecommerce.domain.repository.knowledge.ArticleVersionRepository
     * #findByArticleIdAndVersionNumber} 之後查到重複列而丟出
     * {@code IncorrectResultSizeDataAccessException}（持續性功能性 500，非機率性偶發）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM KnowledgeArticle a WHERE a.id = :id AND a.tenant.id = :tenantId")
    Optional<KnowledgeArticle> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("SELECT a FROM KnowledgeArticle a WHERE a.slug = :slug AND a.status = 'PUBLISHED'")
    Optional<KnowledgeArticle> findPublishedBySlug(@Param("slug") String slug);

    List<KnowledgeArticle> findByTenantIdAndIsPinnedTrueAndStatus(UUID tenantId, ArticleStatus status);

    @Query("SELECT a FROM KnowledgeArticle a WHERE a.category.id = :categoryId AND a.status = 'PUBLISHED' ORDER BY a.viewCount DESC")
    List<KnowledgeArticle> findPopularByCategoryId(@Param("categoryId") UUID categoryId, Pageable pageable);

    Page<KnowledgeArticle> findByCategoryId(UUID categoryId, Pageable pageable);

    /**
     * 租戶範圍的分類引用檢查（DEF-239 縱深防禦）。即使 createArticle/updateArticle 已改用
     * {@code findByIdAndTenantId} 驗證 categoryId 歸屬，deleteCategory 的引用檢查若仍用不分租戶的
     * {@link #findByCategoryId}，任何跨租戶掛錯的既有髒資料（或未來新的寫入路徑重蹈覆轍）依舊會讓
     * 受害租戶的分類永久刪不掉。
     */
    Page<KnowledgeArticle> findByCategoryIdAndTenantId(UUID categoryId, UUID tenantId, Pageable pageable);

    /**
     * 原子遞增瀏覽次數（Sprint 106 / DEF-055）
     *
     * <p>取代「{@code findById} → 記憶體 +1 → {@code save()}」的讀後寫。
     * {@code KnowledgeArticle} 沒有 {@code @Version}，該寫法在併發下會靜默丟失更新
     * （紅燈實測：10 執行緒併發瀏覽只存活 1 次），而
     * {@link #findPopularByCategoryId} 以 {@code viewCount DESC} 取熱門文章，
     * 少計會直接讓排名失真。
     *
     * <p><b>租戶條件（Sprint 107 / DEF-057）</b>：Sprint 106 沿用了修復前無租戶範圍的
     * {@code findById}，使同一個類別對「他租戶的文章」反應不一致——列表看不到、詳情 404，
     * 但瀏覽數端點回 200 且真的 +1。既然 {@code viewCount} 是 {@link #findPopularByCategoryId}
     * 的排序欄位，那等於讓他租戶操縱本租戶的熱門排名。語意由使用者於 Sprint 107 拍板，
     * 收斂為與同類別其餘端點一致。
     *
     * @return 更新筆數；0 表示該文章不存在或不屬於該租戶
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE knowledge_articles SET view_count = COALESCE(view_count, 0) + 1 "
                 + "WHERE id = :articleId AND tenant_id = :tenantId",
           nativeQuery = true)
    int incrementViewCount(@Param("articleId") UUID articleId, @Param("tenantId") UUID tenantId);
}