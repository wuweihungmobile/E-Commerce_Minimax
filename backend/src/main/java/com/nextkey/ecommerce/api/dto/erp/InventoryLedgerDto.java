package com.nextkey.ecommerce.api.dto.erp;

import java.time.Instant;
import java.util.UUID;

import lombok.*;

/**
 * 庫存台帳 DTO
 * PRD §6.7.2「庫存台帳：按 SKU 查看即時庫存、庫存異動記錄」（P0）、§9.15
 *
 * <p>Sprint 116（DEF-066）欄位對齊：數量三欄原本叫 {@code totalQty}／{@code reservedQty}／
 * {@code availableQty}，但前端讀的是 {@code quantity}／{@code reservedQuantity}／
 * {@code availableQuantity}——後端**自己的** {@code InventoryService.InventoryDetailDto} 也用後者，
 * 三者之中只有本 DTO 是例外，故往多數（也是前端實際契約）對齊。
 *
 * <p>同時移除 {@code location}：資料來源改為 {@code product_inventory} 後該欄**沒有任何來源**，
 * 留著只會是一個永遠顯示「-」的欄位——這正是 DEF-066 這類缺陷得以長期存活的土壤。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryLedgerDto {

    private UUID skuId;

    private String skuCode;

    private String productName;

    private Integer quantity;

    private Integer reservedQuantity;

    private Integer availableQuantity;

    private Integer lowStockThreshold;

    /** 最近一次入庫時間，由 {@code stock_movements} 推導（Sprint 115 補齊流水帳後才有意義）。 */
    private Instant lastInboundDate;

    /** 最近一次出庫時間，由 {@code stock_movements} 推導。 */
    private Instant lastOutboundDate;

    private Instant updatedAt;
}
