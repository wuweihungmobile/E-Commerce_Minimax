package com.nextkey.ecommerce.domain.model.tenant;

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

@Entity
@Table(name = "tenant_applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantApplication {

    // Column length constants
    private static final int BUSINESS_TYPE_LENGTH = 50;
    private static final int CONTACT_EMAIL_LENGTH = 255;
    private static final int CONTACT_PHONE_LENGTH = 20;
    private static final int BUSINESS_LICENSE_URL_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "user_id")  // nullable for Guest users
    private UUID userId;

    @Column(name = "store_name", nullable = false, length = 100)
    private String storeName;

    @Column(name = "store_description", columnDefinition = "TEXT")
    private String storeDescription;

    @Column(name = "business_type", nullable = false, length = BUSINESS_TYPE_LENGTH)
    private String businessType;

    @Column(name = "contact_email", length = CONTACT_EMAIL_LENGTH)
    private String contactEmail;

    @Column(name = "contact_phone", length = CONTACT_PHONE_LENGTH)
    private String contactPhone;

    @Column(name = "business_license_url", length = BUSINESS_LICENSE_URL_LENGTH)
    private String businessLicenseUrl;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (submittedAt == null) {
            submittedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum ApplicationStatus {
        PENDING,
        APPROVED,
        REJECTED,
        SUSPENDED
    }
}