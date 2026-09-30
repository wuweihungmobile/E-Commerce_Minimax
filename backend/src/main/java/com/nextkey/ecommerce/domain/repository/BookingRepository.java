package com.nextkey.ecommerce.domain.repository;

import java.time.Instant;
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
     * 搶同一個狀態，恰好一邊成功。回傳 1 表示本次取消成功，0 表示已不符條件。
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Booking b SET b.status = :cancelled
            WHERE b.id = :id AND b.status = :created AND b.paymentDueAt IS NOT NULL AND b.paymentDueAt < :now
              AND NOT EXISTS (
                  SELECT 1 FROM Payment p
                  WHERE p.bookingId = b.id
                    AND (p.status = :success OR (p.status = :processing AND p.createdAt > :checkoutCutoff)))
            """)
    int cancelIfPaymentExpired(@Param("id") UUID id, @Param("created") Booking.BookingStatus created,
            @Param("cancelled") Booking.BookingStatus cancelled, @Param("now") Instant now,
            @Param("checkoutCutoff") Instant checkoutCutoff, @Param("success") Payment.PaymentStatus success,
            @Param("processing") Payment.PaymentStatus processing);

    Page<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Booking> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Optional<Booking> findByIdAndUserId(UUID bookingId, UUID userId);

    Optional<Booking> findByIdAndTenantId(UUID bookingId, UUID tenantId);

    boolean existsByUserIdAndStatusNotIn(UUID userId, List<Booking.BookingStatus> statuses);
}