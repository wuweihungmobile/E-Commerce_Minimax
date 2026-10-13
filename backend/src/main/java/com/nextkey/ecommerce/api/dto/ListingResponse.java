package com.nextkey.ecommerce.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nextkey.ecommerce.domain.model.listing.Listing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公開 listing 回應 DTO（Sprint 130，DEF-093）。
 *
 * <p>刻意不直接回傳 {@code Listing} 實體——其 {@code owner} 為 LAZY 關聯，專案
 * 未設定 Jackson Hibernate 模組亦未關閉 open-in-view，序列化時會觸發懶載入並把整個 {@code User}
 * （含 {@code passwordHash}，無 {@code @JsonIgnore}）序列化進回應。此 DTO 只挑選前端實際依賴的欄位，
 * 且一律用 {@link Listing#getTenantId()} 影子欄位取得 tenantId，不觸碰 LAZY 的 {@code tenant}/{@code owner}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingResponse {

    private UUID id;
    private UUID tenantId;
    private Listing.ListingType listingType;
    private String title;
    private String description;
    private String coverImageUrl;
    private Listing.ListingStatus status;
    private BigDecimal basePrice;
    private String currency;
    private List<String> tags;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * 所屬店鋪名稱/slug（DEF-358(a)，Sprint 259，僅跨店鋪公開瀏覽場景會填入）。{@code null}
     * 代表呼叫端未提供（既有呼叫點不變），刻意不透過 {@link Listing#getTenant()}（LAZY）取得——
     * 由呼叫端批次查好 {@code Tenant} 後傳入，避免同本檔案開頭 Javadoc 說明的裸實體/N+1 風險。
     */
    private String tenantName;
    private String tenantSlug;

    public static ListingResponse fromEntity(final Listing listing) {
        return fromEntity(listing, null, null);
    }

    public static ListingResponse fromEntity(final Listing listing, final String tenantName, final String tenantSlug) {
        return ListingResponse.builder()
                .id(listing.getId())
                .tenantId(listing.getTenantId())
                .listingType(listing.getListingType())
                .title(listing.getTitle())
                .description(listing.getDescription())
                .coverImageUrl(listing.getCoverImageUrl())
                .status(listing.getStatus())
                .basePrice(listing.getBasePrice())
                .currency(listing.getCurrency())
                .tags(listing.getTags())
                .createdAt(listing.getCreatedAt())
                .updatedAt(listing.getUpdatedAt())
                .tenantName(tenantName)
                .tenantSlug(tenantSlug)
                .build();
    }
}
