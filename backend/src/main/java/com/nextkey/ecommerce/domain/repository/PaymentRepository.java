package com.nextkey.ecommerce.domain.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.payment.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * 併發防護：以條件式 UPDATE（{@code WHERE status <> newStatus}）取代「讀狀態→判斷→setStatus→save」，
     * 讓「是否是第一個把此付款標記成功的呼叫者」由資料庫的受影響列數決定，而非應用層讀到的舊快照。
     * 回傳受影響列數：1 代表本次成功轉換（呼叫端才可繼續下游動作，如扣庫存）；0 代表已是該狀態或已被
     * 另一併發呼叫搶先轉換，呼叫端應視為冪等 no-op，不再重複下游動作。
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Payment p SET p.status = :newStatus, "
            + "p.stripePaymentIntentId = COALESCE(:paymentIntentId, p.stripePaymentIntentId), p.paidAt = :paidAt "
            + "WHERE p.id = :id AND p.status <> :newStatus")
    int markSuccessIfNotAlready(@Param("id") UUID id, @Param("newStatus") Payment.PaymentStatus newStatus,
            @Param("paymentIntentId") String paymentIntentId, @Param("paidAt") Instant paidAt);

    /**
     * 併發防護：以 compare-and-swap（{@code WHERE refundedAmount = 讀取當下的舊值}）取代
     * 「讀 refundedAmount→驗證額度→setRefundedAmount→save」，避免同一筆付款被併發送出兩次退款請求時，
     * 兩邊都以同一份舊快照通過額度驗證、各自累加，造成結算帳本被重複扣減或事後可退超過原始金額。
     * 回傳受影響列數：1 代表本次成功佔用退款額度（呼叫端才可繼續呼叫 Stripe／結算調整）；
     * 0 代表 refundedAmount 已被另一併發呼叫搶先改變，呼叫端應拒絕本次請求，不做任何下游動作。
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Payment p SET p.refundedAmount = :newRefundedAmount, p.status = :newStatus "
            + "WHERE p.id = :id AND p.refundedAmount = :expectedCurrentRefundedAmount")
    int applyRefundIfUnchanged(@Param("id") UUID id,
            @Param("expectedCurrentRefundedAmount") BigDecimal expectedCurrentRefundedAmount,
            @Param("newRefundedAmount") BigDecimal newRefundedAmount,
            @Param("newStatus") Payment.PaymentStatus newStatus);

    /**
     * 併發防護：條件式狀態轉換（{@code WHERE status = expectedStatus}），取代「讀狀態→判斷→
     * setStatus→save」。回傳受影響列數：1 代表本次成功轉換，0 代表狀態已被另一併發呼叫搶先轉換，
     * 呼叫端應拒絕本次請求（例如同一筆付款被併發送出兩次退款，僅一邊該視為成功）。
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Payment p SET p.status = :newStatus WHERE p.id = :id AND p.status = :expectedStatus")
    int updateStatusIfCurrent(@Param("id") UUID id, @Param("expectedStatus") Payment.PaymentStatus expectedStatus,
            @Param("newStatus") Payment.PaymentStatus newStatus);

    /**
     * 併發防護（DEF-162，PaymentStateService.markStripeRefunded）：以條件式 UPDATE
     * （{@code WHERE status <> newStatus}）取代「讀 status==REFUNDED 冪等檢查→setStatus/
     * setStripeRefundId→save」。Stripe webhook 可能對同一筆退款事件重複送達，兩個併發呼叫
     * 都可能通過同一份舊快照的冪等檢查，各自對訂單寫入重複的 order_state_log。回傳受影響
     * 列數：1 代表本次成功轉換（呼叫端才可繼續同步訂單狀態/寫稽核）；0 代表已是 REFUNDED
     * 或已被另一併發 webhook 搶先處理，呼叫端應視為冪等 no-op。
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Payment p SET p.status = :newStatus, "
            + "p.stripeRefundId = COALESCE(:refundId, p.stripeRefundId) "
            + "WHERE p.id = :id AND p.status <> :newStatus")
    int markRefundedIfNotAlready(@Param("id") UUID id, @Param("newStatus") Payment.PaymentStatus newStatus,
            @Param("refundId") String refundId);

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByBookingId(UUID bookingId);

    boolean existsByOrderIdAndStatus(UUID orderId, Payment.PaymentStatus status);

    boolean existsByBookingIdAndStatus(UUID bookingId, Payment.PaymentStatus status);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByOrderIdAndStatus(UUID orderId, Payment.PaymentStatus status);

    List<Payment> findByOrderIdInAndStatus(List<UUID> orderIds, Payment.PaymentStatus status);

    Optional<Payment> findByTransactionId(String transactionId);

    // 真實金流 Phase B（AI-2411）：webhook 依 Stripe session / payment_intent id 找 Payment
    Optional<Payment> findByStripeSessionId(String stripeSessionId);

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);
}