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
 * - RETURN: 退貨入庫，PRD 未定義語意，僅供既有資料顯示（見 MovementType.RETURN）
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

    /**
     * 異動類型，值域即 PRD §6.7.4 異動類型表（Sprint 114／DEF-063 對齊）。
     *
     * <p>Sprint 114 之前這裡是另一組命名（{@code PURCHASE_RECEIPT}／{@code SALE}／{@code ADJUSTMENT}／
     * {@code DAMAGE}／{@code THEFT}…），與 PRD、與本類別自己的 javadoc、與 {@code StockMovementDto}
     * 的欄位註解、與前端 {@code StockMovementType} 全部對不上，而 {@code StockMovementRequest.movementType}
     * 是 {@code String} 直接進 {@code valueOf()}，中間沒有轉換層，於是前端 7 個選項有 5 個必定拿到
     * E_7005。既有資料由 {@code V71__Align_Stock_Movement_Types_To_PRD.sql} 轉換。
     */
    public enum MovementType {
        /** 採購入庫 (+total_qty)：由採購單收貨產生，不開放手動 */
        INBOUND,
        /** 訂單出貨 (-total_qty, -reserved_qty)：由訂單流程產生，不開放手動 */
        OUTBOUND,
        /** 訂單建立預留 (+reserved_qty)：由訂單流程產生，不開放手動 */
        RESERVE,
        /** 訂單取消釋放 (-reserved_qty)：由訂單流程產生，不開放手動 */
        RELEASE,
        /** 盤盈調整 (+total_qty) */
        ADJUST_PLUS,
        /** 盤虧調整 (-total_qty) */
        ADJUST_MINUS,
        /** 調撥入庫 (+total_qty) */
        TRANSFER_IN,
        /** 調撥出庫 (-total_qty) */
        TRANSFER_OUT,
        /** 報廢出庫 (-total_qty) */
        SCRAP,
        /**
         * 退貨入庫。<b>PRD §6.7.4 未定義此型的庫存語意</b>，僅保留供既有資料顯示，
         * 不得新建——退款是否回補庫存屬 DEF-044 的業務決策，在該項拍板前不預設語意。
         */
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
        return this.movementType == MovementType.INBOUND
            || this.movementType == MovementType.TRANSFER_IN
            || this.movementType == MovementType.ADJUST_PLUS
            || this.movementType == MovementType.RETURN;
    }

    public boolean isOutbound() {
        return this.movementType == MovementType.OUTBOUND
            || this.movementType == MovementType.TRANSFER_OUT
            || this.movementType == MovementType.ADJUST_MINUS
            || this.movementType == MovementType.SCRAP;
    }
}