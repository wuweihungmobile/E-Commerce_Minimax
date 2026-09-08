package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.tenant.TenantMember;

@Repository
public interface TenantMemberRepository extends JpaRepository<TenantMember, UUID> {

    List<TenantMember> findByUserId(UUID userId);

    List<TenantMember> findByTenantId(UUID tenantId);

    Optional<TenantMember> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    boolean existsByTenantIdAndUserId(UUID tenantId, UUID userId);

    boolean existsByTenantIdAndUserIdAndStoreRole(UUID tenantId, UUID userId, TenantMember.StoreRole storeRole);

    long countByTenantId(UUID tenantId);

    List<TenantMember> findByTenantIdAndStatusNot(UUID tenantId, TenantMember.MemberStatus status);

    List<TenantMember> findByUserIdAndStatus(UUID userId, TenantMember.MemberStatus status);

    /**
     * 原子條件式狀態轉換（Sprint 137 DEF-140/142/164/165）。用於 acceptInvite（INVITED→ACTIVE）、
     * declineInvite（INVITED→REMOVED）、removeMember（{@code 讀到的當下狀態}→REMOVED）、
     * inviteMember 重新邀請已移除成員（REMOVED→INVITED）。兩個併發呼叫（例如同一份邀請被
     * 同時接受與拒絕）都可能通過各自的快照檢查，改用條件式原子 UPDATE 確保只有一邊真的轉換成功。
     *
     * @return 受影響筆數；1 代表本次成功轉換，0 代表狀態已被另一併發請求搶先轉換
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE TenantMember m SET m.status = :newStatus WHERE m.id = :id AND m.status = :expectedStatus")
    int updateStatusIfCurrent(@Param("id") UUID id, @Param("expectedStatus") TenantMember.MemberStatus expectedStatus,
            @Param("newStatus") TenantMember.MemberStatus newStatus);

    /**
     * 原子角色變更，僅在成員未被移除時才生效（Sprint 137 DEF-144）。取代原本「檢查
     * status != REMOVED → setStoreRole → save」：owner 呼叫 updateMemberRole 與另一個併發的
     * removeMember 都可能通過各自的快照檢查；此處用 {@code status <> REMOVED} 條件式 UPDATE，
     * 確保成員若已被併發移除，角色變更直接無效（回傳 0），而不是悄悄寫入一個已被移除成員的新角色。
     *
     * @return 受影響筆數；1 代表本次成功變更角色，0 代表成員已被另一併發請求移除
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE TenantMember m SET m.storeRole = :newRole WHERE m.id = :id AND m.status <> :removedStatus")
    int updateRoleIfNotRemoved(@Param("id") UUID id, @Param("newRole") TenantMember.StoreRole newRole,
            @Param("removedStatus") TenantMember.MemberStatus removedStatus);
}