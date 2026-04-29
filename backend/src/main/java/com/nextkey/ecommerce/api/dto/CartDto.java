package com.nextkey.ecommerce.api.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 購物車 DTO
 */
public class CartDto {

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
        @Max(value = 999, message = "Quantity cannot exceed 999")
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
        @Max(value = 999, message = "Quantity cannot exceed 999")
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
}