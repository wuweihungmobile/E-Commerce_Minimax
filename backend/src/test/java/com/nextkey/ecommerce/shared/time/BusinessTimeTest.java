package com.nextkey.ecommerce.shared.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DEF-269：營運時區（UTC+8）的「現在／今天」不得取決於 JVM 預設時區。
 * 每個測試結束後由全域 {@code ThreadLocalIsolationExtension} 還原時鐘。
 */
@DisplayName("BusinessTime: 營運時區（UTC+8）時鐘")
class BusinessTimeTest {

    /** UTC 2027-01-31 16:30 = 台灣 2027-02-01 00:30：UTC 日期與營運日期相差一天的時段。 */
    private static final Instant UTC_PREVIOUS_DAY_TAIPEI_NEXT_DAY = Instant.parse("2027-01-31T16:30:00Z");

    @Test
    @DisplayName("營運時區為 Asia/Taipei（UTC+8）")
    void zone_isAsiaTaipei() {
        assertThat(BusinessTime.ZONE.getId()).isEqualTo("Asia/Taipei");
    }

    @Test
    @DisplayName("today()/now() 依營運時區換算：UTC 1/31 16:30 → 台灣 2/1 00:30")
    void fixedClock_isConvertedToBusinessZone() {
        BusinessTime.useClockForTesting(Clock.fixed(UTC_PREVIOUS_DAY_TAIPEI_NEXT_DAY, ZoneOffset.UTC));

        assertThat(BusinessTime.today()).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(BusinessTime.now()).isEqualTo(LocalDateTime.of(2027, 2, 1, 0, 30));
    }

    @Test
    @DisplayName("預設時鐘的結果不受 JVM 預設時區影響（JVM 設為 UTC 或檀香山，today() 皆為營運時區日期）")
    void defaultClock_ignoresJvmDefaultTimeZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            for (String jvmZone : new String[] {"UTC", "Pacific/Honolulu", "Asia/Taipei"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(jvmZone));

                LocalDate before = LocalDate.now(BusinessTime.ZONE);
                LocalDate actual = BusinessTime.today();
                LocalDate after = LocalDate.now(BusinessTime.ZONE);

                // before/after 夾住 actual，避免恰好跨過營運時區午夜造成的偶發失敗
                assertThat(actual).as("JVM 預設時區 %s", jvmZone).isIn(before, after);
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    @DisplayName("resetClock() 還原為系統時鐘")
    void resetClock_restoresSystemClock() {
        BusinessTime.useClockForTesting(Clock.fixed(UTC_PREVIOUS_DAY_TAIPEI_NEXT_DAY, ZoneOffset.UTC));
        BusinessTime.resetClock();

        LocalDate before = LocalDate.now(BusinessTime.ZONE);
        LocalDate actual = BusinessTime.today();
        LocalDate after = LocalDate.now(BusinessTime.ZONE);

        assertThat(actual).isIn(before, after);
    }
}
