package com.nextkey.ecommerce.core.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * {@link RefundProcessingService} 的迴圈、退避與失敗隔離（Sprint 226，DEF-303 (5)／DEF-308）。真正的資料庫行為
 * （候選查詢、退款核心、回滾）由 {@code OrderAutoRefundIntegrationTest} 在真實資料庫驗證；這裡只驗證怎麼走頁、怎麼退避。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RefundProcessingService 單元測試（Sprint 226）")
class RefundProcessingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final UUID NIL = new UUID(0L, 0L);

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentStateService paymentStateService;
    @Mock private AuditService auditService;

    @InjectMocks
    private RefundProcessingService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "batchSize", 2);
        ReflectionTestUtils.setField(service, "retryBaseMinutes", 5L);
        ReflectionTestUtils.setField(service, "retryMaxMinutes", 360L);
    }

    private void givenPages(final UUID cursor, final List<UUID> ids) {
        when(orderRepository.findRefundingOrderIdsAfter(eq(Order.OrderStatus.REFUNDING), eq(cursor),
                any(Pageable.class))).thenReturn(ids);
    }

    private static UUID id(final long n) {
        return new UUID(0L, n);
    }

    private void givenFailing(final UUID orderId) {
        when(paymentStateService.refundOrderPaymentAsSystem(eq(orderId), anyString()))
                .thenThrow(new BusinessException(ErrorCode.E_6001, "Stripe refund failed: charge_already_refunded"));
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("逐頁以 id 為游標走完所有待退款訂單（成功的訂單離開 REFUNDING 也不會讓下一頁被跳過），回傳退款張數")
    void walksEveryPageByIdCursor() {
        givenPages(NIL, List.of(id(1), id(2)));
        givenPages(id(2), List.of(id(3)));
        givenPages(id(3), List.of());

        int refunded = service.processPendingOrderRefunds(NOW);

        assertThat(refunded).isEqualTo(3);
        verify(paymentStateService).refundOrderPaymentAsSystem(eq(id(1)), anyString());
        verify(paymentStateService).refundOrderPaymentAsSystem(eq(id(2)), anyString());
        verify(paymentStateService).refundOrderPaymentAsSystem(eq(id(3)), anyString());
    }

    @Test
    @DisplayName("失敗的訂單不擋住後面的訂單：例外被隔離、寫一筆 AUTO_REFUND_FAILED 稽核（含原因），其餘照常退款")
    void oneFailureDoesNotBlockTheOthersAndIsAudited() {
        givenPages(NIL, List.of(id(1), id(2)));
        givenPages(id(2), List.of());
        givenFailing(id(1));

        int refunded = service.processPendingOrderRefunds(NOW);

        assertThat(refunded).isEqualTo(1);
        verify(paymentStateService).refundOrderPaymentAsSystem(eq(id(2)), anyString());
        verify(auditService).record(eq("AUTO_REFUND_FAILED"), eq("ORDER"), eq(id(1)), any(), eq("REFUNDING"),
                eq("REFUNDING"), contains("charge_already_refunded"));
    }

    @Test
    @DisplayName("失敗時訂單已不再是 REFUNDING（別的處理者或管理員搶先退完了）→ 不算失敗：不寫稽核、不退避")
    void failureBecauseAnotherWorkerWonTheRace_isNotAFailure() {
        givenPages(NIL, List.of(id(1)));
        givenPages(id(1), List.of());
        when(paymentStateService.refundOrderPaymentAsSystem(eq(id(1)), anyString()))
                .thenThrow(new BusinessException(ErrorCode.E_6009, "Refund amount conflicts with a concurrent refund"));
        when(orderRepository.findStatusById(id(1))).thenReturn(Optional.of(Order.OrderStatus.REFUNDED));

        int refunded = service.processPendingOrderRefunds(NOW);

        assertThat(refunded).as("不是這一輪退的，不計入").isZero();
        verify(auditService, never()).record(eq("AUTO_REFUND_FAILED"), anyString(), any(), any(), any(), any(), any());
        // 沒有退避：馬上再跑一輪仍會嘗試（若它還是候選的話）
        service.processPendingOrderRefunds(NOW.plusSeconds(1));
        verify(paymentStateService, times(2)).refundOrderPaymentAsSystem(eq(id(1)), anyString());
    }

    @Test
    @DisplayName("整頁都失敗也照樣往下一頁走（永久失敗的訂單排在前面，不會餓死後面的）")
    void aWholePageOfFailuresDoesNotStarveLaterPages() {
        givenPages(NIL, List.of(id(1), id(2)));
        givenPages(id(2), List.of(id(3)));
        givenPages(id(3), List.of());
        givenFailing(id(1));
        givenFailing(id(2));

        int refunded = service.processPendingOrderRefunds(NOW);

        assertThat(refunded).isEqualTo(1);
        verify(paymentStateService).refundOrderPaymentAsSystem(eq(id(3)), anyString());
    }

    @Test
    @DisplayName("失敗後進入退避：退避期間不重試；5 分鐘後重試且退避加倍（第二次失敗要等 10 分鐘）")
    void failedOrderIsBackedOffThenRetriedWithDoubledDelay() {
        givenPages(NIL, List.of(id(1)));
        givenPages(id(1), List.of());
        givenFailing(id(1));

        service.processPendingOrderRefunds(NOW);
        service.processPendingOrderRefunds(NOW.plusSeconds(60));          // 退避中
        verify(paymentStateService, times(1)).refundOrderPaymentAsSystem(eq(id(1)), anyString());

        service.processPendingOrderRefunds(NOW.plusSeconds(5 * 60));      // 第一次退避（5 分鐘）結束
        verify(paymentStateService, times(2)).refundOrderPaymentAsSystem(eq(id(1)), anyString());
        verify(auditService).record(eq("AUTO_REFUND_FAILED"), eq("ORDER"), eq(id(1)), any(), any(), any(),
                contains("attempt=2"));

        service.processPendingOrderRefunds(NOW.plusSeconds(5 * 60 + 9 * 60)); // 第二次退避是 10 分鐘，9 分鐘還在退避
        verify(paymentStateService, times(2)).refundOrderPaymentAsSystem(eq(id(1)), anyString());
        service.processPendingOrderRefunds(NOW.plusSeconds(5 * 60 + 10 * 60));
        verify(paymentStateService, times(3)).refundOrderPaymentAsSystem(eq(id(1)), anyString());
    }

    @Test
    @DisplayName("退避有上限：失敗很多次後最久也只等 360 分鐘")
    void backoffIsCapped() {
        givenPages(NIL, List.of(id(1)));
        givenPages(id(1), List.of());
        givenFailing(id(1));

        Instant t = NOW;
        for (int i = 0; i < 12; i++) {
            service.processPendingOrderRefunds(t);
            t = t.plusSeconds(360L * 60);   // 每次只前進上限那麼久，必定輪得到重試
        }
        verify(paymentStateService, times(12)).refundOrderPaymentAsSystem(eq(id(1)), anyString());

        // 第 12 次失敗發生在 t - 360 分鐘；退避已封頂在 360 分鐘（不是 5 << 11 分鐘）
        Instant lastFailure = t.minusSeconds(360L * 60);
        service.processPendingOrderRefunds(lastFailure.plusSeconds(359L * 60));
        verify(paymentStateService, times(12)).refundOrderPaymentAsSystem(eq(id(1)), anyString());
        service.processPendingOrderRefunds(lastFailure.plusSeconds(360L * 60));
        verify(paymentStateService, times(13)).refundOrderPaymentAsSystem(eq(id(1)), anyString());
    }

    @Test
    @DisplayName("成功會清掉退避：之後再失敗又從第 1 次算起")
    void successResetsBackoff() {
        givenPages(NIL, List.of(id(1)));
        givenPages(id(1), List.of());
        when(paymentStateService.refundOrderPaymentAsSystem(eq(id(1)), anyString()))
                .thenThrow(new IllegalStateException("boom"))
                .thenReturn(null)
                .thenThrow(new IllegalStateException("boom again"));
        when(orderRepository.findById(id(1))).thenReturn(Optional.empty());

        service.processPendingOrderRefunds(NOW);                              // 第 1 次失敗
        assertThat(service.processPendingOrderRefunds(NOW.plusSeconds(6 * 60))).isEqualTo(1); // 成功
        service.processPendingOrderRefunds(NOW.plusSeconds(7 * 60));          // 再失敗

        verify(auditService).record(eq("AUTO_REFUND_FAILED"), eq("ORDER"), eq(id(1)), any(), any(), any(),
                contains("attempt=1,nextAttemptAt=2026-10-01T12:12:00Z"));
    }

    @Test
    @DisplayName("退避狀態不會永遠留著：超過「下次重試時間＋最長退避」沒再出現（訂單已被人工處理）就清掉")
    void staleBackoffStateIsPruned() {
        givenPages(NIL, List.of(id(1)));
        givenPages(id(1), List.of());
        givenFailing(id(1));
        service.processPendingOrderRefunds(NOW);      // attempt=1，下次 12:05

        // 訂單不再是候選（被人工處理掉）很久之後又出現為候選：狀態已被清掉，從第 1 次算起
        Instant muchLater = NOW.plusSeconds(5 * 60 + 360L * 60 + 1);
        service.processPendingOrderRefunds(muchLater);

        verify(auditService, times(2)).record(eq("AUTO_REFUND_FAILED"), eq("ORDER"), eq(id(1)), any(), any(), any(),
                contains("attempt=1"));
        verify(auditService, never()).record(eq("AUTO_REFUND_FAILED"), eq("ORDER"), eq(id(1)), any(), any(), any(),
                contains("attempt=2"));
    }

    @Test
    @DisplayName("沒有待退款訂單 → 什麼都不做")
    void nothingToDo() {
        givenPages(NIL, List.of());

        assertThat(service.processPendingOrderRefunds(NOW)).isZero();
        verify(paymentStateService, never()).refundOrderPaymentAsSystem(any(), anyString());
    }
}
