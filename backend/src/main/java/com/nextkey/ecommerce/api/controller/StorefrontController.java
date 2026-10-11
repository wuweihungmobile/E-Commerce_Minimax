package com.nextkey.ecommerce.api.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.ListingResponse;
import com.nextkey.ecommerce.api.dto.StorefrontDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.util.PageableUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 店鋪前台公開端點（DEF-321(b)，Sprint 252；PRD §12.7 Guest/Buyer 瀏覽店鋪前台流程）。
 *
 * <p>路徑掛在既有 {@code SecurityConfig} 的 {@code /v2/public/**} {@code permitAll()} 規則下
 * （此規則先前無任何 Controller 使用，Sprint 190 稽核時確認過），刻意不動 {@code /v2/listings}
 * 本身的授權，不影響既有已登入流程。只回傳 {@code status=ACTIVE} 的店鋪與上架項目。
 */
@Slf4j
@RestController
@RequestMapping("/v2/public/stores")
@RequiredArgsConstructor
public class StorefrontController {

    private final TenantRepository tenantRepository;
    private final ListingRepository listingRepository;

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<StorefrontDto.StoreProfileResponse>> getStoreProfile(
            @PathVariable final String slug) {
        Tenant tenant = findActiveTenantBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(StorefrontDto.StoreProfileResponse.builder()
                .tenantId(tenant.getId())
                .slug(tenant.getSlug())
                .name(tenant.getName())
                .description(tenant.getDescription())
                .logoUrl(tenant.getLogoUrl())
                .build()));
    }

    @GetMapping("/{slug}/listings")
    public ResponseEntity<ApiResponse<Page<ListingResponse>>> getStoreListings(
            @PathVariable final String slug,
            @RequestParam(required = false) final String type,
            @RequestParam(defaultValue = "false") final boolean featured,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        Tenant tenant = findActiveTenantBySlug(slug);
        PageRequest pageRequest = PageableUtils.of(page, size, 100, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Listing> listings;
        if (featured) {
            listings = listingRepository.findByTenantIdAndStatusAndIsFeaturedTrue(
                    tenant.getId(), Listing.ListingStatus.ACTIVE, pageRequest);
        } else if (type != null && !type.isBlank()) {
            listings = listingRepository.findByTenantIdAndListingTypeAndStatus(
                    tenant.getId(), parseListingType(type), Listing.ListingStatus.ACTIVE, pageRequest);
        } else {
            listings = listingRepository.findByTenantIdAndStatus(
                    tenant.getId(), Listing.ListingStatus.ACTIVE, pageRequest);
        }
        return ResponseEntity.ok(ApiResponse.success(listings.map(ListingResponse::fromEntity)));
    }

    // 不區分「slug 不存在」與「店鋪非 ACTIVE（停權/待審核/已終止）」，避免成為列舉店鋪狀態的側通道
    private Tenant findActiveTenantBySlug(final String slug) {
        return tenantRepository.findBySlug(slug)
                .filter(t -> t.getStatus() == Tenant.TenantStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));
    }

    private Listing.ListingType parseListingType(final String type) {
        try {
            return Listing.ListingType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.E_3001, "Invalid listing type: " + type);
        }
    }
}
