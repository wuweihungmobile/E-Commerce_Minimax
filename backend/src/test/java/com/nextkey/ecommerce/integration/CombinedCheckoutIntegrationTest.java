package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.api.dto.CheckoutDto;
import com.nextkey.ecommerce.core.booking.BookingService;
import com.nextkey.ecommerce.core.cart.RedisCartService;
import com.nextkey.ecommerce.core.checkout.CombinedCheckoutService;
import com.nextkey.ecommerce.core.order.OrderService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.promo.PromoCode;
import com.nextkey.ecommerce.domain.model.promo.PromoCodeUsage;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.PromoCodeRepository;
import com.nextkey.ecommerce.domain.repository.PromoCodeUsageRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * 合併結帳整合測試（Sprint 126，DEF-048 擴大範圍；真實 PostgreSQL + Redis）。
 *
 * <p>為什麼要真實 DB：本測試要證明的不變量是「Order 與 Booking 雖各自獨立 Service，
 * {@link CombinedCheckoutService#checkoutMixedCart} 包在單一 {@code @Transactional} 內仍能
 * 讓兩者共用同一個資料庫交易」——這件事只有真實交易回滾才驗得到，mock 掉 Repository 只能看到
 * 「有沒有呼叫 save()」，看不到失敗時是否真的兩邊都沒留下。同理，「合併結帳用一張券只消費一次
 * 額度」與「兩側都取消才釋放額度」也是資料列的實際狀態，不是呼叫順序。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-CHECKOUT-MIXED: 合併結帳（Sprint 126，DEF-048 擴大範圍）")
class CombinedCheckoutIntegrationTest {

    @Autowired private CombinedCheckoutService combinedCheckoutService;
    @Autowired private OrderService orderService;
    @Autowired private BookingService bookingService;
    @Autowired private RedisCartService cartService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private PromoCodeRepository promoCodeRepository;
    @Autowired private PromoCodeUsageRepository promoCodeUsageRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockBean private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    /** PRODUCT 單價 100 x 2 = 小計 200；ROOM 每晚 1000 x 2 晚 = 2000；合併毛額 2200。 */
    private static final BigDecimal PRODUCT_UNIT_PRICE = new BigDecimal("100.00");
    private static final BigDecimal ROOM_NIGHTLY_PRICE = new BigDecimal("1000.00");
    private static final BigDecimal PRODUCT_SUBTOTAL = new BigDecimal("200.00");
    private static final BigDecimal ROOM_GROSS = new BigDecimal("2000.00");

    private Tenant tenant;
    private User buyer;
    private UUID tenantId;
    private UUID userId;
    private UUID productListingId;
    private UUID roomListingId;
    private LocalDate checkIn;
    private LocalDate checkOut;

    @BeforeEach
    void setUp() {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());

        long stamp = System.nanoTime();
        tenant = tenantRepository.save(Tenant.builder()
                .name("Mixed Checkout Tenant")
                .slug("mixed-checkout-" + stamp)
                .contactEmail("mixed-checkout-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        tenantId = tenant.getId();

        buyer = userRepository.save(User.builder()
                .email("mixed-checkout-buyer-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Mixed Checkout Buyer")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .tenantId(tenantId)
                .build());
        userId = buyer.getId();

        productListingId = listingRepository.save(Listing.builder()
                .tenant(tenant)
                .owner(buyer)
                .listingType(Listing.ListingType.PRODUCT)
                .title("Mixed Checkout Product")
                .basePrice(PRODUCT_UNIT_PRICE)
                .status(Listing.ListingStatus.ACTIVE)
                .build()).getId();

        roomListingId = listingRepository.save(Listing.builder()
                .tenant(tenant)
                .owner(buyer)
                .listingType(Listing.ListingType.ROOM)
                .title("Mixed Checkout Room")
                .basePrice(ROOM_NIGHTLY_PRICE)
                .currency("TWD")
                .status(Listing.ListingStatus.ACTIVE)
                .build()).getId();
        // 改用原生 SQL 直接插入 rooms 列，不透過 roomRepository.save(Room.builder().listing(...))：
        // Room.listing 是 @MapsId 關聯，前一個 listingRepository.save() 回傳的實體在各自獨立的
        // 交易（本測試類別未整體包在 @Transactional 內）結束後已變 detached，@MapsId 需要在同一個
        // persistence context 內解析關聯鍵，傳入 detached 實體會拋
        // 「detached entity passed to persist」（比照 M12OrderStockLedgerIntegrationTest 用原生 SQL
        // 種 product_inventory／product_skus 的同一類理由）。
        jdbcTemplate.update(
                "INSERT INTO rooms (listing_id, max_guests, room_count) VALUES (?, ?, ?)",
                roomListingId, 4, 1);

        checkIn = LocalDate.now().plusDays(30);
        checkOut = checkIn.plusDays(2);
    }

    /** 佈置一張同時有 PRODUCT（小計 200）與 ROOM（毛額 2000）項目的購物車。 */
    private void givenMixedCart() {
        TenantContext.setCurrentUser(userId);
        TenantContext.setCurrentTenant(tenantId);
        cartService.clearCart(userId, tenantId);
        cartService.addItem(userId, tenantId, CartDto.AddItemRequest.builder()
                .listingId(productListingId)
                .quantity(2)
                .build());
        cartService.addItem(userId, tenantId, CartDto.AddItemRequest.builder()
                .listingId(roomListingId)
                .quantity(1)
                .startDate(checkIn)
                .endDate(checkOut)
                .build());
    }

    private CheckoutDto.MixedCheckoutRequest checkoutRequest(final String promoCode) {
        return CheckoutDto.MixedCheckoutRequest.builder()
                .shippingAddress("台北市信義區測試路 1 號")
                .shippingRecipientName("測試收件人")
                .shippingPhone("0912345678")
                .guestCount(2)
                .guestName("測試住客")
                .promoCode(promoCode)
                .build();
    }

    /** 真實寫入一張 FIXED_AMOUNT 220 元的券（產品 200+房 2000=2200，未達毛額不會被二次封頂）。 */
    private PromoCode givenPromoCode() {
        return promoCodeRepository.save(PromoCode.builder()
                .tenant(tenant)
                .code("MIXED" + (System.nanoTime() % 100000))
                .discountType(PromoCode.DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("220.00"))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .maxUsageCount(5)
                .currentUsageCount(0)
                .maxUsagePerUser(1)
                .isActive(true)
                .build());
    }

    @Test
    @DisplayName("合併結帳成功：一次呼叫建立 Order+Booking，一張券只消費一次額度、寫入一筆同時關聯兩者的用券紀錄")
    void checkoutMixedCart_success_createsOrderAndBookingWithSinglePromoUsage() {
        givenMixedCart();
        PromoCode promo = givenPromoCode();

        CheckoutDto.MixedCheckoutResponse response =
                combinedCheckoutService.checkoutMixedCart(checkoutRequest(promo.getCode()));

        assertThat(response.getOrder()).isNotNull();
        assertThat(response.getBooking()).isNotNull();

        // 220 依小計權重分攤：PRODUCT 200/2200*220=20.00，ROOM 2000/2200*220=200.00
        assertThat(response.getOrder().getDiscountAmount()).isEqualByComparingTo("20.00");
        assertThat(response.getOrder().getTotalAmount())
                .isEqualByComparingTo(PRODUCT_SUBTOTAL.subtract(new BigDecimal("20.00")));
        assertThat(response.getBooking().getDiscountAmount()).isEqualByComparingTo("200.00");
        assertThat(response.getBooking().getTotalAmount())
                .isEqualByComparingTo(ROOM_GROSS.subtract(new BigDecimal("200.00")));
        assertThat(response.getTotalDiscountAmount()).isEqualByComparingTo("220.00");

        // 一次合併結帳只算一次額度（使用者拍板）
        PromoCode afterCheckout = promoCodeRepository.findById(promo.getId()).orElseThrow();
        assertThat(afterCheckout.getCurrentUsageCount()).isEqualTo(1);

        // 一筆用券紀錄同時關聯兩者（V77 CHECK 約束已放寬），不是兩筆各自獨立的紀錄
        List<PromoCodeUsage> usages = promoCodeUsageRepository.findByOrderIdAndStatus(
                response.getOrder().getId(), PromoCodeUsage.UsageStatus.ACTIVE);
        assertThat(usages).hasSize(1);
        assertThat(usages.get(0).getBookingId()).isEqualTo(response.getBooking().getId());
        assertThat(usages.get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("其中一邊建立失敗（房型日期已被佔用）→ 整個交易回滾，Order 與 Booking 都不落地，額度未被消費")
    void checkoutMixedCart_roomUnavailable_rollsBackBothOrderAndBookingAndPromoQuota() {
        givenMixedCart();
        PromoCode promo = givenPromoCode();
        // 訂單建立在 Booking 之前，本情境要證明「PRODUCT 側已成功 save() 之後，ROOM 側才失敗」
        // 仍能讓兩者一起回滾，不是只驗「兩者皆未嘗試建立」這種較弱的情況。
        // 直接插入 BOOKED 的 room_calendar 列（而非呼叫 blockDateRange）：後者只會更新「已存在」的
        // 日曆列，全新房型此區間尚無任何列，呼叫了也是無操作，isDateRangeAvailable 仍會判定可訂。
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            jdbcTemplate.update(
                    "INSERT INTO room_calendar (id, room_listing_id, calendar_date, status) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(), roomListingId, d, "BOOKED");
        }

        assertThatThrownBy(() -> combinedCheckoutService.checkoutMixedCart(checkoutRequest(promo.getCode())))
                .isInstanceOf(BusinessException.class);

        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE tenant_id = ?", Integer.class, tenantId);
        assertThat(orderCount).isZero();
        Integer bookingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE tenant_id = ?", Integer.class, tenantId);
        assertThat(bookingCount).isZero();

        PromoCode afterFailure = promoCodeRepository.findById(promo.getId()).orElseThrow();
        assertThat(afterFailure.getCurrentUsageCount()).isZero();
        assertThat(promoCodeUsageRepository.countByPromoCodeIdAndUserIdAndStatus(
                promo.getId(), userId, PromoCodeUsage.UsageStatus.ACTIVE)).isZero();
    }

    @Test
    @DisplayName("只取消 Order（保留 Booking）→ 額度不釋放（使用者拍板：兩側都取消才退還）")
    void cancelOnlyOrderSide_doesNotReleasePromoQuota() {
        givenMixedCart();
        PromoCode promo = givenPromoCode();
        CheckoutDto.MixedCheckoutResponse response =
                combinedCheckoutService.checkoutMixedCart(checkoutRequest(promo.getCode()));

        TenantContext.setCurrentUser(userId);
        orderService.cancelOrder(response.getOrder().getId(), "integration test: cancel product only");

        PromoCode afterOrderCancel = promoCodeRepository.findById(promo.getId()).orElseThrow();
        assertThat(afterOrderCancel.getCurrentUsageCount()).isEqualTo(1);
        List<PromoCodeUsage> usages = promoCodeUsageRepository.findByOrderIdAndStatus(
                response.getOrder().getId(), PromoCodeUsage.UsageStatus.ACTIVE);
        assertThat(usages).hasSize(1);
        assertThat(usages.get(0).getStatus()).isEqualTo(PromoCodeUsage.UsageStatus.ACTIVE);
        assertThat(usages.get(0).getOrderReleasedAt()).isNotNull();
        assertThat(usages.get(0).getBookingReleasedAt()).isNull();
    }

    @Test
    @DisplayName("兩側都取消 → 額度才真正釋放")
    void cancelBothSides_releasesPromoQuota() {
        givenMixedCart();
        PromoCode promo = givenPromoCode();
        CheckoutDto.MixedCheckoutResponse response =
                combinedCheckoutService.checkoutMixedCart(checkoutRequest(promo.getCode()));

        TenantContext.setCurrentUser(userId);
        orderService.cancelOrder(response.getOrder().getId(), "integration test: cancel order side");
        bookingService.cancelBooking(response.getBooking().getId(), "integration test: cancel booking side");

        PromoCode afterBothCancelled = promoCodeRepository.findById(promo.getId()).orElseThrow();
        assertThat(afterBothCancelled.getCurrentUsageCount()).isZero();
        List<PromoCodeUsage> usages = promoCodeUsageRepository.findByOrderIdAndStatus(
                response.getOrder().getId(), PromoCodeUsage.UsageStatus.ACTIVE);
        assertThat(usages).isEmpty();
    }
}
