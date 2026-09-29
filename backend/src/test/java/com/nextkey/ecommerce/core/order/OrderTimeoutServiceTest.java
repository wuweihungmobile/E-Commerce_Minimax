package com.nextkey.ecommerce.core.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.repository.OrderRepository;

/**
 * {@link OrderTimeoutService} 的迴圈行為（Sprint 219，DEF-302）。真正的查詢與取消條件由
 * {@code OrderTimeoutIntegrationTest} 在真實資料庫驗證；這裡只驗證截止時間怎麼算、批次怎麼走、失敗怎麼隔離。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderTimeoutService 單元測試（Sprint 219）")
class OrderTimeoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;

    @InjectMocks
    private OrderTimeoutService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "unpaidHours", 24L);
        ReflectionTestUtils.setField(service, "batchSize", 2);
    }

    private void givenBatches(final List<UUID>... batches) {
        var stub = when(orderRepository.findExpiredUnpaidOrderIds(eq(Order.OrderStatus.CREATED), any(Instant.class),
                eq(Payment.PaymentStatus.SUCCESS), eq(Payment.PaymentStatus.PROCESSING), any(Pageable.class)));
        for (List<UUID> batch : batches) {
            stub = stub.thenReturn(batch);
        }
    }

    @Test
    @DisplayName("截止時間 ＝ 基準時間往前 24 小時，並以批次大小分頁")
    void cutoffIsUnpaidHoursBeforeNow() {
        givenBatches(List.of());

        int cancelled = service.cancelExpiredUnpaidOrders(NOW);

        assertThat(cancelled).isZero();
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findExpiredUnpaidOrderIds(eq(Order.OrderStatus.CREATED), cutoff.capture(),
                eq(Payment.PaymentStatus.SUCCESS), eq(Payment.PaymentStatus.PROCESSING), page.capture());
        assertThat(cutoff.getValue()).isEqualTo(Instant.parse("2026-09-29T12:00:00Z"));
        assertThat(page.getValue().getPageSize()).isEqualTo(2);
        verify(orderService, never()).cancelExpiredUnpaidOrder(any(), any());
    }

    @Test
    @DisplayName("逐批處理直到沒有候選，回傳實際取消的張數")
    void processesBatchesUntilEmpty() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        givenBatches(List.of(a, b), List.of(c), List.of());
        when(orderService.cancelExpiredUnpaidOrder(any(), any())).thenReturn(true);

        int cancelled = service.cancelExpiredUnpaidOrders(NOW);

        assertThat(cancelled).isEqualTo(3);
        verify(orderService).cancelExpiredUnpaidOrder(eq(a), any());
        verify(orderService).cancelExpiredUnpaidOrder(eq(b), any());
        verify(orderService).cancelExpiredUnpaidOrder(eq(c), any());
    }

    @Test
    @DisplayName("一張失敗只留下它自己：例外被隔離，其餘照常取消")
    void oneFailureDoesNotStopTheOthers() {
        UUID ok1 = UUID.randomUUID();
        UUID bad = UUID.randomUUID();
        UUID ok2 = UUID.randomUUID();
        givenBatches(List.of(ok1, bad), List.of(ok2), List.of());
        when(orderService.cancelExpiredUnpaidOrder(eq(ok1), any())).thenReturn(true);
        when(orderService.cancelExpiredUnpaidOrder(eq(bad), any())).thenThrow(new IllegalStateException("boom"));
        when(orderService.cancelExpiredUnpaidOrder(eq(ok2), any())).thenReturn(true);

        int cancelled = service.cancelExpiredUnpaidOrders(NOW);

        assertThat(cancelled).isEqualTo(2);
        verify(orderService).cancelExpiredUnpaidOrder(eq(ok2), any());
    }

    @Test
    @DisplayName("整批都沒有進展（全失敗或都已不符條件）→ 停止，不重複撈同一批")
    void stopsWhenABatchMakesNoProgress() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        givenBatches(List.of(a, b));
        when(orderService.cancelExpiredUnpaidOrder(any(), any())).thenReturn(false);

        int cancelled = service.cancelExpiredUnpaidOrders(NOW);

        assertThat(cancelled).isZero();
        verify(orderRepository, times(1)).findExpiredUnpaidOrderIds(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("每輪批次數有上限，其餘留給下一輪")
    void capsBatchesPerRun() {
        when(orderRepository.findExpiredUnpaidOrderIds(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> List.of(UUID.randomUUID()));
        when(orderService.cancelExpiredUnpaidOrder(any(), any())).thenReturn(true);

        int cancelled = service.cancelExpiredUnpaidOrders(NOW);

        assertThat(cancelled).isEqualTo(20);
        verify(orderRepository, times(20)).findExpiredUnpaidOrderIds(any(), any(), any(), any(), any());
    }
}
