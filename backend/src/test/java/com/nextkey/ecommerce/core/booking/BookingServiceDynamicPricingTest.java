package com.nextkey.ecommerce.core.booking;

import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.api.dto.PricingDto;
import com.nextkey.ecommerce.core.pricing.PricingService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BookingService 動態定價接線單元測試（Sprint 43 US-002 / AI-2402；Sprint 46 AI-2406b 漲價）
 *
 * 驗證 checkAvailability 的動態定價路徑：
 * - 引擎回折扣 → totalPrice 為折扣後總價 + 回原價/有號差額(正)/DISCOUNT/規則名
 * - 引擎回漲價（AI-2406b 選項 B）→ totalPrice 為漲價後總價 + 回原價/有號差額(負)/MARKUP/規則名
 * - 引擎回「沒有規則生效」（adjustedTotal == baseTotal）→ 不套用（totalPrice 為原價）
 *
 * <p>DEF-424（Sprint 309，使用者裁決）：動態定價開關由 {@code PricingService} 以<b>房源所屬店鋪</b>把關；訂房自己不再查任何開關，
 * 一律向引擎查詢（原本查呼叫者租戶的開關，訂房者都是系統租戶，其開關在種子是開啟，店鋪的開關形同虛設）。
 * 開關本身的案例在 {@code PricingServiceTest.StoreToggleGatesApplicationTests}。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService: 動態定價接線（AI-2402 / AI-2406b）")
class BookingServiceDynamicPricingTest {

    @Mock private com.nextkey.ecommerce.domain.repository.BookingRepository bookingRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private com.nextkey.ecommerce.domain.repository.RoomRepository roomRepository;
    @Mock private com.nextkey.ecommerce.domain.repository.RoomCalendarRepository roomCalendarRepository;
    @Mock private RoomCalendarService roomCalendarService;
    @Mock private com.nextkey.ecommerce.domain.repository.TenantRepository tenantRepository;
    @Mock private com.nextkey.ecommerce.domain.repository.UserRepository userRepository;
    @Mock private PricingService pricingService;

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

    private BookingDto.AvailabilityRequest request(LocalDate checkIn, LocalDate checkOut) {
        return BookingDto.AvailabilityRequest.builder()
                .roomListingId(ROOM_LISTING_ID)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .build();
    }

    private PricingDto.CalculatePriceResponse pricingResponse(
            BigDecimal baseTotal, BigDecimal adjustedTotal, String ruleName) {
        PricingDto.PriceBreakdown day = PricingDto.PriceBreakdown.builder()
                .date(LocalDate.of(2026, 8, 1))
                .basePrice(BASE_PRICE)
                .adjustedPrice(adjustedTotal.divide(BigDecimal.valueOf(2)))
                .appliedRuleName(ruleName)
                .adjustmentType("PERCENTAGE")
                .adjustmentValue(BigDecimal.valueOf(15))
                .build();
        return PricingDto.CalculatePriceResponse.builder()
                .roomListingId(ROOM_LISTING_ID)
                .checkInDate(LocalDate.of(2026, 8, 1))
                .checkOutDate(LocalDate.of(2026, 8, 3))
                .nights(2)
                .baseTotal(baseTotal)
                .adjustedTotal(adjustedTotal)
                .discount(baseTotal.subtract(adjustedTotal).max(BigDecimal.ZERO))
                .currency("TWD")
                .breakdown(List.of(day))
                .build();
    }

    @Test
    @DisplayName("UT-BK-DP-001: 引擎回折扣 → totalPrice 為折扣後總價 + 回原價/折扣額/規則名")
    void checkAvailability_dynamicPricingWithDiscount_returnsDiscountedTotal() {
        LocalDate checkIn = LocalDate.of(2026, 8, 1);
        LocalDate checkOut = LocalDate.of(2026, 8, 3); // 2 晚

        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(roomListing()));
        when(roomCalendarService.getCalendarRange(any(), any(), any())).thenReturn(Collections.emptyList());
        // 原價 2000，早鳥 15% off → 1700
        when(pricingService.calculatePrice(any()))
                .thenReturn(pricingResponse(BigDecimal.valueOf(2000), BigDecimal.valueOf(1700), "Early Bird 15% off"));

        BookingDto.AvailabilityResponse resp = bookingService.checkAvailability(request(checkIn, checkOut));

        assertThat(resp.isAvailable()).isTrue();
        assertThat(resp.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(1700));
        assertThat(resp.getOriginalTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        assertThat(resp.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(300));
        assertThat(resp.getAppliedRuleName()).isEqualTo("Early Bird 15% off");
        assertThat(resp.getPriceAdjustmentType()).isEqualTo("DISCOUNT");
    }

    @Test
    @DisplayName("UT-BK-DP-004: 引擎回漲價（AI-2406b）→ totalPrice 為漲價後總價 + 有號差額(負)/MARKUP")
    void checkAvailability_dynamicPricingWithMarkup_returnsMarkedUpTotal() {
        LocalDate checkIn = LocalDate.of(2026, 8, 1);
        LocalDate checkOut = LocalDate.of(2026, 8, 3); // 2 晚

        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(roomListing()));
        when(roomCalendarService.getCalendarRange(any(), any(), any())).thenReturn(Collections.emptyList());
        // 原價 2000，週末加成 20% → 2400（漲價；選項 B 一律計入）
        when(pricingService.calculatePrice(any()))
                .thenReturn(pricingResponse(BigDecimal.valueOf(2000), BigDecimal.valueOf(2400), "Weekend 20% up"));

        BookingDto.AvailabilityResponse resp = bookingService.checkAvailability(request(checkIn, checkOut));

        assertThat(resp.isAvailable()).isTrue();
        assertThat(resp.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(2400));
        assertThat(resp.getOriginalTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        // 有號差額：原價 − 調整後 = 2000 − 2400 = −400（負=加價）
        assertThat(resp.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(-400));
        assertThat(resp.getAppliedRuleName()).isEqualTo("Weekend 20% up");
        assertThat(resp.getPriceAdjustmentType()).isEqualTo("MARKUP");
    }

    @Test
    @DisplayName("UT-BK-DP-002: 店鋪開關關閉時引擎回基準價 → 維持既有計價；訂房一律向引擎查詢（這間房源、這段日期），自己不判斷開關（DEF-424）")
    void checkAvailability_engineReturnsNoAdjustment_usesCalendarBaseTotalAndAlwaysAsksTheEngine() {
        LocalDate checkIn = LocalDate.of(2026, 8, 1);
        LocalDate checkOut = LocalDate.of(2026, 8, 3); // 2 晚，無日曆記錄 → basePrice × 2 = 2000

        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(roomListing()));
        when(roomCalendarService.getCalendarRange(any(), any(), any())).thenReturn(Collections.emptyList());
        // 店鋪的動態定價開關關閉時，PricingService.calculatePrice 的行為：沒有規則生效，調整前後總額相同
        when(pricingService.calculatePrice(any())).thenReturn(BookingPricingFixtures.noAdjustment());

        BookingDto.AvailabilityResponse resp = bookingService.checkAvailability(request(checkIn, checkOut));

        assertThat(resp.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        assertThat(resp.getOriginalTotalPrice()).isNull();
        assertThat(resp.getDiscountAmount()).isNull();
        assertThat(resp.getAppliedRuleName()).isNull();
        // 原本這裡斷言「開關關閉時不呼叫 PricingService」（訂房自己查呼叫者租戶的開關）；現在由引擎以房源所屬店鋪把關
        ArgumentCaptor<PricingDto.CalculatePriceRequest> asked =
                ArgumentCaptor.forClass(PricingDto.CalculatePriceRequest.class);
        verify(pricingService).calculatePrice(asked.capture());
        assertThat(asked.getValue().getRoomListingId()).isEqualTo(ROOM_LISTING_ID);
        assertThat(asked.getValue().getCheckInDate()).isEqualTo(checkIn);
        assertThat(asked.getValue().getCheckOutDate()).isEqualTo(checkOut);
    }

    @Test
    @DisplayName("UT-BK-DP-003: 引擎回的調整前後總額相同（adjustedTotal == baseTotal）→ 不套用，維持原價")
    void checkAvailability_dynamicPricingNoDiscount_usesCalendarBaseTotal() {
        LocalDate checkIn = LocalDate.of(2026, 8, 1);
        LocalDate checkOut = LocalDate.of(2026, 8, 3);

        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(roomListing()));
        when(roomCalendarService.getCalendarRange(any(), any(), any())).thenReturn(Collections.emptyList());
        // 無折扣：adjustedTotal == baseTotal
        when(pricingService.calculatePrice(any()))
                .thenReturn(pricingResponse(BigDecimal.valueOf(2000), BigDecimal.valueOf(2000), null));

        BookingDto.AvailabilityResponse resp = bookingService.checkAvailability(request(checkIn, checkOut));

        assertThat(resp.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        assertThat(resp.getOriginalTotalPrice()).isNull();
        assertThat(resp.getDiscountAmount()).isNull();
    }
}
