package com.nextkey.ecommerce.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.*;

/**
 * 分析與統計 DTO
 */
public class AnalyticsDto {

    // ========== Dashboard Stats ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardStats {
        private BigDecimal todayRevenue;
        private BigDecimal yesterdayRevenue;
        private BigDecimal monthRevenue;
        private BigDecimal yearRevenue;
        private BigDecimal revenueGrowthPercent;
        private Integer todayOrders;
        private Integer yesterdayOrders;
        private Integer monthOrders;
        private Integer pendingOrders;
        private Integer activeListings;
        private Integer totalRooms;
        private Integer totalProducts;
    }

    // ========== Revenue Analytics ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueStats {
        private BigDecimal totalRevenue;
        private Integer totalOrders;
        private BigDecimal averageOrderValue;
        private BigDecimal totalRefunds;
        private BigDecimal netRevenue;
        private String currency;
        private RevenueByDay dailyRevenue;
        private RevenueByCategory categoryRevenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueByDay {
        private LocalDate startDate;
        private LocalDate endDate;
        private List<DailyRevenue> data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyRevenue {
        private LocalDate date;
        private BigDecimal revenue;
        private Integer orderCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueByCategory {
        private List<CategoryRevenue> data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryRevenue {
        private String category;
        private BigDecimal revenue;
        private Integer orderCount;
        private BigDecimal percentage;
    }

    // ========== Order Stats ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderStats {
        private Integer totalOrders;
        private Integer pendingPayment;
        private Integer pendingShipment;
        private Integer inTransit;
        private Integer delivered;
        private Integer completed;
        private Integer cancelled;
        private Integer refunded;
        private Map<String, Integer> ordersByStatus;
    }

    // ========== Listing Stats ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListingStats {
        private Integer totalListings;
        private Integer activeProducts;
        private Integer inactiveProducts;
        private Integer activeRooms;
        private Integer inactiveRooms;
        private List<TopListing> topProducts;
        private List<TopListing> topRooms;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopListing {
        private UUID listingId;
        private String title;
        private String type; // PRODUCT or ROOM
        private Integer orderCount;
        private BigDecimal revenue;
    }

    // ========== Recent Activity ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentActivity {
        private List<ActivityItem> items;
        private Integer totalCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityItem {
        private String type; // ORDER_CREATED, ORDER_PAID, ORDER_SHIPPED, BOOKING_CREATED, etc.
        private String description;
        private UUID referenceId;
        private Instant timestamp;
    }

    // ========== Analytics Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyticsRequest {
        private LocalDate startDate;
        private LocalDate endDate;
        private String granularity; // DAY, WEEK, MONTH
        private UUID listingId;
    }
}
