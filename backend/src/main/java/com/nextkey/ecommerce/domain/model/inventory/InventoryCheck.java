package com.nextkey.ecommerce.domain.model.inventory;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_checks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "check_number", unique = true, nullable = false)
    private String checkNumber;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CheckStatus status = CheckStatus.IN_PROGRESS;

    @Column(name = "scheduled_date")
    private java.time.LocalDate scheduledDate;

    @Column(name = "completed_date")
    private java.time.LocalDate completedDate;

    @Column(name = "checked_by")
    private UUID checkedBy;

    @Column(name = "notes")
    private String notes;

    @Column(name = "variance_count")
    @Builder.Default
    private Integer varianceCount = 0;

    @Column(name = "total_items_checked")
    @Builder.Default
    private Integer totalItemsChecked = 0;

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

    public enum CheckStatus {
        IN_PROGRESS,
        COMPLETED,
        CANCELLED
    }
}
