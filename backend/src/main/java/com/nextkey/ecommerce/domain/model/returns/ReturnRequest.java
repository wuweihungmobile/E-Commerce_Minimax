package com.nextkey.ecommerce.domain.model.returns;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 退貨申請（Sprint 118，DEF-044）。
 *
 * <p>解決的問題：已付款訂單退款後，先前扣除的庫存不會回補，那批貨在系統裡永遠消失。
 * 使用者拍板的作法是「**店家實際收到貨、確認可售後才回補**」——退錢與收貨是兩件事，
 * 退款當下就把庫存加回可售池，等於在賣還沒拿回來的東西。
 *
 * <p>🔴 <b>與退款流程完全獨立</b>（使用者決策）：退款管錢、退貨管貨，互不強制。
 * 可以只退錢不收貨（單價低於運費時貼還不如），也可以只收貨不退錢（換貨）。
 * 因此本實體**不參照 payments**，也不會去改訂單的付款狀態。
 */
@Entity
@Table(name = "return_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** 給人看的退貨單號（如 RMA-20260903-0001）。 */
    @Column(name = "return_number", nullable = false, unique = true, length = 50)
    private String returnNumber;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /** 提出申請的買家。 */
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReturnStatus status = ReturnStatus.REQUESTED;

    /** 買家填寫的退貨原因。 */
    @Column(name = "reason")
    private String reason;

    /** 店家駁回時填寫的理由。 */
    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "received_by")
    private UUID receivedBy;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReturnRequestItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addItem(final ReturnRequestItem item) {
        item.setReturnRequest(this);
        this.items.add(item);
    }

    /**
     * 退貨單狀態。
     *
     * <pre>
     *   REQUESTED ──核准──→ APPROVED ──收貨確認──→ RECEIVED（此時才回補庫存）
     *       │                   │
     *       ├──駁回──→ REJECTED  │
     *       └──買家撤回──→ CANCELLED ←──買家撤回──┘
     * </pre>
     *
     * <p>REJECTED／RECEIVED／CANCELLED 皆為終態。庫存**只在進入 RECEIVED 時**變動——
     * 這正是本功能的重點：核准不等於收到貨。
     */
    public enum ReturnStatus {
        REQUESTED, APPROVED, REJECTED, RECEIVED, CANCELLED
    }

    /**
     * 不再佔用「可退數量」額度的終態：被駁回或買家撤回的單，那些貨從來沒退成，
     * 買家應該可以重新申請。單一事實來源——查詢與服務層都由此推導。
     */
    public static final Set<ReturnStatus> QUOTA_RELEASING_STATUSES =
            Collections.unmodifiableSet(EnumSet.of(ReturnStatus.REJECTED, ReturnStatus.CANCELLED));
}
