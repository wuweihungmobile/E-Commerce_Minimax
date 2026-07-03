package com.nextkey.ecommerce.core.booking;

import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.pricing.PricingService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * BookingService 開放窗語意單元測試（Sprint 47 US-001 / AI-2202e）。
 *
 * 驗證 calendar 顯示層與 availability 檢查層的開放窗行為：
 * - getCalendar：超過開放上限之「無記錄日」補 NOT_OPEN（前端把未回傳日當可訂）
 * - checkAvailability：區間含未開放日 → 不可訂 + 明確原因
 * - 開放窗內 / 無開放窗（room 無設定）→ 維持現狀（回歸保護）
 * booking 寫入層擋訂（createBooking / reschedule）以真 DB 整合測試覆蓋（需 auth context）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService: 開放窗語意（AI-2202e）")
class BookingServiceOpenWindowTest {

    @Mock private com.nextkey.ecommerce.domain.repository.BookingRepository bookingRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private com.nextkey.ecommerce.domain.repository.RoomRepository roomRepository;
    @Mock private com.nextkey.ecommerce.domain.repository.RoomCalendarRepository roomCalendarRepository;
    @Mock private RoomCalendarService roomCalendarService;
    @Mock private com.nextkey.ecommerce.domain.repository.TenantRepository tenantRepository;
    @Mock private com.nextkey.ecommerce.domain.repository.UserRepository userRepository;
    @Mock private PricingService pricingService;
    @Mock private FeatureToggleService featureToggleService;

    @InjectMocks private BookingService bookingService;

    private static final UUID ROOM_LISTING_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440002");
    private static final BigDecimal BASE_PRICE = BigDecimal.valueOf(1000);

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

    private Room roomWithOpenUntil(LocalDate openUntil) {
        return Room.builder().openUntilDate(openUntil).build();
    }

    private BookingDto.AvailabilityRequest request(LocalDate checkIn, LocalDate checkOut) {
        return BookingDto.AvailabilityRequest.builder()
                .roomListingId(ROOM_LISTING_ID)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .build();
    }

    @Test
    @DisplayName("UT-BK-OW-001: getCalendar 超過開放上限之無記錄日補 NOT_OPEN；窗內無記錄日不補")
    void getCalendar_beyondWindow_appendsNotOpen() {
        LocalDate today = LocalDate.now();
        LocalDate openUntil = today.plusDays(3);
        LocalDate start = today;
        LocalDate end = today.plusDays(8);

        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(roomListing()));
        when(roomCalendarService.getCalendarRange(ROOM_LISTING_ID, start, end)).thenReturn(Collections.emptyList());
        when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(roomWithOpenUntil(openUntil)));

        List<BookingDto.CalendarResponse> result = bookingService.getCalendar(ROOM_LISTING_ID, start, end);

        // 窗外（today+4 ~ today+8，共 5 天）補 NOT_OPEN
        assertThat(result).hasSize(5);
        assertThat(result).allMatch(c -> "NOT_OPEN".equals(c.getStatus()));
        assertThat(result).anyMatch(c -> c.getDate().equals(today.plusDays(5)));
        // 窗內（today+2）無記錄 → 不補（維持稀疏）
        assertThat(result).noneMatch(c -> c.getDate().equals(today.plusDays(2)));
        // NOT_OPEN 日以 basePrice 呈現
        assertThat(result).allMatch(c -> c.getPrice().compareTo(BASE_PRICE) == 0);
    }

    @Test
    @DisplayName("UT-BK-OW-002: getCalendar room 無開放窗 → 不補 NOT_OPEN（回歸：維持現狀稀疏）")
    void getCalendar_noWindow_regression() {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(8);

        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(roomListing()));
        when(roomCalendarService.getCalendarRange(ROOM_LISTING_ID, start, end)).thenReturn(Collections.emptyList());
        when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(Room.builder().build()));

        List<BookingDto.CalendarResponse> result = bookingService.getCalendar(ROOM_LISTING_ID, start, end);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("UT-BK-OW-003: checkAvailability 區間含未開放日 → 不可訂 + 未開放原因")
    void checkAvailability_beyondWindow_notAvailable() {
        LocalDate today = LocalDate.now();
        LocalDate openUntil = today.plusDays(2);
        LocalDate checkIn = today.plusDays(5);
        LocalDate checkOut = today.plusDays(7);

        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(roomListing()));
        when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(roomWithOpenUntil(openUntil)));
        when(roomCalendarService.getCalendarRange(any(), any(), any())).thenReturn(Collections.emptyList());

        BookingDto.AvailabilityResponse resp = bookingService.checkAvailability(request(checkIn, checkOut));

        assertThat(resp.isAvailable()).isFalse();
        assertThat(resp.getUnavailableReason()).contains("not open");
    }

    @Test
    @DisplayName("UT-BK-OW-004: checkAvailability 區間在開放窗內 → 可訂（回歸保護）")
    void checkAvailability_withinWindow_available() {
        LocalDate today = LocalDate.now();
        LocalDate openUntil = today.plusDays(30);
        LocalDate checkIn = today.plusDays(2);
        LocalDate checkOut = today.plusDays(4);

        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(roomListing()));
        when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(roomWithOpenUntil(openUntil)));
        when(roomCalendarService.getCalendarRange(any(), any(), any())).thenReturn(Collections.emptyList());

        BookingDto.AvailabilityResponse resp = bookingService.checkAvailability(request(checkIn, checkOut));

        assertThat(resp.isAvailable()).isTrue();
        assertThat(resp.getUnavailableReason()).isNull();
    }
}
