package com.nextkey.ecommerce.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.*;

/**
 * 購物車 DTO
 */
public class CartDto {

    // Validation constraints
    private static final int MAX_QUANTITY = 999;

    // ========== Cart Item Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddItemRequest {
        @NotNull(message = "Listing ID is required")
        private UUID listingId;

        private UUID skuId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = MAX_QUANTITY, message = "Quantity cannot exceed 999")
        private Integer quantity;

        // ROOM 類型房源的日期範圍（可選，對 PRODUCT 類型忽略）
        private java.time.LocalDate startDate;
        private java.time.LocalDate endDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateItemRequest {
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = MAX_QUANTITY, message = "Quantity cannot exceed 999")
        private Integer quantity;
    }

    // ========== Cart Item Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemResponse {
        private String cartItemKey;
        private UUID listingId;
        private String listingName;
        private String coverImageUrl;
        private UUID skuId;
        private String skuCode;
        private String specName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
        private String listingType; // PRODUCT or ROOM
        private java.time.Instant addedAt;
        // ROOM 類型房源的日期範圍
        private java.time.LocalDate startDate;
        private java.time.LocalDate endDate;
        // 動態定價調整（AI-2403 折扣；AI-2406c 放寬含漲價）：PRODUCT 規則生效時填入（unitPrice/subtotal 已為調整後），
        // originalUnitPrice=調整前單價、discountAmount=此項有號差額（正=折扣、負=加價）、appliedRuleName=規則名、
        // priceAdjustmentType=方向（DISCOUNT/MARKUP/NONE）；無調整為 null。
        private BigDecimal originalUnitPrice;
        private BigDecimal discountAmount;
        private String appliedRuleName;
        private String priceAdjustmentType;
    }

    // ========== Cart Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartResponse {
        private UUID userId;
        private String cartId;
        private java.util.List<CartItemResponse> items;

        @JsonProperty("totalItems")
        private Integer itemCount;
        private BigDecimal totalAmount;
        private String appliedPromoCode;
        private BigDecimal discountAmount;
        private BigDecimal finalAmount;
        private String currency;
        private java.time.Instant updatedAt;
    }

    // ========== Add to Cart Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddItemResponse {
        private boolean success;
        private CartItemResponse item;
        private Integer totalItemsInCart;
        private String message;
    }

    // ========== Remove Item Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RemoveItemRequest {
        @NotNull(message = "Listing ID is required")
        private UUID listingId;

        private UUID skuId; // optional, if null removes all items for this listing
    }

    // ========== Promo Validation Result ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromoValidationResult {
        private boolean valid;
        private String promoCode;
        private String discountType; // PERCENTAGE, FIXED_AMOUNT, FREE_SHIPPING
        private BigDecimal discountValue;
        private BigDecimal maxDiscount; // 百分比折扣的最高金額限制
        private String invalidReason; // INVALID, EXPIRED, USAGE_LIMIT

        public static PromoValidationResult invalid(String reason, String message) {
            return PromoValidationResult.builder()
                    .valid(false)
                    .invalidReason(reason + ": " + message)
                    .build();
        }

        public static PromoValidationResult valid(String code, String type, BigDecimal value, BigDecimal max) {
            return PromoValidationResult.builder()
                    .valid(true)
                    .promoCode(code)
                    .discountType(type)
                    .discountValue(value)
                    .maxDiscount(max)
                    .build();
        }
    }

    // ========== Apply Promo Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplyPromoRequest {
        @NotNull(message = "Promo code is required")
        private String promoCode;
    }

    // ========== Apply Promo Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplyPromoResponse {
        private String appliedPromoCode;
        private BigDecimal discountAmount;
        private BigDecimal finalAmount;
        private String discountType;
        private BigDecimal discountValue;
    }
}