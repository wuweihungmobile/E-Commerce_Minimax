package com.nextkey.ecommerce.domain.model.product;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductInventory {

    @Id
    @Column(name = "sku_id")
    private UUID skuId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "sku_id")
    private ProductSku sku;

    @Column(name = "total_qty")
    @Builder.Default
    private Integer totalQty = 0;

    @Column(name = "reserved_qty")
    @Builder.Default
    private Integer reservedQty = 0;

    @Column(name = "available_qty", insertable = false, updatable = false)
    private Integer availableQty;

    @Version
    @Builder.Default
    private Long version = 0L;

    @Column(name = "low_stock_threshold")
    @Builder.Default
    private Integer lowStockThreshold = 10;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Sprint 103（DEF-050）：hasAvailableStock() / reserve() / release() 已隨訂單流程改用
    // ProductInventoryRepository 的原子 UPDATE 而移除，未保留為無人呼叫的方法——留著等於留一個
    // 讓人無徵兆退回「載入 → 改欄位 → save()」讀後寫的入口（與 S101 移除 computeDiscount 舊多載、
    // S102 移除 incrementUsageCount 同一個「大聲失敗」理由）。
    // 以下兩個仍保留：M16 ERP 的進貨入庫與庫存異動（StockMovementService / PurchaseOrderService）
    // 仍走 JPA save，其併發特性另屬 ERP 後台情境，不在 DEF-050 範圍內。

    public void addStock(final int quantity) {
        this.totalQty += quantity;
    }

    public void deductStock(final int quantity) {
        this.totalQty -= quantity;
        this.reservedQty = Math.max(0, this.reservedQty - quantity);
    }
}
