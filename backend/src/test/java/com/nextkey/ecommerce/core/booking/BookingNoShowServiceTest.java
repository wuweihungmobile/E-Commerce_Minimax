package com.nextkey.ecommerce.core.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.nextkey.ecommerce.core.notification.BuyerNotificationService;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.shared.time.BusinessTime;

/**
 * {@link BookingNoShowService} 的排程進入點（Sprint 246，DEF-352）：只驗證「怎麼撈候選、怎麼逐筆委派、
 * 失敗怎麼隔離、通知怎麼接在取消之後」。寬限期限精算的真正邏輯在 {@link BookingService#cancelBookingIfNoShowDue}，
 * 這裡全部 mock 掉（見 {@code BookingServiceNoShowTest}），與 {@code BookingTimeoutServiceTest} mock
 * {@code BookingService.cancelExpiredUnpaidBooking} 同一個分層理由。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookingNoShowService 單元測試（Sprint 246，DEF-352）")
class BookingNoShowServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingService bookingService;
    @Mock private BuyerNotificationService buyerNotificationService;

    @InjectMocks
    private BookingNoShowService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "graceHours", 24L);
        ReflectionTestUtils.setField(service, "batchSize", 500);
    }

    private void givenCandidates(final List<UUID> ids) {
        when(bookingRepository.findPotentialNoShowBookingIds(eq(Booking.BookingStatus.PAID), any(LocalDate.class),
                any())).thenReturn(ids);
    }

    @Test
    @DisplayName("候選查詢帶入營運時區的今天、PAID 狀態與設定的批次大小")
    void queriesWithTodayAndConfiguredBatchSize() {
        ReflectionTestUtils.setField(service, "batchSize", 250);
        givenCandidates(List.of());

        int cancelled = service.cancelNoShowBookings(NOW);

        assertThat(cancelled).isZero();
        verify(bookingRepository).findPotentialNoShowBookingIds(eq(Booking.BookingStatus.PAID),
                eq(BusinessTime.today()), argThat((Pageable p) -> p.getPageSize() == 250));
        verify(bookingService, never()).cancelBookingIfNoShowDue(any(), any(), any(Long.class));
    }

    @Test
    @DisplayName("每個候選都委派給 BookingService 判斷與取消，回傳實際取消的筆數，寬限小時數照設定傳入")
    void delegatesEachCandidateToBookingService() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        givenCandidates(List.of(a, b));
        when(bookingService.cancelBookingIfNoShowDue(eq(a), eq(NOW), eq(24L))).thenReturn(true);
        when(bookingService.cancelBookingIfNoShowDue(eq(b), eq(NOW), eq(24L))).thenReturn(false);

        int cancelled = service.cancelNoShowBookings(NOW);

        assertThat(cancelled).isEqualTo(1);
    }

    @Test
    @DisplayName("通知：只通知真的被取消的訂房，且在取消之後才通知；還沒到期或已不符條件的不通知")
    void notifiesOnlyTheBookingsThatWereActuallyCancelled() {
        UUID cancelled = UUID.randomUUID();
        UUID notDue = UUID.randomUUID();
        givenCandidates(List.of(cancelled, notDue));
        when(bookingService.cancelBookingIfNoShowDue(eq(cancelled), any(), any(Long.class))).thenReturn(true);
        when(bookingService.cancelBookingIfNoShowDue(eq(notDue), any(), any(Long.class))).thenReturn(false);

        service.cancelNoShowBookings(NOW);

        InOrder order = inOrder(bookingService, buyerNotificationService);
        order.verify(bookingService).cancelBookingIfNoShowDue(eq(cancelled), any(), any(Long.class));
        order.verify(buyerNotificationService).notifyBookingNoShowCancelled(cancelled);
        verify(buyerNotificationService, never()).notifyBookingNoShowCancelled(notDue);
    }

    @Test
    @DisplayName("一筆處理失敗（例外）不影響其他筆，失敗的那筆不計入、不通知")
    void oneFailureDoesNotStopTheOthers() {
        UUID ok = UUID.randomUUID();
        UUID bad = UUID.randomUUID();
        givenCandidates(List.of(bad, ok));
        when(bookingService.cancelBookingIfNoShowDue(eq(bad), any(), any(Long.class)))
                .thenThrow(new IllegalStateException("boom"));
        when(bookingService.cancelBookingIfNoShowDue(eq(ok), any(), any(Long.class))).thenReturn(true);

        int cancelled = service.cancelNoShowBookings(NOW);

        assertThat(cancelled).isEqualTo(1);
        verify(buyerNotificationService).notifyBookingNoShowCancelled(ok);
        verify(buyerNotificationService, never()).notifyBookingNoShowCancelled(bad);
    }

    @Test
    @DisplayName("沒有候選 → 回傳 0，不委派、不通知")
    void noCandidates_doesNothing() {
        givenCandidates(List.of());

        assertThat(service.cancelNoShowBookings(NOW)).isZero();
        verify(bookingService, never()).cancelBookingIfNoShowDue(any(), any(), any(Long.class));
        verify(buyerNotificationService, never()).notifyBookingNoShowCancelled(any());
    }
}
