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
    //
    // Sprint 113（DEF-051）：最後兩個 addStock() / deductStock() 也隨 M16 ERP 的手動異動與採購入庫
    // 改走原子 UPDATE 而移除，理由同上。deductStock() 另有一個非移除不可的理由——它同時扣減
    // reserved_qty，那是**訂單出貨**的語意；ERP 的報廢／盤虧／調撥出庫依 PRD §6.7.4 只該動
    // total_qty，共用同一個方法正是那個缺陷的來源。
    //
    // 至此本實體不再提供任何數量異動方法：所有 product_inventory 的數量寫入都在
    // ProductInventoryRepository 的原生 UPDATE 中，各自帶著自己的語意與條件。
}
