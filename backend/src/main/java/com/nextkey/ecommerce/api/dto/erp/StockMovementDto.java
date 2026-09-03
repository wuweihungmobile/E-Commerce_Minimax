package com.nextkey.ecommerce.api.dto.erp;

import java.time.Instant;
import java.util.UUID;

import lombok.*;

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

    /**
     * 店家自行填寫的參考單號（僅手動異動；如「盤點單 2026-09」）。Sprint 117（DEF-064）起真的會被存下來。
     */
    private String referenceNumber;

    /**
     * 來源單據識別（唯讀、由系統推導）：採購收貨為採購單號、訂單異動為訂單 id 前八碼、手動異動為 null。
     *
     * <p>與 {@link #referenceNumber} 是兩件事：前者是系統產生的異動回指來源，後者是店家自己記的單號。
     * Sprint 117（DEF-064）依使用者拍板的選項 C，兩者並存。
     */
    private String sourceDocument;

    private UUID referenceId;

    private String notes;

    private UUID createdBy;

    private Instant createdAt;
}