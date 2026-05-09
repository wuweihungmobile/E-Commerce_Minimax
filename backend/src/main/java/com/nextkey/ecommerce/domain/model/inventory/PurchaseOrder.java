package com.nextkey.ecommerce.domain.model.inventory;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    @Column(name = "total_amount", precision = 12, scale = 2)
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
        APPROVED,
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
        return this.status == POStatus.SUBMITTED || this.status == POStatus.PARTIALLY_RECEIVED;
    }

    public boolean canCancel() {
        return this.status == POStatus.DRAFT || this.status == POStatus.SUBMITTED;
    }
}