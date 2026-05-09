package com.nextkey.ecommerce.api.controller;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.M15Dto;
import com.nextkey.ecommerce.api.filter.UserPrincipal;
import com.nextkey.ecommerce.core.cms.listing.ListingCardService;
import com.nextkey.ecommerce.core.cms.media.MediaService;
import com.nextkey.ecommerce.core.cms.post.PostCategoryService;
import com.nextkey.ecommerce.core.cms.post.PostService;
import com.nextkey.ecommerce.domain.model.cms.media.MediaAsset;
import com.nextkey.ecommerce.domain.model.cms.post.Post;
import com.nextkey.ecommerce.domain.model.tenant.TenantFeatureToggle;
import com.nextkey.ecommerce.domain.repository.TenantFeatureToggleRepository;
import com.nextkey.ecommerce.domain.repository.TenantMemberRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * M15 CMS Post REST API
 * 貼文 CRUD、分類管理、媒體庫
 */
@Slf4j
@RestController
@RequestMapping("/v2")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final PostCategoryService postCategoryService;
    private final ListingCardService listingCardService;
    private final MediaService mediaService;
    private final TenantFeatureToggleRepository featureToggleRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final UserRepository userRepository;

    // ========== Post CRUD ==========

    /**
     * 建立貼文
     * POST /api/v2/dashboard/posts
     */
    @PostMapping("/dashboard/posts")
    @PreAuthorize("hasAnyRole('STORE_OWNER', 'STORE_STAFF', 'SELLER', 'HOST')")
    public ResponseEntity<ApiResponse<M15Dto.PostResponse>> createPost(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody M15Dto.CreatePostRequest request) {

        UUID tenantId = getTenantIdFromUser(userDetails);
        UUID authorId = getUserIdFromUser(userDetails);

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
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody M15Dto.UpdatePostRequest request) {

        UUID tenantId = getTenantIdFromUser(userDetails);

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
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {

        UUID tenantId = getTenantIdFromUser(userDetails);

        // 檢查 CMS_ENABLED feature toggle
        checkFeatureToggle(tenantId, "CMS_ENABLED");

        Post.PostStatus postStatus = status != null ? Post.PostStatus.valueOf(status.toUpperCase()) : null;
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
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID tenantId = getTenantIdFromUser(userDetails);

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
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID tenantId = getTenantIdFromUser(userDetails);

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
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID tenantId = getTenantIdFromUser(userDetails);

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
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID tenantId = getTenantIdFromUser(userDetails);

        // 檢查 CMS_ENABLED feature toggle
        checkFeatureToggle(tenantId, "CMS_ENABLED");

        M15Dto.PostResponse response = postService.unpublishPost(postId, tenantId);
        return ResponseEntity.ok(ApiResponse.success("Post unpublished", response));
    }

    // ========== Public APIs (前台) ==========

    /**
     * 取得已發布貼文列表（公開）
     * GET /api/v2/posts
     */
    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<M15Dto.PostListResponse>> getPublishedPosts(
            @RequestParam UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        M15Dto.PostListResponse response = postService.getPublishedPosts(tenantId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 依 Slug 取得已發布貼文（公開）
     * GET /api/v2/posts/:slug
     */
    @GetMapping("/posts/{slug}")
    public ResponseEntity<ApiResponse<M15Dto.PostResponse>> getPublishedPostBySlug(
            @PathVariable String slug,
            @RequestParam UUID tenantId) {

        M15Dto.PostResponse response = postService.getPublishedPostBySlug(slug, tenantId);
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
    public ResponseEntity<ApiResponse<M15Dto.CategoryListResponse>> getCategories(
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID tenantId = getTenantIdFromUser(userDetails);

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
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody M15Dto.CreateCategoryRequest request) {

        UUID tenantId = getTenantIdFromUser(userDetails);

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
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody M15Dto.UpdateCategoryRequest request) {

        UUID tenantId = getTenantIdFromUser(userDetails);

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
            @PathVariable UUID categoryId,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID tenantId = getTenantIdFromUser(userDetails);

        // 檢查 CMS_ENABLED feature toggle
        checkFeatureToggle(tenantId, "CMS_ENABLED");

        postCategoryService.deleteCategory(categoryId, tenantId);
        return ResponseEntity.ok(ApiResponse.success("Category deleted", null));
    }

    // ========== Media APIs ==========

    /**
     * 取得媒體列表
     * GET /api/v2/dashboard/media
     */
    @GetMapping("/dashboard/media")
    @PreAuthorize("hasAnyRole('STORE_OWNER', 'STORE_STAFF', 'SELLER', 'HOST')")
    public ResponseEntity<ApiResponse<M15Dto.MediaListResponse>> getMediaList(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String fileType) {

        UUID tenantId = getTenantIdFromUser(userDetails);

        // 檢查 CMS_ENABLED feature toggle
        checkFeatureToggle(tenantId, "CMS_ENABLED");

        MediaAsset.FileType type = fileType != null ? MediaAsset.FileType.valueOf(fileType.toUpperCase()) : null;
        M15Dto.MediaListResponse response = mediaService.getMediaList(tenantId, page, size, type);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 上傳媒體（Mock）
     * POST /api/v2/media/upload
     */
    @PostMapping("/media/upload")
    @PreAuthorize("hasAnyRole('STORE_OWNER', 'STORE_STAFF', 'SELLER', 'HOST')")
    public ResponseEntity<ApiResponse<M15Dto.MediaUploadResponse>> uploadMedia(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String fileName,
            @RequestParam String originalName,
            @RequestParam Long fileSize,
            @RequestParam String mimeType,
            @RequestParam String filePath) {

        UUID tenantId = getTenantIdFromUser(userDetails);
        UUID uploaderId = getUserIdFromUser(userDetails);

        // 檢查 CMS_ENABLED feature toggle
        checkFeatureToggle(tenantId, "CMS_ENABLED");

        M15Dto.MediaUploadResponse response = mediaService.uploadMedia(
                tenantId, uploaderId, fileName, originalName, fileSize, mimeType, filePath);
        return ResponseEntity.ok(ApiResponse.success("Media uploaded", response));
    }

    /**
     * 上傳媒體（MultipartFile - 實際上傳到 MinIO）
     * POST /api/v2/dashboard/media/upload-multipart
     */
    @PostMapping(value = "/dashboard/media/upload-multipart", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('STORE_OWNER', 'STORE_STAFF', 'SELLER', 'HOST')")
    public ResponseEntity<ApiResponse<M15Dto.MediaUploadResponse>> uploadMediaMultipart(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) {

        UUID tenantId = getTenantIdFromUser(userDetails);
        UUID uploaderId = getUserIdFromUser(userDetails);

        // 檢查 CMS_ENABLED feature toggle
        checkFeatureToggle(tenantId, "CMS_ENABLED");

        log.info("Upload media (multipart): fileName={}, size={}, contentType={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        M15Dto.MediaUploadResponse response = mediaService.uploadMedia(tenantId, uploaderId, file);
        return ResponseEntity.ok(ApiResponse.success("Media uploaded successfully", response));
    }

    /**
     * 刪除媒體
     * DELETE /api/v2/dashboard/media/:id
     */
    @DeleteMapping("/dashboard/media/{mediaId}")
    @PreAuthorize("hasAnyRole('STORE_OWNER', 'STORE_STAFF', 'SELLER', 'HOST')")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(
            @PathVariable UUID mediaId,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID tenantId = getTenantIdFromUser(userDetails);

        // 檢查 CMS_ENABLED feature toggle
        checkFeatureToggle(tenantId, "CMS_ENABLED");

        mediaService.deleteMedia(mediaId, tenantId);
        return ResponseEntity.ok(ApiResponse.success("Media deleted", null));
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
     * 從 UserDetails 取得 Tenant ID
     * 從 UserPrincipal 取得 userId，再從資料庫查詢該用戶所屬的 Tenant
     */
    private UUID getTenantIdFromUser(UserDetails userDetails) {
        // 首先嘗試從 TenantContext 取得（因為它是在 JwtAuthenticationFilter 中設置的）
        UUID contextUserId = TenantContext.getCurrentUser();
        if (contextUserId != null) {
            log.debug("Got userId from TenantContext: {}", contextUserId);
            var members = tenantMemberRepository.findByUserId(contextUserId);
            if (!members.isEmpty()) {
                return members.get(0).getTenantId();
            }
            // fallback to user.tenantId
            var userOpt = userRepository.findById(contextUserId);
            if (userOpt.isPresent() && userOpt.get().getTenantId() != null) {
                return userOpt.get().getTenantId();
            }
        }

        // 如果 userDetails 可用，嘗試從中取得
        if (userDetails != null) {
            UUID userId = null;
            String tenantIdStr = null;

            try {
                var getPrincipalMethod = userDetails.getClass().getMethod("getPrincipal");
                Object principal = getPrincipalMethod.invoke(userDetails);
                if (principal instanceof UserPrincipal up) {
                    userId = up.getUserId();
                    tenantIdStr = up.getTenantId();
                }
            } catch (Exception e) {
                log.debug("Cannot get principal: {}", e.getMessage());
            }

            // 如果有 tenantId 直接返回
            if (tenantIdStr != null && !tenantIdStr.isEmpty()) {
                try {
                    return UUID.fromString(tenantIdStr);
                } catch (Exception e) {
                    log.warn("Invalid tenantId format: {}", tenantIdStr);
                }
            }

            // 否則從 userId 查詢資料庫
            if (userId == null) {
                try {
                    var method = userDetails.getClass().getMethod("getUserId");
                    Object result = method.invoke(userDetails);
                    if (result instanceof UUID) {
                        userId = (UUID) result;
                    }
                } catch (Exception e) {
                    log.debug("Cannot get userId: {}", e.getMessage());
                }
            }

            if (userId != null) {
                var members = tenantMemberRepository.findByUserId(userId);
                if (!members.isEmpty()) {
                    return members.get(0).getTenantId();
                }
                var userOpt = userRepository.findById(userId);
                if (userOpt.isPresent() && userOpt.get().getTenantId() != null) {
                    return userOpt.get().getTenantId();
                }
            }
        }

        log.warn("Cannot find tenantId, using mock value");
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    /**
     * 從 UserDetails 取得 User ID
     */
    private UUID getUserIdFromUser(UserDetails userDetails) {
        // 首先嘗試從 TenantContext 取得（因為它是在 JwtAuthenticationFilter 中設置的）
        UUID contextUserId = TenantContext.getCurrentUser();
        if (contextUserId != null) {
            log.debug("Got userId from TenantContext: {}", contextUserId);
            return contextUserId;
        }

        if (userDetails == null) {
            log.warn("userDetails is null, cannot get userId");
            return UUID.fromString("00000000-0000-0000-0000-000000000002");
        }

        UUID userId = null;

        // 嘗試從 UserPrincipal 取得
        try {
            var getPrincipalMethod = userDetails.getClass().getMethod("getPrincipal");
            Object principal = getPrincipalMethod.invoke(userDetails);
            if (principal instanceof UserPrincipal up) {
                userId = up.getUserId();
                log.debug("Got userId from UserPrincipal: {}", userId);
            }
        } catch (Exception e) {
            log.debug("Cannot get principal: {}", e.getMessage());
        }

        // Fallback: 直接嘗試反射取得 getUserId
        if (userId == null) {
            try {
                var method = userDetails.getClass().getMethod("getUserId");
                Object result = method.invoke(userDetails);
                if (result instanceof UUID) {
                    userId = (UUID) result;
                    log.debug("Got userId from getUserId(): {}", userId);
                }
            } catch (Exception e) {
                log.debug("Cannot get userId: {}", e.getMessage());
            }
        }

        if (userId == null) {
            log.warn("Cannot find userId, using mock value");
            userId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        }
        return userId;
    }

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