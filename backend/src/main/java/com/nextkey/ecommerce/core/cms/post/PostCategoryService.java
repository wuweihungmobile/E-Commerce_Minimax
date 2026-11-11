package com.nextkey.ecommerce.core.cms.post;

import java.util.List;
import java.util.Locale;
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

        // 名稱不可與同店既有分類相同（Sprint 338，DEF-492）
        requireNameNotTaken(tenantId, request.getName(), null);

        // 網址代稱：名稱不同、只差中文的分類（『旅遊筆記2026』『咖啡手帖2026』）會產生同一個代稱『2026』，撞到就接隨機後綴（比照 updateCategory）。
        // 原本（DEF-388）直接拋 E_8014——店主看到的是兩個名稱明明不同卻建不起來（DEF-492）。代稱是系統產生的，不該要店主負責避開。
        String slug = generateUniqueSlug(request.getName(), null, tenantId);

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
            // 只有名稱「真的改了」才換 slug（Sprint 334）：全中文名稱的 slug 是隨機後備，無條件重算會每存一次就換
            boolean nameChanged = !request.getName().equals(category.getName());
            if (nameChanged) {
                // 改名才檢查同名（DEF-492），且排除分類自己（只改自己名稱的大小寫不算與自己同名）。名稱沒改不檢查：
                // 規則上線前已存在的同名分類（全中文同名原本能建立）不能因此連描述、排序都改不了。
                requireNameNotTaken(tenantId, request.getName(), categoryId);
            }
            category.setName(request.getName());
            if (nameChanged) {
                category.setSlug(generateUniqueSlug(request.getName(), categoryId, tenantId));
            }
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
     *
     * <p>只供應 ACTIVE 店鋪（Sprint 337，DEF-485）：店鋪不是 ACTIVE 或不存在 → 404 E-2000，
     * 與 {@code PostService#getPublishedPosts}、{@code PostService#getPublishedPostBySlug} 同一個規則（這是同一個模組的第三個公開入口）。
     */
    @Transactional(readOnly = true)
    public M15Dto.CategoryListResponse getActiveCategories(UUID tenantId) {
        tenantRepository.findByIdAndStatus(tenantId, Tenant.TenantStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));
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
        String slug = name.toLowerCase()
                .replaceAll("[^a-z0-9\\s\\-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        // DEF-480（Sprint 334）：全中文名稱清完什麼都不剩——空字串 slug 在 (tenant_id, slug) 唯一約束下，
        // 同店第二個全中文分類會被當成「slug 重複」(E-8014) 而建立失敗。改用 category-<8 碼> 後備。
        return slug.isEmpty() ? "category-" + UUID.randomUUID().toString().substring(0, 8) : slug;
    }

    /**
     * @param excludeId 更新時排除分類自己；新建傳 null（沒有自己要排除）。不能把 null 傳給 existsByTenantIdAndSlugAndIdNot：
     *                  SQL 的 {@code id != NULL} 恆為未知，查詢永遠回 false，代稱撞到也不會加後綴，建立時才由唯一約束炸成 500。
     */
    private String generateUniqueSlug(final String name, final UUID excludeId, final UUID tenantId) {
        String slug = generateSlug(name);
        boolean taken = excludeId == null
                ? postCategoryRepository.existsByTenantIdAndSlug(tenantId, slug)
                : postCategoryRepository.existsByTenantIdAndSlugAndIdNot(tenantId, slug, excludeId);
        if (taken) {
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return slug;
    }

    /**
     * 同店不可有同名分類（Sprint 338，DEF-492，使用者以互動選項決定「擋下並提示」）。「同名」＝忽略大小寫與前後空白（含全形空白 U+3000）。
     *
     * <p>在 Java 端比對、不用 SQL：不依賴資料庫的語系設定（lower() 對非 ASCII 字元的行為隨資料庫的 ctype 而變），
     * 而且同店的分類只有幾個到幾十個，查出來比對的成本可以忽略。
     *
     * <p>不加資料庫唯一索引：這項規則上線前，全中文同名的分類本來就能建立，已經存在的同名資料會讓建索引的遷移失敗，
     * 要先修資料才能上；而兩個請求同時建立同名分類的後果只是多一個同名分類（店主可自行改名或刪除），不值得為它動既有資料。
     *
     * @param excludeId 改名時排除分類自己；新建傳 null
     */
    private void requireNameNotTaken(final UUID tenantId, final String name, final UUID excludeId) {
        String wanted = normalizeName(name);
        boolean taken = postCategoryRepository.findByTenantIdOrderBySortOrderAsc(tenantId).stream()
                .filter(existing -> excludeId == null || !excludeId.equals(existing.getId()))
                .anyMatch(existing -> normalizeName(existing.getName()).equals(wanted));
        if (taken) {
            throw new BusinessException(ErrorCode.E_8021, "Category name already exists");
        }
    }

    private String normalizeName(final String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }
}