package com.nextkey.ecommerce.api.dto.erp;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 確認收貨請求
 * PRD §9.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderReceiveRequest {

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<ReceiveItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiveItemRequest {
        @NotNull(message = "Item ID is required")
        private java.util.UUID itemId;

        @NotNull(message = "Received quantity is required")
        @Min(value = 0, message = "Received quantity cannot be negative")
        private Integer receivedQuantity;
    }
}