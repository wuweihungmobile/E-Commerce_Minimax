package com.nextkey.ecommerce.core.order;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.core.notification.BuyerNotificationService;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 未付款訂單逾時自動取消（Sprint 219，DEF-302；使用者 2026-09-29 拍板逾時為 24 小時）。
 *
 * <p>建立後沒付款的訂單會一直佔著預扣庫存與優惠券額度（放棄付款、Stripe 結帳中途離開都會產生）。本排程把
 * 建立超過 {@code app.order-timeout.unpaid-hours}（預設 24）仍是 {@code CREATED} 的訂單取消，補償與買家自己取消
 * 完全相同（{@link OrderService#cancelExpiredUnpaidOrder}，共用 {@code compensateCancellation}）。
 *
 * <p>逐張各自一個交易：一張失敗只留下它自己（下輪重試），不影響其他張。每輪處理多個批次以便在首次啟用時消化
 * 累積的舊單，但遇到整批都沒進展就停止，避免一直重試同一批壞單。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTimeoutService {

    /** 每輪最多處理的批次數（×批次大小 ＝ 每輪上限，其餘留給下一輪）。 */
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final BuyerNotificationService buyerNotificationService;

    @Value("${app.order-timeout.unpaid-hours:24}")
    private long unpaidHours;

    @Value("${app.order-timeout.batch-size:100}")
    private int batchSize;

    /** 排程進入點；間隔與首次延遲可由設定覆寫（測試用）。 */
    @Scheduled(fixedDelayString = "${app.order-timeout.check-interval-ms:300000}",
            initialDelayString = "${app.order-timeout.initial-delay-ms:60000}")
    public void cancelExpiredUnpaidOrders() {
        int cancelled = cancelExpiredUnpaidOrders(Instant.now());
        if (cancelled > 0) {
            log.info("Unpaid order timeout: cancelled {} orders", cancelled);
        }
    }

    /**
     * 取消 {@code now} 當下已逾時的未付款訂單，回傳實際取消的張數。
     *
     * @param now 以此為基準算截止時間（測試可指定；排程傳入現在）
     */
    public int cancelExpiredUnpaidOrders(final Instant now) {
        Instant cutoff = now.minus(Duration.ofHours(unpaidHours));
        int cancelled = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<UUID> ids = orderRepository.findExpiredUnpaidOrderIds(Order.OrderStatus.CREATED, cutoff,
                    Payment.PaymentStatus.SUCCESS, Payment.PaymentStatus.PROCESSING, PageRequest.of(0, batchSize));
            if (ids.isEmpty()) {
                break;
            }
            int cancelledInBatch = 0;
            for (UUID orderId : ids) {
                if (cancelOne(orderId, cutoff)) {
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

    private boolean cancelOne(final UUID orderId, final Instant cutoff) {
        final boolean cancelled;
        try {
            cancelled = orderService.cancelExpiredUnpaidOrder(orderId, cutoff);
        } catch (RuntimeException e) {
            log.error("Failed to cancel expired unpaid order, will retry next run: orderId={}, error={}",
                    orderId, e.getMessage(), e);
            return false;
        }
        if (cancelled) {
            // 取消的交易已提交；只有真的取消了這張才通知（PRD US-014）。通知永不拋例外，也不影響取消結果
            buyerNotificationService.notifyOrderPaymentTimeout(orderId);
        }
        return cancelled;
    }
}
