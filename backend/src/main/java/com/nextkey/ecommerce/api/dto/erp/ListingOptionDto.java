package com.nextkey.ecommerce.api.dto.erp;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import lombok.*;

/**
 * 採購單品項的 listing 選擇器選項（Sprint 129，DEF-076）。
 *
 * <p>刻意不直接回傳 {@code Listing} 實體——其 {@code owner}/{@code tenant} 為 LAZY 關聯，專案
 * 未設定 Jackson Hibernate 模組亦未關閉 open-in-view，序列化時會觸發懶載入並把整個 {@code User}
 * （含 {@code passwordHash}，無 {@code @JsonIgnore}）序列化進回應。此輕量 DTO 只挑選選擇器需要的欄位。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingOptionDto {

    private UUID id;

    private String title;

    private BigDecimal basePrice;

    private String currency;

    /**
     * 此商品已建立的 SKU 選項（Sprint 178）。原本這裡恆為空——全庫沒有任何程式碼會建立
     * {@code ProductSku}，採購單品項的 {@code skuId} 因此永遠送不出去，收貨也就永遠不會真正
     * 入庫（見 {@code PurchaseOrderService.receivePurchaseOrder} 的 {@code item.getSkuId() != null}
     * 判斷式）。商品規格管理功能補上 SKU 建立入口後，此欄位才會有真實資料。
     */
    @Builder.Default
    private List<SkuOptionDto> skus = List.of();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkuOptionDto {
        private UUID id;
        private String skuCode;
        private String specName;
    }
}
