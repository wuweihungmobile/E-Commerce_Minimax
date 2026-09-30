package com.nextkey.ecommerce.core.payment;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.domain.model.order.Order;
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
    private static final int MAX_ERROR_LENGTH = 500;

    private final OrderRepository orderRepository;
    private final PaymentStateService paymentStateService;
    private final AuditService auditService;

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
        int refunded = processPendingOrderRefunds(Instant.now());
        if (refunded > 0) {
            log.info("Automatic refunds: refunded {} orders", refunded);
        }
    }

    /**
     * 退款給 {@code now} 當下所有等待退款（{@code REFUNDING}）且不在退避期間的訂單，回傳實際退款成功的張數。
     *
     * @param now 判斷退避是否結束的基準時間（測試可指定；排程傳入現在）
     */
    public int processPendingOrderRefunds(final Instant now) {
        pruneRetryStates(now);
        int refunded = 0;
        UUID cursor = NIL_UUID;
        for (int page = 0; page < MAX_PAGES_PER_RUN; page++) {
            List<UUID> ids = orderRepository.findRefundingOrderIdsAfter(Order.OrderStatus.REFUNDING, cursor,
                    PageRequest.of(0, batchSize));
            if (ids.isEmpty()) {
                break;
            }
            for (UUID orderId : ids) {
                if (refundOne(orderId, now)) {
                    refunded++;
                }
            }
            cursor = ids.get(ids.size() - 1);
        }
        return refunded;
    }

    private boolean refundOne(final UUID orderId, final Instant now) {
        RetryState state = retryStates.get(orderId);
        if (state != null && now.isBefore(state.nextAttemptAt)) {
            return false; // 上次失敗，還在退避期間
        }
        try {
            paymentStateService.refundOrderPaymentAsSystem(orderId, REASON);
            retryStates.remove(orderId);
            return true;
        } catch (RuntimeException e) {
            if (handledByAnotherWorker(orderId)) {
                log.info("Automatic refund skipped, the order was handled concurrently: orderId={}", orderId);
                return false;
            }
            recordFailure(orderId, state, now, e);
            return false;
        }
    }

    /**
     * 失敗的當下訂單已不再等待退款＝另一個處理者（多實例的另一台、或管理員手動退款）搶先做完了：這不是失敗，不寫稽核、不退避。
     * 搶輸的一方會在行鎖釋放後才發現額度已被佔走（{@code E-6009}）或狀態已變（{@code E-5012}），那時對方的交易已提交，
     * 所以這裡讀到的一定是最新狀態。
     */
    private boolean handledByAnotherWorker(final UUID orderId) {
        return orderRepository.findStatusById(orderId)
                .filter(status -> status != Order.OrderStatus.REFUNDING)
                .isPresent();
    }

    private void recordFailure(final UUID orderId, final RetryState previous, final Instant now,
            final RuntimeException error) {
        int attempts = previous == null ? 1 : previous.attempts + 1;
        // 5 分鐘起，每次加倍，上限 retry-max-minutes（位移量封頂以免溢位）
        long backoffMinutes = Math.min(retryMaxMinutes, retryBaseMinutes << Math.min(attempts - 1, 20));
        Instant next = now.plus(Duration.ofMinutes(backoffMinutes));
        retryStates.put(orderId, new RetryState(attempts, next));

        String message = String.valueOf(error.getMessage());
        if (message.length() > MAX_ERROR_LENGTH) {
            message = message.substring(0, MAX_ERROR_LENGTH);
        }
        log.error("Automatic refund failed, order stays REFUNDING and will be retried: orderId={}, attempt={}, "
                + "nextAttemptAt={}, error={}", orderId, attempts, next, message, error);
        UUID tenantId = orderRepository.findById(orderId).map(Order::getTenantId).orElse(null);
        auditService.record("AUTO_REFUND_FAILED", "ORDER", orderId, tenantId,
                Order.OrderStatus.REFUNDING.name(), Order.OrderStatus.REFUNDING.name(),
                "attempt=" + attempts + ",nextAttemptAt=" + next + ",error=" + message);
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
