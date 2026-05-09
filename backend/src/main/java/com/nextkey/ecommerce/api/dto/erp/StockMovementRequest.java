package com.nextkey.ecommerce.api.dto.erp;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

/**
 * 手動庫存異動請求
 * PRD §9.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementRequest {

    @NotNull(message = "SKU ID is required")
    private UUID skuId;

    @NotNull(message = "Movement type is required")
    private String movementType; // ADJUST_PLUS / ADJUST_MINUS

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    private String notes;
}