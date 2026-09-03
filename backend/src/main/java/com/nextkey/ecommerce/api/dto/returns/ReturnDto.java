package com.nextkey.ecommerce.api.dto.returns;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 退貨申請相關 DTO（Sprint 118，DEF-044）。
 */
public final class ReturnDto {

    private static final int REASON_MAX_LENGTH = 1000;

    private ReturnDto() {
    }

    /** 買家提出退貨申請。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {

        @NotNull(message = "Order ID is required")
        private UUID orderId;

        @Size(max = REASON_MAX_LENGTH, message = "Reason too long")
        private String reason;

        @NotEmpty(message = "At least one item is required")
        @Valid
        private List<CreateItem> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateItem {

        @NotNull(message = "Order item ID is required")
        private UUID orderItemId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;
    }

    /** 店家駁回退貨申請。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RejectRequest {

        @Size(max = REASON_MAX_LENGTH, message = "Reason too long")
        private String rejectionReason;
    }

    /**
     * 店家收貨確認。
     *
     * <p>🔴 這是**唯一會動到庫存**的一步：可售數量回補、不可售數量另寫一筆 SCRAP 抵銷。
     * 核准（APPROVED）不動庫存——那正是本功能存在的理由。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiveRequest {

        @NotEmpty(message = "At least one item is required")
        @Valid
        private List<ReceiveItem> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiveItem {

        @NotNull(message = "Return item ID is required")
        private UUID itemId;

        @NotNull(message = "Sellable quantity is required")
        @Min(value = 0, message = "Sellable quantity cannot be negative")
        private Integer sellableQty;

        @NotNull(message = "Unsellable quantity is required")
        @Min(value = 0, message = "Unsellable quantity cannot be negative")
        private Integer unsellableQty;
    }

    /** 退貨單回應。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {

        private UUID id;
        private String returnNumber;
        private UUID orderId;
        private UUID customerId;
        private UUID tenantId;
        private String status;
        private String reason;
        private String rejectionReason;
        private Instant reviewedAt;
        private Instant receivedAt;
        private Instant createdAt;
        private List<ItemResponse> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemResponse {

        private UUID id;
        private UUID orderItemId;
        private UUID skuId;
        private Integer requestedQty;
        /** 收貨確認後才有值。 */
        private Integer sellableQty;
        private Integer unsellableQty;
    }
}
