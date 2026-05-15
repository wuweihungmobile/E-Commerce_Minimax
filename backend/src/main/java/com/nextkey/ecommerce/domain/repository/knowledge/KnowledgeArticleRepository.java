package com.nextkey.ecommerce.domain.repository.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}