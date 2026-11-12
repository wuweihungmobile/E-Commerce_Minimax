package com.nextkey.ecommerce.domain.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
     * 併發防護（DEF-162、DEF-453，PaymentStateService.markStripeRefunded）：以 {@code SELECT ... FOR UPDATE} 鎖住這筆付款，
     * 序列化 Stripe webhook 的退款記帳與「本系統 API 退款」「其他 webhook」。
     *
     * <p>原本（DEF-162）是條件式 UPDATE（{@code WHERE status <> REFUNDED}），只擋得住「重複送達的同一個事件」；
     * 記帳要算<strong>差額</strong>（Stripe 端的累計已退款金額減去本地 {@code refunded_amount}），差額必須從「鎖住之後」讀到的
     * 最新值算，否則兩個併發的 webhook（或 webhook 與 API 退款）會各自拿舊值算出差額、各扣一次結算單。
     * API 退款端（{@link #applyRefundIfUnchanged}）持有同一列的鎖直到交易結束，webhook 在這裡等它提交後才讀，
     * 所以一定看得到它記下的累計值；反過來 webhook 先鎖住時，API 退款的 compare-and-swap 會等到 webhook 提交，
     * 發現累計值已變而回報衝突（不呼叫 Stripe）。兩邊都是先鎖付款列、之後才碰結算單列。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.stripePaymentIntentId = :paymentIntentId")
    Optional<Payment> findByStripePaymentIntentIdForUpdate(@Param("paymentIntentId") String paymentIntentId);

    /**
     * 這張訂單的所有付款紀錄，由新到舊（Sprint 220，DEF-309）。多筆是正常情況（先付款失敗再成功），
     * 所以不再提供回傳單一 {@code Optional} 的 {@code findByOrderId}——多於一筆會拋
     * {@code IncorrectResultSizeDataAccessException}。要「這張訂單目前的付款」用 {@link #findEffectiveByOrderId}。
     */
    List<Payment> findAllByOrderIdOrderByCreatedAtDesc(UUID orderId);

    /** 這筆訂房的所有付款紀錄，由新到舊；見 {@link #findAllByOrderIdOrderByCreatedAtDesc}。 */
    List<Payment> findAllByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /** 最能代表這張訂單付款狀況的一筆（規則見 {@link Payment#pickEffective}）。 */
    default Optional<Payment> findEffectiveByOrderId(final UUID orderId) {
        return Payment.pickEffective(findAllByOrderIdOrderByCreatedAtDesc(orderId));
    }

    /** 最能代表這筆訂房付款狀況的一筆（規則見 {@link Payment#pickEffective}）。 */
    default Optional<Payment> findEffectiveByBookingId(final UUID bookingId) {
        return Payment.pickEffective(findAllByBookingIdOrderByCreatedAtDesc(bookingId));
    }

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