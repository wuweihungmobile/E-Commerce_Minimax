package com.nextkey.ecommerce.core.booking;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.repository.BookingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 未付款訂房逾時自動取消（Sprint 225，DEF-311；使用者 2026-10-01 對建議方案回覆「依照建議」）。
 *
 * <p>建立後沒付款的訂房會一直佔著日曆與優惠券額度。與訂單（{@code OrderTimeoutService}，24 小時）不同，訂房自己記錄
 * 付款期限 {@code payment_due_at}：新訂房建立時寫入「建立時間＋24 小時」，歷史訂房為 NULL 永不逾時——它們在 Sprint 221
 * 之前根本沒有付款入口，不能追溯適用（否則部署後第一輪就會把它們全部取消、釋放日曆）。
 *
 * <p>本排程把付款期限已過、仍是 {@code CREATED} 的訂房取消，補償與買家自己取消相同
 * （{@link BookingService#cancelExpiredUnpaidBooking}：釋放日曆、退還優惠券額度）。
 *
 * <p>逐筆各自一個交易：一筆失敗只留下它自己（下輪重試），不影響其他筆。每輪處理多個批次以便在首次啟用時消化累積的
 * 舊單，但遇到整批都沒進展就停止，避免一直重試同一批壞單。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingTimeoutService {

    /** 每輪最多處理的批次數（×批次大小 ＝ 每輪上限，其餘留給下一輪）。 */
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    /**
     * Stripe Checkout 工作階段的有效時間（小時）。在這段時間內開始過結帳的訂房不取消：工作階段還開著，買家仍可能
     * 付款成功，太早取消會造成「錢收了、訂房已取消」。與 Stripe 預設的 24 小時到期一致。
     */
    @Value("${app.booking-timeout.stripe-session-hours:24}")
    private long stripeSessionHours;

    @Value("${app.booking-timeout.batch-size:100}")
    private int batchSize;

    /** 排程進入點；間隔與首次延遲可由設定覆寫（測試用）。 */
    @Scheduled(fixedDelayString = "${app.booking-timeout.check-interval-ms:300000}",
            initialDelayString = "${app.booking-timeout.initial-delay-ms:60000}")
    public void cancelExpiredUnpaidBookings() {
        int cancelled = cancelExpiredUnpaidBookings(Instant.now());
        if (cancelled > 0) {
            log.info("Unpaid booking timeout: cancelled {} bookings", cancelled);
        }
    }

    /**
     * 取消 {@code now} 當下付款期限已過的未付款訂房，回傳實際取消的筆數。
     *
     * @param now 以此為基準判斷逾時（測試可指定；排程傳入現在）
     */
    public int cancelExpiredUnpaidBookings(final Instant now) {
        Instant checkoutCutoff = now.minus(Duration.ofHours(stripeSessionHours));
        int cancelled = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<UUID> ids = bookingRepository.findExpiredUnpaidBookingIds(Booking.BookingStatus.CREATED, now,
                    checkoutCutoff, Payment.PaymentStatus.SUCCESS, Payment.PaymentStatus.PROCESSING,
                    PageRequest.of(0, batchSize));
            if (ids.isEmpty()) {
                break;
            }
            int cancelledInBatch = 0;
            for (UUID bookingId : ids) {
                if (cancelOne(bookingId, now, checkoutCutoff)) {
                    cancelledInBatch++;
                }
            }
            cancelled += cancelledInBatch;
            if (cancelledInBatch == 0) {
                break;
            }
        }
        return cancelled;
    }

    private boolean cancelOne(final UUID bookingId, final Instant now, final Instant checkoutCutoff) {
        try {
            return bookingService.cancelExpiredUnpaidBooking(bookingId, now, checkoutCutoff);
        } catch (RuntimeException e) {
            log.error("Failed to cancel expired unpaid booking, will retry next run: bookingId={}, error={}",
                    bookingId, e.getMessage(), e);
            return false;
        }
    }
}
