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
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 跨結算週期退款調整單（PRD §6.2.1，Sprint 86）
 *
 * <p>當已 APPROVED/PAID 的結算單所屬訂單發生退款時產生，於下一次
 * {@code SettlementGenerator.generateStatementForTenant} 生成新結算單時一併折入。
 */
@Entity
@Table(name = "adjustment_statements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "original_statement_id", nullable = false)
    private UUID originalStatementId;

    @Column(name = "adjustment_type", nullable = false)
    @Builder.Default
    private String adjustmentType = "REFUND_DEDUCTION";

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AdjustmentStatus status = AdjustmentStatus.PENDING;

    @Column(name = "applied_statement_id")
    private UUID appliedStatementId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public enum AdjustmentStatus {
        PENDING,
        APPLIED
    }
}
