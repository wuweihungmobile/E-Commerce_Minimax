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
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "invited_at")
    private Instant invitedAt;

    @Column(name = "joined_at")
    private Instant joinedAt;

    /** PRD §8.2.3：INVITED（待被邀請人確認）/ ACTIVE（已加入）/ REMOVED（已移除或已拒絕邀請）。 */
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MemberStatus status = MemberStatus.ACTIVE;

    @PrePersist
    protected void onCreate() {
        if (invitedAt == null) {
            invitedAt = Instant.now();
        }
        if (status == MemberStatus.ACTIVE && joinedAt == null) {
            joinedAt = Instant.now();
        }
    }

    public enum StoreRole {
        STORE_OWNER,
        STORE_STAFF,
        STORE_MANAGER
    }

    public enum MemberStatus {
        INVITED,
        ACTIVE,
        REMOVED
    }
}