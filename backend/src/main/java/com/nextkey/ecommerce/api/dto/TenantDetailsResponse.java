package com.nextkey.ecommerce.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantDetailsResponse {

    private String tenantId;
    private String storeName;
    private String storeDescription;
    private String businessType;
    private String status;
    private String contactEmail;
    private String contactPhone;
    private String logoUrl;
    private String coverImageUrl;
    private BigDecimal purchaseOrderApprovalThreshold;
    private MemberInfo member;
    private StatsInfo stats;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberInfo {
        private String displayName;
        private String avatarUrl;
        private Instant joinedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatsInfo {
        private Integer listingCount;
        private Long totalSales;
        private Double rating;
    }
}