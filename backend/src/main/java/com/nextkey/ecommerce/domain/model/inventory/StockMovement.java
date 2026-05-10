package com.nextkey.ecommerce.domain.model.inventory;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 庫存異動記錄 (Stock Movement)
 * PRD §6.7, §9.15
 *
 * 異動類型:
 * - INBOUND: 採購入庫 (+total_qty)
 * - OUTBOUND: 訂單出貨 (-total_qty, -reserved_qty)
 * - RESERVE: 訂單建立預留 (+reserved_qty)
 * - RELEASE: 訂單取消釋放 (-reserved_qty)
 * - ADJUST_PLUS: 盤盈調整 (+total_qty)
 * - ADJUST_MINUS: 盤虧調整 (-total_qty)
 * - TRANSFER_OUT: 調撥出庫 (-total_qty)
 * - TRANSFER_IN: 調撥入庫 (+total_qty)
 * - SCRAP: 報廢出庫 (-total_qty)
 */
@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private MovementType movementType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "before_total_qty")
    private Integer beforeTotalQty;

    @Column(name = "after_total_qty")
    private Integer afterTotalQty;

    @Column(name = "balance_after", nullable = false)
    @Builder.Default
    private Integer balanceAfter = 0;

    @Column(name = "before_reserved_qty")
    private Integer beforeReservedQty;

    @Column(name = "after_reserved_qty")
    private Integer afterReservedQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type")
    private ReferenceType referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "order_item_id")
    private UUID orderItemId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public enum MovementType {
        // 採購入庫 (對應資料庫 PURCHASE_RECEIPT)
        PURCHASE_RECEIPT,
        // 訂單出貨 (對應資料庫 SALE)
        SALE,
        // 訂單建立預留 (對應資料庫 RESERVATION)
        RESERVATION,
        // 訂單取消釋放 (對應資料庫 RELEASE)
        RELEASE,
        // 盤盈調整 (對應資料庫 ADJUSTMENT)
        ADJUSTMENT,
        // 盤虧調整 (對應資料庫 DAMAGE)
        DAMAGE,
        // 調撥入庫 (對應資料庫 TRANSFER_IN)
        TRANSFER_IN,
        // 調撥出庫 (對應資料庫 TRANSFER_OUT)
        TRANSFER_OUT,
        // 報廢 (對應資料庫 THEFT)
        THEFT,
        // 退貨 (對應資料庫 RETURN)
        RETURN
    }

    public enum ReferenceType {
        PURCHASE_ORDER,
        ORDER,
        INVENTORY_CHECK,
        TRANSFER,
        MANUAL
    }

    /**
     * 異動方向判定
     */
    public boolean isInbound() {
        return this.movementType == MovementType.PURCHASE_RECEIPT
            || this.movementType == MovementType.TRANSFER_IN
            || this.movementType == MovementType.ADJUSTMENT
            || this.movementType == MovementType.RETURN;
    }

    public boolean isOutbound() {
        return this.movementType == MovementType.SALE
            || this.movementType == MovementType.TRANSFER_OUT
            || this.movementType == MovementType.DAMAGE
            || this.movementType == MovementType.THEFT;
    }
}