package com.nextkey.ecommerce.domain.model.settlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "settlement_statements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "tenant_id", insertable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "statement_number", nullable = false)
    private String statementNumber;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "total_orders")
    @Builder.Default
    private Integer totalOrders = 0;

    @Column(name = "total_gmv", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalGmv = BigDecimal.ZERO;

    @Column(name = "total_refunds", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalRefunds = BigDecimal.ZERO;

    @Column(name = "commission_amount", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal commissionAmount = BigDecimal.ZERO;

    @Column(name = "net_settlement_amount", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal netSettlementAmount = BigDecimal.ZERO;

    @Column(name = "currency")
    @Builder.Default
    private String currency = "TWD";

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private SettlementStatus status = SettlementStatus.PENDING;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "notes")
    private String notes;

    @Column(name = "adjustment_amount", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal adjustmentAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_initiated_by")
    private User reversalInitiatedBy;

    @Column(name = "reversal_initiated_by_role")
    private String reversalInitiatedByRole;

    @Column(name = "reversal_requested_at")
    private Instant reversalRequestedAt;

    @Column(name = "reversal_reason")
    private String reversalReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum SettlementStatus {
        PENDING,
        PENDING_REVIEW,
        APPROVED,
        REJECTED,
        PAID,
        FAILED,
        REVERSAL_PENDING,
        REVERSED
    }
}