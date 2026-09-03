package com.nextkey.ecommerce.api.dto.erp;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.*;

/**
 * 手動庫存異動請求
 * PRD §9.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementRequest {

    private static final int REFERENCE_NUMBER_MAX_LENGTH = 100;

    @NotNull(message = "SKU ID is required")
    private UUID skuId;

    @NotNull(message = "Movement type is required")
    private String movementType; // ADJUST_PLUS / ADJUST_MINUS

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    /** 店家自行填寫的參考單號（選填，如「盤點單 2026-09」）。 */
    @Size(max = REFERENCE_NUMBER_MAX_LENGTH, message = "Reference number too long")
    private String referenceNumber;

    private String notes;
}