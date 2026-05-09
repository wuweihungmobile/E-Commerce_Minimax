package com.nextkey.ecommerce.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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
    private String logoUrl;
    private String coverImageUrl;
    private MemberInfo member;
    private StatsInfo stats;
    private Instant createdAt;

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