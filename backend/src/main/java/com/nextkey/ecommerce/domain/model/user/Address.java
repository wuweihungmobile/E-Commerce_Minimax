package com.nextkey.ecommerce.domain.model.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 收貨地址簿（PRD §14.3.1 Phase 2-B，Sprint 87）
 *
 * <p>屬於買家個人資料，與租戶無關（買家可能向多個不同租戶下單），擁有權檢查為
 * {@code userId} 比對，不做租戶擁有權檢查。
 */
@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(nullable = false)
    private String phone;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(nullable = false)
    private String city;

    @Column
    private String district;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

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
}
