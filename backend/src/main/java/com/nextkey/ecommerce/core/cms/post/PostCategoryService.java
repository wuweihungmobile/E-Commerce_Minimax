package com.nextkey.ecommerce.core.cms.post;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.M15Dto;
import com.nextkey.ecommerce.domain.model.cms.post.Post;
import com.nextkey.ecommerce.domain.model.cms.post.PostCategory;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.cms.PostCategoryRepository;
import com.nextkey.ecommerce.domain.repository.cms.PostRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * M15 CMS PostCategory Service
 * 貼文分類服務
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostCategoryService {

    private final PostCategoryRepository postCategoryRepository;
    private final PostRepository postRepository;
    private final TenantRepository tenantRepository;

    /**
     * 建立分類
     */
    @Transactional
    public M15Dto.CategoryResponse createCategory(UUID tenantId, M15Dto.CreateCategoryRequest request) {
        // 驗證 Tenant 存在
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));

        // 驗證 slug 唯一性
        // DEF-388：改用 E_8014（原誤用 E_4102「找不到分類」，與 slug 重複衝突語意無關）。
        String slug = generateSlug(request.getName());
        if (postCategoryRepository.existsByTenantIdAndSlug(tenantId, slug)) {
            throw new BusinessException(ErrorCode.E_8014, "Category slug already exists");
        }

        PostCategory category = PostCategory.builder()
                .tenant(tenant)
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .isActive(true)
                .build();

        category = postCategoryRepository.save(category);
        return M15Dto.CategoryResponse.from(category);
    }

    /**
     * 更新分類
     */
    @Transactional
    public M15Dto.CategoryResponse updateCategory(UUID categoryId, UUID tenantId, M15Dto.UpdateCategoryRequest request) {
        PostCategory category = getCategoryOrThrow(categoryId);

        // 驗證 Tenant 擁有權
        if (!category.getTenant().getId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.E_4031);
        }

        if (request.getName() != null) {
            category.setName(request.getName());
            // 更新 slug
            category.setSlug(generateUniqueSlug(request.getName(), categoryId, tenantId));
        }
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }

        category = postCategoryRepository.save(category);
        return M15Dto.CategoryResponse.from(category);
    }

    /**
     * 刪除分類（無貼文關聯時）
     */
    @Transactional
    public void deleteCategory(final UUID categoryId, final UUID tenantId) {
        PostCategory category = getCategoryOrThrow(categoryId);

        // 驗證 Tenant 擁有權
        if (!category.getTenant().getId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.E_4031);
        }

        // 檢查是否有貼文關聯
        Page<Post> posts = postRepository.findByTenantIdAndCategoryId(
                tenantId, categoryId, PageRequest.of(0, 1));

        // DEF-388：改用 E_8015（原誤用 E_4102「找不到分類」，與「刪除受阻」語意無關）。
        if (posts.getTotalElements() > 0) {
            throw new BusinessException(ErrorCode.E_8015, "Cannot delete category with associated posts");
        }

        postCategoryRepository.delete(category);
    }

    /**
     * 取得分類列表
     */
    @Transactional(readOnly = true)
    public M15Dto.CategoryListResponse getCategories(UUID tenantId) {
        List<PostCategory> categories = postCategoryRepository.findByTenantIdOrderBySortOrderAsc(tenantId);

        List<M15Dto.CategoryResponse> responses = categories.stream()
                .map(M15Dto.CategoryResponse::from)
                .toList();

        return M15Dto.CategoryListResponse.builder()
                .categories(responses)
                .totalCount(responses.size())
                .build();
    }

    /**
     * 前台：取得分類列表（公開，僅回傳啟用中分類，DEF-358(c)，Sprint 256）
     *
     * <p>與 {@link #getCategories} 的差異：Dashboard 管理視角需要看到停用中的分類才能重新啟用，
     * 前台訪客不應看到店主已停用、刻意隱藏的分類。
     */
    @Transactional(readOnly = true)
    public M15Dto.CategoryListResponse getActiveCategories(UUID tenantId) {
        List<PostCategory> categories = postCategoryRepository.findByTenantIdAndIsActiveTrue(tenantId);

        List<M15Dto.CategoryResponse> responses = categories.stream()
                .map(M15Dto.CategoryResponse::from)
                .toList();

        return M15Dto.CategoryListResponse.builder()
                .categories(responses)
                .totalCount(responses.size())
                .build();
    }

    /**
     * 取得分類詳情
     */
    @Transactional(readOnly = true)
    public M15Dto.CategoryResponse getCategory(UUID categoryId, UUID tenantId) {
        PostCategory category = getCategoryOrThrow(categoryId);

        if (!category.getTenant().getId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.E_4031);
        }

        return M15Dto.CategoryResponse.from(category);
    }

    // ========== Helper Methods ==========

    private PostCategory getCategoryOrThrow(final UUID categoryId) {
        return postCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_4102));
    }

    private String generateSlug(final String name) {
        if (name == null || name.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s\\-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private String generateUniqueSlug(final String name, final UUID excludeId, final UUID tenantId) {
        String slug = generateSlug(name);
        if (postCategoryRepository.existsByTenantIdAndSlugAndIdNot(tenantId, slug, excludeId)) {
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return slug;
    }
}