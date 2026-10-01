package com.nextkey.ecommerce.core.payment;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.core.notification.BuyerNotificationService;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 自動退款（Sprint 226，DEF-303 (5)／DEF-308；使用者 2026-10-01 回覆「請依照最佳化進行！」）。
 *
 * <p>PRD §15.2.5：「退款：若已支付，觸發 M04 退款流程」；M07 是「自動化退款流」。取消已付款訂單時
 * （{@code OrderService.compensateCancellation}）訂單轉 {@code REFUNDING}「等待退款」，過去只有管理員直接呼叫退款 API 才會動錢。
 * Stripe 付款成功時訂單已被取消（{@code DEF-308}）同樣會轉 {@code REFUNDING}。本排程把所有 {@code REFUNDING} 訂單的款項
 * 退回原付款方式（{@link PaymentStateService#refundOrderPaymentAsSystem}），成功後訂單轉 {@code REFUNDED}。
 *
 * <p><b>為什麼是排程而不是取消當下同步退款：</b>Stripe 呼叫不該卡在買家的取消請求裡（Stripe 暫時不可用就取消不了訂單），
 * 也不該在取消的資料庫交易裡——外部呼叫與交易無法一起回滾。{@code REFUNDING} 本身就是持久的「待退款」標記：取消與排程各自是
 * 獨立的交易，任何一步失敗都不會留下半套狀態，下一輪自然重試；多個後端實例同時跑也安全，額度由
 * {@code payments.refunded_amount} 的 compare-and-swap 保證同一筆付款只會被退一次。
 *
 * <p><b>訂房（Sprint 227，DEF-312）：</b>訂房沒有 {@code REFUNDING} 狀態，取消時依 PRD Q14 決定的應退金額記在訂房的
 * {@code refund_status = PENDING}（同樣是持久的待退款標記）；本排程用同一套游標、退避與失敗隔離把它們退完
 * （{@link PaymentStateService#refundBookingPaymentAsSystem}），成功後轉 {@code COMPLETED}。
 *
 * <p><b>失敗處理：</b>逐張各自一個交易，一張失敗（Stripe 拒絕、找不到可退款的付款…）只留下它自己——交易整體回滾，訂單維持
 * {@code REFUNDING}，並寫一筆 {@code AUTO_REFUND_FAILED} 稽核紀錄讓人看得到原因。失敗的訂單以指數退避
 * （{@code retry-base-minutes} 起，上限 {@code retry-max-minutes}）重試，避免永久失敗的訂單每輪都打一次 Stripe。
 * 退避狀態只存在記憶體：重啟後會多試一次，不影響正確性（冪等鍵與額度 CAS 會擋住重複退款）。
 * 逐頁以 id 為游標走完所有待退款訂單，永久失敗的訂單不會卡住排在後面的訂單。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundProcessingService {

    /** 游標起點：所有 UUID 都大於它。 */
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    /** 每輪最多處理的頁數（×頁大小 ＝ 每輪上限，其餘留給下一輪）。 */
    private static final int MAX_PAGES_PER_RUN = 20;

    private static final String REASON = "Automatic refund: order cancelled";
    private static final String BOOKING_REASON = "Automatic refund: booking cancelled";
    private static final int MAX_ERROR_LENGTH = 500;

    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final PaymentStateService paymentStateService;
    private final AuditService auditService;
    private final BuyerNotificationService buyerNotificationService;

    @Value("${app.refund.batch-size:50}")
    private int batchSize;

    @Value("${app.refund.retry-base-minutes:5}")
    private long retryBaseMinutes;

    @Value("${app.refund.retry-max-minutes:360}")
    private long retryMaxMinutes;

    /** 失敗的訂單 → 已失敗次數與下次可重試時間（只存記憶體）。 */
    private final Map<UUID, RetryState> retryStates = new ConcurrentHashMap<>();

    /** 排程進入點；間隔與首次延遲可由設定覆寫（測試用）。 */
    @Scheduled(fixedDelayString = "${app.refund.check-interval-ms:60000}",
            initialDelayString = "${app.refund.initial-delay-ms:45000}")
    public void processPendingRefunds() {
        Instant now = Instant.now();
        int orders = processPendingOrderRefunds(now);
        int bookings = processPendingBookingRefunds(now);
        if (orders + bookings > 0) {
            log.info("Automatic refunds: refunded {} orders and {} bookings", orders, bookings);
        }
    }

    /**
     * 退款給 {@code now} 當下所有等待退款（{@code REFUNDING}）且不在退避期間的訂單，回傳實際退款成功的張數。
     *
     * @param now 判斷退避是否結束的基準時間（測試可指定；排程傳入現在）
     */
    public int processPendingOrderRefunds(final Instant now) {
        return sweep(now, new Target("ORDER",
                (afterId, page) -> orderRepository.findRefundingOrderIdsAfter(Order.OrderStatus.REFUNDING, afterId, page),
                orderId -> paymentStateService.refundOrderPaymentAsSystem(orderId, REASON),
                orderId -> orderRepository.findStatusById(orderId)
                        .filter(status -> status != Order.OrderStatus.REFUNDING).isPresent(),
                orderId -> orderRepository.findById(orderId).map(Order::getTenantId).orElse(null),
                Order.OrderStatus.REFUNDING.name(), buyerNotificationService::notifyOrderRefunded));
    }

    /**
     * 退款給 {@code now} 當下所有等待退款（{@code refund_status = PENDING}）且不在退避期間的訂房，回傳實際退款成功的筆數
     * （Sprint 227，DEF-312）。
     */
    public int processPendingBookingRefunds(final Instant now) {
        return sweep(now, new Target("BOOKING",
                (afterId, page) -> bookingRepository.findPendingRefundBookingIds(Booking.RefundStatus.PENDING, afterId,
                        page),
                bookingId -> paymentStateService.refundBookingPaymentAsSystem(bookingId, BOOKING_REASON),
                bookingId -> bookingRepository.findRefundStatusById(bookingId)
                        .filter(status -> status != Booking.RefundStatus.PENDING).isPresent(),
                bookingId -> bookingRepository.findById(bookingId).map(Booking::getTenantId).orElse(null),
                Booking.RefundStatus.PENDING.name(), buyerNotificationService::notifyBookingRefunded));
    }

    private int sweep(final Instant now, final Target target) {
        pruneRetryStates(now);
        int refunded = 0;
        UUID cursor = NIL_UUID;
        for (int page = 0; page < MAX_PAGES_PER_RUN; page++) {
            List<UUID> ids = target.pageAfter().apply(cursor, PageRequest.of(0, batchSize));
            if (ids.isEmpty()) {
                break;
            }
            for (UUID id : ids) {
                if (refundOne(target, id, now)) {
                    refunded++;
                }
            }
            cursor = ids.get(ids.size() - 1);
        }
        return refunded;
    }

    private boolean refundOne(final Target target, final UUID id, final Instant now) {
        RetryState state = retryStates.get(id);
        if (state != null && now.isBefore(state.nextAttemptAt)) {
            return false; // 上次失敗，還在退避期間
        }
        try {
            target.refund().accept(id);
        } catch (RuntimeException e) {
            if (target.handledByAnotherWorker().test(id)) {
                log.info("Automatic refund skipped, the {} was handled concurrently: id={}",
                        target.entityType().toLowerCase(Locale.ROOT), id);
                return false;
            }
            recordFailure(target, id, state, now, e);
            return false;
        }
        retryStates.remove(id);
        // 退款交易已提交。通知在上面失敗判斷的 try 之外：它不拋例外（BuyerNotificationService 的契約），
        // 就算有人改壞了它，也不會被當成「退款失敗」而進入退避與重試
        target.notifyRefunded().accept(id);
        return true;
    }

    private void recordFailure(final Target target, final UUID id, final RetryState previous, final Instant now,
            final RuntimeException error) {
        int attempts = previous == null ? 1 : previous.attempts + 1;
        // 5 分鐘起，每次加倍，上限 retry-max-minutes（位移量封頂以免溢位）
        long backoffMinutes = Math.min(retryMaxMinutes, retryBaseMinutes << Math.min(attempts - 1, 20));
        Instant next = now.plus(Duration.ofMinutes(backoffMinutes));
        retryStates.put(id, new RetryState(attempts, next));

        String message = String.valueOf(error.getMessage());
        if (message.length() > MAX_ERROR_LENGTH) {
            message = message.substring(0, MAX_ERROR_LENGTH);
        }
        log.error("Automatic refund failed, the {} stays pending and will be retried: id={}, attempt={}, "
                + "nextAttemptAt={}, error={}", target.entityType().toLowerCase(Locale.ROOT), id, attempts,
                next, message, error);
        auditService.record("AUTO_REFUND_FAILED", target.entityType(), id, target.tenantOf().apply(id),
                target.pendingMarker(), target.pendingMarker(),
                "attempt=" + attempts + ",nextAttemptAt=" + next + ",error=" + message);
    }

    /**
     * 一種等待退款的對象（訂單或訂房）：怎麼分頁取出、怎麼退一筆、怎麼判斷已被別的處理者處理、失敗稽核要記哪個租戶。
     * 訂單與訂房共用同一套游標、退避與失敗隔離。退款成功後通知買家（{@code notifyRefunded}，Sprint 229）。
     */
    private record Target(String entityType, BiFunction<UUID, Pageable, List<UUID>> pageAfter, Consumer<UUID> refund,
            Predicate<UUID> handledByAnotherWorker, Function<UUID, UUID> tenantOf, String pendingMarker,
            Consumer<UUID> notifyRefunded) {
    }

    /**
     * 清掉早已不會再被用到的退避狀態：超過「下次重試時間＋最長退避」還沒被更新，代表那張訂單已不是候選
     * （已退款、或被人工處理掉了）。仍是候選的訂單每次重試都會刷新自己的狀態，不會被清掉而讓退避歸零。
     */
    private void pruneRetryStates(final Instant now) {
        Duration grace = Duration.ofMinutes(retryMaxMinutes);
        retryStates.values().removeIf(state -> now.isAfter(state.nextAttemptAt.plus(grace)));
    }

    private record RetryState(int attempts, Instant nextAttemptAt) {
    }
}
