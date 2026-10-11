package com.nextkey.ecommerce.api.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 店鋪前台公開回應 DTO（DEF-321(b)，Sprint 252）。
 *
 * <p>僅挑選前台展示所需欄位，不回傳 {@code Tenant} 實體本身——避免未來新增內部經營欄位
 * （如 {@code commissionRate}、{@code purchaseOrderApprovalThreshold}）時被動外洩，
 * 比照既有 {@code ListingResponse}/{@code TenantDetailsResponse} 的做法。
 */
public class StorefrontDto {

    private StorefrontDto() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoreProfileResponse {
        private UUID tenantId;
        private String slug;
        private String name;
        private String description;
        private String logoUrl;
    }
}
