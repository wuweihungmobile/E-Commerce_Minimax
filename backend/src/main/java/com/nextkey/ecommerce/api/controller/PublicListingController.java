package com.nextkey.ecommerce.api.controller;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.ListingResponse;
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
 * 跨店鋪公開商品/房源瀏覽（PRD §10.1.1 `/products`、`/rooms`，DEF-358(a)，Sprint 259）。
 *
 * <p>掛在既有 {@code SecurityConfig} 的 {@code /v2/public/**} {@code permitAll()} 規則下（與
 * {@link StorefrontController} 同一慣例），刻意不動 {@code /v2/listings} 本身的授權範圍——那是
 * 另一條既有、服務已登入流程的端點，兩者職責分開（比照 {@code StorefrontController} 開頭 Javadoc
 * 說明的既有決策）。只回傳 {@code ACTIVE} 且所屬租戶也 {@code ACTIVE} 的項目。
 */
@Slf4j
@RestController
@RequestMapping("/v2/public/listings")
@RequiredArgsConstructor
public class PublicListingController {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt", "basePrice");

    private final ListingRepository listingRepository;
    private final TenantRepository tenantRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ListingResponse>>> getPublicListings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        Sort sort = PageableUtils.sortOf(sortBy, sortDir, ALLOWED_SORT_PROPERTIES);
        PageRequest pageRequest = PageableUtils.of(page, size, 100, sort);

        Page<Listing> listings = findListings(type, keyword, pageRequest);
        Map<UUID, Tenant> tenantsById = batchFetchTenants(listings);

        Page<ListingResponse> response = listings.map(listing -> toResponseWithTenant(listing, tenantsById));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Page<Listing> findListings(final String type, final String keyword, final PageRequest pageRequest) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasType = type != null && !type.isBlank();

        if (hasKeyword && hasType) {
            return listingRepository.searchPubliclyVisibleByTypeAndKeyword(parseListingType(type), keyword, pageRequest);
        }
        if (hasKeyword) {
            return listingRepository.searchPubliclyVisibleByKeyword(keyword, pageRequest);
        }
        if (hasType) {
            return listingRepository.findPubliclyVisibleByType(parseListingType(type), pageRequest);
        }
        return listingRepository.findPubliclyVisible(pageRequest);
    }

    // 批次查詢本頁所有租戶的 name/slug，避免逐筆查詢造成 N+1（本頁最多 100 筆，見 PageableUtils.of 上限）。
    private Map<UUID, Tenant> batchFetchTenants(final Page<Listing> listings) {
        Set<UUID> tenantIds = listings.getContent().stream()
                .map(Listing::getTenantId)
                .collect(Collectors.toSet());
        return tenantRepository.findAllById(tenantIds).stream()
                .collect(Collectors.toMap(Tenant::getId, tenant -> tenant));
    }

    private ListingResponse toResponseWithTenant(final Listing listing, final Map<UUID, Tenant> tenantsById) {
        Tenant tenant = tenantsById.get(listing.getTenantId());
        return ListingResponse.fromEntity(listing, tenant != null ? tenant.getName() : null,
                tenant != null ? tenant.getSlug() : null);
    }

    private Listing.ListingType parseListingType(final String type) {
        try {
            return Listing.ListingType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.E_3001, "Invalid listing type: " + type);
        }
    }
}
