package com.nextkey.ecommerce.domain.model.inventory;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 採購單明細 (Purchase Order Item)
 * PRD §8.1.5
 */
@Entity
@Table(name = "purchase_order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderItem {

    private static final int DECIMAL_PRECISION = 12;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "sku_id")
    private UUID skuId;

    @Column(name = "sku_code")
    private String skuCode;

    @Column(name = "product_name")
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "ordered_qty", nullable = false)
    @Builder.Default
    private Integer orderedQty = 0;

    @Column(name = "received_quantity")
    @Builder.Default
    private Integer receivedQuantity = 0;

    @Column(name = "unit_cost", precision = DECIMAL_PRECISION, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "subtotal", precision = DECIMAL_PRECISION, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "created_at")
    private java.time.Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = java.time.Instant.now();
        if (receivedQuantity == null) {
            receivedQuantity = 0;
        }
    }

    /**
     * 計算小計
     */
    public void calculateSubtotal() {
        if (quantity != null && unitCost != null) {
            this.subtotal = unitCost.multiply(BigDecimal.valueOf(quantity));
        }
    }
}