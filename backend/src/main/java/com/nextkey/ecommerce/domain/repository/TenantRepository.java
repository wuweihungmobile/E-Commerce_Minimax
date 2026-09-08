package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.tenant.Tenant;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID>, JpaSpecificationExecutor<Tenant> {

    Optional<Tenant> findBySlug(String slug);

    Optional<Tenant> findByIdAndStatus(UUID id, Tenant.TenantStatus status);

    List<Tenant> findByStatus(Tenant.TenantStatus status);

    Optional<Tenant> findByStripeConnectAccountId(String stripeConnectAccountId);

    boolean existsBySlug(String slug);

    /**
     * 原子條件式狀態轉換（Sprint 137 DEF-117）。取代 {@code updateTenantStatus} 原本「讀
     * currentStatus → validateStatusTransition → setStatus → save」：兩個 SUPER_ADMIN 併發下達
     * 不同狀態轉換（例如都以 SUSPENDED 為起點，各自要求轉 ACTIVE 與 TERMINATED）都可能通過上面的
     * 快照檢查，最後 commit 者用舊快照覆寫另一邊剛寫入的新狀態。
     *
     * @return 受影響筆數；1 代表本次成功轉換，0 代表狀態已被另一併發請求搶先轉換
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Tenant t SET t.status = :newStatus WHERE t.id = :id AND t.status = :expectedStatus")
    int updateStatusIfCurrent(@Param("id") UUID id, @Param("expectedStatus") Tenant.TenantStatus expectedStatus,
            @Param("newStatus") Tenant.TenantStatus newStatus);
}
