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
import java.util.Map;
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
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.room.RoomCalendar;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
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

    @Mock
    private BookingRepository bookingRepository;

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
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
                ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1))).thenReturn(calendars);

        roomCalendarService.releaseDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT);

        verify(roomCalendarRepository, times(2)).save(argThat(
                c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.AVAILABLE && c.getBookingId() == null));
    }

    @Test
    @DisplayName("非 BOOKED 日期（如 BLOCKED）→ 不受釋放影響")
    void releaseDateRange_nonBookedDates_notModified() {
        List<RoomCalendar> calendars = List.of(calendarOf(CHECK_IN, RoomCalendar.RoomCalendarStatus.BLOCKED, null));
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
                ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1))).thenReturn(calendars);

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
        // Sprint 243（DEF-343）：RedisLockService 的 forceReleaseLock 自己會加 "lock:" 前綴，所以要傳資源 ID。
        // 原測試期望 "lock:" + 資源 ID，把「刪掉不存在的 lock:lock:…」這個缺陷固定成了預期行為
        verify(redisLockService).forceReleaseLock(dateLockKeyDay1);
    }

    @Test
    @DisplayName("unlockDateRange 釋放所有日期鎖與統一 key")
    void unlockDateRange_releasesAllDateLocksAndUnifiedKey() {
        roomCalendarService.unlockDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, "someLockValue");

        // 傳資源 ID（不含 lock: 前綴），見上一個測試的說明（DEF-343）
        verify(redisLockService).forceReleaseLock("room:" + ROOM_LISTING_ID + ":date:" + CHECK_IN);
        verify(redisLockService).forceReleaseLock("room:" + ROOM_LISTING_ID + ":date:" + CHECK_IN.plusDays(1));
        verify(redisLockService).forceReleaseLock(
                "booking:lock:room:" + ROOM_LISTING_ID + ":dates:" + CHECK_IN + ":" + CHECK_OUT);
    }

    // ========== blockDateRange / unblockDateRange ==========

    @Test
    @DisplayName("封鎖日期範圍 → 全部設為 BLOCKED")
    void blockDateRange_setsAllToBlocked() {
        List<RoomCalendar> calendars = List.of(
                calendarOf(CHECK_IN, RoomCalendar.RoomCalendarStatus.AVAILABLE, null),
                calendarOf(CHECK_IN.plusDays(1), RoomCalendar.RoomCalendarStatus.AVAILABLE, null));
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
                ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1))).thenReturn(calendars);

        roomCalendarService.blockDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT);

        verify(roomCalendarRepository, times(2)).save(argThat(c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.BLOCKED));
    }

    @Test
    @DisplayName("DEF-360：全新房源（無任何既有 room_calendar 記錄）封鎖日期範圍 → 補建記錄後設為 BLOCKED，"
            + "不再靜默空操作")
    void blockDateRange_freshRoomNoCalendarRecords_createsEntriesAndSetsBlocked() {
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
                ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1))).thenReturn(List.of());
        Listing listing = Listing.builder().build();
        listing.setId(ROOM_LISTING_ID);
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(listing));
        when(roomCalendarRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        roomCalendarService.blockDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT);

        verify(roomCalendarRepository, times(2)).saveAndFlush(any());
        verify(roomCalendarRepository, times(2)).save(argThat(c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.BLOCKED));
    }

    @Test
    @DisplayName("DEF-363：blockDateRange 悲觀鎖搶占失敗 → E_4001（與 markMaintenance 共用同一個"
            + "lockCalendarRange 私有方法，同一路徑）")
    void blockDateRange_pessimisticLockFailure_throwsE4001() {
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
                ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1)))
                .thenThrow(new PessimisticLockingFailureException("locked"));

        assertThatThrownBy(() -> roomCalendarService.blockDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_4001);
    }

    @Test
    @DisplayName("取消封鎖：僅 BLOCKED 日期回復 AVAILABLE，其餘（如 BOOKED）不受影響")
    void unblockDateRange_onlyBlockedDatesRevert() {
        List<RoomCalendar> calendars = List.of(
                calendarOf(CHECK_IN, RoomCalendar.RoomCalendarStatus.BLOCKED, null),
                calendarOf(CHECK_IN.plusDays(1), RoomCalendar.RoomCalendarStatus.BOOKED, BOOKING_ID));
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
                ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1))).thenReturn(calendars);

        roomCalendarService.unblockDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT);

        verify(roomCalendarRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("DEF-363：unblockDateRange 悲觀鎖搶占失敗 → E_4001")
    void unblockDateRange_pessimisticLockFailure_throwsE4001() {
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
                ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1)))
                .thenThrow(new PessimisticLockingFailureException("locked"));

        assertThatThrownBy(() -> roomCalendarService.unblockDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_4001);
    }

    // ========== markMaintenance / unmarkMaintenance（PRD §5.5.3，Sprint 96） ==========

    @Test
    @DisplayName("標記維護：AVAILABLE → MAINTENANCE，無 booking 不觸發 Booking 更新")
    void markMaintenance_availableDates_setsMaintenanceWithoutTouchingBooking() {
        List<RoomCalendar> calendars = List.of(
                calendarOf(CHECK_IN, RoomCalendar.RoomCalendarStatus.AVAILABLE, null),
                calendarOf(CHECK_IN.plusDays(1), RoomCalendar.RoomCalendarStatus.AVAILABLE, null));
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
                ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1))).thenReturn(calendars);

        roomCalendarService.markMaintenance(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, true);

        verify(roomCalendarRepository, times(2)).save(argThat(c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.MAINTENANCE));
        verify(bookingRepository, never()).findById(any());
    }

    @Test
    @DisplayName("DEF-360：全新房源（無任何既有 room_calendar 記錄）標記維護 → 補建記錄後設為 MAINTENANCE，"
            + "不再回 200 卻靜默空操作")
    void markMaintenance_freshRoomNoCalendarRecords_createsEntriesAndSetsMaintenance() {
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
                ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1))).thenReturn(List.of());
        Listing listing = Listing.builder().build();
        listing.setId(ROOM_LISTING_ID);
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(listing));
        when(roomCalendarRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        roomCalendarService.markMaintenance(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, true);

        // 注意：saveAndFlush 傳入的是與後續 save 同一個物件參照，verify 檢查的是呼叫結束當下的最終狀態
        // （已被下面迴圈改成 MAINTENANCE），不是呼叫那一刻的 AVAILABLE，故這裡只驗證呼叫次數
        verify(roomCalendarRepository, times(2)).saveAndFlush(any());
        verify(roomCalendarRepository, times(2)).save(argThat(c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.MAINTENANCE));
        verify(bookingRepository, never()).findById(any());
    }

    @Test
    @DisplayName("DEF-360：部分日期有既有記錄、部分缺漏 → 只補建缺漏的日期，既有記錄不受影響")
    void markMaintenance_partialCalendarRecords_fillsOnlyMissingDates() {
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
                ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1)))
                .thenReturn(List.of(calendarOf(CHECK_IN, RoomCalendar.RoomCalendarStatus.AVAILABLE, null)));
        Listing listing = Listing.builder().build();
        listing.setId(ROOM_LISTING_ID);
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(listing));
        when(roomCalendarRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        roomCalendarService.markMaintenance(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, true);

        // 只有缺漏的第二天（CHECK_IN+1）會被建立，第一天的既有記錄直接沿用
        verify(roomCalendarRepository, times(1)).saveAndFlush(
                argThat(c -> c.getCalendarDate().equals(CHECK_IN.plusDays(1))));
        verify(roomCalendarRepository, times(2)).save(argThat(c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.MAINTENANCE));
    }

    @Test
    @DisplayName("DEF-360：建立缺漏記錄時發生 unique constraint 衝突（併發）→ 改讀取對方剛建立的記錄並繼續")
    void markMaintenance_concurrentCreationConflict_recoversExistingEntry() {
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
                ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1))).thenReturn(List.of());
        Listing listing = Listing.builder().build();
        listing.setId(ROOM_LISTING_ID);
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(listing));
        when(roomCalendarRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateWithLockNowait(eq(ROOM_LISTING_ID), any()))
                .thenAnswer(inv -> Optional.of(calendarOf(inv.getArgument(1), RoomCalendar.RoomCalendarStatus.AVAILABLE, null)));

        roomCalendarService.markMaintenance(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, true);

        verify(roomCalendarRepository, times(2)).save(argThat(c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.MAINTENANCE));
    }

    @Test
    @DisplayName("標記維護：已 BOOKED 日期 → 保留 bookingId、狀態改 MAINTENANCE，且 Booking.statusFlags 標記 under_maintenance=true")
    void markMaintenance_bookedDate_preservesBookingIdAndFlagsBooking() {
        List<RoomCalendar> calendars = List.of(
                calendarOf(CHECK_IN, RoomCalendar.RoomCalendarStatus.BOOKED, BOOKING_ID),
                calendarOf(CHECK_IN.plusDays(1), RoomCalendar.RoomCalendarStatus.AVAILABLE, null));
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
                ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1))).thenReturn(calendars);
        Booking booking = Booking.builder().id(BOOKING_ID).build();
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        roomCalendarService.markMaintenance(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, true);

        verify(roomCalendarRepository).save(argThat(
                c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.MAINTENANCE && BOOKING_ID.equals(c.getBookingId())));
        verify(bookingRepository).save(argThat(b -> Boolean.TRUE.equals(b.getStatusFlags().get("under_maintenance"))));
    }

    @Test
    @DisplayName("標記維護：已是 MAINTENANCE 的日期 → 略過（idempotent，不重複 save）")
    void markMaintenance_alreadyMaintenance_idempotentSkip() {
        List<RoomCalendar> calendars = List.of(
                calendarOf(CHECK_IN, RoomCalendar.RoomCalendarStatus.MAINTENANCE, null),
                calendarOf(CHECK_IN.plusDays(1), RoomCalendar.RoomCalendarStatus.MAINTENANCE, null));
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
                ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1))).thenReturn(calendars);

        roomCalendarService.markMaintenance(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, true);

        verify(roomCalendarRepository, never()).save(any());
    }

    @Test
    @DisplayName("解除維護：保留 bookingId 者恢復 BOOKED，並清除 Booking 的 under_maintenance 標記")
    void unmarkMaintenance_withBookingId_restoresBookedAndClearsFlag() {
        List<RoomCalendar> calendars = List.of(calendarOf(CHECK_IN, RoomCalendar.RoomCalendarStatus.MAINTENANCE, BOOKING_ID));
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
                ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1))).thenReturn(calendars);
        Booking booking = Booking.builder().id(BOOKING_ID)
                .statusFlags(new java.util.HashMap<>(Map.of("under_maintenance", true)))
                .build();
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        roomCalendarService.unmarkMaintenance(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, true);

        verify(roomCalendarRepository).save(argThat(
                c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.BOOKED && BOOKING_ID.equals(c.getBookingId())));
        verify(bookingRepository).save(argThat(b -> !b.getStatusFlags().containsKey("under_maintenance")));
    }

    @Test
    @DisplayName("解除維護：無 bookingId 者恢復 AVAILABLE")
    void unmarkMaintenance_withoutBookingId_restoresAvailable() {
        List<RoomCalendar> calendars = List.of(calendarOf(CHECK_IN, RoomCalendar.RoomCalendarStatus.MAINTENANCE, null));
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
                ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1))).thenReturn(calendars);

        roomCalendarService.unmarkMaintenance(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, true);

        verify(roomCalendarRepository).save(argThat(c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.AVAILABLE));
        verify(bookingRepository, never()).findById(any());
    }

    @Test
    @DisplayName("解除維護：非 MAINTENANCE 的日期不受影響（idempotent）")
    void unmarkMaintenance_nonMaintenanceDates_notModified() {
        List<RoomCalendar> calendars = List.of(calendarOf(CHECK_IN, RoomCalendar.RoomCalendarStatus.BLOCKED, null));
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
                ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1))).thenReturn(calendars);

        roomCalendarService.unmarkMaintenance(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, true);

        verify(roomCalendarRepository, never()).save(any());
    }

    @Test
    @DisplayName("DEF-134/135/136：markMaintenance 悲觀鎖搶占失敗 → E_4001（releaseDateRange/"
            + "unmarkMaintenance 共用同一個 lockCalendarRange 私有方法，同一路徑）")
    void markMaintenance_pessimisticLockFailure_throwsE4001() {
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
                ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1)))
                .thenThrow(new PessimisticLockingFailureException("locked"));

        assertThatThrownBy(() -> roomCalendarService.markMaintenance(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_4001);
    }

    // ========== DEF-256（Sprint 185）：租戶擁有權檢查 ==========

    @Test
    @DisplayName("🔴 DEF-256：markMaintenance 對他租戶的 roomListingId 呼叫 → E_1007，不得竄改他租戶"
            + "房源的維護狀態")
    void markMaintenance_crossTenantListing_throwsE1007() {
        Listing otherTenantListing = Listing.builder().id(ROOM_LISTING_ID).tenantId(UUID.randomUUID()).build();
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(otherTenantListing));
        com.nextkey.ecommerce.shared.tenant.TenantContext.setCurrentTenant(UUID.randomUUID());

        try {
            assertThatThrownBy(() -> roomCalendarService.markMaintenance(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, false))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.E_1007);
            verify(roomCalendarRepository, never())
                    .findByRoomListingIdAndCalendarDateBetweenWithLockNowait(any(), any(), any());
        } finally {
            com.nextkey.ecommerce.shared.tenant.TenantContext.clear();
        }
    }

    @Test
    @DisplayName("🔴 DEF-256：unmarkMaintenance 對他租戶的 roomListingId 呼叫 → E_1007")
    void unmarkMaintenance_crossTenantListing_throwsE1007() {
        Listing otherTenantListing = Listing.builder().id(ROOM_LISTING_ID).tenantId(UUID.randomUUID()).build();
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(otherTenantListing));
        com.nextkey.ecommerce.shared.tenant.TenantContext.setCurrentTenant(UUID.randomUUID());

        try {
            assertThatThrownBy(() ->
                    roomCalendarService.unmarkMaintenance(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, false))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.E_1007);
            verify(roomCalendarRepository, never())
                    .findByRoomListingIdAndCalendarDateBetweenWithLockNowait(any(), any(), any());
        } finally {
            com.nextkey.ecommerce.shared.tenant.TenantContext.clear();
        }
    }

    @Test
    @DisplayName("markMaintenance：本租戶的 roomListingId → 放行（續走既有維護邏輯）")
    void markMaintenance_sameTenantListing_passesAuthorization() {
        UUID tenantId = UUID.randomUUID();
        Listing ownListing = Listing.builder().id(ROOM_LISTING_ID).tenantId(tenantId).build();
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(ownListing));
        com.nextkey.ecommerce.shared.tenant.TenantContext.setCurrentTenant(tenantId);
        List<RoomCalendar> calendars = List.of(
                calendarOf(CHECK_IN, RoomCalendar.RoomCalendarStatus.AVAILABLE, null),
                calendarOf(CHECK_IN.plusDays(1), RoomCalendar.RoomCalendarStatus.AVAILABLE, null));
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
                ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1))).thenReturn(calendars);

        try {
            roomCalendarService.markMaintenance(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, false);

            verify(roomCalendarRepository, times(2)).save(argThat(c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.MAINTENANCE));
        } finally {
            com.nextkey.ecommerce.shared.tenant.TenantContext.clear();
        }
    }

    // ========== DEF-415（Sprint 304）：沒有店鋪的呼叫者不得管理系統租戶（平台自營）房源的日曆 ==========
    //
    // 沒有店鋪者（含沒有店鋪的 ADMIN）的租戶是系統租戶佔位值，平台自營房源也蓋成同一個值；舊檢查只比「租戶相等」，
    // 所以下列案例的呼叫者與房源「同租戶」。房源以 lenient() 備妥——守門被拿掉時舊的相等檢查會放行、操作會繼續執行，
    // 測試才會紅（而不是被 Mockito 對 Optional 的預設值 empty 掩蓋成別的例外）。

    private static final UUID SYSTEM_TENANT =
            UUID.fromString(com.nextkey.ecommerce.shared.constants.AppConstants.SYSTEM_TENANT_ID);

    @Test
    @DisplayName("DEF-415：markMaintenance 沒有店鋪的呼叫者對系統租戶房源 → E_1007，且在查詢房源之前就擋下（先判斷再查詢）")
    void markMaintenance_storelessCallerOnSystemTenantListing_throwsE1007BeforeAnyLookup() {
        org.mockito.Mockito.lenient().when(listingRepository.findById(ROOM_LISTING_ID))
                .thenReturn(Optional.of(Listing.builder().id(ROOM_LISTING_ID).tenantId(SYSTEM_TENANT).build()));
        com.nextkey.ecommerce.shared.tenant.TenantContext.setCurrentTenant(SYSTEM_TENANT);

        try {
            assertThatThrownBy(() -> roomCalendarService.markMaintenance(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, false))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.E_1007);
            verify(listingRepository, never()).findById(any());
            verify(roomCalendarRepository, never())
                    .findByRoomListingIdAndCalendarDateBetweenWithLockNowait(any(), any(), any());
            verify(roomCalendarRepository, never()).save(any());
        } finally {
            com.nextkey.ecommerce.shared.tenant.TenantContext.clear();
        }
    }

    @Test
    @DisplayName("DEF-415：unmarkMaintenance 沒有店鋪的呼叫者對系統租戶房源 → E_1007，維護中的日期不被動到")
    void unmarkMaintenance_storelessCallerOnSystemTenantListing_throwsE1007AndKeepsMaintenance() {
        org.mockito.Mockito.lenient().when(listingRepository.findById(ROOM_LISTING_ID))
                .thenReturn(Optional.of(Listing.builder().id(ROOM_LISTING_ID).tenantId(SYSTEM_TENANT).build()));
        com.nextkey.ecommerce.shared.tenant.TenantContext.setCurrentTenant(SYSTEM_TENANT);

        try {
            assertThatThrownBy(() ->
                    roomCalendarService.unmarkMaintenance(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, false))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.E_1007);
            verify(roomCalendarRepository, never())
                    .findByRoomListingIdAndCalendarDateBetweenWithLockNowait(any(), any(), any());
            verify(roomCalendarRepository, never()).save(any());
        } finally {
            com.nextkey.ecommerce.shared.tenant.TenantContext.clear();
        }
    }

    @Test
    @DisplayName("DEF-415：SUPER_ADMIN（租戶同為系統租戶）仍可對系統租戶房源標記維護——守門不得連平台管理員一起擋")
    void markMaintenance_superAdminInSystemTenant_stillAllowed() {
        com.nextkey.ecommerce.shared.tenant.TenantContext.setCurrentTenant(SYSTEM_TENANT);
        List<RoomCalendar> calendars = List.of(
                calendarOf(CHECK_IN, RoomCalendar.RoomCalendarStatus.AVAILABLE, null),
                calendarOf(CHECK_IN.plusDays(1), RoomCalendar.RoomCalendarStatus.AVAILABLE, null));
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(
                ROOM_LISTING_ID, CHECK_IN, CHECK_OUT.minusDays(1))).thenReturn(calendars);

        try {
            roomCalendarService.markMaintenance(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, true);

            verify(roomCalendarRepository, times(2)).save(argThat(c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.MAINTENANCE));
        } finally {
            com.nextkey.ecommerce.shared.tenant.TenantContext.clear();
        }
    }
}
