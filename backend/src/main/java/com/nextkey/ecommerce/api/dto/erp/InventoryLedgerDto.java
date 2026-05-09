package com.nextkey.ecommerce.api.dto.erp;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * 庫存台帳 DTO
 * PRD §9.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryLedgerDto {

    private UUID skuId;

    private String skuCode;

    private String productName;

    private Integer totalQty;

    private Integer reservedQty;

    private Integer availableQty;

    private Integer lowStockThreshold;

    private String location;

    private Instant lastInboundDate;

    private Instant lastOutboundDate;

    private Instant updatedAt;
}