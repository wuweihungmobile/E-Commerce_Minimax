package com.nextkey.ecommerce.domain.model.inventory;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private MovementType movementType;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type")
    private ReferenceType referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reason")
    private String reason;

    @Column(name = "performed_by")
    private UUID performedBy;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public enum MovementType {
        PURCHASE_RECEIPT,
        SALE,
        RESERVATION,
        RELEASE,
        ADJUSTMENT,
        DAMAGE,
        THEFT,
        RETURN,
        TRANSFER_IN,
        TRANSFER_OUT
    }

    public enum ReferenceType {
        PURCHASE_ORDER,
        ORDER,
        INVENTORY_CHECK,
        TRANSFER
    }
}
