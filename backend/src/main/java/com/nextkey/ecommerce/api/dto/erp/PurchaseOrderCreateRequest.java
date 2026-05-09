package com.nextkey.ecommerce.api.dto.erp;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 建立採購單請求
 * PRD §9.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderCreateRequest {

    @NotNull(message = "Supplier ID is required")
    private UUID supplierId;

    private String notes;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<PurchaseOrderItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseOrderItemRequest {
        @NotNull(message = "Listing ID is required")
        private UUID listingId;

        private UUID skuId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        @NotNull(message = "Unit cost is required")
        @DecimalMin(value = "0.01", message = "Unit cost must be at least 0.01")
        private BigDecimal unitCost;
    }
}