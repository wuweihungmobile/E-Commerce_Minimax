package com.nextkey.ecommerce.domain.model.logistics;

import java.math.BigDecimal;
import java.time.Instant;
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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shipping_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingTemplate {

    private static final int NAME_MAX_LENGTH = 100;
    private static final int FEE_TYPE_MAX_LENGTH = 20;
    private static final int DECIMAL_PRECISION = 10;
    private static final int DECIMAL_SCALE = 2;

    public enum FeeType {
        FIXED,
        FREE_THRESHOLD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "tenant_id", insertable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false, length = FEE_TYPE_MAX_LENGTH)
    private FeeType feeType;

    @Column(name = "fixed_amount", precision = DECIMAL_PRECISION, scale = DECIMAL_SCALE)
    private BigDecimal fixedAmount;

    @Column(name = "free_threshold", precision = DECIMAL_PRECISION, scale = DECIMAL_SCALE)
    private BigDecimal freeThreshold;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
}
