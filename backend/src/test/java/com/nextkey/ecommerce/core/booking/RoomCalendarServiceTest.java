package com.nextkey.ecommerce.core.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;

import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.RoomCalendar;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.RoomCalendarRepository;
import com.nextkey.ecommerce.infrastructure.redis.RedisLockService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * RoomCalendarService 單元測試（Sprint 71 US-001）。
 *
 * <p>此檔案先前不存在（Sprint 69/70 盤點確認零單元測試覆蓋），從零建立。重點涵蓋：
 * <ul>
 *   <li>{@code bookDateRange} 的 idempotency 核心邏輯——同一 bookingId 重複呼叫應跳過（不重複 save）、
 *       不同 bookingId 衝突應擋（E_4001），以及 unique constraint / 悲觀鎖失敗的並發衝突處理。</li>
 *   <li>{@code isDateRangeAvailable} 的悲觀鎖（FOR UPDATE NOWAIT）與狀態判斷。</li>
 *   <li>{@code releaseDateRange}/{@code blockDateRange}/{@code unblockDateRange} 的狀態轉換（僅動到符合條件的日期）。</li>
 *   <li>{@code lockDateRangeNoWait}/{@code unlockDateRange} 的 Redis 鎖生命週期，含部分取鎖失敗時
 *       回滾已取得的鎖（避免鎖洩漏）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoomCalendarService 單元測試（idempotency 核心）")
class RoomCalendarServiceTest {

    @Mock
    private RoomCalendarRepository roomCalendarRepository;

    @Mock
    private RedisLockService redisLockService;

    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private RoomCalendarService roomCalendarService;

    private static final UUID ROOM_LISTING_ID = UUID.randomUUID();
    private static final UUID BOOKING_ID = UUID.randomUUID();
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 8, 1);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 8, 3); // 2 晚：8/1, 8/2

    private RoomCalendar calendarOf(final LocalDate date, final RoomCalendar.RoomCalendarStatus status, final UUID bookingId) {
        return RoomCalendar.builder()
                .calendarDate(date)
                .status(status)
                .bookingId(bookingId)
                .build();
    }

    // ========== isDateRangeAvailable ==========

    @Test
    @DisplayName("所有日期皆無記錄 → 視為可訂")
    void isDateRangeAvailable_noRecords_returnsTrue() {
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateWithLockNowait(eq(ROOM_LISTING_ID), any()))
                .thenReturn(Optional.empty());

        boolean result = roomCalendarService.isDateRangeAvailable(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("所有日期皆 AVAILABLE → 可訂")
    void isDateRangeAvailable_allAvailable_returnsTrue() {
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateWithLockNowait(eq(ROOM_LISTING_ID), any()))
                .thenAnswer(inv -> Optional.of(calendarOf(inv.getArgument(1), RoomCalendar.RoomCalendarStatus.AVAILABLE, null)));

        boolean result = roomCalendarService.isDateRangeAvailable(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("任一日期已 BOOKED → 不可訂")
    void isDateRangeAvailable_oneDateBooked_returnsFalse() {
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateWithLockNowait(ROOM_LISTING_ID, CHECK_IN))
                .thenReturn(Optional.of(calendarOf(CHECK_IN, RoomCalendar.RoomCalendarStatus.BOOKED, BOOKING_ID)));

        boolean result = roomCalendarService.isDateRangeAvailable(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("悲觀鎖取得失敗（NOWAIT）→ 視為不可用")
    void isDateRangeAvailable_pessimisticLockFailure_returnsFalse() {
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateWithLockNowait(ROOM_LISTING_ID, CHECK_IN))
                .thenThrow(new PessimisticLockingFailureException("locked"));

        boolean result = roomCalendarService.isDateRangeAvailable(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT);

        assertThat(result).isFalse();
    }

    // ========== bookDateRange（idempotency 核心） ==========

    @Test
    @DisplayName("日期無記錄 → 建立新 BOOKED 條目")
    void bookDateRange_noRecord_createsBookedEntry() {
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateWithLockNowait(eq(ROOM_LISTING_ID), any()))
                .thenReturn(Optional.empty());
        Listing listing = Listing.builder().build();
        listing.setId(ROOM_LISTING_ID);
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(listing));

        roomCalendarService.bookDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, BOOKING_ID);

        verify(roomCalendarRepository, times(2)).saveAndFlush(argThat(
                c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.BOOKED && BOOKING_ID.equals(c.getBookingId())));
    }

    @Test
    @DisplayName("建立新條目時發生 unique constraint 衝突 → E_4001（並發訂房）")
    void bookDateRange_uniqueConstraintViolation_throwsE4001() {
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateWithLockNowait(eq(ROOM_LISTING_ID), any()))
                .thenReturn(Optional.empty());
        Listing listing = Listing.builder().build();
        listing.setId(ROOM_LISTING_ID);
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(listing));
        when(roomCalendarRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> roomCalendarService.bookDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, BOOKING_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_4001);
    }

    @Test
    @DisplayName("同一 booking 重複呼叫（idempotent）→ 略過已 BOOKED 的日期，不重複 save")
    void bookDateRange_sameBookingAlreadyBooked_idempotentSkip() {
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateWithLockNowait(eq(ROOM_LISTING_ID), any()))
                .thenAnswer(inv -> Optional.of(calendarOf(inv.getArgument(1), RoomCalendar.RoomCalendarStatus.BOOKED, BOOKING_ID)));

        roomCalendarService.bookDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, BOOKING_ID);

        verify(roomCalendarRepository, never()).save(any());
        verify(roomCalendarRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("已被其他 booking 佔用 → E_4001（衝突，非 idempotent）")
    void bookDateRange_bookedByDifferentBooking_throwsE4001() {
        UUID otherBookingId = UUID.randomUUID();
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateWithLockNowait(ROOM_LISTING_ID, CHECK_IN))
                .thenReturn(Optional.of(calendarOf(CHECK_IN, RoomCalendar.RoomCalendarStatus.BOOKED, otherBookingId)));

        assertThatThrownBy(() -> roomCalendarService.bookDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, BOOKING_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_4001);
    }

    @Test
    @DisplayName("既有記錄非 BOOKED（如 AVAILABLE）→ 更新為 BOOKED")
    void bookDateRange_existingAvailable_updatesToBooked() {
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateWithLockNowait(eq(ROOM_LISTING_ID), any()))
                .thenAnswer(inv -> Optional.of(calendarOf(inv.getArgument(1), RoomCalendar.RoomCalendarStatus.AVAILABLE, null)));

        roomCalendarService.bookDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, BOOKING_ID);

        verify(roomCalendarRepository, times(2)).save(argThat(
                c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.BOOKED && BOOKING_ID.equals(c.getBookingId())));
    }

    @Test
    @DisplayName("悲觀鎖取得失敗 → E_4001")
    void bookDateRange_pessimisticLockFailure_throwsE4001() {
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateWithLockNowait(ROOM_LISTING_ID, CHECK_IN))
                .thenThrow(new PessimisticLockingFailureException("locked"));

        assertThatThrownBy(() -> roomCalendarService.bookDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, BOOKING_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_4001);
    }

    // ========== releaseDateRange ==========

    @Test
    @DisplayName("釋放 BOOKED 日期 → 回復 AVAILABLE 並清空 bookingId")
    void releaseDateRange_bookedDates_revertsToAvailable() {
        List<RoomCalendar> calendars = List.of(
                calendarOf(CHECK_IN, RoomCalendar.RoomCalendarStatus.BOOKED, BOOKING_ID),
                calendarOf(CHECK_IN.plusDays(1), RoomCalendar.RoomCalendarStatus.BOOKED, BOOKING_ID));
        when(roomCalendarRepository.findByListingIdAndCalendarDateBetween(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1)))
                .thenReturn(calendars);

        roomCalendarService.releaseDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT);

        verify(roomCalendarRepository, times(2)).save(argThat(
                c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.AVAILABLE && c.getBookingId() == null));
    }

    @Test
    @DisplayName("非 BOOKED 日期（如 BLOCKED）→ 不受釋放影響")
    void releaseDateRange_nonBookedDates_notModified() {
        List<RoomCalendar> calendars = List.of(calendarOf(CHECK_IN, RoomCalendar.RoomCalendarStatus.BLOCKED, null));
        when(roomCalendarRepository.findByListingIdAndCalendarDateBetween(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1)))
                .thenReturn(calendars);

        roomCalendarService.releaseDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT);

        verify(roomCalendarRepository, never()).save(any());
    }

    // ========== lockDateRangeNoWait / unlockDateRange ==========

    @Test
    @DisplayName("所有日期皆取得鎖 → 回傳統一 lock key")
    void lockDateRangeNoWait_allAcquired_returnsUnifiedKey() {
        when(redisLockService.tryAcquireLockNoWait(anyString())).thenReturn(UUID.randomUUID().toString());

        String result = roomCalendarService.lockDateRangeNoWait(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT);

        assertThat(result).isEqualTo("booking:lock:room:" + ROOM_LISTING_ID + ":dates:" + CHECK_IN + ":" + CHECK_OUT);
    }

    @Test
    @DisplayName("部分日期取鎖失敗 → 釋放已取得的鎖並回傳 null（避免鎖洩漏）")
    void lockDateRangeNoWait_partialFailure_releasesAcquiredLocksAndReturnsNull() {
        String dateLockKeyDay1 = "room:" + ROOM_LISTING_ID + ":date:" + CHECK_IN;
        String dateLockKeyDay2 = "room:" + ROOM_LISTING_ID + ":date:" + CHECK_IN.plusDays(1);
        when(redisLockService.tryAcquireLockNoWait(dateLockKeyDay1)).thenReturn("lockValue1");
        when(redisLockService.tryAcquireLockNoWait(dateLockKeyDay2)).thenReturn(null);

        String result = roomCalendarService.lockDateRangeNoWait(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT);

        assertThat(result).isNull();
        verify(redisLockService).forceReleaseLock("lock:" + dateLockKeyDay1);
    }

    @Test
    @DisplayName("unlockDateRange 釋放所有日期鎖與統一 key")
    void unlockDateRange_releasesAllDateLocksAndUnifiedKey() {
        roomCalendarService.unlockDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, "someLockValue");

        verify(redisLockService).forceReleaseLock("lock:room:" + ROOM_LISTING_ID + ":date:" + CHECK_IN);
        verify(redisLockService).forceReleaseLock("lock:room:" + ROOM_LISTING_ID + ":date:" + CHECK_IN.plusDays(1));
        verify(redisLockService).forceReleaseLock(
                "lock:booking:lock:room:" + ROOM_LISTING_ID + ":dates:" + CHECK_IN + ":" + CHECK_OUT);
    }

    // ========== blockDateRange / unblockDateRange ==========

    @Test
    @DisplayName("封鎖日期範圍 → 全部設為 BLOCKED")
    void blockDateRange_setsAllToBlocked() {
        List<RoomCalendar> calendars = List.of(calendarOf(CHECK_IN, RoomCalendar.RoomCalendarStatus.AVAILABLE, null));
        when(roomCalendarRepository.findByListingIdAndCalendarDateBetween(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1)))
                .thenReturn(calendars);

        roomCalendarService.blockDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT);

        verify(roomCalendarRepository).save(argThat(c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.BLOCKED));
    }

    @Test
    @DisplayName("取消封鎖：僅 BLOCKED 日期回復 AVAILABLE，其餘（如 BOOKED）不受影響")
    void unblockDateRange_onlyBlockedDatesRevert() {
        List<RoomCalendar> calendars = List.of(
                calendarOf(CHECK_IN, RoomCalendar.RoomCalendarStatus.BLOCKED, null),
                calendarOf(CHECK_IN.plusDays(1), RoomCalendar.RoomCalendarStatus.BOOKED, BOOKING_ID));
        when(roomCalendarRepository.findByListingIdAndCalendarDateBetween(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1)))
                .thenReturn(calendars);

        roomCalendarService.unblockDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT);

        verify(roomCalendarRepository, times(1)).save(any());
    }
}
