package com.nextkey.ecommerce.core.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.pricing.PricingService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.RoomCalendarRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * BookingService.updateBooking 日期變更（reschedule）流程單元測試（Sprint 71 US-002）。
 *
 * <p>擁有權檢查已由 {@code BookingServiceOwnershipTest}（Sprint 68 DEF-023）涵蓋，本檔案聚焦於
 * {@code handleDateChange}/{@code processDateRangeChange} 私有邏輯——日期未變更時不動日曆、
 * 日期變更時「釋放舊日期 → 鎖定新日期 → 開放窗守門 → 可用性二次確認 → 更新日期 → 重新計價」的完整流程，
 * 以及新鎖在 {@code finally} 區塊中「無論成功或失敗皆釋放」的健壯性。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BookingService.updateBooking 日期變更流程單元測試")
class BookingServiceUpdateDateChangeTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomCalendarRepository roomCalendarRepository;

    @Mock
    private RoomCalendarService roomCalendarService;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PricingService pricingService;

    @Mock
    private FeatureToggleService featureToggleService;

    @InjectMocks
    private BookingService bookingService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID BOOKING_ID = UUID.randomUUID();
    private static final UUID ROOM_LISTING_ID = UUID.randomUUID();
    private static final BigDecimal BASE_PRICE = BigDecimal.valueOf(1000);
    private static final LocalDate OLD_CHECK_IN = LocalDate.now().plusDays(10);
    private static final LocalDate OLD_CHECK_OUT = LocalDate.now().plusDays(12); // 2 晚

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Booking existingBooking() {
        return Booking.builder()
                .id(BOOKING_ID)
                .userId(USER_ID)
                .roomListingId(ROOM_LISTING_ID)
                .checkInDate(OLD_CHECK_IN)
                .checkOutDate(OLD_CHECK_OUT)
                .guestCount(2)
                .status(Booking.BookingStatus.CREATED)
                .totalAmount(BigDecimal.valueOf(2000))
                .build();
    }

    private Listing roomListing() {
        Listing listing = Listing.builder()
                .listingType(Listing.ListingType.ROOM)
                .basePrice(BASE_PRICE)
                .currency("TWD")
                .status(Listing.ListingStatus.ACTIVE)
                .build();
        listing.setId(ROOM_LISTING_ID);
        return listing;
    }

    @Test
    @DisplayName("日期未變更（與現況相同）→ 不觸發日曆釋放/鎖定/重新計價")
    void updateBooking_datesUnchanged_doesNotTouchCalendar() {
        TenantContext.setCurrentUser(USER_ID);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(existingBooking()));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(roomListing()));

        BookingDto.UpdateRequest request = BookingDto.UpdateRequest.builder()
                .checkInDate(OLD_CHECK_IN)
                .checkOutDate(OLD_CHECK_OUT)
                .guestCount(3)
                .build();

        bookingService.updateBooking(BOOKING_ID, request);

        verify(roomCalendarService, never()).releaseDateRange(any(), any(), any());
        verify(roomCalendarService, never()).lockDateRange(any(), any(), any());
        verify(roomCalendarService, never()).bookDateRange(any(), any(), any(), any());
    }

    @Test
    @DisplayName("日期變更成功：釋放舊日期→鎖定新日期→確認可用→更新日期並重新計價→finally 釋放鎖")
    void updateBooking_dateChanged_success_recalculatesAndReleasesLock() {
        TenantContext.setCurrentUser(USER_ID);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(existingBooking()));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(roomListing()));
        when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(Room.builder().maxGuests(4).build()));
        when(featureToggleService.isFeatureEnabled("DYNAMIC_PRICING_ENABLED")).thenReturn(false);

        LocalDate newCheckIn = OLD_CHECK_IN.plusDays(5);
        LocalDate newCheckOut = OLD_CHECK_OUT.plusDays(5); // 仍是 2 晚
        when(roomCalendarService.lockDateRange(ROOM_LISTING_ID, newCheckIn, newCheckOut)).thenReturn("newLockValue");
        when(roomCalendarService.isDateRangeAvailable(ROOM_LISTING_ID, newCheckIn, newCheckOut)).thenReturn(true);

        BookingDto.UpdateRequest request = BookingDto.UpdateRequest.builder()
                .checkInDate(newCheckIn)
                .checkOutDate(newCheckOut)
                .build();

        BookingDto.BookingResponse response = bookingService.updateBooking(BOOKING_ID, request);

        assertThat(response.getCheckInDate()).isEqualTo(newCheckIn);
        assertThat(response.getCheckOutDate()).isEqualTo(newCheckOut);
        assertThat(response.getTotalAmount()).isEqualByComparingTo(BASE_PRICE.multiply(BigDecimal.valueOf(2)));
        verify(roomCalendarService).releaseDateRange(ROOM_LISTING_ID, OLD_CHECK_IN, OLD_CHECK_OUT);
        verify(roomCalendarService).bookDateRange(ROOM_LISTING_ID, newCheckIn, newCheckOut, BOOKING_ID);
        verify(roomCalendarService).unlockDateRange(ROOM_LISTING_ID, newCheckIn, newCheckOut, "newLockValue");
    }

    @Test
    @DisplayName("新日期取鎖失敗 → E_4001（舊日期已先釋放，@Transactional 回滾負責一致性）")
    void updateBooking_dateChanged_newLockFails_throwsE4001() {
        TenantContext.setCurrentUser(USER_ID);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(existingBooking()));
        LocalDate newCheckIn = OLD_CHECK_IN.plusDays(5);
        LocalDate newCheckOut = OLD_CHECK_OUT.plusDays(5);
        when(roomCalendarService.lockDateRange(ROOM_LISTING_ID, newCheckIn, newCheckOut)).thenReturn(null);

        BookingDto.UpdateRequest request = BookingDto.UpdateRequest.builder()
                .checkInDate(newCheckIn)
                .checkOutDate(newCheckOut)
                .build();

        assertThatThrownBy(() -> bookingService.updateBooking(BOOKING_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_4001);

        verify(roomCalendarService).releaseDateRange(ROOM_LISTING_ID, OLD_CHECK_IN, OLD_CHECK_OUT);
        verify(roomCalendarService, never()).unlockDateRange(any(), any(), any(), any());
    }

    @Test
    @DisplayName("新日期超出開放窗 → E_3002，且 finally 仍釋放新鎖")
    void updateBooking_dateChanged_beyondOpenWindow_throwsE3002AndUnlocks() {
        TenantContext.setCurrentUser(USER_ID);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(existingBooking()));
        LocalDate newCheckIn = OLD_CHECK_IN.plusDays(5);
        LocalDate newCheckOut = OLD_CHECK_OUT.plusDays(5);
        when(roomCalendarService.lockDateRange(ROOM_LISTING_ID, newCheckIn, newCheckOut)).thenReturn("newLockValue");
        when(roomRepository.findByListingId(ROOM_LISTING_ID))
                .thenReturn(Optional.of(Room.builder().maxGuests(4).openUntilDate(OLD_CHECK_IN).build()));

        BookingDto.UpdateRequest request = BookingDto.UpdateRequest.builder()
                .checkInDate(newCheckIn)
                .checkOutDate(newCheckOut)
                .build();

        assertThatThrownBy(() -> bookingService.updateBooking(BOOKING_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_3002);

        verify(roomCalendarService).unlockDateRange(ROOM_LISTING_ID, newCheckIn, newCheckOut, "newLockValue");
    }

    @Test
    @DisplayName("新日期已不可用 → E_4001，且 finally 仍釋放新鎖")
    void updateBooking_dateChanged_newDatesNotAvailable_throwsE4001AndUnlocks() {
        TenantContext.setCurrentUser(USER_ID);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(existingBooking()));
        LocalDate newCheckIn = OLD_CHECK_IN.plusDays(5);
        LocalDate newCheckOut = OLD_CHECK_OUT.plusDays(5);
        when(roomCalendarService.lockDateRange(ROOM_LISTING_ID, newCheckIn, newCheckOut)).thenReturn("newLockValue");
        when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(Room.builder().maxGuests(4).build()));
        when(roomCalendarService.isDateRangeAvailable(ROOM_LISTING_ID, newCheckIn, newCheckOut)).thenReturn(false);

        BookingDto.UpdateRequest request = BookingDto.UpdateRequest.builder()
                .checkInDate(newCheckIn)
                .checkOutDate(newCheckOut)
                .build();

        assertThatThrownBy(() -> bookingService.updateBooking(BOOKING_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_4001);

        verify(roomCalendarService).unlockDateRange(ROOM_LISTING_ID, newCheckIn, newCheckOut, "newLockValue");
    }
}
