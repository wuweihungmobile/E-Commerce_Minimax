package com.nextkey.ecommerce.core.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.nextkey.ecommerce.core.notification.BuyerNotificationService;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.repository.BookingRepository;

/**
 * {@link BookingTimeoutService} 的迴圈行為（Sprint 225，DEF-311）。真正的查詢與取消條件由
 * {@code BookingTimeoutIntegrationTest} 在真實資料庫驗證；這裡只驗證結帳截止時間怎麼算、批次怎麼走、失敗怎麼隔離。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookingTimeoutService 單元測試（Sprint 225）")
class BookingTimeoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingService bookingService;
    @Mock private BuyerNotificationService buyerNotificationService;

    @InjectMocks
    private BookingTimeoutService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "stripeSessionHours", 24L);
        ReflectionTestUtils.setField(service, "batchSize", 2);
    }

    private void givenBatches(final List<UUID>... batches) {
        var stub = when(bookingRepository.findExpiredUnpaidBookingIds(eq(Booking.BookingStatus.CREATED),
                any(Instant.class), any(Instant.class), eq(Payment.PaymentStatus.SUCCESS),
                eq(Payment.PaymentStatus.PROCESSING), any(Pageable.class)));
        for (List<UUID> batch : batches) {
            stub = stub.thenReturn(batch);
        }
    }

    @Test
    @DisplayName("逾時以「現在」判斷（付款期限已存在資料裡）；結帳截止時間 ＝ 現在往前 24 小時；以批次大小分頁")
    void usesNowForDeadlineAndTwentyFourHoursForCheckoutCutoff() {
        givenBatches(List.of());

        int cancelled = service.cancelExpiredUnpaidBookings(NOW);

        assertThat(cancelled).isZero();
        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> checkoutCutoff = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(bookingRepository).findExpiredUnpaidBookingIds(eq(Booking.BookingStatus.CREATED), now.capture(),
                checkoutCutoff.capture(), eq(Payment.PaymentStatus.SUCCESS), eq(Payment.PaymentStatus.PROCESSING),
                page.capture());
        assertThat(now.getValue()).isEqualTo(NOW);
        assertThat(checkoutCutoff.getValue()).isEqualTo(Instant.parse("2026-09-30T12:00:00Z"));
        assertThat(page.getValue().getPageSize()).isEqualTo(2);
        verify(bookingService, never()).cancelExpiredUnpaidBooking(any(), any(), any());
    }

    @Test
    @DisplayName("逐批處理直到沒有候選，回傳實際取消的筆數")
    void processesBatchesUntilEmpty() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        givenBatches(List.of(a, b), List.of(c), List.of());
        when(bookingService.cancelExpiredUnpaidBooking(any(), any(), any())).thenReturn(true);

        int cancelled = service.cancelExpiredUnpaidBookings(NOW);

        assertThat(cancelled).isEqualTo(3);
        verify(bookingService).cancelExpiredUnpaidBooking(eq(a), eq(NOW), any());
        verify(bookingService).cancelExpiredUnpaidBooking(eq(b), eq(NOW), any());
        verify(bookingService).cancelExpiredUnpaidBooking(eq(c), eq(NOW), any());
    }

    @Test
    @DisplayName("一筆失敗只留下它自己：例外被隔離，其餘照常取消")
    void oneFailureDoesNotStopTheOthers() {
        UUID ok1 = UUID.randomUUID();
        UUID bad = UUID.randomUUID();
        UUID ok2 = UUID.randomUUID();
        givenBatches(List.of(ok1, bad), List.of(ok2), List.of());
        when(bookingService.cancelExpiredUnpaidBooking(eq(ok1), any(), any())).thenReturn(true);
        when(bookingService.cancelExpiredUnpaidBooking(eq(bad), any(), any()))
                .thenThrow(new IllegalStateException("boom"));
        when(bookingService.cancelExpiredUnpaidBooking(eq(ok2), any(), any())).thenReturn(true);

        int cancelled = service.cancelExpiredUnpaidBookings(NOW);

        assertThat(cancelled).isEqualTo(2);
        verify(bookingService).cancelExpiredUnpaidBooking(eq(ok2), any(), any());
    }

    @Test
    @DisplayName("整批都沒有進展（全失敗或都已不符條件）→ 停止，不重複撈同一批")
    void stopsWhenABatchMakesNoProgress() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        givenBatches(List.of(a, b));
        when(bookingService.cancelExpiredUnpaidBooking(any(), any(), any())).thenReturn(false);

        int cancelled = service.cancelExpiredUnpaidBookings(NOW);

        assertThat(cancelled).isZero();
        verify(bookingRepository, times(1)).findExpiredUnpaidBookingIds(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("每輪批次數有上限，其餘留給下一輪")
    void capsBatchesPerRun() {
        when(bookingRepository.findExpiredUnpaidBookingIds(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> List.of(UUID.randomUUID()));
        when(bookingService.cancelExpiredUnpaidBooking(any(), any(), any())).thenReturn(true);

        int cancelled = service.cancelExpiredUnpaidBookings(NOW);

        assertThat(cancelled).isEqualTo(20);
        verify(bookingRepository, times(20)).findExpiredUnpaidBookingIds(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("通知（PRD US-014）：取消之後才通知，而且只通知真的被取消的訂房；已不符條件或取消失敗的不通知")
    void notifiesOnlyTheBookingsThatWereActuallyCancelled() {
        UUID cancelled = UUID.randomUUID();
        UUID noLongerEligible = UUID.randomUUID();
        UUID failed = UUID.randomUUID();
        givenBatches(List.of(cancelled, noLongerEligible), List.of(failed));
        when(bookingService.cancelExpiredUnpaidBooking(eq(cancelled), any(), any())).thenReturn(true);
        when(bookingService.cancelExpiredUnpaidBooking(eq(noLongerEligible), any(), any())).thenReturn(false);
        when(bookingService.cancelExpiredUnpaidBooking(eq(failed), any(), any()))
                .thenThrow(new IllegalStateException("boom"));

        service.cancelExpiredUnpaidBookings(NOW);

        InOrder inOrder = inOrder(bookingService, buyerNotificationService);
        inOrder.verify(bookingService).cancelExpiredUnpaidBooking(eq(cancelled), any(), any());
        inOrder.verify(buyerNotificationService).notifyBookingPaymentTimeout(cancelled);
        verify(buyerNotificationService, never()).notifyBookingPaymentTimeout(noLongerEligible);
        verify(buyerNotificationService, never()).notifyBookingPaymentTimeout(failed);
    }
}
