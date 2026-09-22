package com.nextkey.ecommerce.core.cms;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.CmsDto;
import com.nextkey.ecommerce.domain.model.cms.Banner;
import com.nextkey.ecommerce.domain.model.cms.ContentPage;
import com.nextkey.ecommerce.domain.repository.BannerRepository;
import com.nextkey.ecommerce.domain.repository.ContentPageRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import com.nextkey.ecommerce.shared.util.PageableUtils;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * CMS 服務
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsService {

    private final ContentPageRepository contentPageRepository;
    private final BannerRepository bannerRepository;

    // Pagination default
    private static final int DEFAULT_PAGE_SIZE = 50;

    // ========== Content Page ==========

    /**
     * 建立頁面
     */
    @Transactional
    public CmsDto.PageResponse createPage(CmsDto.CreatePageRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();
        UUID userId = TenantContext.getCurrentUser();

        // 檢查 slug 是否已存在
        if (contentPageRepository.findBySlug(request.getSlug()).isPresent()) {
            throw new BusinessException(ErrorCode.E_9005, "Slug already exists");
        }

        ContentPage page = ContentPage.builder()
                .title(request.getTitle())
                .slug(request.getSlug())
                .content(request.getContent())
                .metadata(request.getMetadata())
                .pageType(ContentPage.PageType.valueOf(request.getPageType().name()))
                .template(request.getTemplate())
                .featuredImageUrl(request.getFeaturedImageUrl())
                .sections(request.getSections())
                .status(ContentPage.ContentStatus.DRAFT)
                .authorId(userId)
                .tenantId(tenantId)
                .isIndexable(request.getIsIndexable() != null ? request.getIsIndexable() : true)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();

        // 併發防護（DEF-149）：上面的 findBySlug 檢查與這裡的 save() 之間是 TOCTOU，
        // 兩個併發請求都可能通過檢查各自 INSERT；DB 端已有 cms_pages_slug_key
        // （V49 migration，全域 UNIQUE(slug)）兜底，這裡改用 saveAndFlush 捕捉違反約束
        // 並轉譯為既有的 E_9005，避免第二個請求收到原始的 500。
        try {
            page = contentPageRepository.saveAndFlush(page);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.E_9005, "Slug already exists");
        }

        log.info("Content page created: id={}, slug={}", page.getId(), page.getSlug());

        return toPageResponse(page);
    }

    /**
     * 更新頁面
     */
    @Transactional
    public CmsDto.PageResponse updatePage(UUID pageId, CmsDto.UpdatePageRequest request) {
        ContentPage page = contentPageRepository.findById(pageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8004, "Page not found"));
        checkCmsTenantOwnership(page.getTenantId());

        updatePageFromRequest(page, request);
        page = contentPageRepository.save(page);

        log.info("Content page updated: id={}", pageId);

        return toPageResponse(page);
    }

    private void updatePageFromRequest(ContentPage page, CmsDto.UpdatePageRequest request) {
        updatePageTextFields(page, request);
        updatePageDataFields(page, request);
        updatePageStatusFields(page, request);
    }

    private void updatePageTextFields(ContentPage page, CmsDto.UpdatePageRequest request) {
        if (request.getTitle() != null) {
            page.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            page.setContent(request.getContent());
        }
        if (request.getTemplate() != null) {
            page.setTemplate(request.getTemplate());
        }
        if (request.getFeaturedImageUrl() != null) {
            page.setFeaturedImageUrl(request.getFeaturedImageUrl());
        }
    }

    private void updatePageDataFields(ContentPage page, CmsDto.UpdatePageRequest request) {
        if (request.getMetadata() != null) {
            page.setMetadata(request.getMetadata());
        }
        if (request.getSections() != null) {
            page.setSections(request.getSections());
        }
        if (request.getSortOrder() != null) {
            page.setSortOrder(request.getSortOrder());
        }
    }

    private void updatePageStatusFields(ContentPage page, CmsDto.UpdatePageRequest request) {
        if (request.getStatus() != null) {
            ContentPage.ContentStatus newStatus = ContentPage.ContentStatus.valueOf(request.getStatus().name());
            // Sprint 182（DEF-246）：cms:update（SELLER/HOST/STORE_OWNER/STORE_STAFF 皆持有）先前可
            // 藉此欄位直接把頁面轉為 PUBLISHED，完全繞過刻意分層、只給 OWNER+ADMIN 的 cms:publish
            // 權限（見上方 CMS_* 權限註解、PublishPage 端點）。改為 PUBLISHED 一律拒絕，須改呼叫
            // POST /v2/cms/pages/{id}/publish；DRAFT/ARCHIVED 等非 publish-tier 狀態不受影響。
            if (newStatus == ContentPage.ContentStatus.PUBLISHED) {
                throw new BusinessException(ErrorCode.E_1007,
                        "Publishing requires cms:publish permission, use the publish endpoint instead");
            }
            page.setStatus(newStatus);
        }
        if (request.getIsIndexable() != null) {
            page.setIsIndexable(request.getIsIndexable());
        }
    }

    /**
     * 發布頁面
     */
    @Transactional
    public CmsDto.PageResponse publishPage(UUID pageId) {
        ContentPage page = contentPageRepository.findById(pageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8004, "Page not found"));
        checkCmsTenantOwnership(page.getTenantId());

        page.setStatus(ContentPage.ContentStatus.PUBLISHED);
        page.setPublishedAt(Instant.now());

        page = contentPageRepository.save(page);

        log.info("Content page published: id={}", pageId);

        return toPageResponse(page);
    }

    /**
     * 取得頁面 (公開，訪客瀏覽用，比照 PostService.getPublishedPostBySlug 模式，
     * Sprint 82 DEF-034 修復：呼叫端須明確傳入 tenantId 過濾，避免跨租戶內容混雜)
     */
    @Transactional(readOnly = true)
    public CmsDto.PageResponse getPageBySlug(String slug, UUID tenantId) {
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.E_1002, "tenantId is required");
        }

        ContentPage page = contentPageRepository.findByTenantIdAndSlug(tenantId, slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8004, "Page not found"));

        if (page.getStatus() != ContentPage.ContentStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.E_8004, "Page not published");
        }

        return toPageResponse(page);
    }

    /**
     * 取得頁面列表 (Admin)
     */
    @Transactional(readOnly = true)
    public CmsDto.PageListResponse getPages(int page, int size) {
        PageRequest pageRequest = PageableUtils.of(page, size, DEFAULT_PAGE_SIZE);
        Page<ContentPage> pages;
        if (isCurrentUserAdmin()) {
            pages = contentPageRepository.findByStatusOrderBySortOrderAsc(
                    ContentPage.ContentStatus.PUBLISHED, pageRequest);
        } else {
            UUID tenantId = TenantContext.getCurrentTenant();
            pages = contentPageRepository.findByTenantIdAndStatusOrderBySortOrderAsc(
                    tenantId, ContentPage.ContentStatus.PUBLISHED, pageRequest);
        }

        List<CmsDto.PageResponse> responses = pages.getContent().stream()
                .map(this::toPageResponse)
                .collect(Collectors.toList());

        return CmsDto.PageListResponse.builder()
                .pages(responses)
                .page(page)
                .size(size)
                .totalElements(pages.getTotalElements())
                .totalPages(pages.getTotalPages())
                .build();
    }

    // ========== Banner ==========

    /**
     * 建立橫幅
     */
    @Transactional
    public CmsDto.BannerResponse createBanner(CmsDto.CreateBannerRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();

        Banner banner = Banner.builder()
                .title(request.getTitle())
                .imageUrl(request.getImageUrl())
                .linkUrl(request.getLinkUrl())
                .linkType(request.getLinkType() != null ? Banner.LinkType.valueOf(request.getLinkType().name()) : null)
                .description(request.getDescription())
                .buttonText(request.getButtonText())
                .metadata(request.getMetadata())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .bannerType(Banner.BannerType.valueOf(request.getBannerType().name()))
                .position(Banner.BannerPosition.valueOf(request.getPosition().name()))
                .status(Banner.BannerStatus.DRAFT)
                .tenantId(tenantId)
                .targetAudience(request.getTargetAudience())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .impressionCount(0)
                .clickCount(0)
                .build();

        banner = bannerRepository.save(banner);

        log.info("Banner created: id={}", banner.getId());

        return toBannerResponse(banner);
    }

    /**
     * 更新橫幅
     */
    @Transactional
    public CmsDto.BannerResponse updateBanner(UUID bannerId, CmsDto.UpdateBannerRequest request) {
        Banner banner = bannerRepository.findById(bannerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8005, "Banner not found"));
        checkCmsTenantOwnership(banner.getTenantId());

        updateBannerFromRequest(banner, request);
        banner = bannerRepository.save(banner);

        log.info("Banner updated: id={}", bannerId);

        return toBannerResponse(banner);
    }

    private void updateBannerFromRequest(Banner banner, CmsDto.UpdateBannerRequest request) {
        updateBannerTextFields(banner, request);
        updateBannerLinkFields(banner, request);
        updateBannerScheduleFields(banner, request);
        updateBannerDisplayFields(banner, request);
        updateBannerStatusFields(banner, request);
    }

    private void updateBannerTextFields(Banner banner, CmsDto.UpdateBannerRequest request) {
        if (request.getTitle() != null) {
            banner.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            banner.setDescription(request.getDescription());
        }
        if (request.getButtonText() != null) {
            banner.setButtonText(request.getButtonText());
        }
    }

    private void updateBannerLinkFields(Banner banner, CmsDto.UpdateBannerRequest request) {
        if (request.getImageUrl() != null) {
            banner.setImageUrl(request.getImageUrl());
        }
        if (request.getLinkUrl() != null) {
            banner.setLinkUrl(request.getLinkUrl());
        }
        if (request.getLinkType() != null) {
            banner.setLinkType(Banner.LinkType.valueOf(request.getLinkType().name()));
        }
    }

    private void updateBannerScheduleFields(Banner banner, CmsDto.UpdateBannerRequest request) {
        if (request.getStartDate() != null) {
            banner.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            banner.setEndDate(request.getEndDate());
        }
    }

    private void updateBannerDisplayFields(Banner banner, CmsDto.UpdateBannerRequest request) {
        if (request.getBannerType() != null) {
            banner.setBannerType(Banner.BannerType.valueOf(request.getBannerType().name()));
        }
        if (request.getPosition() != null) {
            banner.setPosition(Banner.BannerPosition.valueOf(request.getPosition().name()));
        }
        if (request.getTargetAudience() != null) {
            banner.setTargetAudience(request.getTargetAudience());
        }
        if (request.getSortOrder() != null) {
            banner.setSortOrder(request.getSortOrder());
        }
    }

    private void updateBannerStatusFields(Banner banner, CmsDto.UpdateBannerRequest request) {
        if (request.getStatus() != null) {
            Banner.BannerStatus newStatus = Banner.BannerStatus.valueOf(request.getStatus().name());
            // Sprint 182（DEF-246）：比照 updatePageStatusFields，cms:update 持有者先前可藉此欄位
            // 繞過 cms:publish 直接發布橫幅。PUBLISHED 一律拒絕，須改呼叫
            // POST /v2/cms/banners/{id}/publish。
            if (newStatus == Banner.BannerStatus.PUBLISHED) {
                throw new BusinessException(ErrorCode.E_1007,
                        "Publishing requires cms:publish permission, use the publish endpoint instead");
            }
            banner.setStatus(newStatus);
        }
        if (request.getMetadata() != null) {
            banner.setMetadata(request.getMetadata());
        }
    }

    /**
     * 發布橫幅
     */
    @Transactional
    public CmsDto.BannerResponse publishBanner(UUID bannerId) {
        Banner banner = bannerRepository.findById(bannerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8005, "Banner not found"));
        checkCmsTenantOwnership(banner.getTenantId());

        banner.setStatus(Banner.BannerStatus.PUBLISHED);
        banner = bannerRepository.save(banner);

        log.info("Banner published: id={}", bannerId);

        return toBannerResponse(banner);
    }

    /**
     * 取得橫幅列表 (Admin)
     */
    @Transactional(readOnly = true)
    public List<CmsDto.BannerResponse> getBanners() {
        List<Banner> banners;
        if (isCurrentUserAdmin()) {
            banners = bannerRepository.findByStatusOrderBySortOrderAsc(Banner.BannerStatus.PUBLISHED,
                    PageRequest.of(0, 100)).getContent();
        } else {
            UUID tenantId = TenantContext.getCurrentTenant();
            banners = bannerRepository.findByTenantIdAndStatus(tenantId, Banner.BannerStatus.PUBLISHED);
        }
        return banners.stream()
                .map(this::toBannerResponse)
                .collect(Collectors.toList());
    }

    /**
     * 取得活躍橫幅 (公開，訪客瀏覽用，Sprint 82 DEF-034 修復：呼叫端須明確傳入 tenantId 過濾)
     */
    @Transactional(readOnly = true)
    public CmsDto.BannerListResponse getActiveBanners(CmsDto.BannerPosition position, UUID tenantId) {
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.E_1002, "tenantId is required");
        }

        LocalDate today = LocalDate.now();

        List<Banner> banners;
        if (position != null) {
            banners = bannerRepository.findActiveByPositionAndTenantId(
                    Banner.BannerPosition.valueOf(position.name()), tenantId, today);
        } else {
            banners = bannerRepository.findAllActiveByTenantId(tenantId, today);
        }

        List<CmsDto.BannerResponse> responses = banners.stream()
                .map(this::toBannerResponse)
                .collect(Collectors.toList());

        return CmsDto.BannerListResponse.builder()
                .banners(responses)
                .position(position != null ? position.name() : null)
                .build();
    }

    /**
     * 記錄點擊 (公開，Sprint 82 DEF-034 修復：呼叫端須明確傳入 tenantId，跨租戶點擊靜默無效果)
     */
    @Transactional
    public void recordBannerClick(final UUID bannerId, final UUID tenantId) {
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.E_1002, "tenantId is required");
        }
        bannerRepository.incrementClickCountForTenant(bannerId, tenantId);
        log.debug("Banner click recorded: id={}, tenantId={}", bannerId, tenantId);
    }

    // ========== Authorization Helpers (Sprint 74 DEF-032) ==========

    /**
     * CMS 資源（頁面/橫幅）租戶擁有權檢查。
     *
     * <p>updatePage/publishPage/updateBanner/publishBanner 原本完全沒有擁有權/租戶檢查，
     * Controller 端僅要求 cms:update/cms:publish 權限，此權限可能分散於各租戶的管理者角色，
     * 任一租戶的管理者皆可竄改/發布其他租戶的頁面或橫幅，屬跨租戶寫入 IDOR，與 DEF-019
     * （LogisticsService.createLogistics）/DEF-024/DEF-028 同一 tenant-based 模式。
     *
     * <p>比照既有前例，簡化為「本租戶 or admin（ROLE_ADMIN/ROLE_SUPER_ADMIN）」放行，
     * 越權拋 E_1007。
     */
    private void checkCmsTenantOwnership(final UUID resourceTenantId) {
        UUID tenantId = TenantContext.getCurrentTenant();
        boolean isSameTenant = tenantId != null && tenantId.equals(resourceTenantId);
        if (!isCurrentUserAdmin() && !isSameTenant) {
            throw new BusinessException(ErrorCode.E_1007, "Not authorized to manage this CMS resource");
        }
    }

    /**
     * 判斷目前使用者是否為系統管理員（ROLE_ADMIN 或 ROLE_SUPER_ADMIN）。
     * 比照 ReviewService.isCurrentUserAdmin 既有前例。
     */
    private boolean isCurrentUserAdmin() {
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && (
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")) ||
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
        );
    }

    // ========== Helper Methods ==========

    private CmsDto.PageResponse toPageResponse(ContentPage page) {
        return CmsDto.PageResponse.builder()
                .pageId(page.getId())
                .title(page.getTitle())
                .slug(page.getSlug())
                .content(page.getContent())
                .metadata(page.getMetadata())
                .pageType(page.getPageType().name())
                .template(page.getTemplate())
                .featuredImageUrl(page.getFeaturedImageUrl())
                .sections(page.getSections())
                .status(page.getStatus().name())
                .publishedAt(page.getPublishedAt())
                .authorId(page.getAuthorId())
                .tenantId(page.getTenantId())
                .isIndexable(page.getIsIndexable())
                .sortOrder(page.getSortOrder())
                .createdAt(page.getCreatedAt())
                .updatedAt(page.getUpdatedAt())
                .build();
    }

    private CmsDto.BannerResponse toBannerResponse(Banner banner) {
        return CmsDto.BannerResponse.builder()
                .bannerId(banner.getId())
                .title(banner.getTitle())
                .imageUrl(banner.getImageUrl())
                .linkUrl(banner.getLinkUrl())
                .linkType(banner.getLinkType() != null ? banner.getLinkType().name() : null)
                .description(banner.getDescription())
                .buttonText(banner.getButtonText())
                .metadata(banner.getMetadata())
                .startDate(banner.getStartDate())
                .endDate(banner.getEndDate())
                .bannerType(banner.getBannerType().name())
                .position(banner.getPosition().name())
                .status(banner.getStatus().name())
                .tenantId(banner.getTenantId())
                .targetAudience(banner.getTargetAudience())
                .impressionCount(banner.getImpressionCount())
                .clickCount(banner.getClickCount())
                .sortOrder(banner.getSortOrder())
                .createdAt(banner.getCreatedAt())
                .updatedAt(banner.getUpdatedAt())
                .build();
    }
}
