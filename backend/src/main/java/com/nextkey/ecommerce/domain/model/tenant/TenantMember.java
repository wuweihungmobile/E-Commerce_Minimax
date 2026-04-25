package com.nextkey.ecommerce.domain.model.tenant;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "store_role", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StoreRole storeRole = StoreRole.STORE_OWNER;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
    }

    public enum StoreRole {
        STORE_OWNER,
        STORE_STAFF,
        STORE_MANAGER
    }
}