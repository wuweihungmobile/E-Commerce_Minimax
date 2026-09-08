package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.tenant.TenantApplication;

@Repository
public interface TenantApplicationRepository extends JpaRepository<TenantApplication, UUID> {

    List<TenantApplication> findByUserId(UUID userId);

    List<TenantApplication> findByStatus(TenantApplication.ApplicationStatus status);

    Optional<TenantApplication> findByUserIdAndStatus(UUID userId, TenantApplication.ApplicationStatus status);

    boolean existsByUserIdAndStatusIn(UUID userId, List<TenantApplication.ApplicationStatus> statuses);

    long countByStatus(TenantApplication.ApplicationStatus status);

    /**
     * 原子搶占審核權（Sprint 137 DEF-114/DEF-115）。取代原本「讀 status → 檢查 PENDING → setStatus →
     * save」：兩個併發審核請求（approve/approve、approve/reject 或 reject/reject）都可能通過上面的
     * 快照檢查，各自繼續往下走——approve 分支甚至會各自建立出一個獨立的 ACTIVE Tenant + STORE_OWNER
     * membership（孤兒資源）。改用條件式原子 UPDATE，只有真正搶到轉換的一邊回傳 1 並繼續往下執行，
     * 另一邊回傳 0 應立即拒絕，不得再建立任何下游資源。
     *
     * @return 受影響筆數；1 代表本次成功轉換，0 代表已被另一併發請求搶先審核
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE TenantApplication a SET a.status = :newStatus WHERE a.id = :id AND a.status = :expectedStatus")
    int updateStatusIfCurrent(@Param("id") UUID id,
            @Param("expectedStatus") TenantApplication.ApplicationStatus expectedStatus,
            @Param("newStatus") TenantApplication.ApplicationStatus newStatus);
}