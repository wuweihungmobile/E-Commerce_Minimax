package com.nextkey.ecommerce.core.faq;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.faq.CreateFaqArticleRequest;
import com.nextkey.ecommerce.api.dto.faq.CreateFaqCategoryRequest;
import com.nextkey.ecommerce.api.dto.faq.FaqArticleDto;
import com.nextkey.ecommerce.api.dto.faq.FaqCategoryDto;
import com.nextkey.ecommerce.api.dto.faq.UpdateFaqArticleRequest;
import com.nextkey.ecommerce.api.dto.faq.UpdateFaqCategoryRequest;
import com.nextkey.ecommerce.domain.model.faq.FaqArticle;
import com.nextkey.ecommerce.domain.model.faq.FaqCategory;
import com.nextkey.ecommerce.domain.repository.faq.FaqArticleRepository;
import com.nextkey.ecommerce.domain.repository.faq.FaqCategoryRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import static com.nextkey.ecommerce.shared.tenant.TenantContext.getCurrentTenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaqService {

    private final FaqArticleRepository articleRepository;
    private final FaqCategoryRepository categoryRepository;

    // ========== Category Operations ==========

    @Transactional(readOnly = true)
    public List<FaqCategoryDto> getCategories() {
        UUID tenantId = getCurrentTenant();
        List<FaqCategory> categories = categoryRepository.findByTenantIdOrderBySortOrderAsc(tenantId);
        return categories.stream()
                .map(this::toCategoryDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FaqCategoryDto getCategory(UUID categoryId) {
        UUID tenantId = getCurrentTenant();
        FaqCategory category = categoryRepository.findByIdAndTenantId(categoryId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Category not found"));
        return toCategoryDto(category);
    }

    @Transactional(readOnly = true)
    public FaqCategoryDto getCategoryBySlug(String slug) {
        UUID tenantId = getCurrentTenant();
        FaqCategory category = categoryRepository.findByTenantIdAndSlug(tenantId, slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Category not found"));
        return toCategoryDto(category);
    }

    @Transactional
    public FaqCategoryDto createCategory(CreateFaqCategoryRequest request) {
        UUID tenantId = getCurrentTenant();
        if (categoryRepository.existsByTenantIdAndSlug(tenantId, request.getSlug())) {
            throw new BusinessException(ErrorCode.E_3001, "Category slug already exists for this tenant");
        }

        FaqCategory category = FaqCategory.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .slug(request.getSlug())
                .description(request.getDescription())
                .icon(request.getIcon())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();

        category = categoryRepository.save(category);
        log.info("Created FAQ category: id={}, name={}, tenantId={}", category.getId(), category.getName(), tenantId);

        return toCategoryDto(category);
    }

    @Transactional
    public FaqCategoryDto updateCategory(UUID categoryId, UpdateFaqCategoryRequest request) {
        UUID tenantId = getCurrentTenant();
        FaqCategory category = categoryRepository.findByIdAndTenantId(categoryId, tenantId)
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
        log.info("Updated FAQ category: id={}", categoryId);

        return toCategoryDto(category);
    }

    @Transactional
    public void deleteCategory(UUID categoryId) {
        UUID tenantId = getCurrentTenant();
        FaqCategory category = categoryRepository.findByIdAndTenantId(categoryId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Category not found"));

        Page<FaqArticle> articles = articleRepository.findByCategoryId(categoryId, PageRequest.of(0, 1));
        if (articles.hasContent()) {
            throw new BusinessException(ErrorCode.E_3001, "Cannot delete category with articles");
        }

        categoryRepository.delete(category);
        log.info("Deleted FAQ category: id={}, tenantId={}", categoryId, tenantId);
    }

    // ========== Article Operations ==========

    @Transactional(readOnly = true)
    public Page<FaqArticleDto> getArticles(int page, int size, UUID categoryId, String keyword) {
        UUID tenantId = getCurrentTenant();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<FaqArticle> articles;
        if (keyword != null && !keyword.isBlank()) {
            articles = articleRepository.searchByTenantIdAndKeyword(tenantId, keyword.trim(), pageable);
        } else if (categoryId != null) {
            articles = articleRepository.findByTenantIdAndCategoryIdAndIsPublishedTrue(tenantId, categoryId, pageable);
        } else {
            articles = articleRepository.findByTenantIdAndIsPublishedTrue(tenantId, pageable);
        }

        return articles.map(this::toArticleDto);
    }

    /**
     * 取得置頂文章列表
     * Phase 2-C: FAQ 置頂排序功能
     */
    @Transactional(readOnly = true)
    public List<FaqArticleDto> getPinnedArticles() {
        UUID tenantId = getCurrentTenant();
        List<FaqArticle> pinnedArticles = articleRepository.findByTenantIdAndIsPinnedTrueOrderBySortOrderAsc(tenantId);
        return pinnedArticles.stream()
                .map(this::toArticleDto)
                .collect(Collectors.toList());
    }

    /**
     * 搜尋文章並高亮關鍵字
     * Phase 2-C: 關鍵字高亮功能
     * 返回結果包含高亮後的 question 和 answer
     */
    @Transactional(readOnly = true)
    public Page<FaqArticleDto> searchArticlesWithHighlight(
            int page, int size, String keyword, String highlightPrefix, String highlightSuffix) {

        UUID tenantId = getCurrentTenant();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));

        if (keyword == null || keyword.isBlank()) {
            return articleRepository.findByTenantIdAndIsPublishedTrue(tenantId, pageable)
                    .map(this::toArticleDto);
        }

        Page<FaqArticle> articles = articleRepository.searchByTenantIdAndKeyword(
                tenantId, keyword.trim(), pageable);

        return articles.map(article -> {
            FaqArticleDto dto = toArticleDto(article);

            // 高亮關鍵字
            String prefix = highlightPrefix != null ? highlightPrefix : "<mark>";
            String suffix = highlightSuffix != null ? highlightSuffix : "</mark>";
            String lowerKeyword = keyword.toLowerCase();

            // 高亮 question
            String highlightedQuestion = highlightKeyword(dto.getQuestion(), lowerKeyword, prefix, suffix);
            dto.setHighlightedQuestion(highlightedQuestion);

            // 高亮 answer
            String highlightedAnswer = highlightKeyword(dto.getAnswer(), lowerKeyword, prefix, suffix);
            dto.setHighlightedAnswer(highlightedAnswer);

            return dto;
        });
    }

    /**
     * 取得分類統計
     * Phase 2-C: FAQ 分類統計 API
     */
    @Transactional(readOnly = true)
    public List<CategoryStatsDto> getCategoryStats() {
        UUID tenantId = getCurrentTenant();
        List<FaqCategory> categories = categoryRepository.findByTenantIdOrderBySortOrderAsc(tenantId);

        return categories.stream().map(category -> {
            long articleCount = articleRepository.countByTenantIdAndCategoryId(
                    tenantId, category.getId());
            long publishedCount = articleRepository.countByTenantIdAndCategoryIdAndIsPublishedTrue(
                    tenantId, category.getId());

            return new CategoryStatsDto(
                    category.getId(),
                    category.getName(),
                    category.getSlug(),
                    articleCount,
                    publishedCount
            );
        }).collect(Collectors.toList());
    }

    /**
     * 高亮關鍵字
     */
    private String highlightKeyword(String text, String keyword, String prefix, String suffix) {
        if (text == null || keyword == null) {
            return text;
        }

        String regex = "(?i)(" + java.util.regex.Pattern.quote(keyword) + ")";
        return text.replaceAll(regex, prefix + "$1" + suffix);
    }

    @Transactional(readOnly = true)
    public FaqArticleDto getArticle(UUID articleId) {
        UUID tenantId = getCurrentTenant();
        FaqArticle article = articleRepository.findByIdAndTenantId(articleId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Article not found"));
        return toArticleDto(article);
    }

    @Transactional(readOnly = true)
    public FaqArticleDto getArticleBySlug(String slug) {
        UUID tenantId = getCurrentTenant();
        FaqArticle article = articleRepository.findByTenantIdAndSlug(tenantId, slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Article not found"));
        return toArticleDto(article);
    }

    @Transactional
    public FaqArticleDto createArticle(CreateFaqArticleRequest request) {
        UUID tenantId = getCurrentTenant();
        FaqCategory category = categoryRepository.findByIdAndTenantId(request.getCategoryId(), tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Category not found"));

        if (articleRepository.existsByTenantIdAndSlug(tenantId, request.getSlug())) {
            throw new BusinessException(ErrorCode.E_3001, "Article slug already exists for this tenant");
        }

        FaqArticle article = FaqArticle.builder()
                .tenantId(tenantId)
                .category(category)
                .question(request.getQuestion())
                .answer(request.getAnswer())
                .slug(request.getSlug())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .isPinned(request.getIsPinned() != null ? request.getIsPinned() : false)
                .build();

        article = articleRepository.save(article);
        log.info("Created FAQ article: id={}, question={}, tenantId={}", article.getId(), article.getQuestion(), tenantId);

        return toArticleDto(article);
    }

    @Transactional
    public FaqArticleDto updateArticle(UUID articleId, UpdateFaqArticleRequest request) {
        UUID tenantId = getCurrentTenant();
        FaqArticle article = articleRepository.findByIdAndTenantId(articleId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Article not found"));

        if (request.getCategoryId() != null) {
            FaqCategory category = categoryRepository.findByIdAndTenantId(request.getCategoryId(), tenantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Category not found"));
            article.setCategory(category);
        }

        if (request.getQuestion() != null) {
            article.setQuestion(request.getQuestion());
        }

        if (request.getAnswer() != null) {
            article.setAnswer(request.getAnswer());
        }

        if (request.getSlug() != null && !request.getSlug().equals(article.getSlug())) {
            if (articleRepository.existsByTenantIdAndSlug(tenantId, request.getSlug())) {
                throw new BusinessException(ErrorCode.E_3001, "Article slug already exists for this tenant");
            }
            article.setSlug(request.getSlug());
        }

        if (request.getSortOrder() != null) {
            article.setSortOrder(request.getSortOrder());
        }

        if (request.getIsPinned() != null) {
            article.setIsPinned(request.getIsPinned());
        }

        if (request.getIsPublished() != null) {
            article.setIsPublished(request.getIsPublished());
            if (request.getIsPublished() && article.getPublishedAt() == null) {
                article.publish();
            }
        }

        article = articleRepository.save(article);
        log.info("Updated FAQ article: id={}", articleId);

        return toArticleDto(article);
    }

    @Transactional
    public void deleteArticle(UUID articleId) {
        UUID tenantId = getCurrentTenant();
        FaqArticle article = articleRepository.findByIdAndTenantId(articleId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Article not found"));

        articleRepository.delete(article);
        log.info("Deleted FAQ article: id={}, tenantId={}", articleId, tenantId);
    }

    @Transactional
    public void incrementViewCount(UUID articleId) {
        UUID tenantId = getCurrentTenant();
        FaqArticle article = articleRepository.findByIdAndTenantId(articleId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4000, "Article not found"));
        article.incrementViewCount();
        articleRepository.save(article);
    }

    // ========== Helper Methods ==========

    private FaqCategoryDto toCategoryDto(FaqCategory category) {
        return FaqCategoryDto.builder()
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

    private FaqArticleDto toArticleDto(FaqArticle article) {
        FaqArticleDto.FaqArticleDtoBuilder builder = FaqArticleDto.builder()
                .id(article.getId())
                .question(article.getQuestion())
                .answer(article.getAnswer())
                .slug(article.getSlug())
                .sortOrder(article.getSortOrder())
                .viewCount(article.getViewCount())
                .isPinned(article.getIsPinned())
                .isPublished(article.getIsPublished())
                .publishedAt(article.getPublishedAt())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt());

        if (article.getCategory() != null) {
            builder.categoryId(article.getCategory().getId())
                   .categoryName(article.getCategory().getName());
        }

        return builder.build();
    }

    /**
     * 分類統計 DTO
     */
    public record CategoryStatsDto(
            UUID categoryId,
            String categoryName,
            String categorySlug,
            long totalArticles,
            long publishedArticles
    ) {}
}