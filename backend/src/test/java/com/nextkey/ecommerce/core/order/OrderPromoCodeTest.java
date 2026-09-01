package com.nextkey.ecommerce.core.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import org.springframework.security.core.context.SecurityContextHolder;

import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.core.cart.RedisCartService;
import com.nextkey.ecommerce.core.logistics.ShippingTemplateService;
import com.nextkey.ecommerce.core.product.ProductInventoryService;
import com.nextkey.ecommerce.core.promo.PromoService;
import com.nextkey.ecommerce.core.user.AddressService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.promo.PromoCode;
import com.nextkey.ecommerce.domain.model.promo.PromoCodeUsage;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.OrderStateLogRepository;
import com.nextkey.ecommerce.domain.repository.ProductRepository;
import com.nextkey.ecommerce.domain.repository.ProductSkuRepository;
import com.nextkey.ecommerce.domain.repository.PromoCodeRepository;
import com.nextkey.ecommerce.domain.repository.PromoCodeUsageRepository;
import com.nextkey.ecommerce.domain.repository.RoomRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * 訂單建立/取消時的優惠券機制專題測試（Sprint 100 US-001）。
 *
 * <p>背景：第八輪 PRD 全文掃描以「孤兒錯誤碼 → 零呼叫死碼 → 欄位零讀取」三個訊號連續命中，
 * 查出優惠券機制存在完整斷鏈。PRD §9.5.1 明訂「**M05 訂單建立時**，系統在校驗 totalAmount
 * 之後、寫入訂單之前」須依序驗證促銷碼（存在且 ACTIVE → 有效時間範圍 → 使用上限 → 套用折扣
 * → 折扣後金額不得為負），PRD §2630 另訂「優惠券：若已使用促銷碼，則退還」。修復前的實際行為：
 *
 * <ul>
 *   <li>{@code createOrderFromCart} 取購物車用 {@code getCart}（不含 promo 的版本），
 *       金額為 {@code Σsubtotal + shippingFee}，折扣從未扣除——買家在購物車看到折扣，下單被收全額。</li>
 *   <li>{@code PromoService.incrementUsageCount} 在 main 程式碼零呼叫者，
 *       {@code current_usage_count} 永遠是 0，{@code max_usage_count} 總量上限形同虛設。</li>
 *   <li>{@code max_usage_per_user} 自 V20 建表即存在，但全庫零讀取。</li>
 *   <li>促銷碼只在加入購物車時驗一次，Redis TTL 期間過期/停用/售罄都不會被攔。</li>
 * </ul>
 *
 * <p>本測試類別比照 {@code AuthServiceRefreshTokenTest} 的「單一主題獨立檔」慣例建立
 * （不併入已達 1070 行的 {@code OrderServiceTest}）。每個測試都先以紅燈證實缺口存在，再行修復。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderService 優惠券機制（Sprint 100）")
class OrderPromoCodeTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderStateLogRepository orderStateLogRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductSkuRepository productSkuRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private RedisCartService cartService;
    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private ShippingTemplateService shippingTemplateService;
    @Mock private AddressService addressService;
    @Mock private ProductInventoryService productInventoryService;
    @Mock private PromoService promoService;
    @Mock private PromoCodeRepository promoCodeRepository;
    @Mock private PromoCodeUsageRepository promoCodeUsageRepository;

    @InjectMocks
    private OrderService orderService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ORDER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID LISTING_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PROMO_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private static final String PROMO_CODE = "SAVE100";

    /** 商品小計 200 + 運費 60，套用 100 元折扣券 → 應付 160。 */
    private static final BigDecimal ITEMS_TOTAL = BigDecimal.valueOf(200);
    private static final BigDecimal SHIPPING_FEE = BigDecimal.valueOf(60);
    private static final BigDecimal DISCOUNT = BigDecimal.valueOf(100);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ========== 共用 fixture ==========

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

    /**
     * 佈置「小計 200 + 運費 60、已套用 100 元折扣券」的購物車情境。
     *
     * <p>購物車本身回傳品項與小計，已套用的券碼另由 {@code cartService.getAppliedPromoCode}
     * 提供——結帳流程刻意只取券碼、不採信購物車算好的折扣金額，訂單金額一律以結帳當下
     * 重新驗證的結果為準（PRD §9.5.1）。
     */
    private void givenCartWithPromo(final String appliedPromoCode) {
        TenantContext.setCurrentUser(USER_ID);
        TenantContext.setCurrentTenant(TENANT_ID);

        User user = User.builder().build();
        user.setId(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        Tenant tenant = Tenant.builder().build();
        tenant.setId(TENANT_ID);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        CartDto.CartItemResponse item = CartDto.CartItemResponse.builder()
                .cartItemKey("key-" + LISTING_ID)
                .listingId(LISTING_ID)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(100))
                .subtotal(ITEMS_TOTAL)
                .listingType("PRODUCT")
                .build();

        CartDto.CartResponse cart = CartDto.CartResponse.builder()
                .items(List.of(item))
                .totalAmount(ITEMS_TOTAL)
                .appliedPromoCode(appliedPromoCode)
                .discountAmount(appliedPromoCode == null ? null : DISCOUNT)
                .build();

        when(cartService.getCart(USER_ID, TENANT_ID)).thenReturn(cart);
        when(cartService.getAppliedPromoCode(USER_ID, TENANT_ID)).thenReturn(appliedPromoCode);

        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(Listing.builder()
                .id(LISTING_ID)
                .listingType(Listing.ListingType.PRODUCT)
                .status(Listing.ListingStatus.ACTIVE)
                .title("Test Listing")
                .basePrice(BigDecimal.valueOf(100))
                .build()));

        when(shippingTemplateService.calculateFeeForTenant(any(), any())).thenReturn(SHIPPING_FEE);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(ORDER_ID);
            return o;
        });
    }

    /** 佈置一張通過所有驗證、可折 100 元的有效券。 */
    private PromoCode givenValidPromo() {
        PromoCode promo = promoFixture();
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId(PROMO_CODE, TENANT_ID))
                .thenReturn(Optional.of(promo));
        when(promoService.computeDiscount(eq(promo), any(), any())).thenReturn(DISCOUNT);
        when(promoCodeUsageRepository.countByPromoCodeIdAndUserIdAndStatus(
                PROMO_ID, USER_ID, PromoCodeUsage.UsageStatus.ACTIVE)).thenReturn(0L);
        return promo;
    }

    private OrderDto.CreateRequest productRequest() {
        return OrderDto.CreateRequest.builder().orderType("PRODUCT").build();
    }

    @Nested
    @DisplayName("缺口 1-3、6：折扣落單、額度佔用、購物車清券")
    class CheckoutApplyTests {

        @Test
        @DisplayName("已套用優惠券 → 訂單總額扣除折扣（PRD §9.5.1 步驟 4）")
        void discountDeductedFromTotal() {
            givenCartWithPromo(PROMO_CODE);
            givenValidPromo();

            OrderDto.OrderResponse response = orderService.createOrderFromCart(productRequest());

            // 小計 200 - 折扣 100 + 運費 60 = 160
            // 修復前為 260：買家在購物車看到折扣，下單卻被收全額。
            assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(160));
            assertThat(response.getDiscountAmount()).isEqualByComparingTo(DISCOUNT);
            assertThat(response.getPromoCode()).isEqualTo(PROMO_CODE);
        }

        @Test
        @DisplayName("已套用優惠券 → 遞增總量使用次數（修復 incrementUsageCount 零呼叫者）")
        void incrementsUsageCount() {
            givenCartWithPromo(PROMO_CODE);
            PromoCode promo = givenValidPromo();

            orderService.createOrderFromCart(productRequest());

            verify(promoService).incrementUsageCount(promo);
        }

        @Test
        @DisplayName("已套用優惠券 → 寫入用券紀錄（支撐 max_usage_per_user）")
        void recordsUsageForUser() {
            givenCartWithPromo(PROMO_CODE);
            givenValidPromo();

            orderService.createOrderFromCart(productRequest());

            ArgumentCaptor<PromoCodeUsage> captor = ArgumentCaptor.forClass(PromoCodeUsage.class);
            verify(promoCodeUsageRepository).save(captor.capture());
            PromoCodeUsage saved = captor.getValue();
            assertThat(saved.getPromoCodeId()).isEqualTo(PROMO_ID);
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(saved.getStatus()).isEqualTo(PromoCodeUsage.UsageStatus.ACTIVE);
        }

        @Test
        @DisplayName("下單成功 → 清除購物車促銷碼，避免同張券被下一張訂單重複沿用")
        void clearsPromoFromCartAfterCheckout() {
            givenCartWithPromo(PROMO_CODE);
            givenValidPromo();

            orderService.createOrderFromCart(productRequest());

            verify(cartService).removePromoCode(USER_ID, TENANT_ID);
        }

        @Test
        @DisplayName("折扣大於應付金額 → 總額為 0 不得為負（PRD §9.5.1 步驟 5）")
        void discountNeverMakesTotalNegative() {
            givenCartWithPromo(PROMO_CODE);
            PromoCode promo = promoFixture();
            when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId(PROMO_CODE, TENANT_ID))
                    .thenReturn(Optional.of(promo));
            // 折扣 500 遠大於應付 260
            when(promoService.computeDiscount(eq(promo), any(), any())).thenReturn(BigDecimal.valueOf(500));
            when(promoCodeUsageRepository.countByPromoCodeIdAndUserIdAndStatus(
                    PROMO_ID, USER_ID, PromoCodeUsage.UsageStatus.ACTIVE)).thenReturn(0L);

            OrderDto.OrderResponse response = orderService.createOrderFromCart(productRequest());

            assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("購物車未套用優惠券 → 金額與流程完全不變（回歸保護）")
        void noPromoLeavesOrderUnchanged() {
            givenCartWithPromo(null);

            OrderDto.OrderResponse response = orderService.createOrderFromCart(productRequest());

            assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(260));
            assertThat(response.getPromoCode()).isNull();
            assertThat(response.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(promoService, never()).incrementUsageCount(any());
            verify(promoCodeUsageRepository, never()).save(any());
            verify(cartService, never()).removePromoCode(any(), any());
        }
    }

    @Nested
    @DisplayName("DEF-045（Sprint 101）：FREE_SHIPPING 券折抵運費")
    class FreeShippingCheckoutTests {

        @Test
        @DisplayName("結帳計算折扣時必須把運費一併傳給 PromoService（缺了它 FREE_SHIPPING 就會靜默回 0）")
        void passesShippingFeeToDiscountCalculation() {
            givenCartWithPromo(PROMO_CODE);
            PromoCode promo = givenValidPromo();

            orderService.createOrderFromCart(productRequest());

            // 這正是 DEF-045 的失效點：折扣基數若只傳商品小計，免運券永遠算不出金額
            verify(promoService).computeDiscount(eq(promo), eq(ITEMS_TOTAL), eq(SHIPPING_FEE));
        }

        @Test
        @DisplayName("免運券 → 運費被折抵，實收回到商品小計")
        void freeShippingCouponWaivesShippingFee() {
            givenCartWithPromo(PROMO_CODE);
            PromoCode promo = freeShippingPromoFixture();
            when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId(PROMO_CODE, TENANT_ID))
                    .thenReturn(Optional.of(promo));
            // 真實 PromoService 對 FREE_SHIPPING 現在回傳的就是運費全額
            when(promoService.computeDiscount(eq(promo), any(), any())).thenReturn(SHIPPING_FEE);
            when(promoCodeUsageRepository.countByPromoCodeIdAndUserIdAndStatus(
                    PROMO_ID, USER_ID, PromoCodeUsage.UsageStatus.ACTIVE)).thenReturn(0L);

            OrderDto.OrderResponse response = orderService.createOrderFromCart(productRequest());

            // 小計 200 + 運費 60 - 折抵 60 = 200（修復前為 260，買家選了免運券仍被收滿運費）
            assertThat(response.getTotalAmount()).isEqualByComparingTo(ITEMS_TOTAL);
            assertThat(response.getDiscountAmount()).isEqualByComparingTo(SHIPPING_FEE);
            assertThat(response.getShippingFee()).isEqualByComparingTo(SHIPPING_FEE);
        }

        private PromoCode freeShippingPromoFixture() {
            return PromoCode.builder()
                    .id(PROMO_ID)
                    .code(PROMO_CODE)
                    .discountType(PromoCode.DiscountType.FREE_SHIPPING)
                    .discountValue(BigDecimal.ZERO)
                    .startDate(LocalDateTime.now().minusDays(1))
                    .endDate(LocalDateTime.now().plusDays(1))
                    .maxUsageCount(100)
                    .currentUsageCount(0)
                    .isActive(true)
                    .build();
        }
    }

    @Nested
    @DisplayName("缺口 4：下單時重新驗證促銷碼（PRD §9.5.1 步驟 1-3）")
    class CheckoutRevalidationTests {

        @Test
        @DisplayName("券在購物車存活期間過期 → 拒絕下單（E-5008），不靜默改收原價")
        void expiredPromoRejectedAtCheckout() {
            givenCartWithPromo(PROMO_CODE);
            PromoCode expired = promoFixture();
            expired.setStartDate(LocalDateTime.now().minusDays(10));
            expired.setEndDate(LocalDateTime.now().minusDays(1));
            when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId(PROMO_CODE, TENANT_ID))
                    .thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> orderService.createOrderFromCart(productRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.E_5008);

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("券已停用 → 拒絕下單（E-5007）")
        void inactivePromoRejectedAtCheckout() {
            givenCartWithPromo(PROMO_CODE);
            PromoCode inactive = promoFixture();
            inactive.setIsActive(false);
            when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId(PROMO_CODE, TENANT_ID))
                    .thenReturn(Optional.of(inactive));

            assertThatThrownBy(() -> orderService.createOrderFromCart(productRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.E_5007);
        }

        @Test
        @DisplayName("券總量已用罄 → 拒絕下單（E-5009，修復 max_usage_count 形同虛設）")
        void totalUsageLimitReachedRejected() {
            givenCartWithPromo(PROMO_CODE);
            PromoCode soldOut = promoFixture();
            soldOut.setMaxUsageCount(100);
            soldOut.setCurrentUsageCount(100);
            when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId(PROMO_CODE, TENANT_ID))
                    .thenReturn(Optional.of(soldOut));

            assertThatThrownBy(() -> orderService.createOrderFromCart(productRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.E_5009);
        }

        @Test
        @DisplayName("同一買家超過每人限用次數 → 拒絕下單（E-5009，修復 max_usage_per_user 零讀取）")
        void perUserLimitReachedRejected() {
            givenCartWithPromo(PROMO_CODE);
            PromoCode promo = promoFixture();
            promo.setMaxUsagePerUser(1);
            when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId(PROMO_CODE, TENANT_ID))
                    .thenReturn(Optional.of(promo));
            // 該買家已用過 1 次
            when(promoCodeUsageRepository.countByPromoCodeIdAndUserIdAndStatus(
                    PROMO_ID, USER_ID, PromoCodeUsage.UsageStatus.ACTIVE)).thenReturn(1L);

            assertThatThrownBy(() -> orderService.createOrderFromCart(productRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.E_5009);

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("券在購物車存活期間被刪除 → 拒絕下單（E-5007）")
        void deletedPromoRejectedAtCheckout() {
            givenCartWithPromo(PROMO_CODE);
            when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId(PROMO_CODE, TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrderFromCart(productRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.E_5007);
        }
    }

    @Nested
    @DisplayName("缺口 5：取消訂單退還優惠券額度（PRD §2630）")
    class CancellationRefundTests {

        private Order orderWithPromo(final Order.OrderStatus status) {
            Order order = Order.builder()
                    .userId(USER_ID)
                    .tenantId(TENANT_ID)
                    .orderType(Listing.ListingType.PRODUCT)
                    .status(status)
                    .totalAmount(BigDecimal.valueOf(160))
                    .promoCode(PROMO_CODE)
                    .discountAmount(DISCOUNT)
                    .currency("TWD")
                    .items(new java.util.ArrayList<>())
                    .build();
            order.setId(ORDER_ID);
            return order;
        }

        @Test
        @DisplayName("取消用券訂單 → 用券紀錄轉 REVOKED 且總量次數回補")
        void cancelRefundsPromoUsage() {
            TenantContext.setCurrentUser(USER_ID);
            Order order = orderWithPromo(Order.OrderStatus.CREATED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            PromoCodeUsage usage = PromoCodeUsage.builder()
                    .id(UUID.randomUUID())
                    .promoCodeId(PROMO_ID)
                    .userId(USER_ID)
                    .orderId(ORDER_ID)
                    .status(PromoCodeUsage.UsageStatus.ACTIVE)
                    .build();
            when(promoCodeUsageRepository.findByOrderIdAndStatus(
                    ORDER_ID, PromoCodeUsage.UsageStatus.ACTIVE)).thenReturn(List.of(usage));

            PromoCode promo = promoFixture();
            promo.setCurrentUsageCount(5);
            when(promoCodeRepository.findById(PROMO_ID)).thenReturn(Optional.of(promo));

            orderService.cancelOrder(ORDER_ID, "buyer changed mind");

            assertThat(usage.getStatus()).isEqualTo(PromoCodeUsage.UsageStatus.REVOKED);
            assertThat(usage.getRevokedAt()).isNotNull();
            assertThat(promo.getCurrentUsageCount()).isEqualTo(4);
            verify(promoCodeUsageRepository).save(usage);
        }

        @Test
        @DisplayName("取消未用券訂單 → 完全不碰優惠券資料（回歸保護）")
        void cancelWithoutPromoTouchesNothing() {
            TenantContext.setCurrentUser(USER_ID);
            Order order = orderWithPromo(Order.OrderStatus.CREATED);
            order.setPromoCode(null);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            orderService.cancelOrder(ORDER_ID, "buyer changed mind");

            verify(promoCodeUsageRepository, never()).findByOrderIdAndStatus(any(), any());
            verify(promoCodeUsageRepository, never()).save(any());
        }

        @Test
        @DisplayName("總量次數已為 0 時取消 → 不得回補成負數")
        void refundNeverGoesNegative() {
            TenantContext.setCurrentUser(USER_ID);
            Order order = orderWithPromo(Order.OrderStatus.CREATED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            PromoCodeUsage usage = PromoCodeUsage.builder()
                    .id(UUID.randomUUID())
                    .promoCodeId(PROMO_ID)
                    .userId(USER_ID)
                    .orderId(ORDER_ID)
                    .status(PromoCodeUsage.UsageStatus.ACTIVE)
                    .build();
            when(promoCodeUsageRepository.findByOrderIdAndStatus(
                    ORDER_ID, PromoCodeUsage.UsageStatus.ACTIVE)).thenReturn(List.of(usage));

            PromoCode promo = promoFixture();
            promo.setCurrentUsageCount(0);
            when(promoCodeRepository.findById(PROMO_ID)).thenReturn(Optional.of(promo));

            orderService.cancelOrder(ORDER_ID, "edge case");

            assertThat(promo.getCurrentUsageCount()).isZero();
        }
    }
}
