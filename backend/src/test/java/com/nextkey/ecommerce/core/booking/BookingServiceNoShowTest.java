package com.nextkey.ecommerce.core.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;

/**
 * {@link BookingService#cancelBookingIfNoShowDue}（Sprint 246，DEF-352；PRD §17.4.6 Q15）。
 *
 * <p>這裡守的是「寬限期限精算對了」與「不退款、系統取消、併發搶不到就拒絕」；排程怎麼撈候選、怎麼隔離失敗、
 * 怎麼通知由 {@code BookingNoShowServiceTest} 驗證（mock 本服務）。必須是{@code @Transactional}的公開方法、
 * 放在這個類別而不是排程類別自己，理由見方法上的 Javadoc。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BookingService no-show 自動取消（Sprint 246，DEF-352）")
class BookingServiceNoShowTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final LocalTime CHECK_IN_TIME = LocalTime.of(15, 0);

    @Mock private BookingRepository bookingRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private BookingService bookingService;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID roomListingId = UUID.randomUUID();

    private Booking paidBooking(final LocalDate checkInDate) {
        return Booking.builder().id(bookingId).tenantId(TENANT).roomListingId(roomListingId)
                .status(Booking.BookingStatus.PAID).checkInDate(checkInDate).checkOutDate(checkInDate.plusDays(1))
                .guestCount(1).totalAmount(new BigDecimal("1000")).build();
    }

    private void givenBooking(final Booking booking) {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
    }

    private void givenRoomCheckInTime(final LocalTime time) {
        when(roomRepository.findByListingId(roomListingId)).thenReturn(Optional.of(Room.builder().checkInTime(time).build()));
    }

    @Test
    @DisplayName("入住時刻＋寬限小時數已過、仍是 PAID → 取消（SYSTEM，不退款），稽核記下這次轉換")
    void pastGracePeriod_isCancelledAsSystemWithoutRefund() {
        givenBooking(paidBooking(LocalDate.of(2026, 10, 7)));
        givenRoomCheckInTime(CHECK_IN_TIME);
        Instant now = Instant.parse("2026-10-09T10:00:00Z"); // checkInAt(+8)=10-07T07:00Z，+24h=10-08T07:00Z，早於 now
        when(bookingRepository.cancelIfNoShow(bookingId, Booking.BookingStatus.PAID, Booking.BookingStatus.CANCELLED,
                Booking.CancelledBy.SYSTEM, now)).thenReturn(1);

        boolean cancelled = bookingService.cancelBookingIfNoShowDue(bookingId, now, 24L);

        assertThat(cancelled).isTrue();
        verify(auditService).record("BOOKING_CANCELLED", "BOOKING", bookingId, TENANT, "PAID", "CANCELLED",
                "No-show: not checked in within 24 hours of check-in time", null);
    }

    @Test
    @DisplayName("入住時刻已到但寬限小時數還沒過 → 不取消、不寫稽核")
    void withinGracePeriod_isNotCancelled() {
        givenBooking(paidBooking(LocalDate.of(2026, 10, 7)));
        givenRoomCheckInTime(CHECK_IN_TIME);
        Instant now = Instant.parse("2026-10-08T00:00:00Z"); // checkInAt(+8)=10-07T07:00Z，+24h=10-08T07:00Z，now 還沒到

        boolean cancelled = bookingService.cancelBookingIfNoShowDue(bookingId, now, 24L);

        assertThat(cancelled).isFalse();
        verify(bookingRepository, never()).cancelIfNoShow(any(), any(), any(), any(), any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("找不到房源資料 → 用預設入住時間 15:00 精算，不因缺少房源資料而拋例外")
    void missingRoom_fallsBackToDefaultCheckInTime() {
        givenBooking(paidBooking(LocalDate.of(2026, 10, 7)));
        when(roomRepository.findByListingId(roomListingId)).thenReturn(Optional.empty());
        Instant now = Instant.parse("2026-10-09T10:00:00Z");
        when(bookingRepository.cancelIfNoShow(any(), any(), any(), any(), any())).thenReturn(1);

        assertThat(bookingService.cancelBookingIfNoShowDue(bookingId, now, 24L)).isTrue();
    }

    @Test
    @DisplayName("併發搶不到（條件式 UPDATE 回 0，例如同時入住）→ 拒絕，不寫稽核")
    void losingARaceToAConcurrentCheckIn_isRejected() {
        givenBooking(paidBooking(LocalDate.of(2026, 10, 7)));
        givenRoomCheckInTime(CHECK_IN_TIME);
        Instant now = Instant.parse("2026-10-09T10:00:00Z");
        when(bookingRepository.cancelIfNoShow(any(), any(), any(), any(), any())).thenReturn(0);

        assertThat(bookingService.cancelBookingIfNoShowDue(bookingId, now, 24L)).isFalse();
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("已不是 PAID（已入住、已取消…）→ 拒絕，連房源都不查")
    void notPaid_isRejectedWithoutLookingUpRoom() {
        Booking checkedIn = paidBooking(LocalDate.of(2026, 10, 7));
        checkedIn.setStatus(Booking.BookingStatus.CHECKED_IN);
        givenBooking(checkedIn);

        assertThat(bookingService.cancelBookingIfNoShowDue(bookingId, Instant.now(), 24L)).isFalse();
        verify(roomRepository, never()).findByListingId(any());
    }

    @Test
    @DisplayName("找不到訂房（已被刪除）→ 拒絕，不拋例外")
    void bookingNotFound_isRejectedWithoutThrowing() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThat(bookingService.cancelBookingIfNoShowDue(bookingId, Instant.now(), 24L)).isFalse();
    }
}
