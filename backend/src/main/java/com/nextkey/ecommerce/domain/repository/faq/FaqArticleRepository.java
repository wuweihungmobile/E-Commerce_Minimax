package com.nextkey.ecommerce.domain.repository.faq;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query("SELECT fa FROM FaqArticle fa WHERE fa.tenantId = :tenantId AND fa.isPublished = true AND " +
           "(LOWER(fa.question) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(fa.answer) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<FaqArticle> searchByTenantIdAndKeyword(@Param("tenantId") UUID tenantId, @Param("keyword") String keyword, Pageable pageable);
}