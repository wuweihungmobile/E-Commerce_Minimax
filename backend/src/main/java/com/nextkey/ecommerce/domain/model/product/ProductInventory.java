package com.nextkey.ecommerce.domain.model.product;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

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

    public boolean hasAvailableStock(int quantity) {
        return (totalQty - reservedQty) >= quantity;
    }

    public void reserve(int quantity) {
        this.reservedQty += quantity;
    }

    public void release(int quantity) {
        this.reservedQty = Math.max(0, this.reservedQty - quantity);
    }

    public void addStock(int quantity) {
        this.totalQty += quantity;
    }

    public void deductStock(int quantity) {
        this.totalQty -= quantity;
        this.reservedQty = Math.max(0, this.reservedQty - quantity);
    }
}
