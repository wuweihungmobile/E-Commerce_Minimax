package com.nextkey.ecommerce.core.knowledge;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.knowledge.CreateKnowledgeArticleRequest;
import com.nextkey.ecommerce.api.dto.knowledge.CreateKnowledgeCategoryRequest;
import com.nextkey.ecommerce.api.dto.knowledge.KnowledgeArticleDto;
import com.nextkey.ecommerce.api.dto.knowledge.KnowledgeCategoryDto;
import com.nextkey.ecommerce.api.dto.knowledge.UpdateKnowledgeArticleRequest;
import com.nextkey.ecommerce.api.dto.knowledge.UpdateKnowledgeCategoryRequest;
import com.nextkey.ecommerce.domain.model.knowledge.ArticleVersion;
import com.nextkey.ecommerce.domain.model.knowledge.KnowledgeArticle;
import com.nextkey.ecommerce.domain.model.knowledge.KnowledgeArticle.ArticleStatus;
import com.nextkey.ecommerce.domain.model.knowledge.KnowledgeCategory;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.knowledge.ArticleVersionRepository;
import com.nextkey.ecommerce.domain.repository.knowledge.KnowledgeArticleRepository;
import com.nextkey.ecommerce.domain.repository.knowledge.KnowledgeCategoryRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import static com.nextkey.ecommerce.shared.tenant.TenantContext.getCurrentTenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeArticleRepository articleRepository;
    private final KnowledgeCategoryRepository categoryRepository;
    private final ArticleVersionRepository articleVersionRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    // ========== Category Operations ==========

    @Transactional(readOnly = true)
    public List<KnowledgeCategoryDto> getCategories() {
        UUID tenantId = getCurrentTenant();
        List<KnowledgeCategory> categories = categoryRepository.findByTenantIdOrderBySortOrderAsc(tenantId);
        return categories.stream()
                .map(this::toCategoryDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public KnowledgeCategoryDto getCategory(UUID categoryId) {
        UUID tenantId = getCurrentTenant();
        KnowledgeCategory category = categoryRepository.findByIdAndTenantId(categoryId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Category not found"));
        return toCategoryDto(category);
    }

    @Transactional(readOnly = true)
    public KnowledgeCategoryDto getCategoryBySlug(String slug) {
        UUID tenantId = getCurrentTenant();
        KnowledgeCategory category = categoryRepository.findByTenantIdAndSlug(tenantId, slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Category not found"));
        return toCategoryDto(category);
    }

    @Transactional
    public KnowledgeCategoryDto createCategory(CreateKnowledgeCategoryRequest request) {
        UUID tenantId = getCurrentTenant();
        if (categoryRepository.existsByTenantIdAndSlug(tenantId, request.getSlug())) {
            throw new BusinessException(ErrorCode.E_3001, "Category slug already exists for this tenant");
        }

        KnowledgeCategory category = KnowledgeCategory.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .slug(request.getSlug())
                .description(request.getDescription())
                .icon(request.getIcon())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();

        category = categoryRepository.save(category);
        log.info("Created knowledge category: id={}, name={}, tenantId={}", category.getId(), category.getName(), tenantId);

        return toCategoryDto(category);
    }

    @Transactional
    public KnowledgeCategoryDto updateCategory(UUID categoryId, UpdateKnowledgeCategoryRequest request) {
        UUID tenantId = getCurrentTenant();
        KnowledgeCategory category = categoryRepository.findByIdAndTenantId(categoryId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Category not found"));

        if (request.getName() != null) {
            category.setName(request.getName());
        }

        if (request.getSlug() != null && !request.getSlug().equals(category.getSlug())) {
            if (categoryRepository.existsByTenantIdAndSlug(tenantId, request.getSlug())) {
                throw new BusinessException(ErrorCode.E_3001, "Category slug already exists for this tenant");
            }
            category.setSlug(request.getSlug());
        }

        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }

        if (request.getIcon() != null) {
            category.setIcon(request.getIcon());
        }

        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }

        category = categoryRepository.save(category);
        log.info("Updated knowledge category: id={}", categoryId);

        return toCategoryDto(category);
    }

    @Transactional
    public void deleteCategory(UUID categoryId) {
        UUID tenantId = getCurrentTenant();
        KnowledgeCategory category = categoryRepository.findByIdAndTenantId(categoryId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Category not found"));

        // 檢查是否有文章引用
        Page<KnowledgeArticle> articles = articleRepository.findByCategoryId(categoryId, PageRequest.of(0, 1));
        if (articles.hasContent()) {
            throw new BusinessException(ErrorCode.E_3001, "Cannot delete category with articles");
        }

        categoryRepository.delete(category);
        log.info("Deleted knowledge category: id={}, tenantId={}", categoryId, tenantId);
    }

    // ========== Article Operations ==========

    @Transactional(readOnly = true)
    public Page<KnowledgeArticleDto> getArticles(int page, int size, UUID categoryId, String keyword) {
        UUID tenantId = getCurrentTenant();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<KnowledgeArticle> articles;
        if (keyword != null && !keyword.isBlank()) {
            articles = articleRepository.searchByKeyword(tenantId, keyword.trim(), pageable);
        } else if (categoryId != null) {
            articles = articleRepository.findByTenantIdAndCategoryIdAndStatus(tenantId, categoryId, ArticleStatus.PUBLISHED, pageable);
        } else {
            articles = articleRepository.findByTenantIdAndStatus(tenantId, ArticleStatus.PUBLISHED, pageable);
        }

        return articles.map(this::toArticleDto);
    }

    @Transactional(readOnly = true)
    public KnowledgeArticleDto getArticle(UUID articleId) {
        UUID tenantId = getCurrentTenant();
        KnowledgeArticle article = articleRepository.findByIdAndTenantId(articleId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Article not found"));
        return toArticleDto(article);
    }

    @Transactional(readOnly = true)
    public KnowledgeArticleDto getArticleBySlug(String slug) {
        KnowledgeArticle article = articleRepository.findPublishedBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Article not found"));
        return toArticleDto(article);
    }

    @Transactional
    public KnowledgeArticleDto createArticle(CreateKnowledgeArticleRequest request) {
        UUID tenantId = getCurrentTenant();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000, "Tenant not found"));

        KnowledgeCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Category not found"));

        // 取得系統管理員作為預設作者
        User author = userRepository.findById(request.getAuthorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Author not found"));

        // 檢查 slug 唯一性
        if (articleRepository.findByTenantIdAndSlug(tenantId, request.getSlug()).isPresent()) {
            throw new BusinessException(ErrorCode.E_3001, "Article slug already exists for this tenant");
        }

        KnowledgeArticle article = KnowledgeArticle.builder()
                .tenant(tenant)
                .category(category)
                .author(author)
                .title(request.getTitle())
                .slug(request.getSlug())
                .content(request.getContent())
                .excerpt(request.getExcerpt())
                .coverImageUrl(request.getCoverImageUrl())
                .status(ArticleStatus.DRAFT)
                .isPinned(request.getIsPinned() != null ? request.getIsPinned() : false)
                .build();

        article = articleRepository.save(article);
        log.info("Created knowledge article: id={}, title={}, tenantId={}", article.getId(), article.getTitle(), tenantId);

        return toArticleDto(article);
    }

    @Transactional
    public KnowledgeArticleDto updateArticle(UUID articleId, UpdateKnowledgeArticleRequest request) {
        UUID tenantId = getCurrentTenant();
        KnowledgeArticle article = articleRepository.findByIdAndTenantId(articleId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Article not found"));

        if (request.getCategoryId() != null) {
            KnowledgeCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Category not found"));
            article.setCategory(category);
        }

        if (request.getTitle() != null) {
            article.setTitle(request.getTitle());
        }

        if (request.getSlug() != null && !request.getSlug().equals(article.getSlug())) {
            if (articleRepository.findByTenantIdAndSlug(tenantId, request.getSlug()).isPresent()) {
                throw new BusinessException(ErrorCode.E_3001, "Article slug already exists for this tenant");
            }
            article.setSlug(request.getSlug());
        }

        if (request.getContent() != null) {
            article.setContent(request.getContent());
        }

        if (request.getExcerpt() != null) {
            article.setExcerpt(request.getExcerpt());
        }

        if (request.getCoverImageUrl() != null) {
            article.setCoverImageUrl(request.getCoverImageUrl());
        }

        if (request.getStatus() != null) {
            article.setStatus(ArticleStatus.valueOf(request.getStatus()));
            if (request.getStatus().equals("PUBLISHED") && article.getPublishedAt() == null) {
                article.publish();
            }
        }

        if (request.getIsPinned() != null) {
            article.setIsPinned(request.getIsPinned());
        }

        article = articleRepository.save(article);
        log.info("Updated knowledge article: id={}", articleId);

        return toArticleDto(article);
    }

    @Transactional
    public void deleteArticle(UUID articleId) {
        UUID tenantId = getCurrentTenant();
        KnowledgeArticle article = articleRepository.findByIdAndTenantId(articleId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Article not found"));

        articleRepository.delete(article);
        log.info("Deleted knowledge article: id={}", articleId);
    }

    /**
     * 增加文章瀏覽次數（Sprint 106 / DEF-055：改為 DB 原子遞增）
     *
     * <p>不再載入實體後在記憶體 +1 再 {@code save()}——{@code KnowledgeArticle} 沒有
     * {@code @Version}，那種讀後寫在併發下會靜默丟失更新，而熱門文章是以
     * {@code viewCount DESC} 取的，少計會讓排名失真。
     *
     * <p>維持修復前的無租戶範圍語意（本類別其餘方法都用 {@code findByIdAndTenantId}，
     * 只有這裡是 {@code findById}）；該不一致屬權限語意而非併發語意，記錄為 DEF-057。
     */
    @Transactional
    public void incrementViewCount(UUID articleId) {
        int updated = articleRepository.incrementViewCount(articleId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.E_4000, "Article not found");
        }
    }

    // ========== Article Version Control ==========

    @Transactional
    public void createVersionSnapshot(UUID articleId) {
        UUID tenantId = getCurrentTenant();
        KnowledgeArticle article = articleRepository.findByIdAndTenantId(articleId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Article not found"));

        Integer maxVersion = articleVersionRepository.findMaxVersionNumberByArticleId(articleId);
        int newVersion = (maxVersion != null ? maxVersion : 0) + 1;

        ArticleVersion version = ArticleVersion.builder()
                .article(article)
                .versionNumber(newVersion)
                .title(article.getTitle())
                .content(article.getContent())
                .tags(article.getTags() != null ? List.of(article.getTags().split(",")) : null)
                .category(article.getCategory())
                .isPublished(article.getStatus() == ArticleStatus.PUBLISHED)
                .isPinned(article.getIsPinned())
                .sortOrder(article.getSortOrder())
                .tenant(article.getTenant())
                .createdBy(article.getAuthor())
                .build();

        articleVersionRepository.save(version);
        log.info("Created version snapshot for article: articleId={}, version={}", articleId, newVersion);
    }

    @Transactional(readOnly = true)
    public Page<ArticleVersion> getArticleVersions(UUID articleId, int page, int size) {
        UUID tenantId = getCurrentTenant();
        articleRepository.findByIdAndTenantId(articleId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Article not found"));

        Pageable pageable = PageRequest.of(page, Math.min(size, 50), Sort.by(Sort.Direction.DESC, "versionNumber"));
        return articleVersionRepository.findByArticleIdAndTenantId(articleId, tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public ArticleVersion getArticleVersion(UUID articleId, Integer versionNumber) {
        UUID tenantId = getCurrentTenant();
        articleRepository.findByIdAndTenantId(articleId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Article not found"));

        return articleVersionRepository.findByArticleIdAndVersionNumber(articleId, versionNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Version not found"));
    }

    @Transactional
    public KnowledgeArticleDto restoreVersion(UUID articleId, Integer versionNumber) {
        UUID tenantId = getCurrentTenant();
        KnowledgeArticle article = articleRepository.findByIdAndTenantId(articleId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Article not found"));

        ArticleVersion version = articleVersionRepository.findByArticleIdAndVersionNumber(articleId, versionNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Version not found"));

        // 建立目前版本的快照
        createVersionSnapshot(articleId);

        // 恢復到指定版本
        article.setTitle(version.getTitle());
        article.setContent(version.getContent());
        article.setCategory(version.getCategory());
        article.setIsPinned(version.getIsPinned());
        article.setSortOrder(version.getSortOrder());

        article = articleRepository.save(article);
        log.info("Restored article to version: articleId={}, version={}", articleId, versionNumber);

        return toArticleDto(article);
    }

    @Transactional
    public KnowledgeArticleDto schedulePublish(UUID articleId, java.time.Instant scheduledTime) {
        UUID tenantId = getCurrentTenant();
        KnowledgeArticle article = articleRepository.findByIdAndTenantId(articleId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Article not found"));

        article.setScheduledPublishAt(scheduledTime);
        article = articleRepository.save(article);

        log.info("Scheduled article for publish: articleId={}, scheduledTime={}", articleId, scheduledTime);
        return toArticleDto(article);
    }

    // ========== Helper Methods ==========

    private KnowledgeCategoryDto toCategoryDto(KnowledgeCategory category) {
        return KnowledgeCategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .icon(category.getIcon())
                .sortOrder(category.getSortOrder())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    private KnowledgeArticleDto toArticleDto(KnowledgeArticle article) {
        KnowledgeArticleDto.KnowledgeArticleDtoBuilder builder = KnowledgeArticleDto.builder()
                .id(article.getId())
                .tenantId(article.getTenantId())
                .title(article.getTitle())
                .slug(article.getSlug())
                .content(article.getContent())
                .excerpt(article.getExcerpt())
                .coverImageUrl(article.getCoverImageUrl())
                .status(article.getStatus().name())
                .viewCount(article.getViewCount())
                .isPinned(article.getIsPinned())
                .publishedAt(article.getPublishedAt())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt());

        if (article.getCategory() != null) {
            builder.categoryId(article.getCategory().getId())
                   .categoryName(article.getCategory().getName());
        }

        if (article.getAuthor() != null) {
            builder.authorId(article.getAuthor().getId())
                   .authorName(article.getAuthor().getFullName());
        }

        return builder.build();
    }
}