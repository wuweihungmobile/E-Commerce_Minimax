package com.nextkey.ecommerce.api.dto.erp;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 採購單 DTO
 * PRD §9.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderDto {

    private UUID id;

    private String poNumber;

    private UUID supplierId;

    private String supplierName;

    private String status; // DRAFT / SUBMITTED / PARTIAL_RECEIVED / RECEIVED / CANCELLED

    private BigDecimal totalAmount;

    private String currency;

    private String expectedDeliveryDate;

    private String notes;

    private List<PurchaseOrderItemDto> items;

    private Instant submittedAt;

    private Instant receivedAt;

    private Instant createdAt;

    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseOrderItemDto {
        private UUID id;
        private UUID listingId;
        private UUID skuId;
        private String skuCode;
        private String productName;
        private Integer quantity;
        private Integer receivedQuantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }
}