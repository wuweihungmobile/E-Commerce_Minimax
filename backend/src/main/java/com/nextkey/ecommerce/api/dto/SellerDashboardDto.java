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
        /**
         * 近 30 天「已實現營收」：只加已完成（COMPLETED）的訂單。與 {@code AnalyticsService} 的「營收」（已付款、未取消、
         * 未退款）是不同的數字，兩者刻意並存、不可互相比較（DEF-407，Sprint 301）。欄位名維持 revenue30d 以免破壞既有契約。
         */
        private BigDecimal revenue30d;
        private long activeListingCount;
        private long pendingOrderCount;
        private Instant lastOrderAt;
    }
}
