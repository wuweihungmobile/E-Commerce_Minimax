package com.nextkey.ecommerce.api.dto.erp;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * 庫存異動 DTO
 * PRD §6.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementDto {

    private UUID id;

    private UUID tenantId;

    private UUID skuId;

    private String skuCode;

    private String productName;

    private String movementType; // INBOUND, OUTBOUND, ADJUST_PLUS, ADJUST_MINUS...

    private Integer quantity;

    private Integer beforeTotalQty;

    private Integer afterTotalQty;

    private String referenceType; // PURCHASE_ORDER, ORDER, MANUAL...

    private String referenceNumber; // 給使用者看的參考編號

    private UUID referenceId;

    private String notes;

    private UUID createdBy;

    private Instant createdAt;
}