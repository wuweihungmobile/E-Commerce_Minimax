package com.nextkey.ecommerce.api.dto.erp;

import java.util.UUID;

import lombok.*;

/**
 * 低庫存預警 DTO
 * PRD §6.7.2「庫存預警：低庫存 / 過期預警通知」、§9.15
 *
 * <p>Sprint 116（DEF-066）欄位對齊：{@code currentQty} → {@code currentQuantity}（前端讀的名字）。
 *
 * <p>移除 {@code reorderPoint}／{@code safetyStock}：前端警示卡原本顯示這兩個數字，但資料來源改為
 * {@code product_inventory} 後只有單一的 {@code low_stock_threshold}，**沒有補貨點與安全庫存之分**。
 * 硬把同一個數字填進兩欄會讓畫面看起來有兩種門檻、實際卻是同一個——寧可誠實顯示一個門檻加嚴重度。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowStockAlertDto {

    private UUID skuId;

    private String skuCode;

    private String productName;

    /** 目前可售量（{@code available_qty}）。 */
    private Integer currentQuantity;

    private Integer lowStockThreshold;

    /** LOW / CRITICAL；可售量低於門檻一半時為 CRITICAL。 */
    private String severity;
}
