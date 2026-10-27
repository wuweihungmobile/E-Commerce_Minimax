package com.nextkey.ecommerce.core.booking;

import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.core.pricing.PricingService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * BookingService.getCalendar 輸入驗證單元測試（DEF-341，Sprint 279）。
 *
 * <p>先前「endDate 早於 startDate」與「區間超過 92 天」皆誤用 {@code E_3001}（預設訊息「無效的
 * 刊登類型」，與日期區間完全無關）。改用與同模組 §4.3 建立訂房一致的 {@code E_4003}（「無效的日期
 * 區間」）。「不是 ROOM 類型」維持 {@code E_3001}（語意正確，未受本次修改影響，此處作回歸確認）。
 *
 * <p>本方法先前完全沒有針對這三個驗證分支的測試覆蓋（見 {@code BookingServiceOwnerCalendarTest}
 * 註解：「資料內容本身沿用既有 getCalendar 邏輯，不重複驗證（已由 BookingServiceOpenWindowTest 等
 * 覆蓋）」——實際查核後，開放窗測試只覆蓋正常資料路徑，三個驗證分支皆未被任何既有測試觸及）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService.getCalendar: 輸入驗證（DEF-341）")
class BookingServiceGetCalendarValidationTest {

    @Mock private ListingRepository listingRepository;
    @Mock private com.nextkey.ecommerce.domain.repository.BookingRepository bookingRepository;
    @Mock private com.nextkey.ecommerce.domain.repository.RoomRepository roomRepository;
    @Mock private com.nextkey.ecommerce.domain.repository.RoomCalendarRepository roomCalendarRepository;
    @Mock private RoomCalendarService roomCalendarService;
    @Mock private com.nextkey.ecommerce.domain.repository.TenantRepository tenantRepository;
    @Mock private com.nextkey.ecommerce.domain.repository.UserRepository userRepository;
    @Mock private PricingService pricingService;
    @Mock private AuditService auditService;

    @InjectMocks private BookingService bookingService;

    /**
     * DEF-424（Sprint 309）：BookingService 不再自己判斷動態定價開關，一律向引擎查詢。這個類別測的是輸入驗證、不是定價，
     * 引擎預設回「沒有規則生效」（與店鋪開關關閉時的實際結果相同）。部分案例在驗證階段就丟例外、不會呼叫引擎，所以用 lenient。
     */
    @BeforeEach
    void stubPricingEngine() {
        lenient().when(pricingService.calculatePrice(any())).thenReturn(BookingPricingFixtures.noAdjustment());
    }

    private static final UUID ROOM_LISTING_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440020");

    private Listing roomListing() {
        Listing listing = Listing.builder()
                .listingType(Listing.ListingType.ROOM)
                .status(Listing.ListingStatus.ACTIVE)
                .build();
        listing.setId(ROOM_LISTING_ID);
        return listing;
    }

    @Test
    @DisplayName("endDate 早於 startDate → E_4003（DEF-341，原誤用 E_3001「無效的刊登類型」）")
    void endDateBeforeStartDate_throwsE4003() {
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(roomListing()));
        LocalDate start = LocalDate.now();
        LocalDate end = start.minusDays(1);

        assertThatThrownBy(() -> bookingService.getCalendar(ROOM_LISTING_ID, start, end))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_4003);
    }

    @Test
    @DisplayName("區間超過 92 天 → E_4003（DEF-341，原誤用 E_3001「無效的刊登類型」）")
    void rangeTooLarge_throwsE4003() {
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(roomListing()));
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(93); // 含頭含尾 94 天 > 92 天上限

        assertThatThrownBy(() -> bookingService.getCalendar(ROOM_LISTING_ID, start, end))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_4003);
    }

    @Test
    @DisplayName("恰好 92 天（含頭含尾）→ 不拋例外，剛好落在上限內")
    void rangeExactlyAtLimit_doesNotThrow() {
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(roomListing()));
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(91); // 含頭含尾恰好 92 天
        when(roomCalendarService.getCalendarRange(ROOM_LISTING_ID, start, end)).thenReturn(java.util.List.of());

        assertThat(bookingService.getCalendar(ROOM_LISTING_ID, start, end)).isEmpty();
    }

    @Test
    @DisplayName("不是 ROOM 類型 → 仍是 E_3001（語意正確，未受 DEF-341 影響，回歸確認）")
    void notRoomType_stillThrowsE3001() {
        Listing productListing = Listing.builder().listingType(Listing.ListingType.PRODUCT).build();
        productListing.setId(ROOM_LISTING_ID);
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(productListing));
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(5);

        assertThatThrownBy(() -> bookingService.getCalendar(ROOM_LISTING_ID, start, end))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_3001);
    }
}
