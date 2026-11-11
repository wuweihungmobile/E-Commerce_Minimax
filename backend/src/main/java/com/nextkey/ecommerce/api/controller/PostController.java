package com.nextkey.ecommerce.api.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.M15Dto;
import com.nextkey.ecommerce.core.cms.listing.ListingCardService;
import com.nextkey.ecommerce.core.cms.post.PostCategoryService;
import com.nextkey.ecommerce.core.cms.post.PostService;
import com.nextkey.ecommerce.domain.model.cms.post.Post;
import com.nextkey.ecommerce.domain.model.tenant.TenantFeatureToggle;
import com.nextkey.ecommerce.domain.repository.TenantFeatureToggleRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;



/**
 * M15 CMS Post REST API
 * 貼文 CRUD、分類管理
 * （媒體庫：Sprint 334 起只剩 M18 的 /v2/media/*；原本掛在這裡的 /v2/dashboard/media/* 五個端點已移除，DEF-474）
 */
@Slf4j
@RestController
@RequestMapping("/v2")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final PostCategoryService postCategoryService;
    private final ListingCardService listingCardService;
    private final TenantFeatureToggleRepository featureToggleRepository;

    // ========== Post CRUD ==========

    /**
     * 建立貼文
     * POST /api/v2/dashboard/posts
     */
    @PostMapping("/dashboard/posts")
    @PreAuthorize("hasAnyRole('STORE_OWNER', 'STORE_STAFF', 'SELLER', 'HOST')")
    public ResponseEntity<ApiResponse<M15Dto.PostResponse>> createPost(
            @Valid @RequestBody M15Dto.CreatePostRequest request) {

        UUID tenantId = TenantContext.getCurrentTenant();
        UUID authorId = TenantContext.getCurrentUser();

        // 檢查 CMS_ENABLED feature toggle
        checkFeatureToggle(tenantId, "CMS_ENABLED");

        M15Dto.PostResponse response = postService.createPost(tenantId, authorId, request);
        return ResponseEntity.ok(ApiResponse.success("Post created", response));
    }

    /**
     * 更新貼文
     * PUT /api/v2/dashboard/posts/:id
     */
    @PutMapping("/dashboard/posts/{postId}")
    @PreAuthorize("hasAnyRole('STORE_OWNER', 'STORE_STAFF', 'SELLER', 'HOST')")
    public ResponseEntity<ApiResponse<M15Dto.PostResponse>> updatePost(
            @PathVariable UUID postId,
            @Valid @RequestBody M15Dto.UpdatePostRequest request) {

        UUID tenantId = TenantContext.getCurrentTenant();

        // 檢查 CMS_ENABLED feature toggle
        checkFeatureToggle(tenantId, "CMS_ENABLED");

        M15Dto.PostResponse response = postService.updatePost(postId, tenantId, request);
        return ResponseEntity.ok(ApiResponse.success("Post updated", response));
    }

    /**
     * 取得貼文列表
     * GET /api/v2/dashboard/posts
     */
    @GetMapping("/dashboard/posts")
    @PreAuthorize("hasAnyRole('STORE_OWNER', 'STORE_STAFF', 'SELLER', 'HOST')")
    public ResponseEntity<ApiResponse<M15Dto.PostListResponse>> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {

        UUID tenantId = TenantContext.getCurrentTenant();

        // 檢查 CMS_ENABLED feature toggle
        checkFeatureToggle(tenantId, "CMS_ENABLED");

        Post.PostStatus postStatus;
        try {
            postStatus = status != null ? Post.PostStatus.valueOf(status.toUpperCase()) : null;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.E_9000, "Invalid status: " + status);
        }
        M15Dto.PostListResponse response = postService.getPosts(tenantId, page, size, postStatus);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 取得貼文詳情
     * GET /api/v2/dashboard/posts/:id
     */
    @GetMapping("/dashboard/posts/{postId}")
    @PreAuthorize("hasAnyRole('STORE_OWNER', 'STORE_STAFF', 'SELLER', 'HOST')")
    public ResponseEntity<ApiResponse<M15Dto.PostResponse>> getPost(
            @PathVariable UUID postId) {

        UUID tenantId = TenantContext.getCurrentTenant();

        // 檢查 CMS_ENABLED feature toggle
        checkFeatureToggle(tenantId, "CMS_ENABLED");

        M15Dto.PostResponse response = postService.getPost(postId, tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 刪除貼文
     * DELETE /api/v2/dashboard/posts/:id
     */
    @DeleteMapping("/dashboard/posts/{postId}")
    @PreAuthorize("hasAnyRole('STORE_OWNER', 'STORE_STAFF', 'SELLER', 'HOST')")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable UUID postId) {

        UUID tenantId = TenantContext.getCurrentTenant();

        // 檢查 CMS_ENABLED feature toggle
        checkFeatureToggle(tenantId, "CMS_ENABLED");

        postService.deletePost(postId, tenantId);
        return ResponseEntity.ok(ApiResponse.success("Post deleted", null));
    }

    /**
     * 發布貼文
     * POST /api/v2/dashboard/posts/:id/publish
     */
    @PostMapping("/dashboard/posts/{postId}/publish")
    @PreAuthorize("hasAnyRole('STORE_OWNER', 'STORE_STAFF', 'SELLER', 'HOST')")
    public ResponseEntity<ApiResponse<M15Dto.PostResponse>> publishPost(
            @PathVariable UUID postId) {

        UUID tenantId = TenantContext.getCurrentTenant();

        // 檢查 CMS_ENABLED feature toggle
        checkFeatureToggle(tenantId, "CMS_ENABLED");

        M15Dto.PostResponse response = postService.publishPost(postId, tenantId);
        return ResponseEntity.ok(ApiResponse.success("Post published", response));
    }

    /**
     * 下架貼文
     * DELETE /api/v2/dashboard/posts/:id/publish
     */
    @DeleteMapping("/dashboard/posts/{postId}/publish")
    @PreAuthorize("hasAnyRole('STORE_OWNER', 'STORE_STAFF', 'SELLER', 'HOST')")
    public ResponseEntity<ApiResponse<M15Dto.PostResponse>> unpublishPost(
            @PathVariable UUID postId) {

        UUID tenantId = TenantContext.getCurrentTenant();

        // 檢查 CMS_ENABLED feature toggle
        checkFeatureToggle(tenantId, "CMS_ENABLED");

        M15Dto.PostResponse response = postService.unpublishPost(postId, tenantId);
        return ResponseEntity.ok(ApiResponse.success("Post unpublished", response));
    }

    // ========== Public APIs (前台) ==========
    // Sprint 337（DEF-485）：下面三個匿名端點（貼文列表、單篇、公開分類）只供應 ACTIVE 店鋪；店鋪不是 ACTIVE 或不存在 → 404 E-2000，
    // 外人分不出是哪一種。規則在 PostService／PostCategoryService（讀店鋪現在的狀態），這裡不重複判斷。

    /**
     * 取得已發布貼文列表（公開）
     * GET /api/v2/posts
     */
    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<M15Dto.PostListResponse>> getPublishedPosts(
            @RequestParam UUID tenantId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        M15Dto.PostListResponse response = postService.getPublishedPosts(tenantId, categoryId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 跨店鋪的公開貼文列表（匿名可讀；Sprint 339，DEF-495，使用者選了『做成跨店鋪部落格』）
     * GET /api/v2/public/posts
     *
     * <p>所有已發布、且所屬店鋪是 ACTIVE 的貼文，發布時間新到舊，只回摘要（不含內文與作者）。掛在 SecurityConfig 既有的
     * {@code /v2/public/**} permitAll 規則下（與 {@link PublicListingController} 同一慣例），不動 {@code /v2/posts} 本身的規則。
     * 單店鋪的列表仍是 {@code GET /v2/posts?tenantId=}。
     */
    @GetMapping("/public/posts")
    public ResponseEntity<ApiResponse<M15Dto.PublicPostFeedResponse>> getPublicPostFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        M15Dto.PublicPostFeedResponse response = postService.getPublicPostFeed(page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 依 Slug 取得已發布貼文（公開）
     * GET /api/v2/posts/:slug
     *
     * <p>{@code countView} 預設 true（每次讀取瀏覽數 +1）；Next 伺服器端組頁面標題時傳 false，
     * 才不會讓一次瀏覽被算兩次（Sprint 336，DEF-483）。
     */
    @GetMapping("/posts/{slug}")
    public ResponseEntity<ApiResponse<M15Dto.PostResponse>> getPublishedPostBySlug(
            @PathVariable String slug,
            @RequestParam UUID tenantId,
            @RequestParam(defaultValue = "true") boolean countView) {

        M15Dto.PostResponse response = postService.getPublishedPostBySlug(slug, tenantId, countView);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 取得分類列表（公開，僅回傳啟用中分類）
     * GET /api/v2/posts/categories
     * PRD §9.15、DEF-358(c)（Sprint 256）
     */
    @GetMapping("/posts/categories")
    public ResponseEntity<ApiResponse<M15Dto.CategoryListResponse>> getPublicCategories(
            @RequestParam UUID tenantId) {

        M15Dto.CategoryListResponse response = postCategoryService.getActiveCategories(tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ========== Listing Card API ==========

    /**
     * 取得嵌入卡片資訊
     * GET /api/v2/listings/:id/card
     */
    @GetMapping("/listings/{listingId}/card")
    public ResponseEntity<ApiResponse<M15Dto.ListingCardResponse>> getListingCard(
            @PathVariable UUID listingId) {

        M15Dto.ListingCardResponse response = listingCardService.getListingCard(listingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ========== Category APIs ==========

    /**
     * 取得分類列表
     * GET /api/v2/dashboard/post-categories
     */
    @GetMapping("/dashboard/post-categories")
    @PreAuthorize("hasAnyRole('STORE_OWNER', 'STORE_STAFF', 'SELLER', 'HOST')")
    public ResponseEntity<ApiResponse<M15Dto.CategoryListResponse>> getCategories() {

        UUID tenantId = TenantContext.getCurrentTenant();

        // 檢查 CMS_ENABLED feature toggle
        checkFeatureToggle(tenantId, "CMS_ENABLED");

        M15Dto.CategoryListResponse response = postCategoryService.getCategories(tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 建立分類
     * POST /api/v2/dashboard/post-categories
     */
    @PostMapping("/dashboard/post-categories")
    @PreAuthorize("hasAnyRole('STORE_OWNER', 'STORE_STAFF', 'SELLER', 'HOST')")
    public ResponseEntity<ApiResponse<M15Dto.CategoryResponse>> createCategory(
            @Valid @RequestBody M15Dto.CreateCategoryRequest request) {

        UUID tenantId = TenantContext.getCurrentTenant();

        // 檢查 CMS_ENABLED feature toggle
        checkFeatureToggle(tenantId, "CMS_ENABLED");

        M15Dto.CategoryResponse response = postCategoryService.createCategory(tenantId, request);
        return ResponseEntity.ok(ApiResponse.success("Category created", response));
    }

    /**
     * 更新分類
     * PUT /api/v2/dashboard/post-categories/:id
     */
    @PutMapping("/dashboard/post-categories/{categoryId}")
    @PreAuthorize("hasAnyRole('STORE_OWNER', 'STORE_STAFF', 'SELLER', 'HOST')")
    public ResponseEntity<ApiResponse<M15Dto.CategoryResponse>> updateCategory(
            @PathVariable UUID categoryId,
            @Valid @RequestBody M15Dto.UpdateCategoryRequest request) {

        UUID tenantId = TenantContext.getCurrentTenant();

        // 檢查 CMS_ENABLED feature toggle
        checkFeatureToggle(tenantId, "CMS_ENABLED");

        M15Dto.CategoryResponse response = postCategoryService.updateCategory(categoryId, tenantId, request);
        return ResponseEntity.ok(ApiResponse.success("Category updated", response));
    }

    /**
     * 刪除分類
     * DELETE /api/v2/dashboard/post-categories/:id
     */
    @DeleteMapping("/dashboard/post-categories/{categoryId}")
    @PreAuthorize("hasAnyRole('STORE_OWNER', 'STORE_STAFF', 'SELLER', 'HOST')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @PathVariable UUID categoryId) {

        UUID tenantId = TenantContext.getCurrentTenant();

        // 檢查 CMS_ENABLED feature toggle
        checkFeatureToggle(tenantId, "CMS_ENABLED");

        postCategoryService.deleteCategory(categoryId, tenantId);
        return ResponseEntity.ok(ApiResponse.success("Category deleted", null));
    }

    // ========== Admin APIs ==========

    /**
     * Admin: 取得所有店鋪的貼文列表
     * GET /api/v2/admin/posts
     */
    @GetMapping("/admin/posts")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<M15Dto.PostListResponse>> getAllPosts(
            @RequestParam UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        M15Dto.PostListResponse response = postService.getPosts(tenantId, page, size, null);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Admin: 下架店鋪貼文
     * DELETE /api/v2/admin/posts/:id/unpublish
     */
    @DeleteMapping("/admin/posts/{postId}/unpublish")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<M15Dto.PostResponse>> adminUnpublishPost(
            @PathVariable UUID postId,
            @RequestParam UUID tenantId) {

        // Admin 可以操作任何店鋪的貼文，不驗證 tenant ownership
        M15Dto.PostResponse response = postService.unpublishPost(postId, tenantId);
        return ResponseEntity.ok(ApiResponse.success("Post unpublished by admin", response));
    }

    /**
     * Admin: 刪除店鋪貼文
     * DELETE /api/v2/admin/posts/:id
     */
    @DeleteMapping("/admin/posts/{postId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> adminDeletePost(
            @PathVariable UUID postId,
            @RequestParam UUID tenantId) {

        // Admin 可以操作任何店鋪的貼文
        postService.deletePost(postId, tenantId);
        return ResponseEntity.ok(ApiResponse.success("Post deleted by admin", null));
    }

    // ========== Helper Methods ==========

    /**
     * 檢查 Feature Toggle
     */
    private void checkFeatureToggle(UUID tenantId, String featureKey) {
        var toggleOpt = featureToggleRepository.findByTenantIdAndFeatureKey(tenantId, featureKey);
        if (toggleOpt.isPresent()) {
            TenantFeatureToggle toggle = toggleOpt.get();
            if (!Boolean.TRUE.equals(toggle.getIsEnabled())) {
                throw new BusinessException(ErrorCode.E_2004,
                        String.format("Feature %s is disabled for this tenant", featureKey));
            }
        }
        // 如果沒有 toggle 記錄，預設允許（向後相容）
    }
}
