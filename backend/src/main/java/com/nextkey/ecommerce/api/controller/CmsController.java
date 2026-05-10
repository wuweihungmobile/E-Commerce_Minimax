package com.nextkey.ecommerce.api.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.CmsDto;
import com.nextkey.ecommerce.core.cms.CmsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CMS REST API
 */
@Slf4j
@RestController
@RequestMapping("/v2/cms")
@RequiredArgsConstructor
public class CmsController {

    private final CmsService cmsService;

    // ========== Content Pages ==========

    /**
     * 建立頁面 (Admin)
     */
    @PostMapping("/pages")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('cms:create')")
    public ResponseEntity<ApiResponse<CmsDto.PageResponse>> createPage(
            @Valid @RequestBody CmsDto.CreatePageRequest request) {
        log.info("Create page: slug={}", request.getSlug());
        CmsDto.PageResponse response = cmsService.createPage(request);
        return ResponseEntity.ok(ApiResponse.success("Page created", response));
    }

    /**
     * 更新頁面 (Admin)
     */
    @PutMapping("/pages/{pageId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('cms:update')")
    public ResponseEntity<ApiResponse<CmsDto.PageResponse>> updatePage(
            @PathVariable UUID pageId,
            @Valid @RequestBody CmsDto.UpdatePageRequest request) {
        log.info("Update page: pageId={}", pageId);
        CmsDto.PageResponse response = cmsService.updatePage(pageId, request);
        return ResponseEntity.ok(ApiResponse.success("Page updated", response));
    }

    /**
     * 發布頁面 (Admin)
     */
    @PostMapping("/pages/{pageId}/publish")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('cms:publish')")
    public ResponseEntity<ApiResponse<CmsDto.PageResponse>> publishPage(@PathVariable UUID pageId) {
        log.info("Publish page: pageId={}", pageId);
        CmsDto.PageResponse response = cmsService.publishPage(pageId);
        return ResponseEntity.ok(ApiResponse.success("Page published", response));
    }

    /**
     * 取得頁面列表 (Admin)
     */
    @GetMapping("/pages")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('cms:read')")
    public ResponseEntity<ApiResponse<CmsDto.PageListResponse>> getPages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CmsDto.PageListResponse response = cmsService.getPages(page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 取得頁面 (公開)
     */
    @GetMapping("/pages/{slug}")
    public ResponseEntity<ApiResponse<CmsDto.PageResponse>> getPageBySlug(@PathVariable String slug) {
        CmsDto.PageResponse response = cmsService.getPageBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ========== Banners ==========

    /**
     * 建立橫幅 (Admin)
     */
    @PostMapping("/banners")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('cms:create')")
    public ResponseEntity<ApiResponse<CmsDto.BannerResponse>> createBanner(
            @Valid @RequestBody CmsDto.CreateBannerRequest request) {
        log.info("Create banner: title={}", request.getTitle());
        CmsDto.BannerResponse response = cmsService.createBanner(request);
        return ResponseEntity.ok(ApiResponse.success("Banner created", response));
    }

    /**
     * 更新橫幅 (Admin)
     */
    @PutMapping("/banners/{bannerId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('cms:update')")
    public ResponseEntity<ApiResponse<CmsDto.BannerResponse>> updateBanner(
            @PathVariable UUID bannerId,
            @Valid @RequestBody CmsDto.UpdateBannerRequest request) {
        log.info("Update banner: bannerId={}", bannerId);
        CmsDto.BannerResponse response = cmsService.updateBanner(bannerId, request);
        return ResponseEntity.ok(ApiResponse.success("Banner updated", response));
    }

    /**
     * 發布橫幅 (Admin)
     */
    @PostMapping("/banners/{bannerId}/publish")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('cms:publish')")
    public ResponseEntity<ApiResponse<CmsDto.BannerResponse>> publishBanner(@PathVariable UUID bannerId) {
        log.info("Publish banner: bannerId={}", bannerId);
        CmsDto.BannerResponse response = cmsService.publishBanner(bannerId);
        return ResponseEntity.ok(ApiResponse.success("Banner published", response));
    }

    /**
     * 取得橫幅列表 (Admin)
     */
    @GetMapping("/banners")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('cms:read')")
    public ResponseEntity<ApiResponse<List<CmsDto.BannerResponse>>> getBanners() {
        List<CmsDto.BannerResponse> response = cmsService.getBanners();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 取得活躍橫幅 (公開)
     */
    @GetMapping("/banners/active")
    public ResponseEntity<ApiResponse<CmsDto.BannerListResponse>> getActiveBanners(
            @RequestParam(required = false) CmsDto.BannerPosition position) {
        CmsDto.BannerListResponse response = cmsService.getActiveBanners(position);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 記錄橫幅點擊 (公開)
     */
    @PostMapping("/banners/{bannerId}/click")
    public ResponseEntity<ApiResponse<Void>> recordBannerClick(final @PathVariable UUID bannerId) {
        cmsService.recordBannerClick(bannerId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
