package com.nextkey.ecommerce.domain.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.order.Order;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * 併發防護：條件式狀態轉換（{@code WHERE status = expectedStatus}），取代「讀狀態→
     * OrderStateMachine 檢查→setStatus→save」。回傳受影響列數：1 代表本次成功轉換，
     * 0 代表狀態已被另一併發呼叫搶先轉換（例如同一張訂單被併發觸發 CONFIRMED 與 REFUNDING
     * 兩種不同的下一步狀態），呼叫端應拒絕本次請求，而非用各自的舊快照互相覆寫。
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Order o SET o.status = :newStatus WHERE o.id = :id AND o.status = :expectedStatus")
    int updateStatusIfCurrent(@Param("id") UUID id, @Param("expectedStatus") Order.OrderStatus expectedStatus,
            @Param("newStatus") Order.OrderStatus newStatus);

    /**
     * 併發防護（DEF-132，ReturnRequestService.createReturnRequest）：以
     * {@code SELECT ... FOR UPDATE} 鎖住訂單列，序列化「讀各品項已申請退貨總量→比對可退量→
     * 建立新退貨單」這段複合操作。可退量檢查是跨列 SUM 聚合（{@code
     * ReturnRequestRepository.sumActiveRequestedQtyByOrderItem}），無法用單一 DB 唯一約束
     * 兜底；兩個併發的退貨申請都可能讀到彼此 INSERT 之前的舊總量，各自通過檢查，使同一
     * 訂單品項的退貨申請總量超過實際購買量。比照既有 {@code UserRepository.findByIdForUpdate}
     * 模式。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    Page<Order> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Order> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND o.status = :status")
    Page<Order> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") Order.OrderStatus status, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.tenant.id = :tenantId AND o.status = :status")
    Page<Order> findByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") Order.OrderStatus status, Pageable pageable);

    Optional<Order> findByIdAndUserId(UUID orderId, UUID userId);

    Optional<Order> findByIdAndTenantId(UUID orderId, UUID tenantId);

    @Query("SELECT o FROM Order o WHERE o.tenant.id = :tenantId")
    List<Order> findByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT o FROM Order o WHERE o.tenant.id = :tenantId AND o.createdAt >= :start AND o.createdAt <= :end")
    List<Order> findByTenantIdAndCreatedAtBetween(
            @Param("tenantId") UUID tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.tenant.id = :tenantId AND o.createdAt >= :start AND o.createdAt <= :end")
    int countByTenantIdAndCreatedAtBetween(
            @Param("tenantId") UUID tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.tenant.id = :tenantId AND o.status = :status")
    int countByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") Order.OrderStatus status);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.tenant.id = :tenantId AND o.createdAt >= :after")
    long countByTenantIdAndCreatedAtAfter(@Param("tenantId") UUID tenantId, @Param("after") Instant after);

    @Query("SELECT o FROM Order o WHERE o.tenant.id = :tenantId ORDER BY o.createdAt DESC")
    List<Order> findTopByTenantIdOrderByCreatedAtDesc(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o " +
            "WHERE o.tenant.id = :tenantId AND o.status = :status AND o.createdAt >= :after")
    BigDecimal sumTotalAmountByTenantIdAndStatusAndCreatedAtAfter(
            @Param("tenantId") UUID tenantId,
            @Param("status") Order.OrderStatus status,
            @Param("after") Instant after);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.tenant.id = :tenantId AND o.status IN :statuses")
    long countByTenantIdAndStatusIn(
            @Param("tenantId") UUID tenantId,
            @Param("statuses") List<Order.OrderStatus> statuses);

    boolean existsByUserIdAndStatusNotIn(UUID userId, List<Order.OrderStatus> statuses);
}