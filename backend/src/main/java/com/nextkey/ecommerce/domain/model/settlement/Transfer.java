package com.nextkey.ecommerce.domain.model.settlement;

import java.math.BigDecimal;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 結算單審核通過（{@code APPROVED}）後，實際呼叫 Stripe {@code Transfer.create} 轉給賣家
 * Connect 帳戶的明細記錄（Sprint 80，AI-2416 Phase D-2）。
 *
 * <p>{@code settlementStatementId} 唯一，一張結算單僅一筆 transfer 記錄（冪等）；
 * {@code transferAmount} 為觸發當下 {@code SettlementStatement.netSettlementAmount} 的快照。
 */
@Entity
@Table(name = "transfers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transfer {

    private static final int DECIMAL_PRECISION = 14;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "settlement_statement_id", nullable = false)
    private UUID settlementStatementId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "stripe_transfer_id")
    private String stripeTransferId;

    @Column(name = "transfer_amount", nullable = false, precision = DECIMAL_PRECISION, scale = 2)
    private BigDecimal transferAmount;

    @Column(length = 3, nullable = false)
    @Builder.Default
    private String currency = "TWD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransferStatus status = TransferStatus.PENDING;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum TransferStatus {
        PENDING,
        COMPLETED,
        FAILED,
        // 賣家 Connect 帳戶尚未完成 onboarding，無法轉帳；結算單維持 APPROVED，支援事後補建
        SKIPPED_ONBOARDING_INCOMPLETE
    }
}
