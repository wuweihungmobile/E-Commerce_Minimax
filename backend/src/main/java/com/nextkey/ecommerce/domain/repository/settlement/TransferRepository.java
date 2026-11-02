package com.nextkey.ecommerce.domain.repository.settlement;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.settlement.Transfer;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findBySettlementStatementId(UUID settlementStatementId);

    Page<Transfer> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Optional<Transfer> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Transfer> findByStripeTransferId(String stripeTransferId);

    /**
     * 條件式刪除一筆 transfer（Sprint 323，DEF-447）：只有該筆仍處於 {@code statuses} 之一才刪，回傳受影響筆數。
     *
     * <p>重試端點（{@code TransferService.retryFailedTransfer}）用它「搶占」舊記錄：兩位管理員同時重試同一筆失敗的撥款，
     * 都讀到同一筆 {@code FAILED} 的 transfer、都打算刪掉它。原本用實體層級的 {@code delete}，輸家在先到者提交之後刪不到列，
     * Hibernate 的列數檢查丟 {@code ObjectOptimisticLockingFailureException}（HTTP 500 E-9900，實測）。
     * 這裡由資料庫在 DELETE 生效的那一刻判斷，被先到者的列鎖擋住的 DELETE 在鎖釋放後會對最新版本重新評估 {@code WHERE}
     * （PostgreSQL READ COMMITTED），列已不存在就回 0，呼叫端據此給輸家乾淨的 {@code E-5014}。
     *
     * <p>不清除持久化上下文（{@code clearAutomatically} 預設 false）：呼叫端接著還要用同一交易裡已載入的結算單。
     *
     * @return 受影響筆數；1 代表本次成功搶占，0 代表已被另一個併發請求刪除、或狀態已不在 {@code statuses}
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Transfer t WHERE t.id = :id AND t.status IN :statuses")
    int deleteByIdAndStatusIn(@Param("id") UUID id, @Param("statuses") Collection<Transfer.TransferStatus> statuses);
}
