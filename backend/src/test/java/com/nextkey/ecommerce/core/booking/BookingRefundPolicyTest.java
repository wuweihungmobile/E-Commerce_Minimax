package com.nextkey.ecommerce.core.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nextkey.ecommerce.domain.model.order.Booking;

/**
 * 訂房取消退款規則（Sprint 227，DEF-312；PRD §15.2.5、§17.4.5 Q14）。邊界是這條規則的重點：剛好 24 小時算「足夠」
 * （PRD：距離預訂時段 &gt;= 24 小時：全額退款；&lt; 24 小時：不退款）。
 */
@DisplayName("BookingRefundPolicy（PRD Q14 取消補償邊界）")
class BookingRefundPolicyTest {

    private static final BigDecimal PAID = new BigDecimal("3000.00");
    /** 2026-10-02 15:00（台北）＝ 07:00Z。 */
    private static final Instant CHECK_IN = Instant.parse("2026-10-02T07:00:00Z");

    @Test
    @DisplayName("入住時刻是營運時區（台北）的入住日＋入住時間，不受 JVM 預設時區影響")
    void checkInInstantUsesBusinessTimeZone() {
        assertThat(BookingRefundPolicy.checkInInstant(LocalDate.of(2026, 10, 2), LocalTime.of(15, 0)))
                .isEqualTo(CHECK_IN);
    }

    @Test
    @DisplayName("買家取消：剛好 24 小時前 → 全額退款（PRD：>= 24 小時）")
    void customerExactlyTwentyFourHoursBefore_isFullRefund() {
        assertThat(refund(Booking.CancelledBy.CUSTOMER, CHECK_IN.minus(Duration.ofHours(24))))
                .isEqualByComparingTo(PAID);
    }

    @Test
    @DisplayName("買家取消：24 小時又 1 秒前 → 全額退款")
    void customerJustOverTwentyFourHoursBefore_isFullRefund() {
        assertThat(refund(Booking.CancelledBy.CUSTOMER, CHECK_IN.minus(Duration.ofHours(24)).minusSeconds(1)))
                .isEqualByComparingTo(PAID);
    }

    @Test
    @DisplayName("買家取消：差 1 秒滿 24 小時 → 不退款（PRD：< 24 小時）")
    void customerOneSecondShortOfTwentyFourHours_isNoRefund() {
        assertThat(refund(Booking.CancelledBy.CUSTOMER, CHECK_IN.minus(Duration.ofHours(24)).plusSeconds(1)))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("買家取消：入住時間已過 → 不退款")
    void customerAfterCheckInTime_isNoRefund() {
        assertThat(refund(Booking.CancelledBy.CUSTOMER, CHECK_IN.plus(Duration.ofHours(2))))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("商家（含管理員代為取消）取消：即使入住前 1 小時甚至入住後，一律全額退款")
    void merchantCancellationIsAlwaysFullRefund() {
        assertThat(refund(Booking.CancelledBy.MERCHANT, CHECK_IN.minus(Duration.ofHours(1))))
                .isEqualByComparingTo(PAID);
        assertThat(refund(Booking.CancelledBy.MERCHANT, CHECK_IN.plus(Duration.ofHours(1))))
                .isEqualByComparingTo(PAID);
    }

    @Test
    @DisplayName("系統取消：一律全額退款")
    void systemCancellationIsAlwaysFullRefund() {
        assertThat(refund(Booking.CancelledBy.SYSTEM, CHECK_IN.minus(Duration.ofMinutes(5))))
                .isEqualByComparingTo(PAID);
    }

    @Test
    @DisplayName("退款金額以「付款還可退的金額」為基準，不是訂房總額")
    void refundIsBasedOnWhatIsStillRefundable() {
        BigDecimal remaining = new BigDecimal("1800.00");
        assertThat(BookingRefundPolicy.refundAmount(remaining, Booking.CancelledBy.CUSTOMER,
                CHECK_IN.minus(Duration.ofDays(3)), CHECK_IN)).isEqualByComparingTo(remaining);
    }

    private static BigDecimal refund(final Booking.CancelledBy by, final Instant cancelledAt) {
        return BookingRefundPolicy.refundAmount(PAID, by, cancelledAt, CHECK_IN);
    }
}
