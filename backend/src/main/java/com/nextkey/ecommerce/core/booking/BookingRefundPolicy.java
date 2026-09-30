package com.nextkey.ecommerce.core.booking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.shared.time.BusinessTime;

/**
 * 訂房取消退款規則（Sprint 227，DEF-312；PRD §15.2.5 與 §17.4.5 Q14「取消補償邊界」）。
 *
 * <ul>
 *   <li>買家本人取消：距離入住 <b>&gt;= 24 小時</b> 全額退款；<b>&lt; 24 小時</b>（含入住時間已過）不退款。</li>
 *   <li>商家主動取消（含管理員代為取消）、系統取消：一律全額退款——買家沒有選擇取消，不能由買家承擔。</li>
 * </ul>
 *
 * <p>入住時刻是「入住日當天、房型設定的入住時間」在營運時區（Asia/Taipei，{@link BusinessTime}）的絕對時刻。不用 JVM 預設時區：
 * 雲端容器是 UTC，同一筆訂房的退款結果不能取決於部署環境（DEF-269 同一類問題）。
 */
public final class BookingRefundPolicy {

    /** Q14：取消時距離入住至少這麼久，才全額退款。 */
    public static final Duration FULL_REFUND_NOTICE = Duration.ofHours(24);

    private BookingRefundPolicy() {
    }

    /** 入住時刻：營運時區的入住日＋入住時間。 */
    public static Instant checkInInstant(final LocalDate checkInDate, final LocalTime checkInTime) {
        return checkInDate.atTime(checkInTime).atZone(BusinessTime.ZONE).toInstant();
    }

    /**
     * 這次取消的應退金額。
     *
     * @param refundable  付款目前還可退的金額（付款金額減去已退金額）
     * @param cancelledBy 取消方
     * @param cancelledAt 取消時刻
     * @param checkInAt   入住時刻（{@link #checkInInstant}）
     * @return 應退金額；{@link BigDecimal#ZERO} 表示依規則不退款
     */
    public static BigDecimal refundAmount(final BigDecimal refundable, final Booking.CancelledBy cancelledBy,
            final Instant cancelledAt, final Instant checkInAt) {
        if (cancelledBy != Booking.CancelledBy.CUSTOMER) {
            return refundable;
        }
        boolean enoughNotice = Duration.between(cancelledAt, checkInAt).compareTo(FULL_REFUND_NOTICE) >= 0;
        return enoughNotice ? refundable : BigDecimal.ZERO;
    }
}
