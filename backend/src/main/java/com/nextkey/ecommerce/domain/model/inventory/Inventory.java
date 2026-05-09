package com.nextkey.ecommerce.domain.model.inventory;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "total_qty", nullable = false)
    @Builder.Default
    private Integer totalQty = 0;

    @Column(name = "reserved_qty", nullable = false)
    @Builder.Default
    private Integer reservedQty = 0;

    @Column(name = "available_qty", nullable = false)
    @Builder.Default
    private Integer availableQty = 0;

    @Column(name = "safety_stock")
    @Builder.Default
    private Integer safetyStock = 0;

    @Column(name = "reorder_point")
    @Builder.Default
    private Integer reorderPoint = 0;

    @Column(name = "sku_code")
    private String skuCode;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "location")
    private String location;

    @Column(name = "last_inbound_date")
    private Instant lastInboundDate;

    @Column(name = "last_outbound_date")
    private Instant lastOutboundDate;

    @Version
    private Long version; // Optimistic lock

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        availableQty = totalQty - reservedQty;
    }
}
