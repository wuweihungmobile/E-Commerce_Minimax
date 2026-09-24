package com.nextkey.ecommerce.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 營運時區的「現在／今天」（DEF-269）。
 *
 * <p>PRD／FRD 明訂營運時區為 UTC+8（台灣，幣別 TWD）。過去業務邏輯直接呼叫
 * {@code LocalDate.now()}／{@code LocalDateTime.now()}，取得的是 <b>JVM 預設時區</b>的日期：
 * 開發機是 Asia/Taipei 所以看不出問題，但雲端 CI 與正式容器（{@code eclipse-temurin:21-jre-alpine}，
 * compose 未設 {@code TZ}）是 UTC，於是台灣時間每天 00:00～08:00 「今天」是前一天——早鳥／末班車折扣資格、
 * 商品促銷價有效日、優惠券起訖時間全部差 8 小時。
 *
 * <p>凡是「判斷資格／有效期／價格適用日」的邏輯一律改用本類別，使結果不再取決於部署環境的時區設定。
 * 僅用於稽核戳記、單號日期字串等不影響金額的用途者不在此列。
 */
public final class BusinessTime {

    /** 營運時區 ID（獨立成編譯期常數，供 {@code @Scheduled(zone = ...)} 這類註解屬性使用）。 */
    public static final String ZONE_ID = "Asia/Taipei";

    /** 營運時區（台灣無日光節約時間，故用 Asia/Taipei 而非固定偏移）。 */
    public static final ZoneId ZONE = ZoneId.of(ZONE_ID);

    private static volatile Clock clock = Clock.system(ZONE);

    private BusinessTime() {
    }

    /** 營運時區的今天。 */
    public static LocalDate today() {
        return LocalDate.now(clock);
    }

    /** 營運時區的現在（無時區的牆上時間，與賣家輸入的 {@code LocalDateTime} 起訖時間同一語意）。 */
    public static LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /**
     * 營運日 {@code date} 的 00:00 對應的絕對時刻。以 {@code startOfDay(d)} 與 {@code startOfDay(d.plusDays(1))}
     * 組成半開區間 {@code [d 00:00, d+1 00:00)}，相鄰兩日／兩週共用同一個邊界時刻，不重疊也不留縫。
     */
    public static Instant startOfDay(final LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }

    /**
     * 僅供測試：固定「現在」。測試結束由全域的 {@code ThreadLocalIsolationExtension} 自動呼叫
     * {@link #resetClock()}，不會洩漏給下一個測試。
     */
    public static void useClockForTesting(final Clock fixed) {
        clock = fixed.withZone(ZONE);
    }

    /** 還原為系統時鐘（營運時區）。 */
    public static void resetClock() {
        clock = Clock.system(ZONE);
    }
}
