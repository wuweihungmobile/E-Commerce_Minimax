package com.nextkey.ecommerce.domain.repository;

import java.math.BigDecimal;
import java.time.Instant;
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

import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.payment.Payment;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /**
     * 併發防護（Sprint 221）：條件式狀態轉換（{@code WHERE status = expectedStatus}），取代「讀狀態→判斷→
     * setStatus→save」。與 {@code OrderRepository.updateStatusIfCurrent} 同一模式。回傳受影響列數：1 代表本次成功轉換，
     * 0 代表狀態已被另一併發呼叫搶先轉換，呼叫端應拒絕本次請求（同一筆訂房被併發付款兩次，僅一邊該視為成功）。
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Booking b SET b.status = :newStatus WHERE b.id = :id AND b.status = :expectedStatus")
    int updateStatusIfCurrent(@Param("id") UUID id, @Param("expectedStatus") Booking.BookingStatus expectedStatus,
            @Param("newStatus") Booking.BookingStatus newStatus);

    /**
     * 逾時未付款訂房的 ID（Sprint 225，DEF-311），依付款期限由舊到新。條件與 {@link #cancelIfPaymentExpired} 相同：
     * 仍是 CREATED、有付款期限且已過 {@code now}，並排除已有成功付款、或在 {@code checkoutCutoff} 之後開始過結帳
     * （PROCESSING）的訂房。付款期限為 NULL 的是歷史訂房，永遠不會被撈到。查詢只用來挑候選，真正把關的是取消時
     * 那條條件式 UPDATE。
     */
    @Query("""
            SELECT b.id FROM Booking b
            WHERE b.status = :created AND b.paymentDueAt IS NOT NULL AND b.paymentDueAt < :now
              AND NOT EXISTS (
                  SELECT 1 FROM Payment p
                  WHERE p.bookingId = b.id
                    AND (p.status = :success OR (p.status = :processing AND p.createdAt > :checkoutCutoff)))
            ORDER BY b.paymentDueAt ASC
            """)
    List<UUID> findExpiredUnpaidBookingIds(@Param("created") Booking.BookingStatus created,
            @Param("now") Instant now, @Param("checkoutCutoff") Instant checkoutCutoff,
            @Param("success") Payment.PaymentStatus success, @Param("processing") Payment.PaymentStatus processing,
            Pageable pageable);

    /**
     * 逾時未付款訂房的原子取消（Sprint 225，DEF-311）：把「仍是 CREATED、付款期限已過、沒有成功付款、也沒有
     * checkoutCutoff 之後開始的結帳」與狀態轉換寫在同一條 UPDATE，與買家付款的 {@link #updateStatusIfCurrent}
     * 搶同一個狀態，恰好一邊成功。回傳 1 表示本次取消成功，0 表示已不符條件。Sprint 227 起一併記錄取消時間與取消方
     * （{@code cancelledBy}，逾時取消為 SYSTEM）。
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Booking b SET b.status = :cancelled, b.cancelledAt = :now, b.cancelledBy = :cancelledBy
            WHERE b.id = :id AND b.status = :created AND b.paymentDueAt IS NOT NULL AND b.paymentDueAt < :now
              AND NOT EXISTS (
                  SELECT 1 FROM Payment p
                  WHERE p.bookingId = b.id
                    AND (p.status = :success OR (p.status = :processing AND p.createdAt > :checkoutCutoff)))
            """)
    int cancelIfPaymentExpired(@Param("id") UUID id, @Param("created") Booking.BookingStatus created,
            @Param("cancelled") Booking.BookingStatus cancelled, @Param("cancelledBy") Booking.CancelledBy cancelledBy,
            @Param("now") Instant now, @Param("checkoutCutoff") Instant checkoutCutoff,
            @Param("success") Payment.PaymentStatus success, @Param("processing") Payment.PaymentStatus processing);

    /**
     * no-show 候選（Sprint 246，DEF-352；PRD §17.4.6 Q15）：仍是 PAID、入住日已到或已過，依入住日由舊到新。
     * 精確的「入住時刻＋寬限小時數是否已過」依房源的 {@code checkInTime} 而不同（本查詢不 join 房源，避免時區換算
     * 寫在 SQL 裡），由呼叫端（{@code BookingNoShowService}）逐筆用 {@code BookingRefundPolicy.checkInInstant}
     * 精算後決定是否取消；查詢只用來縮小候選範圍，真正把關的是取消時的條件式 UPDATE（{@link #cancelIfNoShow}）。
     */
    @Query("SELECT b.id FROM Booking b WHERE b.status = :paid AND b.checkInDate <= :today ORDER BY b.checkInDate ASC")
    List<UUID> findPotentialNoShowBookingIds(@Param("paid") Booking.BookingStatus paid,
            @Param("today") LocalDate today, Pageable pageable);

    /**
     * no-show 自動取消的原子更新（Sprint 246，DEF-352）：把「仍是 PAID」與狀態轉換寫在同一條 UPDATE，與店家的
     * 入住（{@link #updateStatusIfCurrent}）搶同一個狀態，恰好一邊成功。回傳 1 表示本次取消成功，0 表示已不符條件
     * （已入住、已被取消…，交由呼叫端視為冪等跳過）。{@code refundStatus} 不在此變更，維持建構時的預設
     * {@code NONE}——no-show 不退款是使用者的明確決定（DEF-351），不經過自動退款排程。
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Booking b SET b.status = :cancelled, b.cancelledAt = :now, b.cancelledBy = :cancelledBy
            WHERE b.id = :id AND b.status = :paid
            """)
    int cancelIfNoShow(@Param("id") UUID id, @Param("paid") Booking.BookingStatus paid,
            @Param("cancelled") Booking.BookingStatus cancelled, @Param("cancelledBy") Booking.CancelledBy cancelledBy,
            @Param("now") Instant now);

    /**
     * 訂房目前在資料庫裡的狀態。以純量查詢取得而非載入實體：open-in-view 讓同一個請求共用 persistence context，
     * 先前載入的實體不會反映條件式 UPDATE 的結果（與 {@code OrderRepository.findStatusById} 同一理由）。
     */
    @Query("SELECT b.status FROM Booking b WHERE b.id = :id")
    Optional<Booking.BookingStatus> findStatusById(@Param("id") UUID id);

    /** 訂房目前的退款進度（純量查詢，理由同 {@link #findStatusById}）。 */
    @Query("SELECT b.refundStatus FROM Booking b WHERE b.id = :id")
    Optional<Booking.RefundStatus> findRefundStatusById(@Param("id") UUID id);

    /**
     * 等待退款（{@code refundStatus = PENDING}）的訂房 ID，由小到大（Sprint 227，DEF-312）。以 id 為游標逐頁取出
     * （{@code id > afterId}），理由同 {@code OrderRepository.findRefundingOrderIdsAfter}。
     */
    @Query("SELECT b.id FROM Booking b WHERE b.refundStatus = :pending AND b.id > :afterId ORDER BY b.id ASC")
    List<UUID> findPendingRefundBookingIds(@Param("pending") Booking.RefundStatus pending,
            @Param("afterId") UUID afterId, Pageable pageable);

    /**
     * 退款完成：{@code PENDING → COMPLETED}（條件式 UPDATE）。回傳 0 代表已被另一個處理者搶先完成，呼叫端視為冪等。
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Booking b SET b.refundStatus = :completed WHERE b.id = :id AND b.refundStatus = :pending")
    int completeRefundIfPending(@Param("id") UUID id, @Param("pending") Booking.RefundStatus pending,
            @Param("completed") Booking.RefundStatus completed);

    /**
     * 把一筆「已取消、尚未安排退款」的訂房排入自動退款（{@code NONE → PENDING}，並記下應退金額）。用於付款成功時訂房已被取消
     * （DEF-308）：錢已收、訂房沒成立，全額退回。條件式 UPDATE 同時確認訂房仍是 CANCELLED 且尚未有退款安排；
     * 回傳 0 代表不符條件（不是已取消，或已有退款安排）。
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Booking b SET b.refundStatus = :pending, b.refundAmount = :amount
            WHERE b.id = :id AND b.status = :cancelled AND b.refundStatus = :none
            """)
    int requestRefundIfCancelled(@Param("id") UUID id, @Param("cancelled") Booking.BookingStatus cancelled,
            @Param("none") Booking.RefundStatus none, @Param("pending") Booking.RefundStatus pending,
            @Param("amount") BigDecimal amount);

    /**
     * 管理員人工退款（Sprint 246，DEF-354；PRD §17.4.6 Q15「店家漏標的例外」）：已取消、原本依 no-show
     * 規則不退款（{@code refundStatus = NONE}）的訂房，經管理員核實為店家漏標入住造成的誤取消後，
     * 直接完成退款（{@code NONE → COMPLETED}），不經過 {@code PENDING} 中繼態——這是管理員即時人工動作，
     * 不是排程處理。條件式 UPDATE 同時確認訂房仍是 CANCELLED 且尚未有退款安排；回傳 0 代表不符條件
     * （已被另一次退款處理，或狀態已變），呼叫端須拒絕並要求重試。
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Booking b SET b.refundStatus = :completed, b.refundAmount = :amount
            WHERE b.id = :id AND b.status = :cancelled AND b.refundStatus = :none
            """)
    int completeManualRefundIfNone(@Param("id") UUID id, @Param("cancelled") Booking.BookingStatus cancelled,
            @Param("none") Booking.RefundStatus none, @Param("completed") Booking.RefundStatus completed,
            @Param("amount") BigDecimal amount);

    /**
     * 某租戶「尚未結算、且已處於可結算狀態」的訂房，限建立時間早於 {@code createdBefore}（Sprint 247，DEF-353；
     * 比照 {@code OrderRepository.findUnsettledByTenantIdAndStatusInAndCreatedAtBefore}）。
     *
     * <p>可結算條件：{@code status = COMPLETED}（退房完成）；或 {@code status = CANCELLED} 且
     * {@code refundStatus = NONE} 且存在一筆 {@code SUCCESS}／{@code PARTIALLY_REFUNDED} 付款——
     * {@code refundStatus = NONE} 本身無法分辨「從未收款」（逾時／買家在 CREATED 取消）與「已收款、
     * 依政策不退款」（Q14 入住前 24 小時內取消、no-show），必須額外確認存在成功付款，否則從未收款的
     * 取消訂房會被誤計為商家收益。
     */
    @Query("""
            SELECT b FROM Booking b WHERE b.tenant.id = :tenantId AND b.settledStatementId IS NULL
              AND b.createdAt < :createdBefore
              AND (b.status = :completed
                   OR (b.status = :cancelled AND b.refundStatus = :none
                       AND EXISTS (SELECT 1 FROM Payment p WHERE p.bookingId = b.id
                                   AND p.status IN (:success, :partiallyRefunded))))
            """)
    List<Booking> findUnsettledEligibleByTenantIdAndCreatedAtBefore(
            @Param("tenantId") UUID tenantId,
            @Param("createdBefore") Instant createdBefore,
            @Param("completed") Booking.BookingStatus completed,
            @Param("cancelled") Booking.BookingStatus cancelled,
            @Param("none") Booking.RefundStatus none,
            @Param("success") Payment.PaymentStatus success,
            @Param("partiallyRefunded") Payment.PaymentStatus partiallyRefunded);

    /**
     * 原子認領訂房（Sprint 247，DEF-353；比照 {@code OrderRepository.markSettled}）：只有
     * {@code settled_statement_id IS NULL} 的訂房會被標記。回傳實際標記筆數，用法與併發防護理由同訂單版。
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE bookings SET settled_statement_id = :statementId"
            + " WHERE id IN (:ids) AND settled_statement_id IS NULL", nativeQuery = true)
    int markSettled(@Param("ids") List<UUID> ids, @Param("statementId") UUID statementId);

    /**
     * 釋放某結算單認領的所有訂房（Sprint 247，DEF-353；比照 {@code OrderRepository.releaseOrdersOfStatement}）：
     * 結算單被駁回時呼叫，讓這些訂房在下一次結算重新被撈到。
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE bookings SET settled_statement_id = NULL WHERE settled_statement_id = :statementId",
            nativeQuery = true)
    int releaseBookingsOfStatement(@Param("statementId") UUID statementId);

    /** 訂房被哪張結算單結算；尚未結算（或訂房不存在）回 empty。供退款調整定位結算單。 */
    @Query("SELECT b.settledStatementId FROM Booking b WHERE b.id = :bookingId AND b.settledStatementId IS NOT NULL")
    Optional<UUID> findSettledStatementId(@Param("bookingId") UUID bookingId);

    Page<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Booking> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Optional<Booking> findByIdAndUserId(UUID bookingId, UUID userId);

    Optional<Booking> findByIdAndTenantId(UUID bookingId, UUID tenantId);

    boolean existsByUserIdAndStatusNotIn(UUID userId, List<Booking.BookingStatus> statuses);
}