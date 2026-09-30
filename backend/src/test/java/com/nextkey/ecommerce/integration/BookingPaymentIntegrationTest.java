package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.api.dto.payment.CheckoutSessionResponse;
import com.nextkey.ecommerce.api.dto.payment.OrderPaymentStateDto;
import com.nextkey.ecommerce.core.booking.BookingService;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.payment.PaymentStateService;
import com.nextkey.ecommerce.core.payment.PaymentWebhookService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.RolePermissionMapping;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayFactory;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayRequestResponse;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * 訂房付款（Sprint 221，DEF-303 (1)）：前端訂房流程原本完全沒有付款步驟，訂房唯一的付款入口是舊版
 * {@code POST /v2/payments}，而該端點的 {@code orderId} 是必填，所以訂房其實根本付不了款。
 *
 * <p>真實 PostgreSQL＋真實 Redis（日曆鎖）。Stripe 閘道以 {@code @MockBean} 取代（不打外部網路）；
 * {@code FeatureToggleService} 同樣以 {@code @MockBean} 控制 Stripe 開關。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-BOOKING-PAY: 訂房付款（Sprint 221）")
class BookingPaymentIntegrationTest {

    private static final int RACE_THREADS = 8;
    private static final String STRIPE_TOGGLE = "STRIPE_PAYMENT_ENABLED";

    @Autowired private BookingService bookingService;
    @Autowired private PaymentStateService paymentStateService;
    @Autowired private PaymentWebhookService paymentWebhookService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;

    @MockBean private FeatureToggleService featureToggleService;
    @MockBean private PaymentGatewayFactory paymentGatewayFactory;

    private Tenant tenant;
    private User buyer;
    private User otherBuyer;
    private UUID roomListingId;
    private int nextNight;

    @BeforeEach
    void setUp() {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().when(featureToggleService.isFeatureEnabled("DYNAMIC_PRICING_ENABLED")).thenReturn(false);
        lenient().when(featureToggleService.isFeatureEnabled(STRIPE_TOGGLE)).thenReturn(false);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());
        long stamp = System.nanoTime();
        tenant = tenantRepository.save(Tenant.builder().name("Booking Pay Tenant").slug("booking-pay-" + stamp)
                .contactEmail("booking-pay-" + stamp + "@tenant.com").contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE).build());
        User host = userRepository.save(User.builder().email("booking-pay-host-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("Host").role(User.UserRole.STORE_OWNER).status("ACTIVE")
                .tenantId(tenant.getId()).build());
        buyer = userRepository.save(User.builder().email("booking-pay-buyer-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("Buyer").role(User.UserRole.BUYER).status("ACTIVE").build());
        otherBuyer = userRepository.save(User.builder().email("booking-pay-other-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("Other Buyer").role(User.UserRole.BUYER).status("ACTIVE").build());
        roomListingId = listingRepository.save(Listing.builder().tenant(tenant).owner(host)
                .listingType(Listing.ListingType.ROOM).title("Booking Pay Room")
                .basePrice(new BigDecimal("1500.00")).status(Listing.ListingStatus.ACTIVE).build()).getId();
        jdbcTemplate.update("INSERT INTO rooms (listing_id, max_guests, room_count, check_in_time, check_out_time, "
                + "created_at, updated_at) VALUES (?, 4, 1, '15:00'::time, '11:00'::time, NOW(), NOW())", roomListingId);
        // integration-test profile 以 ddl-auto=update 建表，沒有 Flyway V79 的 payments.idempotency_key 唯一索引。補上生產有的
        // 那條（只涵蓋本類別產生的鍵，不影響其他測試）；否則併發發起結帳會寫出重複列，測不到唯一索引兜底的行為。
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS it_booking_checkout_key_unique ON payments "
                + "(idempotency_key) WHERE idempotency_key LIKE 'BOOKING-CHECKOUT-%'");
        nextNight = 5;
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ── Mock 付款 ─────────────────────────────────────────────

    @Test
    @DisplayName("Mock 付款：訂房 CREATED → PAID，留下一筆成功的付款紀錄（金額＝訂房總額），日曆仍為已預訂")
    void mockPay_marksBookingPaidAndRecordsSuccessfulPayment() {
        UUID bookingId = buyerBooksTwoNights();

        OrderPaymentStateDto state = paymentStateService.mockBookingPaymentSuccess(bookingId);

        assertThat(state.getOrderStatus()).isEqualTo("PAID");
        assertThat(state.getPaymentStatus()).isEqualTo("SUCCESS");
        assertThat(state.getCanPay()).isFalse();
        assertThat(state.getPaymentProvider()).isEqualTo("mock");
        assertThat(bookingStatus(bookingId)).isEqualTo("PAID");
        assertThat(paymentStatuses(bookingId)).containsExactly("SUCCESS");
        assertThat(jdbcTemplate.queryForObject("SELECT amount FROM payments WHERE booking_id = ?", BigDecimal.class,
                bookingId)).isEqualByComparingTo("3000.00");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments WHERE booking_id = ? AND order_id IS NULL "
                + "AND payment_method = 'MOCK' AND currency = 'TWD'", Integer.class, bookingId)).isEqualTo(1);
        assertThat(bookedCalendarNights(bookingId)).as("付款不可動日曆").isEqualTo(2);
    }

    @Test
    @DisplayName("付款前的狀態：待付款、可付款、付款提供者 mock；啟用 Stripe 後提供者變 stripe")
    void paymentState_beforePaying_reportsProvider() {
        UUID bookingId = buyerBooksTwoNights();

        OrderPaymentStateDto mock = paymentStateService.getBookingPaymentState(bookingId);
        assertThat(mock.getOrderStatus()).isEqualTo("CREATED");
        assertThat(mock.getCanPay()).isTrue();
        assertThat(mock.getPaymentStatus()).isNull();
        assertThat(mock.getPaymentProvider()).isEqualTo("mock");

        stripeEnabled(true);
        assertThat(paymentStateService.getBookingPaymentState(bookingId).getPaymentProvider()).isEqualTo("stripe");
    }

    @Test
    @DisplayName("啟用 Stripe 後 Mock 付款一律拒絕（E-6004）——否則買家不必付錢就能把訂房標成已付款；訂房與付款紀錄都不動")
    void mockPay_whileStripeEnabled_isRejectedAndChangesNothing() {
        UUID bookingId = buyerBooksTwoNights();
        stripeEnabled(true);

        assertThatThrownBy(() -> paymentStateService.mockBookingPaymentSuccess(bookingId))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_6004));

        assertThat(bookingStatus(bookingId)).isEqualTo("CREATED");
        assertThat(paymentStatuses(bookingId)).isEmpty();
    }

    @Test
    @DisplayName("別人的訂房不能付款（E-1007），訂房不動")
    void mockPay_byAnotherBuyer_isForbidden() {
        UUID bookingId = buyerBooksTwoNights();
        asUser(otherBuyer);

        assertThatThrownBy(() -> paymentStateService.mockBookingPaymentSuccess(bookingId))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_1007));

        assertThat(bookingStatus(bookingId)).isEqualTo("CREATED");
        assertThat(paymentStatuses(bookingId)).isEmpty();
    }

    @Test
    @DisplayName("已付款的訂房再付一次 → E-5011，付款紀錄仍只有一筆")
    void mockPay_twice_secondIsRejected() {
        UUID bookingId = buyerBooksTwoNights();
        paymentStateService.mockBookingPaymentSuccess(bookingId);

        assertThatThrownBy(() -> paymentStateService.mockBookingPaymentSuccess(bookingId))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_5011));

        assertThat(paymentStatuses(bookingId)).containsExactly("SUCCESS");
    }

    @Test
    @DisplayName("已取消的訂房不能付款（E-5011）——日曆已釋放，付了錢也訂不到房")
    void mockPay_cancelledBooking_isRejected() {
        UUID bookingId = buyerBooksTwoNights();
        bookingService.cancelBooking(bookingId, "changed my mind");

        assertThatThrownBy(() -> paymentStateService.mockBookingPaymentSuccess(bookingId))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_5011));

        assertThat(bookingStatus(bookingId)).isEqualTo("CANCELLED");
        assertThat(paymentStatuses(bookingId)).isEmpty();
    }

    @Test
    @DisplayName("同一筆訂房 8 個請求同時付款 → 恰好一個成功、只留下一筆付款紀錄（其餘被拒：E-5011 或已有成功付款 E-6003）")
    void concurrentMockPay_exactlyOneWins() throws Exception {
        UUID bookingId = buyerBooksTwoNights();

        List<ErrorCode> failures = race(RACE_THREADS, () -> paymentStateService.mockBookingPaymentSuccess(bookingId));

        assertThat(failures).as("輸掉競態的只能被這兩種錯誤拒絕（先讀到舊狀態的是 E-6003，之後的是 E-5011）")
                .hasSize(RACE_THREADS - 1).isSubsetOf(ErrorCode.E_5011, ErrorCode.E_6003);
        assertThat(bookingStatus(bookingId)).isEqualTo("PAID");
        assertThat(paymentStatuses(bookingId)).as("重複付款會重複計入營收").containsExactly("SUCCESS");
    }

    // ── Stripe 付款 ───────────────────────────────────────────

    @Test
    @DisplayName("Stripe：發起結帳 → 建一筆屬於這筆訂房的 PROCESSING 付款；請求以訂房 id 帶 metadata、金額為訂房總額")
    void stripeCheckout_createsProcessingPaymentForTheBooking() {
        UUID bookingId = buyerBooksTwoNights();
        stripeEnabled(true);
        stubCheckoutSession();

        CheckoutSessionResponse response = paymentStateService.initiateStripeBookingCheckout(bookingId);

        assertThat(response.getBookingId()).isEqualTo(bookingId);
        assertThat(response.getOrderId()).isNull();
        assertThat(response.getSessionUrl()).startsWith("https://checkout.stripe.com/");
        assertThat(paymentStatuses(bookingId)).containsExactly("PROCESSING");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments WHERE booking_id = ? AND order_id IS NULL "
                + "AND payment_method = 'STRIPE' AND transaction_id = ? AND stripe_session_id = ? "
                + "AND idempotency_key = ? AND amount = 3000.00", Integer.class, bookingId, response.getSessionId(),
                response.getSessionId(), "BOOKING-CHECKOUT-" + bookingId)).isEqualTo(1);
        assertThat(bookingStatus(bookingId)).as("還沒付款，訂房仍是 CREATED").isEqualTo("CREATED");

        ArgumentCaptor<PaymentGatewayRequestResponse.CheckoutSessionRequest> request =
                ArgumentCaptor.forClass(PaymentGatewayRequestResponse.CheckoutSessionRequest.class);
        verify(paymentGatewayFactory).createCheckoutSession(eq("STRIPE"), request.capture());
        assertThat(request.getValue().getBookingId()).isEqualTo(bookingId);
        assertThat(request.getValue().getOrderId()).isNull();
        assertThat(request.getValue().getAmount()).isEqualByComparingTo("3000.00");
        assertThat(request.getValue().getSuccessUrl()).contains("/bookings/" + bookingId + "/payment/success");
        assertThat(request.getValue().getCancelUrl()).contains("/bookings/" + bookingId + "/payment/cancel");
    }

    @Test
    @DisplayName("Stripe：未啟用時發起結帳被拒（E-6002）；別人的訂房被拒（E-1007）；不是待付款的訂房被拒（E-5011）")
    void stripeCheckout_guards() {
        UUID bookingId = buyerBooksTwoNights();
        stubCheckoutSession();

        assertThatThrownBy(() -> paymentStateService.initiateStripeBookingCheckout(bookingId))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_6002));

        stripeEnabled(true);
        asUser(otherBuyer);
        assertThatThrownBy(() -> paymentStateService.initiateStripeBookingCheckout(bookingId))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_1007));

        asUser(buyer);
        bookingService.cancelBooking(bookingId, "no longer needed");
        assertThatThrownBy(() -> paymentStateService.initiateStripeBookingCheckout(bookingId))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_5011));

        assertThat(paymentStatuses(bookingId)).isEmpty();
    }

    @Test
    @DisplayName("Stripe：同一筆訂房連續發起兩次結帳（買家在 Stripe 頁按返回、重新付款）→ 兩次都成功、拿到同一個 session、只有一列付款紀錄")
    void stripeCheckout_startedTwice_keepsOnePaymentRow() {
        UUID bookingId = buyerBooksTwoNights();
        stripeEnabled(true);
        String sessionId = "cs_test_" + UUID.randomUUID();
        // Stripe 端以相同冪等鍵回傳同一個 session
        lenient().when(paymentGatewayFactory.createCheckoutSession(eq("STRIPE"), any()))
                .thenReturn(PaymentGatewayRequestResponse.CheckoutSessionResult.builder().sessionId(sessionId)
                        .sessionUrl("https://checkout.stripe.com/c/pay/" + sessionId).status("open").paymentStatus("unpaid").build());

        CheckoutSessionResponse first = paymentStateService.initiateStripeBookingCheckout(bookingId);
        CheckoutSessionResponse second = paymentStateService.initiateStripeBookingCheckout(bookingId);

        assertThat(second.getSessionId()).isEqualTo(first.getSessionId());
        assertThat(paymentStatuses(bookingId)).as("重複寫入會撞 payments.idempotency_key 唯一索引").containsExactly("PROCESSING");
    }

    @Test
    @DisplayName("Stripe：同一筆訂房 8 個請求同時發起結帳 → 沒有人得到非預期的錯誤（輸家至多是可重試的 E-6005），只有一列付款紀錄")
    void stripeCheckout_startedConcurrently_keepsOnePaymentRow() throws Exception {
        UUID bookingId = buyerBooksTwoNights();
        stripeEnabled(true);
        String sessionId = "cs_test_" + UUID.randomUUID();
        lenient().when(paymentGatewayFactory.createCheckoutSession(eq("STRIPE"), any()))
                .thenReturn(PaymentGatewayRequestResponse.CheckoutSessionResult.builder().sessionId(sessionId)
                        .sessionUrl("https://checkout.stripe.com/c/pay/" + sessionId).status("open").paymentStatus("unpaid").build());

        List<ErrorCode> failures = race(RACE_THREADS, () -> paymentStateService.initiateStripeBookingCheckout(bookingId));

        assertThat(failures).as("其他例外（例如提交時的 UnexpectedRollbackException）會直接讓 race() 拋出").isSubsetOf(ErrorCode.E_6005);
        assertThat(paymentStatuses(bookingId)).containsExactly("PROCESSING");
    }

    @Test
    @DisplayName("Stripe webhook：checkout.session.completed（已付款）→ 訂房 PAID、付款 SUCCESS；同一事件重送不重複處理")
    void stripeWebhook_paid_marksBookingPaid_andReplayIsIdempotent() {
        UUID bookingId = buyerBooksTwoNights();
        String sessionId = startStripeCheckout(bookingId);
        String event = checkoutCompletedEvent("evt_" + UUID.randomUUID(), sessionId, "pi_" + UUID.randomUUID());

        paymentWebhookService.handleEvent(event);

        assertThat(bookingStatus(bookingId)).isEqualTo("PAID");
        assertThat(paymentStatuses(bookingId)).containsExactly("SUCCESS");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments WHERE booking_id = ? AND paid_at IS NOT NULL "
                + "AND stripe_payment_intent_id IS NOT NULL", Integer.class, bookingId)).isEqualTo(1);
        int audits = auditCount("STRIPE_PAYMENT_SUCCEEDED_WEBHOOK", bookingPaymentId(bookingId));

        paymentWebhookService.handleEvent(event);

        assertThat(bookingStatus(bookingId)).isEqualTo("PAID");
        assertThat(auditCount("STRIPE_PAYMENT_SUCCEEDED_WEBHOOK", bookingPaymentId(bookingId)))
                .as("重送同一事件不可再寫一筆成功稽核").isEqualTo(audits);
    }

    @Test
    @DisplayName("Stripe 回跳確認：Stripe 說已付款 → 訂房 PAID；再收到 webhook 是無動作（只轉換一次）")
    void stripeReturn_confirm_thenWebhook_transitionsOnce() {
        UUID bookingId = buyerBooksTwoNights();
        String sessionId = startStripeCheckout(bookingId);
        stubRetrievePaid(sessionId, "pi_return");

        OrderPaymentStateDto state = paymentStateService.confirmStripeBookingCheckout(bookingId, sessionId);

        assertThat(state.getOrderStatus()).isEqualTo("PAID");
        assertThat(state.getPaymentStatus()).as("條件式 UPDATE 不經過 persistence context，沒重新整理會讀到舊的 PROCESSING").isEqualTo("SUCCESS");
        assertThat(state.getPaidAt()).isNotNull();
        int audits = auditCount("STRIPE_PAYMENT_SUCCEEDED_WEBHOOK", bookingPaymentId(bookingId));

        paymentWebhookService.handleEvent(checkoutCompletedEvent("evt_" + UUID.randomUUID(), sessionId, "pi_return"));

        assertThat(bookingStatus(bookingId)).isEqualTo("PAID");
        assertThat(auditCount("STRIPE_PAYMENT_SUCCEEDED_WEBHOOK", bookingPaymentId(bookingId))).isEqualTo(audits);
    }

    @Test
    @DisplayName("Stripe 回跳確認與 webhook 同時抵達 → 付款只標成功一次、訂房 PAID")
    void stripeReturnAndWebhookAtTheSameTime_transitionOnce() throws Exception {
        UUID bookingId = buyerBooksTwoNights();
        String sessionId = startStripeCheckout(bookingId);
        stubRetrievePaid(sessionId, "pi_race");
        List<Callable<Void>> arrivals = List.of(
                () -> {
                    asUser(buyer);
                    paymentStateService.confirmStripeBookingCheckout(bookingId, sessionId);
                    return null;
                },
                () -> {
                    paymentWebhookService.handleEvent(checkoutCompletedEvent("evt_" + UUID.randomUUID(), sessionId, "pi_race"));
                    return null;
                });

        raceAll(arrivals);

        assertThat(bookingStatus(bookingId)).isEqualTo("PAID");
        assertThat(paymentStatuses(bookingId)).containsExactly("SUCCESS");
        assertThat(auditCount("STRIPE_PAYMENT_SUCCEEDED_WEBHOOK", bookingPaymentId(bookingId)))
                .as("成功稽核只該有一筆——兩條路徑都轉換成功代表狀態被寫了兩次").isEqualTo(1);
    }

    @Test
    @DisplayName("Stripe 回跳確認：拿別筆訂房的 session 不能確認這一筆（E-6000）；那一筆的付款也不被動")
    void stripeReturn_withAnotherBookingsSession_isRejected() {
        UUID bookingA = buyerBooksTwoNights();
        UUID bookingB = buyerBooksTwoNights();
        String sessionOfA = startStripeCheckout(bookingA);
        stubRetrievePaid(sessionOfA, "pi_a");

        assertThatThrownBy(() -> paymentStateService.confirmStripeBookingCheckout(bookingB, sessionOfA))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.E_6000));

        assertThat(bookingStatus(bookingB)).isEqualTo("CREATED");
        assertThat(bookingStatus(bookingA)).isEqualTo("CREATED");
        assertThat(paymentStatuses(bookingA)).containsExactly("PROCESSING");
    }

    @Test
    @DisplayName("已知缺口（DEF-308 同型）：買家付款前先取消訂房，Stripe 隨後才通知付款成功 → 付款 SUCCESS、訂房維持 CANCELLED，"
            + "但一定留下稽核紀錄可查（不再靜默）")
    void stripePaymentSucceedsAfterBookingCancelled_leavesAuditTrail() {
        UUID bookingId = buyerBooksTwoNights();
        String sessionId = startStripeCheckout(bookingId);
        bookingService.cancelBooking(bookingId, "cancelled while on the Stripe page");

        paymentWebhookService.handleEvent(checkoutCompletedEvent("evt_" + UUID.randomUUID(), sessionId, "pi_late"));

        assertThat(paymentStatuses(bookingId)).containsExactly("SUCCESS");
        assertThat(bookingStatus(bookingId)).as("訂房已取消、日曆已釋放，不可被拉回已付款").isEqualTo("CANCELLED");
        assertThat(auditCount("STRIPE_PAYMENT_BOOKING_NOT_PAYABLE", bookingPaymentId(bookingId)))
                .as("錢收了但訂房不成立，必須留下可查的紀錄").isEqualTo(1);
    }

    // ── 測試輔助 ──────────────────────────────────────────────

    private UUID buyerBooksTwoNights() {
        asUser(buyer);
        LocalDate checkIn = LocalDate.now().plusDays(nextNight);
        nextNight += 3;
        return bookingService.createBooking(BookingDto.CreateRequest.builder()
                .roomListingId(roomListingId).checkInDate(checkIn).checkOutDate(checkIn.plusDays(2))
                .guestCount(2).guestName("Test Guest").guestPhone("0912345678").guestEmail("guest@example.com")
                .build(), null).getId();
    }

    private void asUser(final User user) {
        TenantContext.setCurrentUser(user.getId());
        TenantContext.setCurrentTenant(tenant.getId());
        List<SimpleGrantedAuthority> authorities = new RolePermissionMapping().getAuthorities(user.getRole())
                .stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getId().toString(), null, authorities));
    }

    private void stripeEnabled(final boolean enabled) {
        lenient().when(featureToggleService.isFeatureEnabled(STRIPE_TOGGLE)).thenReturn(enabled);
    }

    private void stubCheckoutSession() {
        lenient().when(paymentGatewayFactory.createCheckoutSession(eq("STRIPE"), any()))
                .thenAnswer(invocation -> {
                    String sessionId = "cs_test_" + UUID.randomUUID();
                    return PaymentGatewayRequestResponse.CheckoutSessionResult.builder()
                            .sessionId(sessionId).sessionUrl("https://checkout.stripe.com/c/pay/" + sessionId)
                            .status("open").paymentStatus("unpaid").build();
                });
    }

    private void stubRetrievePaid(final String sessionId, final String paymentIntentId) {
        lenient().when(paymentGatewayFactory.retrieveCheckoutSession("STRIPE", sessionId))
                .thenReturn(PaymentGatewayRequestResponse.CheckoutSessionResult.builder()
                        .sessionId(sessionId).paymentIntentId(paymentIntentId).status("complete").paymentStatus("paid").build());
    }

    private String startStripeCheckout(final UUID bookingId) {
        stripeEnabled(true);
        stubCheckoutSession();
        asUser(buyer);
        return paymentStateService.initiateStripeBookingCheckout(bookingId).getSessionId();
    }

    private static String checkoutCompletedEvent(final String eventId, final String sessionId, final String paymentIntentId) {
        return "{\"id\":\"" + eventId + "\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\""
                + sessionId + "\",\"payment_status\":\"paid\",\"payment_intent\":\"" + paymentIntentId + "\"}}}";
    }

    /** 同時放行 {@code threads} 個相同的呼叫，回傳失敗者的錯誤碼（成功者不在其中）。 */
    private List<ErrorCode> race(final int threads, final Runnable call) throws Exception {
        List<Callable<Void>> calls = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            calls.add(() -> {
                asUser(buyer);
                call.run();
                return null;
            });
        }
        return raceAll(calls);
    }

    private List<ErrorCode> raceAll(final List<Callable<Void>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<ErrorCode>> results = new ArrayList<>();
        try {
            for (Callable<Void> call : calls) {
                results.add(pool.submit(() -> {
                    startGun.await();
                    try {
                        call.call();
                        return null;
                    } catch (BusinessException e) {
                        return e.getErrorCode();
                    } finally {
                        TenantContext.clear();
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            startGun.countDown();
            List<ErrorCode> failures = new ArrayList<>();
            for (Future<ErrorCode> result : results) {
                ErrorCode code = result.get(60, TimeUnit.SECONDS);
                if (code != null) {
                    failures.add(code);
                }
            }
            return failures;
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private String bookingStatus(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingId);
    }

    private List<String> paymentStatuses(final UUID bookingId) {
        return jdbcTemplate.queryForList("SELECT status FROM payments WHERE booking_id = ? ORDER BY created_at",
                String.class, bookingId);
    }

    private UUID bookingPaymentId(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT id FROM payments WHERE booking_id = ?", UUID.class, bookingId);
    }

    private int bookedCalendarNights(final UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM room_calendar WHERE booking_id = ? AND status = 'BOOKED'",
                Integer.class, bookingId);
    }

    private int auditCount(final String action, final UUID entityId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE entity_id = ? AND action = ?",
                Integer.class, entityId, action);
    }
}
