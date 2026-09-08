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

import org.hibernate.annotations.DynamicUpdate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sprint 137 DEF-140/142/144/164：{@code @DynamicUpdate} 讓 Hibernate 只 UPDATE 本次交易內
 * 實際被 setter 改動過的欄位，避免 {@code updateMemberRole}（改 storeRole）與
 * {@code removeMember}/{@code acceptInvite}（改 status）併發時，後 commit 者用自己交易一開始
 * 讀到的舊快照悄悄覆寫另一邊剛寫入的欄位（例如剛被移除的成員因併發角色變更而「復活」）。
 * 同欄位（status）互斥轉換的競態另由 {@link com.nextkey.ecommerce.domain.repository.TenantMemberRepository}
 * 的原子 CAS 方法把關，@DynamicUpdate 無法解決「兩邊都合法寫同一欄位」的問題。
 */
@Entity
@Table(name = "tenant_members")
@DynamicUpdate
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