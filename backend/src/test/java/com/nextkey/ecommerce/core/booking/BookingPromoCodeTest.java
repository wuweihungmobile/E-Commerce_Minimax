package com.nextkey.ecommerce.core.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import com.nextkey.ecommerce.domain.model.promo.PromoCode;
import com.nextkey.ecommerce.domain.model.promo.PromoCodeUsage;
import com.nextkey.ecommerce.domain.model.room.Room;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.PromoCodeRepository;
import com.nextkey.ecommerce.domain.repository.PromoCodeUsageRepository;
import com.nextkey.ecommerce.domain.repository.RoomCalendarRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * BookingService 訂房結帳優惠券機制專題測試（Sprint 124，DEF-047／PRD US-010）。
 *
 * <p>背景：PRD US-010「作為預訂買家，我希望在結帳時套用優惠碼」。DEF-047 原記錄誤指
 * {@code OrderService.createRoomOrder} 為修復目標，但查證後發現該路徑從未被任何前端頁面呼叫
 * ——真正的訂房結帳（{@code /checkout} 頁 → {@code POST /v2/bookings}）走的是完全獨立的
 * {@code BookingController}／{@code BookingService}／{@code Booking} 實體，且該實體從未有
 * 促銷碼相關欄位（見 V76 migration）。本測試比照 {@code OrderPromoCodeTest}（Sprint 100）
 * 同一套結構，驗證訂房結帳的促銷碼驗證／折扣套用／額度佔用／取消退還，以及訂房特有的
 * 「異動日期後折扣是否被靜默丟棄」情境（Order 沒有日期異動功能，這是 Booking 特有的風險）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BookingService 訂房結帳優惠券機制（Sprint 124，DEF-047）")
class BookingPromoCodeTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private RoomCalendarRepository roomCalendarRepository;
    @Mock private RoomCalendarService roomCalendarService;
    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private PricingService pricingService;
    @Mock private FeatureToggleService featureToggleService;
    @Mock private PromoService promoService;
    @Mock private PromoCodeRepository promoCodeRepository;
    @Mock private PromoCodeUsageRepository promoCodeUsageRepository;

    @InjectMocks
    private BookingService bookingService;

    private static final UUID ROOM_LISTING_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID BOOKING_ID = UUID.randomUUID();
    private static final UUID PROMO_ID = UUID.randomUUID();
    private static final String PROMO_CODE = "ROOM500";

    private static final BigDecimal BASE_PRICE = BigDecimal.valueOf(1000);
    private static final LocalDate CHECK_IN = LocalDate.now().plusDays(1);
    private static final LocalDate CHECK_OUT = LocalDate.now().plusDays(3); // 2 晚
    private static final BigDecimal GROSS_AMOUNT = BigDecimal.valueOf(2000); // 1000 * 2 晚
    private static final BigDecimal DISCOUNT = BigDecimal.valueOf(500);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ========== 共用 fixture ==========

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

    private PromoCode promoFixture() {
        return PromoCode.builder()
                .id(PROMO_ID)
                .code(PROMO_CODE)
                .discountType(PromoCode.DiscountType.FIXED_AMOUNT)
                .discountValue(DISCOUNT)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .maxUsageCount(100)
                .currentUsageCount(0)
                .maxUsagePerUser(1)
                .isActive(true)
                .build();
    }

    private BookingDto.CreateRequest createRequest(final String promoCode) {
        return BookingDto.CreateRequest.builder()
                .roomListingId(ROOM_LISTING_ID)
                .checkInDate(CHECK_IN)
                .checkOutDate(CHECK_OUT)
                .guestCount(2)
                .guestName("Alice")
                .promoCode(promoCode)
                .build();
    }

    /** 通過房源/人數/日期/開放窗檢查、取得鎖、可用性確認、user/tenant 查詢的共用 stub。 */
    private void stubHappyPath() {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);
        when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(activeRoomListing()));
        when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room()));
        when(roomCalendarService.lockDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT)).thenReturn("lockValue");
        when(featureToggleService.isFeatureEnabled("DYNAMIC_PRICING_ENABLED")).thenReturn(false);
        when(roomCalendarService.isDateRangeAvailable(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT)).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder().id(USER_ID).build()));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(Tenant.builder().id(TENANT_ID).build()));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(BOOKING_ID);
            return b;
        });
        // Sprint 126（DEF-048 擴大範圍）：resolveValidPromoForCheckout／computeCappedDiscount
        // 從 BookingService 私有方法移至 PromoService 共用，本檔案改為 @Mock PromoService，
        // 未 stub 的 mock 對 BigDecimal 回傳型別預設是 null 而非 BigDecimal.ZERO，
        // 「未套用促銷碼」情境需要這個預設值才不會在金額運算中 NPE；個別測試若套用促銷碼，
        // 會在其自己的 stub 中覆蓋此預設值（Mockito 後定義的 stub 優先）。
        when(promoService.computeCappedDiscount(any(), any(), any())).thenReturn(BigDecimal.ZERO);
    }

    /** 佈置一張通過所有驗證、可折 500 元的有效券。 */
    private PromoCode givenValidPromo() {
        PromoCode promo = promoFixture();
        when(promoService.resolveValidPromoForCheckout(PROMO_CODE, TENANT_ID, USER_ID)).thenReturn(promo);
        when(promoService.computeCappedDiscount(eq(promo), any(), any())).thenReturn(DISCOUNT);
        when(promoService.tryConsumeUsageQuota(promo)).thenReturn(true);
        return promo;
    }

    @Nested
    @DisplayName("結帳套用：折扣落單、額度佔用")
    class CheckoutApplyTests {

        @Test
        @DisplayName("已套用優惠券 → 訂房總額扣除折扣")
        void discountDeductedFromTotal() {
            stubHappyPath();
            givenValidPromo();

            BookingDto.BookingResponse response = bookingService.createBooking(createRequest(PROMO_CODE), "idem");

            // 2000 - 500 = 1500（修復前訂房完全沒有促銷碼路徑，一律收滿額）
            assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500));
            assertThat(response.getDiscountAmount()).isEqualByComparingTo(DISCOUNT);
            assertThat(response.getPromoCode()).isEqualTo(PROMO_CODE);
        }

        @Test
        @DisplayName("已套用優惠券 → 原子佔用一次總量額度，並寫入用券紀錄（bookingId 非 orderId）")
        void consumesQuotaAndRecordsBookingUsage() {
            stubHappyPath();
            PromoCode promo = givenValidPromo();

            bookingService.createBooking(createRequest(PROMO_CODE), "idem");

            verify(promoService).tryConsumeUsageQuota(promo);
            ArgumentCaptor<PromoCodeUsage> captor = ArgumentCaptor.forClass(PromoCodeUsage.class);
            verify(promoCodeUsageRepository).save(captor.capture());
            PromoCodeUsage saved = captor.getValue();
            assertThat(saved.getPromoCodeId()).isEqualTo(PROMO_ID);
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getBookingId()).isEqualTo(BOOKING_ID);
            assertThat(saved.getOrderId()).isNull();
            assertThat(saved.getStatus()).isEqualTo(PromoCodeUsage.UsageStatus.ACTIVE);
        }

        @Test
        @DisplayName("折扣達應付金額上限 → 總額為 0 不得為負（封頂規則本身見 PromoServiceTest）")
        void discountNeverMakesTotalNegative() {
            stubHappyPath();
            PromoCode promo = promoFixture();
            when(promoService.resolveValidPromoForCheckout(PROMO_CODE, TENANT_ID, USER_ID)).thenReturn(promo);
            // Sprint 126 起「折扣不得超過毛額」的封頂邏輯下沉到 PromoService.computeCappedDiscount
            // 內部；此處直接餵入已封頂的值（訂房毛額 = 2000），驗證 BookingService 正確採信該值
            // 算出總額 0，不再自行二次封頂。
            when(promoService.computeCappedDiscount(eq(promo), any(), any())).thenReturn(GROSS_AMOUNT);
            when(promoService.tryConsumeUsageQuota(promo)).thenReturn(true);

            BookingDto.BookingResponse response = bookingService.createBooking(createRequest(PROMO_CODE), "idem");

            assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("未提供促銷碼 → 金額與流程完全不變（回歸保護）")
        void noPromoLeavesBookingUnchanged() {
            stubHappyPath();

            BookingDto.BookingResponse response = bookingService.createBooking(createRequest(null), "idem");

            assertThat(response.getTotalAmount()).isEqualByComparingTo(GROSS_AMOUNT);
            assertThat(response.getPromoCode()).isNull();
            assertThat(response.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(promoService, never()).tryConsumeUsageQuota(any());
            verify(promoCodeUsageRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("結帳驗證：無效／過期／額度用盡的促銷碼一律拒絕，不靜默改收原價")
    class CheckoutValidationTests {

        // Sprint 126（DEF-048 擴大範圍）：實際驗證規則（過期/停用/額度用罄/每人限用/不存在）
        // 已下沉到 PromoService，並在 PromoServiceTest 對真實邏輯逐一驗證。本類別改為驗證
        // BookingService 這一層「正確傳遞 PromoService 丟出的每一種拒絕原因、且拒絕時不建立
        // 預訂」——delegation-level 回歸保護，非重複驗證規則本身。

        @Test
        @DisplayName("促銷碼不存在 → E-5007，且不建立預訂")
        void unknownPromoRejected() {
            stubHappyPath();
            when(promoService.resolveValidPromoForCheckout(PROMO_CODE, TENANT_ID, USER_ID))
                    .thenThrow(new BusinessException(ErrorCode.E_5007, "no longer available"));

            assertThatThrownBy(() -> bookingService.createBooking(createRequest(PROMO_CODE), "idem"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.E_5007);

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("促銷碼已過期 → E-5008")
        void expiredPromoRejected() {
            stubHappyPath();
            when(promoService.resolveValidPromoForCheckout(PROMO_CODE, TENANT_ID, USER_ID))
                    .thenThrow(new BusinessException(ErrorCode.E_5008, "expired"));

            assertThatThrownBy(() -> bookingService.createBooking(createRequest(PROMO_CODE), "idem"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.E_5008);
        }

        @Test
        @DisplayName("促銷碼總量已用罄 → E-5009")
        void usageLimitReachedRejected() {
            stubHappyPath();
            when(promoService.resolveValidPromoForCheckout(PROMO_CODE, TENANT_ID, USER_ID))
                    .thenThrow(new BusinessException(ErrorCode.E_5009, "usage limit reached"));

            assertThatThrownBy(() -> bookingService.createBooking(createRequest(PROMO_CODE), "idem"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.E_5009);
        }

        @Test
        @DisplayName("同一買家超過每人限用次數 → E-5009")
        void perUserLimitReachedRejected() {
            stubHappyPath();
            when(promoService.resolveValidPromoForCheckout(PROMO_CODE, TENANT_ID, USER_ID))
                    .thenThrow(new BusinessException(ErrorCode.E_5009, "per-user limit reached"));

            assertThatThrownBy(() -> bookingService.createBooking(createRequest(PROMO_CODE), "idem"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.E_5009);

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("前置檢查通過但原子佔用失敗（併發搶完額度）→ E-5009，且交易回滾（不留下用券紀錄）")
        void atomicConsumeFailureRejects() {
            stubHappyPath();
            PromoCode promo = promoFixture();
            when(promoService.resolveValidPromoForCheckout(PROMO_CODE, TENANT_ID, USER_ID)).thenReturn(promo);
            when(promoService.computeCappedDiscount(eq(promo), any(), any())).thenReturn(DISCOUNT);
            when(promoService.tryConsumeUsageQuota(promo)).thenReturn(false);

            assertThatThrownBy(() -> bookingService.createBooking(createRequest(PROMO_CODE), "idem"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.E_5009);

            verify(promoCodeUsageRepository, never()).save(any());
            // 鎖仍須在 finally 中釋放，即使促銷碼佔用失敗（承既有並發控制不變量）
            verify(roomCalendarService).unlockDateRange(ROOM_LISTING_ID, CHECK_IN, CHECK_OUT, "lockValue");
        }
    }

    @Nested
    @DisplayName("取消預訂退還優惠券額度（PRD §2630 同一原則）")
    class CancellationRefundTests {

        private Booking bookingWithPromo() {
            Booking booking = Booking.builder()
                    .id(BOOKING_ID)
                    .userId(USER_ID)
                    .tenantId(TENANT_ID)
                    .roomListingId(ROOM_LISTING_ID)
                    .checkInDate(CHECK_IN)
                    .checkOutDate(CHECK_OUT)
                    .guestCount(2)
                    .status(Booking.BookingStatus.CREATED)
                    .totalAmount(BigDecimal.valueOf(1500))
                    .promoCode(PROMO_CODE)
                    .discountAmount(DISCOUNT)
                    .build();
            return booking;
        }

        @Test
        @DisplayName("取消用券預訂 → 交給 PromoService 評估該側是否可釋放額度（PRD §2630）")
        void cancelRefundsPromoUsage() {
            TenantContext.setCurrentUser(USER_ID);
            Booking booking = bookingWithPromo();
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

            PromoCodeUsage usage = PromoCodeUsage.builder()
                    .id(UUID.randomUUID())
                    .promoCodeId(PROMO_ID)
                    .userId(USER_ID)
                    .bookingId(BOOKING_ID)
                    .status(PromoCodeUsage.UsageStatus.ACTIVE)
                    .build();
            when(promoCodeUsageRepository.findByBookingIdAndStatus(
                    BOOKING_ID, PromoCodeUsage.UsageStatus.ACTIVE)).thenReturn(List.of(usage));

            bookingService.cancelBooking(BOOKING_ID, "buyer changed mind");

            // Sprint 126（DEF-048 擴大範圍）：REVOKED 轉態／額度回補的實際規則下沉到
            // PromoService.releaseBookingSide（單一類型立即釋放、合併結帳需雙側都取消才釋放，
            // 見 PromoServiceTest 對此規則的直接驗證）。BookingService 這一層只負責「找出該預訂的
            // ACTIVE 用券紀錄、逐筆交給 PromoService 評估」，故此處驗證改為確認有委派。
            verify(promoService).releaseBookingSide(usage);
        }

        @Test
        @DisplayName("取消未用券預訂 → 完全不碰優惠券資料（回歸保護）")
        void cancelWithoutPromoTouchesNothing() {
            TenantContext.setCurrentUser(USER_ID);
            Booking booking = bookingWithPromo();
            booking.setPromoCode(null);
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

            bookingService.cancelBooking(BOOKING_ID, "buyer changed mind");

            verify(promoCodeUsageRepository, never()).findByBookingIdAndStatus(any(), any());
            verify(promoCodeUsageRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("異動日期（Booking 特有）：折扣不得因重算總額而被靜默丟棄")
    class DateChangeRecalculationTests {

        @Test
        @DisplayName("已用券預訂異動日期 → 依新總額對同一張券重算折扣，非直接歸零")
        void dateChangePreservesDiscount() {
            TenantContext.setCurrentUser(USER_ID);
            LocalDate newCheckIn = CHECK_IN.plusDays(5);
            LocalDate newCheckOut = newCheckIn.plusDays(3); // 改為 3 晚 → 新總額 3000

            Booking booking = Booking.builder()
                    .id(BOOKING_ID)
                    .userId(USER_ID)
                    .tenantId(TENANT_ID)
                    .roomListingId(ROOM_LISTING_ID)
                    .checkInDate(CHECK_IN)
                    .checkOutDate(CHECK_OUT)
                    .guestCount(2)
                    .status(Booking.BookingStatus.CREATED)
                    .totalAmount(BigDecimal.valueOf(1500))
                    .promoCode(PROMO_CODE)
                    .discountAmount(DISCOUNT)
                    .build();
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
            when(roomRepository.findByListingId(ROOM_LISTING_ID)).thenReturn(Optional.of(room()));
            when(roomCalendarService.lockDateRange(ROOM_LISTING_ID, newCheckIn, newCheckOut)).thenReturn("lockValue");
            when(roomCalendarService.isDateRangeAvailable(ROOM_LISTING_ID, newCheckIn, newCheckOut)).thenReturn(true);
            when(listingRepository.findById(ROOM_LISTING_ID)).thenReturn(Optional.of(activeRoomListing()));
            when(featureToggleService.isFeatureEnabled("DYNAMIC_PRICING_ENABLED")).thenReturn(false);
            PromoCode promo = promoFixture();
            when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId(PROMO_CODE, TENANT_ID))
                    .thenReturn(Optional.of(promo));
            // recalculateAndBookDateRange 不重新驗證促銷碼／不重佔額度，只依已記錄的券別對新總額
            // 重算折扣（見該方法註解）——此路徑仍直接查 promoCodeRepository，非本 Sprint 126 動的
            // resolveValidPromoForCheckout；封頂邏輯已下沉為 computeCappedDiscount。
            when(promoService.computeCappedDiscount(eq(promo), eq(BigDecimal.valueOf(3000)), any()))
                    .thenReturn(DISCOUNT);

            BookingDto.UpdateRequest request = BookingDto.UpdateRequest.builder()
                    .checkInDate(newCheckIn)
                    .checkOutDate(newCheckOut)
                    .build();
            BookingDto.BookingResponse response = bookingService.updateBooking(BOOKING_ID, request);

            // 新總額 3000 - 折扣 500 = 2500（修復前會直接把 totalAmount 設回 3000，折扣憑空消失）
            assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(2500));
        }
    }
}
