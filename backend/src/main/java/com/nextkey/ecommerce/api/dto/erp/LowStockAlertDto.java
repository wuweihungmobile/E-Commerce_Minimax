package com.nextkey.ecommerce.api.dto.erp;

import lombok.*;

import java.util.UUID;

/**
 * 低庫存預警 DTO
 * PRD §9.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowStockAlertDto {

    private UUID skuId;

    private String skuCode;

    private String productName;

    private Integer currentQty; // available_qty

    private Integer lowStockThreshold;

    private String severity; // LOW / CRITICAL
}