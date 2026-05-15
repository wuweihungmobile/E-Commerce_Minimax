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

import com.nextkey.ecommerce.domain.model.knowledge.ArticleVersion;

@Repository
public interface ArticleVersionRepository extends JpaRepository<ArticleVersion, UUID> {

    Page<ArticleVersion> findByArticleIdOrderByVersionNumberDesc(UUID articleId, Pageable pageable);

    Optional<ArticleVersion> findByArticleIdAndVersionNumber(UUID articleId, Integer versionNumber);

    @Query("SELECT av FROM ArticleVersion av WHERE av.article.id = :articleId ORDER BY av.versionNumber DESC")
    List<ArticleVersion> findAllVersionsByArticleId(@Param("articleId") UUID articleId);

    @Query("SELECT MAX(av.versionNumber) FROM ArticleVersion av WHERE av.article.id = :articleId")
    Integer findMaxVersionNumberByArticleId(@Param("articleId") UUID articleId);

    @Query("SELECT av FROM ArticleVersion av WHERE av.article.id = :articleId AND av.article.tenant.id = :tenantId ORDER BY av.versionNumber DESC")
    Page<ArticleVersion> findByArticleIdAndTenantId(@Param("articleId") UUID articleId, @Param("tenantId") UUID tenantId, Pageable pageable);

    void deleteByArticleId(UUID articleId);
}