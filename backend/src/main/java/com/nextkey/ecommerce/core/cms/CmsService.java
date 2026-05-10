package com.nextkey.ecommerce.core.cms;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

        page = contentPageRepository.save(page);

        log.info("Content page created: id={}, slug={}", page.getId(), page.getSlug());

        return toPageResponse(page);
    }

    /**
     * 更新頁面
     */
    @Transactional
    public CmsDto.PageResponse updatePage(UUID pageId, CmsDto.UpdatePageRequest request) {
        ContentPage page = contentPageRepository.findById(pageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8000, "Page not found"));

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
            page.setStatus(ContentPage.ContentStatus.valueOf(request.getStatus().name()));
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
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8000, "Page not found"));

        page.setStatus(ContentPage.ContentStatus.PUBLISHED);
        page.setPublishedAt(Instant.now());

        page = contentPageRepository.save(page);

        log.info("Content page published: id={}", pageId);

        return toPageResponse(page);
    }

    /**
     * 取得頁面 (公開)
     */
    @Transactional(readOnly = true)
    public CmsDto.PageResponse getPageBySlug(String slug) {
        ContentPage page = contentPageRepository.findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8000, "Page not found"));

        if (page.getStatus() != ContentPage.ContentStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.E_8000, "Page not published");
        }

        return toPageResponse(page);
    }

    /**
     * 取得頁面列表 (Admin)
     */
    @Transactional(readOnly = true)
    public CmsDto.PageListResponse getPages(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, DEFAULT_PAGE_SIZE));
        Page<ContentPage> pages = contentPageRepository.findByStatusOrderBySortOrderAsc(
                ContentPage.ContentStatus.PUBLISHED, pageRequest);

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
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8000, "Banner not found"));

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
            banner.setStatus(Banner.BannerStatus.valueOf(request.getStatus().name()));
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
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8000, "Banner not found"));

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
        return bannerRepository.findByStatusOrderBySortOrderAsc(Banner.BannerStatus.PUBLISHED,
                PageRequest.of(0, 100)).getContent().stream()
                .map(this::toBannerResponse)
                .collect(Collectors.toList());
    }

    /**
     * 取得活躍橫幅 (公開)
     */
    @Transactional(readOnly = true)
    public CmsDto.BannerListResponse getActiveBanners(CmsDto.BannerPosition position) {
        LocalDate today = LocalDate.now();

        List<Banner> banners;
        if (position != null) {
            banners = bannerRepository.findActiveByPosition(Banner.BannerPosition.valueOf(position.name()), today);
        } else {
            banners = bannerRepository.findAllActive(today);
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
     * 記錄點擊
     */
    @Transactional
    public void recordBannerClick(final UUID bannerId) {
        bannerRepository.incrementClickCount(bannerId);
        log.debug("Banner click recorded: id={}", bannerId);
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
