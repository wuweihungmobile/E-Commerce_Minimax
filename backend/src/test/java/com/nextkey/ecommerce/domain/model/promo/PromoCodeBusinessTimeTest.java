package com.nextkey.ecommerce.domain.model.promo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nextkey.ecommerce.shared.time.BusinessTime;

/**
 * DEF-269：優惠券起訖時間是賣家輸入的無時區牆上時間（營運時區 UTC+8），
 * 過去以 JVM 預設時區（正式環境為 UTC）的 {@code LocalDateTime.now()} 比對，
 * 造成優惠券過期後仍多有效 8 小時、開始日晚 8 小時才生效。
 */
@DisplayName("PromoCode: 起訖時間以營運時區判斷有效性")
class PromoCodeBusinessTimeTest {

    private static PromoCode promo(final LocalDateTime start, final LocalDateTime end) {
        return PromoCode.builder().code("BT").startDate(start).endDate(end).build();
    }

    private static void taipeiNowIs(final String utcInstant) {
        BusinessTime.useClockForTesting(Clock.fixed(Instant.parse(utcInstant), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("結束時間 9/30 23:59:59 已過（台灣 10/1 00:30）→ 已過期，不可再被使用")
    void endedDuringTaipeiNightIsExpired() {
        taipeiNowIs("2026-09-30T16:30:00Z"); // 台灣 2026-10-01 00:30；UTC 仍是 9/30
        PromoCode promo = promo(LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 30, 23, 59, 59));

        assertThat(promo.isExpired()).isTrue();
    }

    @Test
    @DisplayName("結束時間 9/30 23:59:59 尚未到（台灣 9/30 23:00）→ 未過期（不可提早失效）")
    void beforeEndInTaipeiIsNotExpired() {
        taipeiNowIs("2026-09-30T15:00:00Z"); // 台灣 2026-09-30 23:00
        PromoCode promo = promo(LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 30, 23, 59, 59));

        assertThat(promo.isExpired()).isFalse();
    }

    @Test
    @DisplayName("開始時間 10/1 00:00 已到（台灣 10/1 00:30）→ 已生效，不可晚 8 小時")
    void startedAtTaipeiMidnightIsActive() {
        taipeiNowIs("2026-09-30T16:30:00Z"); // 台灣 2026-10-01 00:30
        PromoCode promo = promo(LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 31, 23, 59, 59));

        assertThat(promo.isNotYetActive()).isFalse();
    }

    @Test
    @DisplayName("開始時間 10/1 00:00 未到（台灣 9/30 23:59）→ 尚未生效")
    void beforeStartInTaipeiIsNotYetActive() {
        taipeiNowIs("2026-09-30T15:59:00Z"); // 台灣 2026-09-30 23:59
        PromoCode promo = promo(LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 31, 23, 59, 59));

        assertThat(promo.isNotYetActive()).isTrue();
    }
}
