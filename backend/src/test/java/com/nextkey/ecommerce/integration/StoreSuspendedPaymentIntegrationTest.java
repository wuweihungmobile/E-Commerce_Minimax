package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.api.dto.PaymentDto;
import com.nextkey.ecommerce.core.booking.BookingService;
import com.nextkey.ecommerce.core.cart.RedisCartService;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.order.OrderService;
import com.nextkey.ecommerce.core.payment.PaymentService;
import com.nextkey.ecommerce.core.payment.PaymentStateService;
import com.nextkey.ecommerce.core.payment.PaymentWebhookService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayFactory;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayRequestResponse;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * 停權店鋪的未付款訂單／訂房不能再付款（Sprint 242，使用者拍板；Sprint 239 的收尾）。
 *
 * <p>Sprint 239 只擋「建立」：店鋪停權前就已成立、尚未付款的單，停權後消費者仍可把款項付給停權的店鋪。本類別以真實 PostgreSQL＋Redis、
 * 真實服務（訂單走購物車結帳、訂房走 {@code BookingService}，所以訂單／訂房的租戶是真實結帳蓋上的店鋪）驗證：
 * ① 所有會收錢的入口（Mock 付款、Stripe Checkout、舊版 {@code /v2/payments}，訂單與訂房）都擋；② 擋下時什麼都沒發生，Stripe 完全沒被呼叫；
 * ③ 取消、付款狀態查詢照常；④ 停權前已建立的 Stripe session 在停權後付完仍入帳（錢已收）；⑤ 店鋪恢復營業後又能付款。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-STORE-SUSPENDED-PAY: 停權店鋪不能再收款（Sprint 242）")
class StoreSuspendedPaymentIntegrationTest {

    private static final UUID SYSTEM_TENANT_ID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
    private static final String STRIPE_TOGGLE = "STRIPE_PAYMENT_ENABLED";

    @Autowired private OrderService orderService;
    @Autowired private BookingService bookingService;
    @Autowired private PaymentService paymentService;
    @Autowired private PaymentStateService paymentStateService;
    @Autowired private PaymentWebhookService paymentWebhookService;
    @Autowired private RedisCartService cartService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockBean private FeatureToggleService featureToggleService;
    @MockBean private PaymentGatewayFactory paymentGatewayFactory;

    private Tenant store;
    private UUID consumerId;
    private UUID productId;
    private UUID roomId;
    private int nextNight;

    @BeforeEach
    void setUp() {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().when(featureToggleService.isFeatureEnabled("DYNAMIC_PRICING_ENABLED")).thenReturn(false);
        lenient().when(featureToggleService.isFeatureEnabled(STRIPE_TOGGLE)).thenReturn(false);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());
        lenient().when(paymentGatewayFactory.createCheckoutSession(eq("STRIPE"), any())).thenAnswer(invocation -> {
            String sessionId = "cs_test_" + UUID.randomUUID();
            return PaymentGatewayRequestResponse.CheckoutSessionResult.builder().sessionId(sessionId)
                    .sessionUrl("https://checkout.stripe.com/c/pay/" + sessionId).status("open").paymentStatus("unpaid").build();
        });

        long stamp = System.nanoTime();
        store = tenantRepository.save(Tenant.builder().name("停權付款測試店").slug("suspended-pay-" + stamp)
                .contactEmail("suspended-pay-" + stamp + "@tenant.com").contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE).build());
        User owner = userRepository.save(User.builder().email("suspended-pay-owner-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("店主").role(User.UserRole.STORE_OWNER).status("ACTIVE")
                .tenantId(store.getId()).build());
        // 沒有店鋪的真實消費者：租戶脈絡是系統租戶佔位值（與真實買家相同）
        consumerId = userRepository.save(User.builder().email("suspended-pay-buyer-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("消費者").role(User.UserRole.BUYER).status("ACTIVE").build()).getId();
        productId = listingRepository.save(Listing.builder().tenant(store).owner(owner)
                .listingType(Listing.ListingType.PRODUCT).title("停權付款商品").basePrice(new BigDecimal("100.00"))
                .currency("TWD").status(Listing.ListingStatus.ACTIVE).build()).getId();
        roomId = listingRepository.save(Listing.builder().tenant(store).owner(owner)
                .listingType(Listing.ListingType.ROOM).title("停權付款房間").basePrice(new BigDecimal("1500.00"))
                .currency("TWD").status(Listing.ListingStatus.ACTIVE).build()).getId();
        jdbcTemplate.update("INSERT INTO rooms (listing_id, max_guests, room_count, check_in_time, check_out_time, "
                + "created_at, updated_at) VALUES (?, 4, 1, '15:00'::time, '11:00'::time, NOW(), NOW())", roomId);
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS it_suspended_pay_checkout_key_unique ON payments "
                + "(idempotency_key) WHERE idempotency_key LIKE 'ORDER-CHECKOUT-%' OR idempotency_key LIKE 'BOOKING-CHECKOUT-%'");
        nextNight = 10;

        asConsumer();
        cartService.clearCart(consumerId, SYSTEM_TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── 訂單 ─────────────────────────────────────────────────

    @ParameterizedTest(name = "店鋪狀態 {0}")
    @EnumSource(value = Tenant.TenantStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
    @DisplayName("訂單：店鋪不是 ACTIVE（停權、終止、待審核、已駁回）→ Mock 付款回 E-2010，訂單仍是待付款、沒有任何付款紀錄")
    void order_mockPay_isRejectedForEveryNonActiveStatus(final Tenant.TenantStatus status) {
        UUID orderId = consumerOrders();
        setStoreStatus(status);

        assertThatThrownBy(() -> paymentStateService.mockPaymentSuccess(orderId)).satisfies(this::isStoreClosed);

        assertThat(orderStatus(orderId)).isEqualTo("CREATED");
        assertThat(orderPayments(orderId)).isEmpty();
    }

    @Test
    @DisplayName("訂單：店鋪營業時照常付款（對照：守門沒有擋掉正常付款）")
    void order_mockPay_worksWhileStoreIsOpen() {
        UUID orderId = consumerOrders();

        paymentStateService.mockPaymentSuccess(orderId);

        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(orderPayments(orderId)).containsExactly("SUCCESS");
    }

    @Test
    @DisplayName("訂單：停權後發起 Stripe Checkout 回 E-2010——Stripe 完全沒被呼叫、沒有付款紀錄")
    void order_stripeCheckout_neverReachesStripeForSuspendedStore() {
        UUID orderId = consumerOrders();
        stripeEnabled();
        suspend();

        assertThatThrownBy(() -> paymentStateService.initiateStripeCheckout(orderId)).satisfies(this::isStoreClosed);

        verify(paymentGatewayFactory, never()).createCheckoutSession(any(), any());
        assertThat(orderPayments(orderId)).isEmpty();
        assertThat(orderStatus(orderId)).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("訂單：舊版 POST /v2/payments 同樣被擋（否則只擋新端點，改打舊端點就繞過）")
    void order_legacyPaymentEndpoint_isAlsoGuarded() {
        UUID orderId = consumerOrders();
        suspend();

        assertThatThrownBy(() -> paymentService.processPayment(paymentRequest(orderId, null))).satisfies(this::isStoreClosed);

        assertThat(orderStatus(orderId)).isEqualTo("CREATED");
        assertThat(orderPayments(orderId)).isEmpty();
    }

    @Test
    @DisplayName("訂單：停權後消費者仍能取消（提示消費者取消的前提），而且付款狀態仍查得到：canPay 照訂單狀態、storeOpen=false")
    void order_suspended_consumerCanStillCancelAndSeeWhy() {
        UUID orderId = consumerOrders();
        assertThat(paymentStateService.getOrderPaymentState(orderId).getStoreOpen()).isTrue();
        suspend();

        var state = paymentStateService.getOrderPaymentState(orderId);
        assertThat(state.getStoreOpen()).as("前端據此顯示「暫停營業」並收起付款按鈕").isFalse();
        assertThat(state.getCanPay()).as("canPay 仍是「訂單狀態允許付款」，店鋪狀態由 storeOpen 單獨表達").isTrue();
        assertThat(state.getCanCancel()).isTrue();

        orderService.cancelOrder(orderId, "店鋪暫停營業，取消訂單");

        assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("訂單：店鋪恢復營業後又能付款（暫停不是永久）")
    void order_afterReactivation_canBePaid() {
        UUID orderId = consumerOrders();
        suspend();
        assertThatThrownBy(() -> paymentStateService.mockPaymentSuccess(orderId)).satisfies(this::isStoreClosed);

        setStoreStatus(Tenant.TenantStatus.ACTIVE);
        paymentStateService.mockPaymentSuccess(orderId);

        assertThat(orderStatus(orderId)).isEqualTo("PAID");
    }

    @Test
    @DisplayName("錯誤先後不變：停權店鋪已取消的訂單回 E-5011（不是 E-2010）——不可付款的原因先於店鋪停業")
    void order_cancelledOfSuspendedStore_reportsNotPayableFirst() {
        UUID orderId = consumerOrders();
        orderService.cancelOrder(orderId, "先取消");
        suspend();

        assertThatThrownBy(() -> paymentStateService.mockPaymentSuccess(orderId))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_5011));
    }

    @Test
    @DisplayName("刻意不擋：停權前已建立的 Stripe session，停權後付完仍入帳——錢已經在 Stripe 收了，擋掉就是收了錢卻不記帳")
    void order_stripeSessionCreatedBeforeSuspension_isStillRecordedWhenPaidAfter() {
        UUID orderId = consumerOrders();
        stripeEnabled();
        String sessionId = paymentStateService.initiateStripeCheckout(orderId).getSessionId();
        suspend();

        paymentWebhookService.handleEvent(checkoutCompletedEvent(sessionId));

        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(orderPayments(orderId)).containsExactly("SUCCESS");
    }

    // ── 訂房 ─────────────────────────────────────────────────

    @ParameterizedTest(name = "店鋪狀態 {0}")
    @EnumSource(value = Tenant.TenantStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
    @DisplayName("訂房：店鋪不是 ACTIVE → Mock 付款回 E-2010，訂房仍是待付款、沒有任何付款紀錄")
    void booking_mockPay_isRejectedForEveryNonActiveStatus(final Tenant.TenantStatus status) {
        UUID bookingId = consumerBooking();
        setStoreStatus(status);

        assertThatThrownBy(() -> paymentStateService.mockBookingPaymentSuccess(bookingId)).satisfies(this::isStoreClosed);

        assertThat(bookingStatus(bookingId)).isEqualTo("CREATED");
        assertThat(bookingPayments(bookingId)).isEmpty();
    }

    @Test
    @DisplayName("訂房：停權後發起 Stripe Checkout 回 E-2010——Stripe 完全沒被呼叫")
    void booking_stripeCheckout_neverReachesStripeForSuspendedStore() {
        UUID bookingId = consumerBooking();
        stripeEnabled();
        suspend();

        assertThatThrownBy(() -> paymentStateService.initiateStripeBookingCheckout(bookingId)).satisfies(this::isStoreClosed);

        verify(paymentGatewayFactory, never()).createCheckoutSession(any(), any());
        assertThat(bookingPayments(bookingId)).isEmpty();
    }

    @Test
    @DisplayName("訂房：舊版 POST /v2/payments 同樣被擋")
    void booking_legacyPaymentEndpoint_isAlsoGuarded() {
        UUID bookingId = consumerBooking();
        suspend();

        assertThatThrownBy(() -> paymentService.processPayment(paymentRequest(null, bookingId))).satisfies(this::isStoreClosed);

        assertThat(bookingStatus(bookingId)).isEqualTo("CREATED");
        assertThat(bookingPayments(bookingId)).isEmpty();
    }

    @Test
    @DisplayName("訂房：停權後消費者仍能取消、付款狀態 storeOpen=false；恢復營業後又能付款")
    void booking_suspended_canCancel_andPayableAgainAfterReactivation() {
        UUID bookingId = consumerBooking();
        assertThat(paymentStateService.getBookingPaymentState(bookingId).getStoreOpen()).isTrue();
        suspend();
        assertThat(paymentStateService.getBookingPaymentState(bookingId).getStoreOpen()).isFalse();

        setStoreStatus(Tenant.TenantStatus.ACTIVE);
        paymentStateService.mockBookingPaymentSuccess(bookingId);
        assertThat(bookingStatus(bookingId)).isEqualTo("PAID");

        UUID second = consumerBooking();
        suspend();
        bookingService.cancelBooking(second, "店鋪暫停營業，取消訂房");
        assertThat(bookingStatus(second)).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("刻意不擋：停權前已建立的訂房 Stripe session，停權後付完仍入帳")
    void booking_stripeSessionCreatedBeforeSuspension_isStillRecordedWhenPaidAfter() {
        UUID bookingId = consumerBooking();
        stripeEnabled();
        String sessionId = paymentStateService.initiateStripeBookingCheckout(bookingId).getSessionId();
        suspend();

        paymentWebhookService.handleEvent(checkoutCompletedEvent(sessionId));

        assertThat(bookingStatus(bookingId)).isEqualTo("PAID");
        assertThat(bookingPayments(bookingId)).containsExactly("SUCCESS");
    }

    // ── 輔助 ─────────────────────────────────────────────────

    private void isStoreClosed(final Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.E_2010);
    }

    private void asConsumer() {
        TenantContext.setCurrentUser(consumerId);
        TenantContext.setCurrentTenant(SYSTEM_TENANT_ID);
    }

    private void stripeEnabled() {
        lenient().when(featureToggleService.isFeatureEnabled(STRIPE_TOGGLE)).thenReturn(true);
    }

    private void suspend() {
        setStoreStatus(Tenant.TenantStatus.SUSPENDED);
    }

    private void setStoreStatus(final Tenant.TenantStatus status) {
        jdbcTemplate.update("UPDATE tenants SET status = ? WHERE id = ?", status.name(), store.getId());
    }

    /** 消費者走真實結帳（購物車→訂單），訂單蓋上商品所屬的店鋪。 */
    private UUID consumerOrders() {
        asConsumer();
        cartService.addItem(consumerId, SYSTEM_TENANT_ID,
                CartDto.AddItemRequest.builder().listingId(productId).quantity(1).build());
        OrderDto.CreateRequest request = new OrderDto.CreateRequest();
        request.setOrderType("PRODUCT");
        request.setShippingAddress("台北市信義區測試路 1 號");
        request.setShippingRecipientName("測試收件人");
        request.setShippingPhone("0912345678");
        UUID orderId = orderService.createOrderFromCart(request).getId();
        assertThat(jdbcTemplate.queryForObject("SELECT tenant_id FROM orders WHERE id = ?", UUID.class, orderId))
                .as("前提：訂單蓋的是商品所屬的店鋪（不是買家的系統租戶）").isEqualTo(store.getId());
        return orderId;
    }

    private UUID consumerBooking() {
        asConsumer();
        LocalDate checkIn = LocalDate.now().plusDays(nextNight);
        nextNight += 3;
        UUID bookingId = bookingService.createBooking(BookingDto.CreateRequest.builder().roomListingId(roomId)
                .checkInDate(checkIn).checkOutDate(checkIn.plusDays(2)).guestCount(2).guestName("測試住客")
                .guestPhone("0912345678").guestEmail("guest@example.com").build(), "idem-" + UUID.randomUUID()).getId();
        assertThat(jdbcTemplate.queryForObject("SELECT tenant_id FROM bookings WHERE id = ?", UUID.class, bookingId))
                .as("前提：訂房蓋的是房源所屬的店鋪").isEqualTo(store.getId());
        return bookingId;
    }

    private static PaymentDto.PaymentRequest paymentRequest(final UUID orderId, final UUID bookingId) {
        return PaymentDto.PaymentRequest.builder().orderId(orderId).bookingId(bookingId)
                .paymentMethod(PaymentDto.PaymentMethod.CREDIT_CARD).build();
    }

    private static String checkoutCompletedEvent(final String sessionId) {
        return "{\"id\":\"evt_" + UUID.randomUUID() + "\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":"
                + "{\"id\":\"" + sessionId + "\",\"payment_status\":\"paid\",\"payment_intent\":\"pi_" + UUID.randomUUID() + "\"}}}";
    }

    private String orderStatus(final UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private String bookingStatus(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingId);
    }

    private List<String> orderPayments(final UUID orderId) {
        return jdbcTemplate.queryForList("SELECT status FROM payments WHERE order_id = ? ORDER BY created_at",
                String.class, orderId);
    }

    private List<String> bookingPayments(final UUID bookingId) {
        return jdbcTemplate.queryForList("SELECT status FROM payments WHERE booking_id = ? ORDER BY created_at",
                String.class, bookingId);
    }
}
