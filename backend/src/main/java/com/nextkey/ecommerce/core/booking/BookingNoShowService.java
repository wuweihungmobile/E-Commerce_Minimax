package com.nextkey.ecommerce.core.booking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.core.notification.BuyerNotificationService;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.shared.time.BusinessTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * no-show 自動取消的排程進入點（Sprint 246，DEF-352；PRD §17.4.6 Q15，使用者 2026-10-09 確認時限維持
 * 入住時刻＋24 小時）。只負責撈候選、逐筆委派、隔離失敗、事後通知——真正的判斷與取消在
 * {@link BookingService#cancelBookingIfNoShowDue}（<b>必須</b>在那裡，不能在本類別自己：{@code @Transactional}
 * 靠 Spring AOP 代理，同一個 bean 內的自呼叫不會經過代理，見該方法的說明），與
 * {@link BookingTimeoutService} 委派 {@link BookingService#cancelExpiredUnpaidBooking} 同一個分層。
 *
 * <p>已付款、入住時刻起 {@code grace-hours}（預設 24）小時仍未標記入住的訂房，系統自動取消
 * （{@code cancelledBy=SYSTEM}），<b>不退款</b>——與未付款逾時取消（{@link BookingTimeoutService}）不同，
 * 那邊訂房從未成立；這裡已成立、買家已付款，不退款是使用者的明確決定（DEF-351）。店家漏標入住造成的誤取消，
 * 管理員核實後可人工退款（DEF-354，見 {@code PaymentStateService#refundBookingPaymentManually}）。
 *
 * <p><b>上線開關預設關閉</b>（{@code app.booking-no-show.enabled}，環境變數 {@code BOOKING_NO_SHOW_AUTO_CANCEL_ENABLED}）：
 * PRD 風險評估要求 DEF-350（店家入住按鈕）上線並驗證店家真的能入住、且 DEF-354（人工退款入口）就位後才能開啟，
 * 否則店家介面還沒跟上就啟用本規則，真實住客會被誤取消且求助無門。
 *
 * <p>候選查詢只用入住日粗篩（{@link BookingRepository#findPotentialNoShowBookingIds}），精確的「入住時刻＋寬限
 * 小時數是否已過」依房源的 {@code checkInTime} 逐筆精算；單輪一次性取出候選（不像 {@link BookingTimeoutService}
 * 多批次重查同一頁——這裡的候選不保證每筆都已到期，重查同一頁會卡在還沒到期的候選上），超出單輪上限的候選
 * 留給下一輪（預設 5 分鐘後）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.booking-no-show.enabled", havingValue = "true", matchIfMissing = false)
public class BookingNoShowService {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final BuyerNotificationService buyerNotificationService;

    @Value("${app.booking-no-show.grace-hours:24}")
    private long graceHours;

    @Value("${app.booking-no-show.batch-size:500}")
    private int batchSize;

    /** 排程進入點；間隔與首次延遲可由設定覆寫（測試用）。 */
    @Scheduled(fixedDelayString = "${app.booking-no-show.check-interval-ms:300000}",
            initialDelayString = "${app.booking-no-show.initial-delay-ms:60000}")
    public void cancelNoShowBookings() {
        int cancelled = cancelNoShowBookings(Instant.now());
        if (cancelled > 0) {
            log.info("No-show booking auto-cancel: cancelled {} bookings", cancelled);
        }
    }

    /**
     * 取消 {@code now} 當下已過 no-show 期限的訂房，回傳實際取消的筆數。候選本身不保證都已到期
     * （見類別說明），逐筆精算後才決定是否取消；沒到期的跳過，不計入回傳值、也不視為失敗。
     *
     * @param now 以此為基準判斷是否已過期限（測試可指定；排程傳入現在）
     */
    public int cancelNoShowBookings(final Instant now) {
        List<UUID> candidates = bookingRepository.findPotentialNoShowBookingIds(Booking.BookingStatus.PAID,
                BusinessTime.today(), PageRequest.of(0, batchSize));
        int cancelled = 0;
        for (UUID bookingId : candidates) {
            if (cancelOne(bookingId, now)) {
                cancelled++;
            }
        }
        return cancelled;
    }

    private boolean cancelOne(final UUID bookingId, final Instant now) {
        final boolean cancelled;
        try {
            cancelled = bookingService.cancelBookingIfNoShowDue(bookingId, now, graceHours);
        } catch (RuntimeException e) {
            log.error("Failed to evaluate booking for no-show auto-cancel, will retry next run: bookingId={}, error={}",
                    bookingId, e.getMessage(), e);
            return false;
        }
        if (cancelled) {
            // 取消的交易已提交；只有真的取消了這筆才通知，通知永不拋例外、也不影響取消結果
            buyerNotificationService.notifyBookingNoShowCancelled(bookingId);
        }
        return cancelled;
    }
}
