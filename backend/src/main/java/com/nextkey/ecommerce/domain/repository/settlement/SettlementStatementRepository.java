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
     * <p>Sprint 322（DEF-446）：<strong>狀態條件由資料庫在 UPDATE 生效的那一刻判斷</strong>。呼叫端（{@code SettlementAdjustmentService}）
     * 是先讀結算單狀態、再依狀態分流：只有 {@code PENDING}／{@code PENDING_REVIEW}（尚未核准撥款）才直接扣款，
     * {@code APPROVED} 以後走調整單、下一期折入。讀與 UPDATE 之間批准／駁回若搶先拿到鎖並提交，沒有條件的 UPDATE 等到鎖釋放後
     * 仍會生效——退款直接扣在已批准（將撥款）的結算單上、沒有調整單，撥款金額與結算單對不上。加上條件後，被鎖擋住的 UPDATE
     * 在鎖釋放時會對該列最新已提交的版本重新評估 WHERE（PostgreSQL READ COMMITTED），狀態已不符就回 0 列。
     *
     * @return 受影響筆數；1 表示扣除成功，0 表示該 id 不存在，<strong>或</strong>狀態已不在直接扣款範圍
     *         （呼叫端必須用 {@link #findStatusById} 重讀目前狀態再分流，不可把 0 當成「什麼都不用做」）
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE settlement_statements
               SET total_refunds = COALESCE(total_refunds, 0) + :refundAmount,
                   net_settlement_amount = COALESCE(net_settlement_amount, 0) - :refundAmount,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = :id
               AND status IN ('PENDING', 'PENDING_REVIEW')
            """, nativeQuery = true)
    int applyRefundDeduction(@Param("id") UUID id, @Param("refundAmount") BigDecimal refundAmount);

    /**
     * 只讀回結算單目前的狀態（Sprint 322，DEF-446）。純量投影，<strong>每次都查資料庫</strong>，不會回傳同一個持久化上下文裡
     * 已快取的實體：退款剛用 {@code findById} 讀過這張結算單，之後它的狀態在別的交易被改了，此時 {@code findById}
     * 仍回傳快取的舊狀態，只有這個查詢拿得到資料庫現在的狀態。結算單不存在時回 empty。
     */
    @Query("SELECT s.status FROM SettlementStatement s WHERE s.id = :id")
    Optional<SettlementStatement.SettlementStatus> findStatusById(@Param("id") UUID id);

    /**
     * 原子搶占結算單逆轉發起權（Sprint 137 DEF-138，PAID→REVERSAL_PENDING）。取代原本
     * {@code SettlementReversalService.initiateReversal} 「讀 status → 檢查 PAID → setStatus →
     * save」：兩個併發發起請求（可能分屬 SUPER_ADMIN 與 CFO 兩種不同角色）都可能通過上面的快照檢查，
     * 後 commit 者會用自己的 initiator 身份悄悄覆寫先前已寫入的發起人/角色，可能讓
     * {@code confirmReversal} 之後比對「確認人角色須與發起人不同」時，比對到的是被覆寫後的錯誤角色，
     * 削弱雙重授權（dual authorization）的安全承諾。
     *
     * <p>Sprint 320（DEF-442）：{@code SettlementReviewer} 的批准／駁回（{@code PENDING_REVIEW → APPROVED／REJECTED}）
     * 也用它搶占，原本同樣是「讀 status → 檢查 → setStatus → save」。實測（真實 PostgreSQL，兩位管理員同時送出）：
     * 批准 vs 駁回兩邊都回 200，後寫者覆寫先寫者——結算單 {@code REJECTED} 但 transfer 已建立且訂單被釋放（或相反）。
     * 呼叫端必須<strong>在任何寫入之前</strong>先搶占，輸家（回傳 0）就不會留下稽核、transfer 或訂單釋放。
     *
     * <p>Sprint 322（DEF-445）：店主提交審核（{@code PENDING → PENDING_REVIEW}）同樣用它搶占——原本沒有任何保護，兩次提交各自整列寫回，
     * 而且整列寫回會用舊金額蓋掉同時入帳的退款。
     *
     * @return 受影響筆數；1 代表本次成功搶占，0 代表已被另一併發請求搶先轉換
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE SettlementStatement s SET s.status = :newStatus WHERE s.id = :id AND s.status = :expectedStatus")
    int updateStatusIfCurrent(@Param("id") UUID id,
            @Param("expectedStatus") SettlementStatement.SettlementStatus expectedStatus,
            @Param("newStatus") SettlementStatement.SettlementStatus newStatus);
}