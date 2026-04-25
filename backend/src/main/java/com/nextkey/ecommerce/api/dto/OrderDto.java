package com.nextkey.ecommerce.api.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 訂單 DTO
 */
public class OrderDto {

    // ========== Create Order Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull(message = "Order type is required")
        private String orderType; // PRODUCT or ROOM

        @Size(max = 500, message = "Shipping address too long")
        private String shippingAddress;

        @Size(max = 200, message = "Recipient name too long")
        private String shippingRecipientName;

        @Size(max = 50, message = "Phone too long")
        private String shippingPhone;

        private String notes;

        // For ROOM (booking)
        private UUID listingId; // Required for ROOM type orders
        private LocalDate checkInDate;
        private LocalDate checkOutDate;
        private Integer guestCount;
        private String guestName;
        private String guestPhone;
        private String guestEmail;
        private String specialRequests;
    }

    // ========== Update Order Status Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateStatusRequest {
        @NotNull(message = "Target status is required")
        private String targetStatus;

        private String reason;
    }

    // ========== Order Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderResponse {
        private UUID id;
        private UUID tenantId;
        private UUID userId;
        private String orderType;
        private String status;
        private BigDecimal totalAmount;
        private String currency;
        private String shippingAddress;
        private String shippingRecipientName;
        private String shippingPhone;
        private String notes;
        private Integer guestCount;
        private String guestName;
        private String guestPhone;
        private String guestEmail;
        private List<OrderItemResponse> items;
        private Instant createdAt;
        private Instant updatedAt;
    }

    // ========== Order Item Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private UUID id;
        private UUID listingId;
        private String listingTitle;
        private String coverImageUrl;
        private UUID skuId;
        private String skuCode;
        private String specName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }

    // ========== Order List Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderListResponse {
        private UUID id;
        private String orderType;
        private String status;
        private BigDecimal totalAmount;
        private String currency;
        private Integer itemCount;
        private Instant createdAt;
    }

    // ========== Order State Log ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StateLogResponse {
        private UUID id;
        private UUID orderId;
        private Integer sequence;
        private String fromStatus;
        private String toStatus;
        private UUID changedBy;
        private String reason;
        private Instant createdAt;
    }

    // ========== Booking Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingResponse {
        private UUID id;
        private UUID tenantId;
        private UUID userId;
        private UUID roomListingId;
        private String roomTitle;
        private LocalDate checkInDate;
        private LocalDate checkOutDate;
        private Integer guestCount;
        private String status;
        private BigDecimal totalAmount;
        private String guestName;
        private String guestPhone;
        private String guestEmail;
        private String specialRequests;
        private Instant createdAt;
        private Instant updatedAt;
    }
}