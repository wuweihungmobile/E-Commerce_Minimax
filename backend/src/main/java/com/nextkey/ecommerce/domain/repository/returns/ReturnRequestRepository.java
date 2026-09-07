package com.nextkey.ecommerce.domain.repository.returns;

import java.time.Instant;
import java.util.Collection;
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

import com.nextkey.ecommerce.domain.model.returns.ReturnRequest;

/**
 * 退貨申請 Repository（Sprint 118，DEF-044）。
 */
@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, UUID> {

    /** 買家自己的退貨申請。 */
    Page<ReturnRequest> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    /** 店家後台：本租戶的退貨申請。 */
    Page<ReturnRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    /** 同一張訂單既有的退貨申請（用於計算某品項還剩多少可退）。 */
    List<ReturnRequest> findByOrderId(UUID orderId);

    Optional<ReturnRequest> findByReturnNumber(String returnNumber);

    /**
     * 某訂單品項在「尚未終結為駁回／取消」的退貨單裡已被申請的總數。
     *
     * <p>用於擋住「同一件貨重複申請退貨」——駁回與取消的單不佔額度。
     *
     * <p>排除狀態以參數傳入而非寫在 JPQL 裡：Hibernate 6 無法解析巢狀枚舉的完整路徑
     * （{@code ReturnRequest.ReturnStatus.REJECTED} 會拋 {@code SemanticException}），
     * 且規則本身屬於領域模型，應由 {@link ReturnRequest#QUOTA_RELEASING_STATUSES} 單一處定義。
     */
    @Query("""
            SELECT COALESCE(SUM(i.requestedQty), 0)
              FROM ReturnRequest r JOIN r.items i
             WHERE i.orderItemId = :orderItemId
               AND r.status NOT IN :excludedStatuses
            """)
    int sumRequestedQtyByOrderItemExcluding(@Param("orderItemId") UUID orderItemId,
            @Param("excludedStatuses") Collection<ReturnRequest.ReturnStatus> excludedStatuses);

    /** 已佔用額度的申請總數（駁回／撤回的不計）。 */
    default int sumActiveRequestedQtyByOrderItem(final UUID orderItemId) {
        return sumRequestedQtyByOrderItemExcluding(orderItemId, ReturnRequest.QUOTA_RELEASING_STATUSES);
    }

    /**
     * 併發防護（DEF-136，ReturnRequestService.receiveReturn）：條件式狀態轉換
     * （{@code WHERE status = expectedStatus}），取代「讀狀態→判斷→setStatus→save」。
     * 回傳受影響列數：1 代表本次成功轉換（呼叫端才可繼續執行 productInventoryService.
     * applyReturnReceipt 的庫存加回）；0 代表已被另一併發呼叫搶先轉換，呼叫端應拒絕本次請求，
     * 避免同一筆退貨單被併發呼叫兩次時，庫存被重複加回（幽靈庫存）。狀態以參數傳入而非寫在
     * JPQL 裡（同檔案 sumRequestedQtyByOrderItemExcluding 的既有做法）：Hibernate 6 無法解析
     * 巢狀枚舉的完整路徑。
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE ReturnRequest r SET r.status = :newStatus, r.receivedBy = :receivedBy, "
            + "r.receivedAt = :receivedAt WHERE r.id = :id AND r.status = :expectedStatus")
    int updateStatusIfCurrent(@Param("id") UUID id, @Param("expectedStatus") ReturnRequest.ReturnStatus expectedStatus,
            @Param("newStatus") ReturnRequest.ReturnStatus newStatus, @Param("receivedBy") UUID receivedBy,
            @Param("receivedAt") Instant receivedAt);
}
