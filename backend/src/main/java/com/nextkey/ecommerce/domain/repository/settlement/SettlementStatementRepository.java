package com.nextkey.ecommerce.domain.repository.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;

@Repository
public interface SettlementStatementRepository extends JpaRepository<SettlementStatement, UUID> {

    Page<SettlementStatement> findByTenantIdOrderByPeriodStartDesc(UUID tenantId, Pageable pageable);

    @Query("""
            SELECT s FROM SettlementStatement s
            WHERE s.tenantId = :tenantId
            AND s.periodStart >= :startDate
            AND s.periodEnd <= :endDate
            ORDER BY s.periodStart DESC
            """)
    List<SettlementStatement> findByTenantIdAndPeriodStartBetween(
            @Param("tenantId") UUID tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    Optional<SettlementStatement> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SettlementStatement> findByStatus(SettlementStatement.SettlementStatus status);

    @Query("SELECT s FROM SettlementStatement s WHERE s.status = :status ORDER BY s.generatedAt DESC")
    Page<SettlementStatement> findByStatusOrderByGeneratedAtDesc(
            @Param("status") SettlementStatement.SettlementStatus status,
            Pageable pageable);

    @Query("""
            SELECT s FROM SettlementStatement s
            WHERE s.tenantId = :tenantId AND s.status = :status
            ORDER BY s.generatedAt DESC
            """)
    Page<SettlementStatement> findByTenantIdAndStatusOrderByGeneratedAtDesc(
            @Param("tenantId") UUID tenantId,
            @Param("status") SettlementStatement.SettlementStatus status,
            Pageable pageable);

    boolean existsByTenantIdAndStatementNumber(UUID tenantId, String statementNumber);

    /**
     * 依多個狀態查詢，跨租戶（Sprint 90，M07 結算逆轉候選清單用，SUPER_ADMIN/CFO 專屬）
     */
    @Query("SELECT s FROM SettlementStatement s WHERE s.status IN :statuses ORDER BY s.generatedAt DESC")
    Page<SettlementStatement> findByStatusInOrderByGeneratedAtDesc(
            @Param("statuses") List<SettlementStatement.SettlementStatus> statuses,
            Pageable pageable);

    /**
     * 依租戶 + 多個狀態查詢（Sprint 90，M07 結算逆轉候選清單依租戶篩選用）
     */
    @Query("""
            SELECT s FROM SettlementStatement s
            WHERE s.tenantId = :tenantId AND s.status IN :statuses
            ORDER BY s.generatedAt DESC
            """)
    Page<SettlementStatement> findByTenantIdAndStatusInOrderByGeneratedAtDesc(
            @Param("tenantId") UUID tenantId,
            @Param("statuses") List<SettlementStatement.SettlementStatus> statuses,
            Pageable pageable);

    /**
     * 依租戶 + 訂單日期，找出涵蓋該日期的結算單（Sprint 86，PRD §6.2.1 跨週期退款判斷用）
     */
    @Query("""
            SELECT s FROM SettlementStatement s
            WHERE s.tenantId = :tenantId
            AND s.periodStart <= :orderDate
            AND s.periodEnd >= :orderDate
            """)
    Optional<SettlementStatement> findByTenantIdAndPeriodCovering(
            @Param("tenantId") UUID tenantId,
            @Param("orderDate") LocalDate orderDate);

    /**
     * 原子套用退款扣除（Sprint 105，DEF-053）。
     *
     * <p>取代原本 {@code SettlementAdjustmentService.applyDirectDeduction} 的
     * 「讀出金額 → 記憶體加減 → {@code save()}」。同一結算期間內**不同訂單**的退款會落在
     * **同一列**結算單上，形成讀後寫窗口。
     *
     * <p>與 Sprint 103（DEF-050 庫存）的關鍵差異：{@code ProductInventory} 帶 {@code @Version}，
     * 併發時會拋 {@code ObjectOptimisticLockingFailureException}——會失敗、看得見。
     * {@code SettlementStatement} **沒有** {@code @Version}，因此修復前的失效模式是
     * **靜默丟失更新**。{@code M07SettlementRefundConcurrencyIntegrationTest} 實測：
     * 10 筆併發退款只有 **1 筆**存活（10 條執行緒全讀到 0，全寫回 10.00），
     * 且**零例外、零日誌**——賣家因此拿到本應扣除的 90% 退款金額。
     *
     * <p>兩個金額欄位在同一敘述內相對增減，資料庫保證原子性；
     * {@code COALESCE} 對齊既有語意（欄位 DEFAULT 0，但允許為 NULL）。
     * 本表無 {@code version} 欄位，故不需比照 Sprint 103 推進版號。
     *
     * @return 受影響筆數；1 表示扣除成功，0 表示該 id 不存在
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE settlement_statements
               SET total_refunds = COALESCE(total_refunds, 0) + :refundAmount,
                   net_settlement_amount = COALESCE(net_settlement_amount, 0) - :refundAmount,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = :id
            """, nativeQuery = true)
    int applyRefundDeduction(@Param("id") UUID id, @Param("refundAmount") BigDecimal refundAmount);

    /**
     * 原子搶占結算單逆轉發起權（Sprint 137 DEF-138，PAID→REVERSAL_PENDING）。取代原本
     * {@code SettlementReversalService.initiateReversal} 「讀 status → 檢查 PAID → setStatus →
     * save」：兩個併發發起請求（可能分屬 SUPER_ADMIN 與 CFO 兩種不同角色）都可能通過上面的快照檢查，
     * 後 commit 者會用自己的 initiator 身份悄悄覆寫先前已寫入的發起人/角色，可能讓
     * {@code confirmReversal} 之後比對「確認人角色須與發起人不同」時，比對到的是被覆寫後的錯誤角色，
     * 削弱雙重授權（dual authorization）的安全承諾。
     *
     * @return 受影響筆數；1 代表本次成功搶占發起權，0 代表已被另一併發請求搶先發起
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE SettlementStatement s SET s.status = :newStatus WHERE s.id = :id AND s.status = :expectedStatus")
    int updateStatusIfCurrent(@Param("id") UUID id,
            @Param("expectedStatus") SettlementStatement.SettlementStatus expectedStatus,
            @Param("newStatus") SettlementStatement.SettlementStatus newStatus);
}