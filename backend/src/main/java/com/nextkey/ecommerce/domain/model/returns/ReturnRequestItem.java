package com.nextkey.ecommerce.domain.model.returns;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 退貨申請的品項（Sprint 118，DEF-044）。
 *
 * <p>{@code requestedQty} 由買家申請時填；{@code sellableQty}／{@code unsellableQty}
 * 由店家在**收貨確認**時填，兩者相加不得超過申請數量（買家可能少寄）。
 */
@Entity
@Table(name = "return_request_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "return_request_id", nullable = false)
    private ReturnRequest returnRequest;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "requested_qty", nullable = false)
    private Integer requestedQty;

    /** 收貨確認後：可再售的數量，回補庫存（寫 RETURN 流水帳）。 */
    @Column(name = "sellable_qty")
    private Integer sellableQty;

    /**
     * 收貨確認後：損壞或缺件、不可再售的數量。
     *
     * <p>依使用者決策「記錄但不回補」：這些數量**不會**留在可售庫存裡，但台帳上看得到
     * ——實作方式見 {@code ReturnRequestService.receiveReturn} 的說明。
     */
    @Column(name = "unsellable_qty")
    private Integer unsellableQty;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    /** 實際收到的數量（可售＋不可售）；尚未收貨時為 0。 */
    public int receivedQty() {
        return (sellableQty != null ? sellableQty : 0) + (unsellableQty != null ? unsellableQty : 0);
    }
}
