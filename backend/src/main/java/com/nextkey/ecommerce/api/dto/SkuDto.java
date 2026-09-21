package com.nextkey.ecommerce.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商品規格（SKU）DTO（Sprint 178）。
 *
 * <p>PRD 原將「SKU 多規格管理」列為 Phase 2+ 排除項目，但 ERP 庫存子系統（stock_movements、
 * product_inventory、採購單收貨入庫、低庫存預警）早已建置完成且依賴 SKU 存在；本檔補上唯一
 * 缺少的一環——SKU 的建立/管理入口，使既有庫存管線得以真正被觸發。
 */
public class SkuDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        // 對齊 V1 schema product_skus.sku_code VARCHAR(50)，避免超長輸入在資料庫層以
        // 未經處理的 500（value too long for type character varying）失敗
        @NotBlank(message = "SKU code is required")
        @Size(max = 50, message = "SKU code must be at most 50 characters")
        private String skuCode;

        // 對齊 V1 schema product_skus.spec_name VARCHAR(100)
        @Size(max = 100, message = "Spec name must be at most 100 characters")
        private String specName;

        @DecimalMin(value = "0.01", message = "Price override must be positive")
        private BigDecimal priceOverride;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @Size(max = 100, message = "Spec name must be at most 100 characters")
        private String specName;

        @DecimalMin(value = "0.01", message = "Price override must be positive")
        private BigDecimal priceOverride;

        // ACTIVE / INACTIVE，比照 SupplierDto 既有的狀態切換慣例，不提供硬刪除
        // （stock_movements.sku_id 一旦有異動記錄即無法刪除，見 V1 schema FK 無 ON DELETE CASCADE）
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private UUID id;
        private UUID listingId;
        private String skuCode;
        private String specName;
        private BigDecimal priceOverride;
        private String status;
        private Integer totalQty;
        private Integer reservedQty;
        private Integer availableQty;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
