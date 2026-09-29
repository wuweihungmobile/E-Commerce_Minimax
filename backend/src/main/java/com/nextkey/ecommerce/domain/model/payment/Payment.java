package com.nextkey.ecommerce.domain.model.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    private static final int DECIMAL_PRECISION = 12;
    private static final int IDEMPOTENCY_KEY_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, precision = DECIMAL_PRECISION, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    @Builder.Default
    private String currency = "TWD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "transaction_id")
    private String transactionId;

    // 真實金流（Sprint 50 AI-2410）：Stripe 端識別碼；mock 路徑為 null
    @Column(name = "stripe_session_id")
    private String stripeSessionId;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "stripe_charge_id")
    private String stripeChargeId;

    // 真實金流 Phase C（Sprint 52 AI-2412）：Stripe 退款 id（re_xxx）；未退款為 null
    @Column(name = "stripe_refund_id")
    private String stripeRefundId;

    // 部分退款（Sprint 56 AI-2415）：累計已退款金額；未退款為 0
    @Column(name = "refunded_amount", precision = DECIMAL_PRECISION, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "idempotency_key", length = IDEMPOTENCY_KEY_LENGTH)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payment_data", columnDefinition = "jsonb")
    private String paymentData;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == PaymentStatus.SUCCESS && paidAt == null) {
            paidAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * 從同一張訂單／訂房的多筆付款紀錄中挑出最能代表它付款狀況的一筆（Sprint 220，DEF-309）。
     *
     * <p>{@code payments.order_id}／{@code booking_id} 沒有唯一約束，而多筆是正常情況：Mock 模式先「模擬付款失敗」再
     * 付款成功會留下一筆 FAILED 加一筆 SUCCESS。原本 {@code findByOrderId} 回傳單一 {@code Optional}，多於一筆時
     * 拋 {@code IncorrectResultSizeDataAccessException}——付款狀態端點永遠 500、週結算對該租戶整個失敗。
     *
     * <p>優先順序：已有金流結果的（SUCCESS／PARTIALLY_REFUNDED／REFUNDED）→ 進行中的結帳（PROCESSING）→ PENDING →
     * FAILED；同一順位取建立時間較晚者。
     */
    public static Optional<Payment> pickEffective(final Collection<Payment> payments) {
        return payments.stream().min(Comparator
                .comparingInt((Payment p) -> p.getStatus().effectiveRank())
                .thenComparing(Payment::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    public enum PaymentMethod {
        LINE_PAY, CREDIT_CARD, MOCK,
        // 真實金流（Sprint 50 AI-2410）
        STRIPE
    }

    public enum PaymentStatus {
        PENDING, SUCCESS, FAILED, REFUNDED,
        // 真實金流（Sprint 50 AI-2410）：Checkout Session 已建、待買家於 Stripe 完成付款
        PROCESSING,
        // 部分退款（Sprint 56 AI-2415）：已退款金額 > 0 但未達 amount 全額
        PARTIALLY_REFUNDED;

        /** {@link Payment#pickEffective} 的優先順序：數字越小越能代表付款狀況。 */
        int effectiveRank() {
            return switch (this) {
                case SUCCESS, PARTIALLY_REFUNDED, REFUNDED -> 0;
                case PROCESSING -> 1;
                case PENDING -> 2;
                case FAILED -> 3;
            };
        }
    }
}