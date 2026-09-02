package com.nextkey.ecommerce.domain.repository.faq;

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

import com.nextkey.ecommerce.domain.model.faq.FaqArticle;

@Repository
public interface FaqArticleRepository extends JpaRepository<FaqArticle, UUID> {

    Page<FaqArticle> findByCategoryId(UUID categoryId, Pageable pageable);

    Page<FaqArticle> findByTenantIdAndIsPublishedTrue(UUID tenantId, Pageable pageable);

    Page<FaqArticle> findByTenantIdAndCategoryIdAndIsPublishedTrue(UUID tenantId, UUID categoryId, Pageable pageable);

    Optional<FaqArticle> findByTenantIdAndSlug(UUID tenantId, String slug);

    boolean existsByTenantIdAndSlug(UUID tenantId, String slug);

    Optional<FaqArticle> findByIdAndTenantId(UUID id, UUID tenantId);

    List<FaqArticle> findByIsPinnedTrueAndIsPublishedTrueOrderBySortOrderAsc();

    List<FaqArticle> findByTenantIdAndIsPinnedTrueOrderBySortOrderAsc(UUID tenantId);

    @Query("SELECT fa FROM FaqArticle fa WHERE fa.tenantId = :tenantId AND fa.isPublished = true AND " +
           "(LOWER(fa.question) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(fa.answer) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<FaqArticle> searchByTenantIdAndKeyword(@Param("tenantId") UUID tenantId, @Param("keyword") String keyword, Pageable pageable);

    /**
     * 原子遞增瀏覽次數（Sprint 106 / DEF-055）
     *
     * <p>取代「{@code findByIdAndTenantId} → 記憶體 +1 → {@code save()}」的讀後寫。
     * {@code FaqArticle} 沒有 {@code @Version}，該寫法在併發下會靜默丟失更新
     * （紅燈實測：10 執行緒併發瀏覽只存活 1 次）。
     *
     * <p>租戶條件保留在 WHERE 子句，維持原本「跨租戶視為查無文章」的行為。
     *
     * @return 更新筆數；0 表示該文章不存在或不屬於該租戶
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE faq_articles SET view_count = COALESCE(view_count, 0) + 1 "
                 + "WHERE id = :articleId AND tenant_id = :tenantId",
           nativeQuery = true)
    int incrementViewCount(@Param("articleId") UUID articleId, @Param("tenantId") UUID tenantId);

    long countByTenantIdAndCategoryId(UUID tenantId, UUID categoryId);

    long countByTenantIdAndCategoryIdAndIsPublishedTrue(UUID tenantId, UUID categoryId);
}