package com.nextkey.ecommerce.domain.model.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
 * 採購單 (Purchase Order)
 * PRD §8.1.5, §9.15
 */
@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {

    private static final int DECIMAL_PRECISION = 12;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "po_number", unique = true, nullable = false)
    private String poNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private POStatus status = POStatus.DRAFT;

    @Column(name = "total_amount", precision = DECIMAL_PRECISION, scale = 2)
    private BigDecimal totalAmount;

    @Column
    @Builder.Default
    private String currency = "TWD";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderItem> items = new ArrayList<>();

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

    public enum POStatus {
        DRAFT,
        SUBMITTED,
        PENDING_APPROVAL,
        APPROVED,
        REJECTED,
        ORDER,
        SHIPPED,
        RECEIVED,
        PARTIALLY_RECEIVED,
        CANCELLED
    }

    /**
     * 狀態機轉換驗證
     */
    public boolean canSubmit() {
        return this.status == POStatus.DRAFT;
    }

    public boolean canReceive() {
        return this.status == POStatus.SUBMITTED
                || this.status == POStatus.PARTIALLY_RECEIVED
                || this.status == POStatus.APPROVED;
    }

    public boolean canCancel() {
        return this.status == POStatus.DRAFT
                || this.status == POStatus.SUBMITTED
                || this.status == POStatus.PENDING_APPROVAL
                || this.status == POStatus.APPROVED;
    }

    /**
     * 是否可由 SUPER_ADMIN 核准/駁回（Sprint 85，PRD §6.7.2）
     */
    public boolean canReview() {
        return this.status == POStatus.PENDING_APPROVAL;
    }
}