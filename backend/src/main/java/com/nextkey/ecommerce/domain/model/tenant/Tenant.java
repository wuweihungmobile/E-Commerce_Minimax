package com.nextkey.ecommerce.domain.model.tenant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    // Default commission rate
    private static final double DEFAULT_COMMISSION_RATE = 0.05;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TenantStatus status = TenantStatus.PENDING_REVIEW;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "commission_rate")
    @Builder.Default
    private Double commissionRate = DEFAULT_COMMISSION_RATE;

    @Column(name = "stripe_connect_account_id")
    private String stripeConnectAccountId;

    @Column(name = "connect_onboarding_status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ConnectOnboardingStatus connectOnboardingStatus = ConnectOnboardingStatus.NOT_STARTED;

    @Column(name = "connect_charges_enabled", nullable = false)
    @Builder.Default
    private Boolean connectChargesEnabled = false;

    @Column(name = "connect_payouts_enabled", nullable = false)
    @Builder.Default
    private Boolean connectPayoutsEnabled = false;

    /**
     * 採購單審批金額上限（PRD §6.7.2，Sprint 85）。null = 不啟用審批門檻，超過此金額的採購單需 SUPER_ADMIN 核准。
     */
    @Column(name = "purchase_order_approval_threshold", precision = 12, scale = 2)
    private BigDecimal purchaseOrderApprovalThreshold;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

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

    public enum TenantStatus {
        PENDING_REVIEW, ACTIVE, REJECTED, SUSPENDED, TERMINATED
    }

    /** Stripe Connect Express 帳戶 onboarding 狀態（Sprint 53 AI-2413 Phase D-1）。 */
    public enum ConnectOnboardingStatus {
        NOT_STARTED, PENDING, COMPLETE
    }
}
