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
}