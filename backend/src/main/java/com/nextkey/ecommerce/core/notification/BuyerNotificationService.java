package com.nextkey.ecommerce.core.notification;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.nextkey.ecommerce.api.dto.NotificationDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 買家通知（Sprint 229）：PRD US-005「取消後即時收到退款狀態通知」、US-014「付款逾時後通知買家訂單未完成並提供重試連結」，
 * 以及自動退款完成（{@code REFUND_COMPLETED}）。
 *
 * <p>只送站內通知：通知管線的消費者只把資料列標成已送出，EMAIL／SMS／PUSH 沒有真正的送出實作（見 DEF-317），
 * 送了也不會到買家手上，所以這裡不假裝有。
 *
 * <p><b>盡力而為，永不拋例外：</b>通知是取消與退款的附帶效果，Redis 或資料庫暫時失敗不可讓已經完成的取消失敗，
 * 更不可讓已退的款被排程當成失敗而重試。所有公開方法都吞掉例外、只留警告日誌——代價是失敗的那則通知不會補送。
 *
 * <p><b>要在交易提交之後呼叫。</b>預建列與入佇在同一個交易裡：呼叫端的交易還沒提交就送出，消費者可能先看到訊息卻查不到資料列，
 * 呼叫端回滾時還會留下孤兒訊息。本類別自己以 {@code REQUIRES_NEW} 開新交易（而不是加入呼叫端的）：在
 * {@code afterCommit} 回呼裡呼叫時，原交易雖已提交、資源卻仍綁在執行緒上，直接加入它的話，通知的寫入永遠不會被提交。
 */
@Slf4j
@Service
public class BuyerNotificationService {

    private static final String REASON_PAYMENT_TIMEOUT = "PAYMENT_TIMEOUT";
    private static final int SHORT_ID_LENGTH = 8;

    private final NotificationService notificationService;
    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final ListingRepository listingRepository;
    private final PaymentRepository paymentRepository;
    private final TransactionTemplate requiresNew;

    public BuyerNotificationService(final NotificationService notificationService,
            final OrderRepository orderRepository, final BookingRepository bookingRepository,
            final ListingRepository listingRepository, final PaymentRepository paymentRepository,
            final PlatformTransactionManager transactionManager) {
        this.notificationService = notificationService;
        this.orderRepository = orderRepository;
        this.bookingRepository = bookingRepository;
        this.listingRepository = listingRepository;
        this.paymentRepository = paymentRepository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 訂房取消後通知買家這次取消的退款結果（PRD US-005）：買家本人取消、商家或管理員代為取消都通知。
     *
     * @param wasPaid 取消前是否已付款：取消後的 {@code refund_status = NONE} 分不出「沒付過款」與「依 Q14 不退」，
     *                由取消當下知道的人傳入
     */
    public void notifyBookingCancelled(final UUID bookingId, final boolean wasPaid) {
        deliver("BOOKING_CANCELLED", bookingId, () -> bookingRepository.findById(bookingId).map(booking -> request(
                booking.getUserId(), NotificationDto.NotificationType.ORDER_CANCELLED, "訂房已取消",
                bookingCancelledContent(bookingName(booking), booking.getCheckInDate(), booking.getCancelledBy(),
                        wasPaid, booking.getRefundAmount()),
                data("bookingId", booking.getId(), "refundAmount", booking.getRefundAmount()))));
    }

    /** 未付款訂房逾時被系統取消後通知買家，並附上重新預訂的房源（PRD US-014）。 */
    public void notifyBookingPaymentTimeout(final UUID bookingId) {
        deliver("BOOKING_PAYMENT_TIMEOUT", bookingId, () -> bookingRepository.findById(bookingId).map(booking -> request(
                booking.getUserId(), NotificationDto.NotificationType.ORDER_CANCELLED, "訂房因逾期未付款已取消",
                bookingTimeoutContent(bookingName(booking), booking.getCheckInDate()),
                data("bookingId", booking.getId(), "listingId", booking.getRoomListingId(), "reason",
                        REASON_PAYMENT_TIMEOUT))));
    }

    /** 未付款訂單逾時被系統取消後通知買家（PRD US-014）。 */
    public void notifyOrderPaymentTimeout(final UUID orderId) {
        deliver("ORDER_PAYMENT_TIMEOUT", orderId, () -> orderRepository.findById(orderId).map(order -> request(
                order.getUserId(), NotificationDto.NotificationType.ORDER_CANCELLED, "訂單因逾期未付款已取消",
                orderTimeoutContent(order.getId(), order.getTotalAmount(), order.getCurrency()),
                data("orderId", order.getId(), "reason", REASON_PAYMENT_TIMEOUT))));
    }

    /** 訂單的款項已自動退回原付款方式。 */
    public void notifyOrderRefunded(final UUID orderId) {
        deliver("ORDER_REFUNDED", orderId, () -> orderRepository.findById(orderId).map(order -> {
            final BigDecimal refunded = paymentRepository.findEffectiveByOrderId(orderId)
                    .map(Payment::getRefundedAmount).filter(amount -> amount.signum() > 0).orElse(null);
            return request(order.getUserId(), NotificationDto.NotificationType.REFUND_COMPLETED, "退款已完成",
                    orderRefundedContent(order.getId(), refunded, order.getCurrency()),
                    data("orderId", order.getId(), "refundAmount", refunded));
        }));
    }

    /** 訂房的款項已自動退回原付款方式。 */
    public void notifyBookingRefunded(final UUID bookingId) {
        deliver("BOOKING_REFUNDED", bookingId, () -> bookingRepository.findById(bookingId).map(booking -> request(
                booking.getUserId(), NotificationDto.NotificationType.REFUND_COMPLETED, "退款已完成",
                bookingRefundedContent(bookingName(booking), booking.getRefundAmount()),
                data("bookingId", booking.getId(), "refundAmount", booking.getRefundAmount()))));
    }

    /** 在新交易裡讀取並送出；任何失敗都只記警告（見類別說明）。{@code build} 回傳空表示沒有東西要送。 */
    private void deliver(final String event, final UUID id,
            final Supplier<Optional<NotificationDto.SendRequest>> build) {
        try {
            requiresNew.executeWithoutResult(status -> build.get().ifPresentOrElse(notificationService::sendNotification,
                    () -> log.warn("Buyer notification skipped, the record no longer exists: event={}, id={}", event,
                            id)));
        } catch (RuntimeException e) {
            log.warn("Buyer notification failed, the business operation is unaffected: event={}, id={}", event, id,
                    e);
        }
    }

    private String bookingName(final Booking booking) {
        final String title = listingRepository.findById(booking.getRoomListingId()).map(Listing::getTitle)
                .orElse(null);
        return bookingName(title, booking.getId());
    }

    // ========== 文案（純函式，單元測試直接驗證）==========

    /** 與前端一致：有房源標題用「標題」，否則用 #編號前 8 碼。 */
    static String bookingName(final String roomTitle, final UUID bookingId) {
        return roomTitle != null && !roomTitle.isBlank() ? "訂房「" + roomTitle + "」"
                : "訂房 #" + shortId(bookingId);
    }

    static String bookingCancelledContent(final String bookingName, final LocalDate checkInDate,
            final Booking.CancelledBy cancelledBy, final boolean wasPaid, final BigDecimal refundAmount) {
        final String who = cancelledBy == Booking.CancelledBy.CUSTOMER ? "您已取消" : "商家已取消您的";
        final String first = who + bookingName + "（" + checkInDate + " 入住）。";
        if (!wasPaid) {
            return first + "\n此訂房尚未付款，不需退款。";
        }
        if (refundAmount != null && refundAmount.signum() > 0) {
            return first + "\n退款 " + formatMoney(refundAmount, null) + " 已進入處理，完成後會退回原付款方式並再通知您。";
        }
        if (cancelledBy == Booking.CancelledBy.CUSTOMER) {
            return first + "\n依取消政策，入住前不足 24 小時取消不予退款。";
        }
        // 商家取消一律全額退款；已付款卻沒有可退的金額是異常（找不到有效付款），不替客服下結論
        return first + "\n退款狀態需由客服確認，請與我們聯絡。";
    }

    static String bookingTimeoutContent(final String bookingName, final LocalDate checkInDate) {
        return "您的" + bookingName + "（" + checkInDate + " 入住）超過付款期限，已自動取消並釋出日期。如仍想入住，可重新預訂。";
    }

    static String orderTimeoutContent(final UUID orderId, final BigDecimal totalAmount, final String currency) {
        return "您的訂單 #" + shortId(orderId) + "（" + formatMoney(totalAmount, currency) + "）超過付款期限，已自動取消。如仍需要，請重新下單。";
    }

    static String orderRefundedContent(final UUID orderId, final BigDecimal refundedAmount, final String currency) {
        return "訂單 #" + shortId(orderId) + " 的款項" + amountPhrase(refundedAmount, currency)
                + "已退回原付款方式，實際入帳時間依付款機構而定。";
    }

    static String bookingRefundedContent(final String bookingName, final BigDecimal refundAmount) {
        return bookingName + "的款項" + amountPhrase(refundAmount, null)
                + "已退回原付款方式，實際入帳時間依付款機構而定。";
    }

    /** 有金額時前後各留一個空格（中文接數字要有間隔），沒有金額就整段省略。 */
    private static String amountPhrase(final BigDecimal amount, final String currency) {
        return amount != null ? " " + formatMoney(amount, currency) + " " : "";
    }

    /** 訂房沒有幣別欄位（平台只收新台幣）；訂單帶幣別，非新台幣時顯示幣別代碼。 */
    static String formatMoney(final BigDecimal amount, final String currency) {
        final String symbol = currency == null || "TWD".equals(currency) ? "NT$" : currency + " ";
        return symbol + new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US)).format(amount);
    }

    private static String shortId(final UUID id) {
        return id.toString().substring(0, SHORT_ID_LENGTH);
    }

    private static NotificationDto.SendRequest request(final UUID userId,
            final NotificationDto.NotificationType type, final String title, final String content,
            final Map<String, Object> data) {
        return NotificationDto.SendRequest.builder().userId(userId).notificationType(type).title(title)
                .content(content).data(data).channel(NotificationDto.Channel.IN_APP).build();
    }

    /** 鍵值成對的 data（值為 null 的略過；UUID 轉字串，跨 Redis JSON 往返時不會變型）。 */
    private static Map<String, Object> data(final Object... keyValues) {
        final Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            final Object value = keyValues[i + 1];
            if (value != null) {
                data.put((String) keyValues[i], value instanceof UUID ? value.toString() : value);
            }
        }
        return data;
    }
}
