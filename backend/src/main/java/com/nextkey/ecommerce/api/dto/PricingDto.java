package com.nextkey.ecommerce.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.*;

/**
 * 動態定價 DTO
 */
public class PricingDto {

    // ========== Pricing Rule Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRuleRequest {
        private UUID roomListingId;

        private UUID listingId;

        @NotNull(message = "Rule type is required")
        private PricingRuleType ruleType;

        @NotBlank(message = "Rule name is required")
        private String ruleName;

        private Integer priority;

        @NotNull(message = "Config is required")
        private Map<String, Object> config;

        @NotNull(message = "Valid from date is required")
        private LocalDate validFrom;

        @NotNull(message = "Valid to date is required")
        private LocalDate validTo;

        private Boolean isActive;

        /**
         * 同類型規則時間重疊時，是否確認覆蓋現有 active 規則（PRD §5.5.1）。
         * 預設 false/null：重疊時拒絕（E-4001），需房東明確確認才軟刪除舊規則。
         */
        private Boolean confirmOverride;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRuleRequest {
        private String ruleName;
        private Integer priority;
        private Map<String, Object> config;
        private LocalDate validFrom;
        private LocalDate validTo;
        private Boolean isActive;
    }

    public enum PricingRuleType {
        WEEKDAY_WEEKEND,
        SEASONAL,
        EARLY_BIRD,
        LONG_STAY,
        MANUAL_OVERRIDE,
        LAST_MINUTE
    }

    // ========== Pricing Rule Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleResponse {
        private UUID ruleId;
        private UUID tenantId;
        private UUID roomListingId;
        private UUID listingId;
        private String ruleType;
        private String ruleName;
        private Integer priority;
        private Map<String, Object> config;
        private LocalDate validFrom;
        private LocalDate validTo;
        private Boolean isActive;
        private Instant createdAt;
        private Instant updatedAt;
    }

    // ========== Calculate Price Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalculatePriceRequest {
        @NotNull(message = "Room listing ID is required")
        private UUID roomListingId;

        @NotNull(message = "Check-in date is required")
        private LocalDate checkInDate;

        @NotNull(message = "Check-out date is required")
        private LocalDate checkOutDate;

        private Integer guestCount;

        /**
         * 下單日期（用於早鳥/末班車「今天 vs 入住日」的提前/臨近天數判斷）。
         * 選填，未提供時服務端以今日計（AI-2401）。
         */
        private LocalDate bookingDate;
    }

    // ========== Calculate Price Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceBreakdown {
        private LocalDate date;
        private BigDecimal basePrice;
        private BigDecimal adjustedPrice;
        private String appliedRuleName;
        private String adjustmentType; // PERCENTAGE, FIXED_AMOUNT
        private BigDecimal adjustmentValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalculatePriceResponse {
        private UUID roomListingId;
        private LocalDate checkInDate;
        private LocalDate checkOutDate;
        private int nights;
        private BigDecimal baseTotal;
        private BigDecimal adjustedTotal;
        private BigDecimal discount;
        private String currency;
        private java.util.List<PriceBreakdown> breakdown;
    }

    // ========== Calendar Price Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SetCalendarPriceRequest {
        @NotNull(message = "Room listing ID is required")
        private UUID roomListingId;

        @NotNull(message = "Date is required")
        private LocalDate date;

        @NotNull(message = "Price is required")
        @Positive(message = "Price must be positive")
        private BigDecimal price;

        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalendarPriceResponse {
        private UUID roomListingId;
        private LocalDate date;
        private BigDecimal price;
        private String priceType; // BASE, SEASONAL, MANUAL
        private String appliedRuleName;
        private Instant updatedAt;
    }

    // ========== Rule Override Request/Response ==========
    // T-M12-03: 手動覆蓋價格端點

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleOverrideRequest {
        @NotNull(message = "Start date is required")
        private LocalDate startDate;

        @NotNull(message = "End date is required")
        private LocalDate endDate;

        @NotNull(message = "Override price is required")
        @Positive(message = "Override price must be positive")
        private BigDecimal overridePrice;

        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleOverrideResponse {
        private UUID ruleId;
        private UUID roomListingId;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal overridePrice;
        private String reason;
        private String status;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EffectivePriceResponse {
        private UUID listingId;
        private LocalDate checkDate;
        private int stayDays;
        private BigDecimal basePrice;
        private BigDecimal effectivePrice;
        private String appliedRuleType;
        private UUID appliedRuleId;
        // 套用的規則名（AI-2403，供購物車/訂單折扣顯示，與 ROOM appliedRuleName 對齊）
        private String appliedRuleName;
    }
}
