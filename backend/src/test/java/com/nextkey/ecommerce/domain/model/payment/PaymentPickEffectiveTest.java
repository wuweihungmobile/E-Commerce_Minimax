package com.nextkey.ecommerce.domain.model.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nextkey.ecommerce.domain.model.payment.Payment.PaymentStatus;

/**
 * {@link Payment#pickEffective}：同一張訂單／訂房有多筆付款紀錄時，挑哪一筆代表它的付款狀況（Sprint 220，DEF-309）。
 * 挑錯的後果是實際的：付款狀態端點顯示「失敗」而訂單其實已付款，或結算單漏掉退款。
 */
@DisplayName("Payment.pickEffective（DEF-309）")
class PaymentPickEffectiveTest {

    private static final Instant T0 = Instant.parse("2026-09-30T00:00:00Z");

    private static Payment payment(final PaymentStatus status, final long minutesAfterT0) {
        return Payment.builder().status(status).createdAt(T0.plusSeconds(minutesAfterT0 * 60)).build();
    }

    @Test
    @DisplayName("沒有付款紀錄 → 空")
    void empty() {
        assertThat(Payment.pickEffective(List.of())).isEmpty();
    }

    @Test
    @DisplayName("先失敗、後成功 → 成功的那筆（Mock 模式前端的正常操作）")
    void failedThenSuccess_picksSuccess() {
        Payment failed = payment(PaymentStatus.FAILED, 0);
        Payment success = payment(PaymentStatus.SUCCESS, 5);

        assertThat(Payment.pickEffective(List.of(success, failed))).containsSame(success);
    }

    @Test
    @DisplayName("先成功、後來又有一筆更新的失敗紀錄 → 仍是成功的那筆，不能被較新的失敗蓋掉")
    void successThenNewerFailed_stillPicksSuccess() {
        Payment success = payment(PaymentStatus.SUCCESS, 0);
        Payment laterFailed = payment(PaymentStatus.FAILED, 30);

        assertThat(Payment.pickEffective(List.of(laterFailed, success))).containsSame(success);
    }

    @Test
    @DisplayName("已有金流結果的三種狀態（成功／部分退款／已退款）同一順位，取最新一筆")
    void moneyCarryingStatusesShareTheTopRank() {
        Payment partially = payment(PaymentStatus.PARTIALLY_REFUNDED, 10);
        Payment refunded = payment(PaymentStatus.REFUNDED, 20);

        assertThat(Payment.pickEffective(List.of(partially, refunded))).containsSame(refunded);
    }

    @Test
    @DisplayName("進行中的結帳（PROCESSING）優先於 PENDING 與 FAILED")
    void processingBeatsPendingAndFailed() {
        Payment failed = payment(PaymentStatus.FAILED, 30);
        Payment pending = payment(PaymentStatus.PENDING, 20);
        Payment processing = payment(PaymentStatus.PROCESSING, 10);

        assertThat(Payment.pickEffective(List.of(failed, pending, processing))).containsSame(processing);
    }

    @Test
    @DisplayName("同一順位取建立時間較晚者；缺建立時間的排在最後")
    void sameRankPicksLatest_nullCreatedAtLast() {
        Payment older = payment(PaymentStatus.FAILED, 0);
        Payment newer = payment(PaymentStatus.FAILED, 10);
        Payment unknownTime = Payment.builder().status(PaymentStatus.FAILED).build();

        Optional<Payment> picked = Payment.pickEffective(List.of(unknownTime, older, newer));

        assertThat(picked).containsSame(newer);
    }
}
