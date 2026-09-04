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
import org.junit.jupiter.api.BeforeEach;
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
import com.nextkey.ecommerce.core.promo.PromoService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
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
 * BookingService.createBooking 單元測試（Sprint 71 US-002）。
 *
 * <p>建立預訂流程先前僅由 E2E/整合測試（真實 Spring Context + PostgreSQL + Redis）間接涵蓋
 * （見 {@code BookingIntegrationTest}/{@code BookingControllerE2ETest}），純 Mockito 單元測試從零建立。
 * 重點涵蓋房源/房型/人數/日期前置驗證、開放窗守門，以及 Redis 鎖取得 → 可用性二次確認（並發控制）→
 * 寫入 → 更新日曆的完整流程，並驗證鎖在 {@code finally} 區塊中「無論成功或失敗皆釋放」的健壯性
 * （避免並發鎖洩漏，屬冪等/並發安全相關考量）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BookingService.createBooking 單元測試")
class BookingServiceCreateBookingTest {

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

    @Mock
    private PromoService promoService;

    @InjectMocks
    private BookingService bookingService;

    /**
     * Sprint 126（DEF-048 擴大範圍）：{@code resolveValidPromoForCheckout}／
     * {@code computeCappedDiscount} 從 {@code BookingService} 私有方法移至 {@code PromoService}
     * 共用。本檔案的測試皆不涉及促銷碼情境（{@code request.getPromoCode()} 未設定，
     * {@code resolveValidPromoForCheckout} 未 stub 也預設回傳 null，與修改前行為一致，不需額外
     * stub）；但 {@code computeCappedDiscount} 回傳型別是 {@code BigDecimal}，未 stub 的 mock
     * 預設回傳 null 而非 {@code BigDecimal.ZERO}，會在金額運算中 NPE，故仍需明確給定預設值。
     */
    @BeforeEach
    void setUpPromoDefaults() {
        when(promoService.computeCappedDiscount(any(), any(), any())).thenReturn(BigDecimal.ZERO);
    }

    private static final UUID ROOM_LISTING_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final BigDecimal BASE_PRICE = BigDecimal.valueOf(1000);
    private static final LocalDate CHECK_IN = LocalDate.now().plusDays(1);
    private static final LocalDate CHECK_OUT = LocalDate.now().plusDays(3); // 2 晚

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Listing activeRoomListing() {
        Listing listing = Listing.builder()
                .listingType(Listing.ListingType.ROOM)
                .basePrice(BASE_PRICE)
                .currency("TWD")
                .status(Listing.ListingStatus.ACTIVE)
                .build();
        listing.setId(ROOM_LISTING_ID);
        return listing;
    }

    private Room room() {
        return Room.builder().maxGuests(4).build();
    }

    private BookingDto.CreateRequest createRequest() {
        return BookingDto.CreateRequest.builder()
                .roomListingId(ROOM_LISTING_ID)
                .checkInDate(CHECK_IN)
                .checkOutDate(CHECK_OUT)
                .guestCount(2)
                .guestName("Alice")
                .build();
    }

    /** 通過房源/人數/日期/開放窗檢查、取得鎖之前的共用 stub。 */
    private void stubHappyPathUpToLock() {
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(activeRoomListing()));
        when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room()));
        when(roomCalendarService.lockDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT)).thenReturn("lockValue");
        when(featureToggleService.isFeatureEnabled("DYNAMIC_PRICING_ENABLED")).thenReturn(false);
    }

    // ========== 正常路徑 ==========

    @Test
    @DisplayName("正常建立預訂：鎖定→可用性確認→寫入 booking→更新日曆→finally 釋放鎖")
    void createBooking_success_savesBookingAndReleasesLock() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        stubHappyPathUpToLock();
        when(roomCalendarService.isDateRangeAvailable(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT)).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder().id(USER_ID).build()));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(Tenant.builder().id(TENANT_ID).build()));
        UUID savedBookingId = UUID.randomUUID();
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(savedBookingId);
            return b;
        });

        BookingDto.BookingResponse response = bookingService.createBooking(createRequest(), "idem-key");

        // 註：Booking.roomListingId 為 insertable=false/updatable=false 影子欄位，純 mock（無真實 JPA
        // 持久化/reload）情境下不會自動由 roomListing 關聯回填，故改用 checkIn/checkOut/totalAmount 驗證
        // 回應內容正確，並以 ArgumentCaptor 驗證實際存入的 Booking 關聯房源正確。
        assertThat(response.getCheckInDate()).isEqualTo(CHECK_IN);
        assertThat(response.getCheckOutDate()).isEqualTo(CHECK_OUT);
        assertThat(response.getTotalAmount()).isEqualByComparingTo(BASE_PRICE.multiply(BigDecimal.valueOf(2)));
        verify(bookingRepository).save(org.mockito.ArgumentMatchers.argThat(
                b -> ROOM_LISTING_ID.equals(b.getRoomListing().getId())));
        verify(roomCalendarService).bookDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, savedBookingId);
        verify(roomCalendarService).unlockDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, "lockValue");
        verify(bookingRepository).flush();
    }

    // ========== 前置驗證錯誤路徑 ==========

    @Test
    @DisplayName("房源不存在 → E_4000")
    void createBooking_listingNotFound_throwsE4000() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(createRequest(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_4000);
    }

    @Test
    @DisplayName("刊登類型非 ROOM → E_3001")
    void createBooking_notRoomListing_throwsE3001() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        Listing productListing = Listing.builder()
                .listingType(Listing.ListingType.PRODUCT)
                .basePrice(BASE_PRICE)
                .status(Listing.ListingStatus.ACTIVE)
                .build();
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(productListing));

        assertThatThrownBy(() -> bookingService.createBooking(createRequest(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_3001);
    }

    @Test
    @DisplayName("房源未上架（非 ACTIVE）→ E_3002")
    void createBooking_roomNotActive_throwsE3002() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        Listing inactiveListing = Listing.builder()
                .listingType(Listing.ListingType.ROOM)
                .basePrice(BASE_PRICE)
                .status(Listing.ListingStatus.INACTIVE)
                .build();
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(inactiveListing));

        assertThatThrownBy(() -> bookingService.createBooking(createRequest(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_3002);
    }

    @Test
    @DisplayName("Room 資料不存在 → E_4000")
    void createBooking_roomDataNotFound_throwsE4000() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(activeRoomListing()));
        when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(createRequest(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_4000);
    }

    @Test
    @DisplayName("入住人數超過房源容納上限 → E_4005")
    void createBooking_guestCountExceedsCapacity_throwsE4005() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(activeRoomListing()));
        when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(Room.builder().maxGuests(1).build()));

        assertThatThrownBy(() -> bookingService.createBooking(createRequest(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_4005);
    }

    @Test
    @DisplayName("退房日早於入住日 → E_4003")
    void createBooking_checkoutBeforeCheckin_throwsE4003() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(activeRoomListing()));
        when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room()));
        BookingDto.CreateRequest invalidDates = BookingDto.CreateRequest.builder()
                .roomListingId(ROOM_LISTING_ID)
                .checkInDate(CHECK_OUT)
                .checkOutDate(CHECK_IN)
                .guestCount(2)
                .guestName("Alice")
                .build();

        assertThatThrownBy(() -> bookingService.createBooking(invalidDates, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_4003);
    }

    @Test
    @DisplayName("區間含未開放日（超出開放窗）→ E_3002")
    void createBooking_beyondOpenWindow_throwsE3002() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(activeRoomListing()));
        when(roomRepository.findByListingId(ROOM_LISTING_ID))
                .thenReturn(Optional.of(Room.builder().maxGuests(4).openUntilDate(LocalDate.now()).build()));

        assertThatThrownBy(() -> bookingService.createBooking(createRequest(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_3002);
    }

    // ========== 鎖定 / 並發控制 ==========

    @Test
    @DisplayName("無法取得日期鎖（並發請求）→ E_4001，且不呼叫 unlock（未曾持鎖）")
    void createBooking_lockAcquisitionFails_throwsE4001NoUnlock() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(activeRoomListing()));
        when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room()));
        when(roomCalendarService.lockDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT)).thenReturn(null);

        assertThatThrownBy(() -> bookingService.createBooking(createRequest(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_4001);

        verify(roomCalendarService, never()).unlockDateRange(any(), any(), any(), any());
    }

    @Test
    @DisplayName("取得鎖後二次確認發現日期已不可用 → E_4001，且 finally 仍釋放鎖")
    void createBooking_dateNoLongerAvailable_throwsE4001AndUnlocksInFinally() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        stubHappyPathUpToLock();
        when(roomCalendarService.isDateRangeAvailable(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT)).thenReturn(false);

        assertThatThrownBy(() -> bookingService.createBooking(createRequest(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_4001);

        verify(roomCalendarService).unlockDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, "lockValue");
    }

    @Test
    @DisplayName("使用者不存在 → E_1006，且 finally 仍釋放鎖")
    void createBooking_userNotFound_throwsE1006AndUnlocksInFinally() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        stubHappyPathUpToLock();
        when(roomCalendarService.isDateRangeAvailable(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT)).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(createRequest(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_1006);

        verify(roomCalendarService).unlockDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, "lockValue");
    }

    @Test
    @DisplayName("租戶不存在 → E_2000，且 finally 仍釋放鎖")
    void createBooking_tenantNotFound_throwsE2000AndUnlocksInFinally() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        stubHappyPathUpToLock();
        when(roomCalendarService.isDateRangeAvailable(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT)).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder().id(USER_ID).build()));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(createRequest(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.E_2000);

        verify(roomCalendarService).unlockDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, "lockValue");
    }
}
