package com.nextkey.ecommerce.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class SellerDashboardDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardResponse {
        private long orderCount7d;
        private long orderCount30d;
        private BigDecimal revenue30d;
        private long activeListingCount;
        private long pendingOrderCount;
        private Instant lastOrderAt;
    }
}
