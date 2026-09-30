package com.nextkey.ecommerce.core.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.nextkey.ecommerce.api.dto.payment.CheckoutSessionResponse;
import com.nextkey.ecommerce.api.dto.payment.OrderPaymentStateDto;
import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.settlement.SettlementAdjustmentService;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.OrderStateLogRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayFactory;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayRequestResponse;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * {@link PaymentStateService} 的訂房付款（Sprint 221，DEF-303 (1)）。
 *
 * <p>重點在「先搶占狀態轉換、才有副作用」與「錢收了但訂房不能付款時必留紀錄」：這兩件事錯了，
 * 後果分別是重複付款紀錄／營收重複計入，以及買家付了錢而系統毫無跡象。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentStateService: 訂房付款（Sprint 221）")
class PaymentStateServiceBookingTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private FeatureToggleService featureToggleService;
    @Mock private PaymentGatewayFactory paymentGatewayFactory;
    @Mock private SettlementAdjustmentService settlementAdjustmentService;
    @Mock private OrderStateLogRepository orderStateLogRepository;
    @Mock private AuditService auditService;
    @Mock private EntityManager entityManager;

    private PaymentStateService service;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID BOOKING_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_BOOKING_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID TENANT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID PAYMENT_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @BeforeEach
    void setUp() {
        service = new PaymentStateService(paymentRepository, orderRepository, bookingRepository,
                featureToggleService, paymentGatewayFactory, settlementAdjustmentService,
                orderStateLogRepository, auditService);
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        TenantContext.setCurrentUser(USER_ID);
        when(bookingRepository.updateStatusIfCurrent(any(), any(), any())).thenReturn(1);
        when(paymentRepository.markSuccessIfNotAlready(any(), any(), any(), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Booking bookingOf(final UUID userId, final Booking.BookingStatus status) {
        Booking booking = Booking.builder().userId(userId).tenantId(TENANT_ID).status(status)
                .totalAmount(new BigDecimal("3000.00")).build();
        booking.setId(BOOKING_ID);
        return booking;
    }

    private void bookingExists(final Booking.BookingStatus status) {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(bookingOf(USER_ID, status)));
    }

    private static ErrorCode codeOf(final Throwable thrown) {
        return ((BusinessException) thrown).getErrorCode();
    }

    private void stripeEnabled() {
        when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(true);
    }

    private static PaymentGatewayRequestResponse.CheckoutSessionResult session(final String sessionId) {
        return PaymentGatewayRequestResponse.CheckoutSessionResult.builder().sessionId(sessionId)
                .sessionUrl("https://checkout.stripe.com/c/pay/" + sessionId).status("open").paymentStatus("unpaid").build();
    }

    @Nested
    @DisplayName("mockBookingPaymentSuccess")
    class MockBookingPaymentSuccess {

        @Test
        @DisplayName("成功：以 CREATED→PAID 條件式更新搶占，再建立成功的 Mock 付款（金額＝訂房總額）並寫稽核")
        void success_claimsThenRecordsPayment() {
            bookingExists(Booking.BookingStatus.CREATED);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment saved = invocation.getArgument(0);
                saved.setId(PAYMENT_ID);
                return saved;
            });

            OrderPaymentStateDto state = service.mockBookingPaymentSuccess(BOOKING_ID);

            verify(bookingRepository).updateStatusIfCurrent(BOOKING_ID, Booking.BookingStatus.CREATED, Booking.BookingStatus.PAID);
            verify(paymentRepository).save(argThat((Payment p) -> BOOKING_ID.equals(p.getBookingId())
                    && p.getOrderId() == null
                    && p.getPaymentMethod() == Payment.PaymentMethod.MOCK
                    && p.getStatus() == Payment.PaymentStatus.SUCCESS
                    && p.getAmount().compareTo(new BigDecimal("3000.00")) == 0
                    && "TWD".equals(p.getCurrency())));
            verify(auditService).record(eq("BOOKING_PAYMENT_MOCK_SUCCESS"), eq("PAYMENT"), eq(PAYMENT_ID), eq(TENANT_ID),
                    eq("CREATED"), eq("PAID"), any(), eq(USER_ID));
            assertThat(state.getOrderStatus()).isEqualTo("PAID");
            assertThat(state.getPaymentStatus()).isEqualTo("SUCCESS");
            assertThat(state.getCanPay()).isFalse();
        }

        @Test
        @DisplayName("搶占失敗（已被併發的付款或取消搶先）→ E-5011，且不建立任何付款紀錄")
        void lostClaim_rejectsWithoutSideEffects() {
            bookingExists(Booking.BookingStatus.CREATED);
            when(bookingRepository.updateStatusIfCurrent(any(), any(), any())).thenReturn(0);

            assertThatThrownBy(() -> service.mockBookingPaymentSuccess(BOOKING_ID))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_5011));

            verify(paymentRepository, never()).save(any(Payment.class));
            verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("啟用 Stripe 時拒絕（E-6004），連狀態轉換都不嘗試")
        void stripeEnabled_isRejected() {
            bookingExists(Booking.BookingStatus.CREATED);
            stripeEnabled();

            assertThatThrownBy(() -> service.mockBookingPaymentSuccess(BOOKING_ID))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_6004));

            verify(bookingRepository, never()).updateStatusIfCurrent(any(), any(), any());
        }

        @Test
        @DisplayName("不是待付款（已付款／已取消…）→ E-5011")
        void notCreated_isRejected() {
            bookingExists(Booking.BookingStatus.CANCELLED);

            assertThatThrownBy(() -> service.mockBookingPaymentSuccess(BOOKING_ID))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_5011));

            verify(bookingRepository, never()).updateStatusIfCurrent(any(), any(), any());
        }

        @Test
        @DisplayName("已有成功的付款紀錄 → E-6003")
        void alreadyHasSuccessfulPayment_isRejected() {
            bookingExists(Booking.BookingStatus.CREATED);
            when(paymentRepository.existsByBookingIdAndStatus(BOOKING_ID, Payment.PaymentStatus.SUCCESS)).thenReturn(true);

            assertThatThrownBy(() -> service.mockBookingPaymentSuccess(BOOKING_ID))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_6003));

            verify(bookingRepository, never()).updateStatusIfCurrent(any(), any(), any());
        }

        @Test
        @DisplayName("別人的訂房 → E-1007；找不到訂房 → E-4006")
        void ownershipAndNotFound() {
            when(bookingRepository.findById(BOOKING_ID))
                    .thenReturn(Optional.of(bookingOf(OTHER_USER_ID, Booking.BookingStatus.CREATED)));
            assertThatThrownBy(() -> service.mockBookingPaymentSuccess(BOOKING_ID))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_1007));

            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.mockBookingPaymentSuccess(BOOKING_ID))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_4006));
        }
    }

    @Nested
    @DisplayName("getBookingPaymentState")
    class GetBookingPaymentState {

        @Test
        @DisplayName("回報付款提供者：未啟用 Stripe → mock；啟用 → stripe（前端據此決定顯示模擬付款按鈕或重導 Stripe）")
        void reportsPaymentProvider() {
            bookingExists(Booking.BookingStatus.CREATED);
            when(paymentRepository.findEffectiveByBookingId(BOOKING_ID)).thenReturn(Optional.empty());
            assertThat(service.getBookingPaymentState(BOOKING_ID).getPaymentProvider()).isEqualTo("mock");

            stripeEnabled();
            assertThat(service.getBookingPaymentState(BOOKING_ID).getPaymentProvider()).isEqualTo("stripe");
        }

        @Test
        @DisplayName("有付款紀錄時帶出累計已退款金額")
        void reportsRefundedAmount() {
            bookingExists(Booking.BookingStatus.PAID);
            Payment payment = Payment.builder().bookingId(BOOKING_ID).status(Payment.PaymentStatus.SUCCESS)
                    .refundedAmount(new BigDecimal("500.00")).paidAt(Instant.now()).build();
            when(paymentRepository.findEffectiveByBookingId(BOOKING_ID)).thenReturn(Optional.of(payment));

            assertThat(service.getBookingPaymentState(BOOKING_ID).getRefundedAmount()).isEqualByComparingTo("500.00");
        }
    }

    @Nested
    @DisplayName("initiateStripeBookingCheckout")
    class InitiateStripeBookingCheckout {

        @Test
        @DisplayName("成功：請求帶訂房 id／總額／回跳網址，建立屬於這筆訂房的 PROCESSING 付款，回傳重導網址")
        void success() {
            bookingExists(Booking.BookingStatus.CREATED);
            stripeEnabled();
            when(paymentGatewayFactory.createCheckoutSession(eq("STRIPE"), any())).thenReturn(session("cs_1"));

            CheckoutSessionResponse response = service.initiateStripeBookingCheckout(BOOKING_ID);

            assertThat(response.getBookingId()).isEqualTo(BOOKING_ID);
            assertThat(response.getOrderId()).isNull();
            assertThat(response.getSessionUrl()).isEqualTo("https://checkout.stripe.com/c/pay/cs_1");
            verify(paymentGatewayFactory).createCheckoutSession(eq("STRIPE"), argThat(
                    (PaymentGatewayRequestResponse.CheckoutSessionRequest r) -> BOOKING_ID.equals(r.getBookingId())
                            && r.getOrderId() == null
                            && r.getAmount().compareTo(new BigDecimal("3000.00")) == 0
                            && "TWD".equals(r.getCurrency())
                            && ("BOOKING-CHECKOUT-" + BOOKING_ID).equals(r.getIdempotencyKey())
                            && r.getSuccessUrl().equals("http://localhost:3000/bookings/" + BOOKING_ID
                                    + "/payment/success?session_id={CHECKOUT_SESSION_ID}")
                            && r.getCancelUrl().equals("http://localhost:3000/bookings/" + BOOKING_ID + "/payment/cancel")));
            verify(paymentRepository).saveAndFlush(argThat((Payment p) -> BOOKING_ID.equals(p.getBookingId())
                    && p.getOrderId() == null
                    && p.getPaymentMethod() == Payment.PaymentMethod.STRIPE
                    && p.getStatus() == Payment.PaymentStatus.PROCESSING
                    && "cs_1".equals(p.getTransactionId())
                    && "cs_1".equals(p.getStripeSessionId())
                    && ("BOOKING-CHECKOUT-" + BOOKING_ID).equals(p.getIdempotencyKey())));
        }

        @Test
        @DisplayName("未啟用 Stripe → E-6002，不呼叫 Stripe")
        void toggleOff_isRejected() {
            bookingExists(Booking.BookingStatus.CREATED);

            assertThatThrownBy(() -> service.initiateStripeBookingCheckout(BOOKING_ID))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_6002));

            verify(paymentGatewayFactory, never()).createCheckoutSession(any(), any());
        }

        @Test
        @DisplayName("不是待付款 → E-5011；已有成功付款 → E-6003；別人的訂房 → E-1007（都不呼叫 Stripe）")
        void guards() {
            stripeEnabled();
            bookingExists(Booking.BookingStatus.PAID);
            assertThatThrownBy(() -> service.initiateStripeBookingCheckout(BOOKING_ID))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_5011));

            bookingExists(Booking.BookingStatus.CREATED);
            when(paymentRepository.existsByBookingIdAndStatus(BOOKING_ID, Payment.PaymentStatus.SUCCESS)).thenReturn(true);
            assertThatThrownBy(() -> service.initiateStripeBookingCheckout(BOOKING_ID))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_6003));

            when(bookingRepository.findById(BOOKING_ID))
                    .thenReturn(Optional.of(bookingOf(OTHER_USER_ID, Booking.BookingStatus.CREATED)));
            assertThatThrownBy(() -> service.initiateStripeBookingCheckout(BOOKING_ID))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_1007));

            verify(paymentGatewayFactory, never()).createCheckoutSession(any(), any());
        }

        @Test
        @DisplayName("再次發起（Stripe 以冪等鍵回傳同一個 session、付款紀錄已存在）→ 不再寫第二列，仍回傳 session")
        void secondInitiation_reusesTheExistingPaymentRow() {
            bookingExists(Booking.BookingStatus.CREATED);
            stripeEnabled();
            when(paymentGatewayFactory.createCheckoutSession(eq("STRIPE"), any())).thenReturn(session("cs_1"));
            when(paymentRepository.findByIdempotencyKey("BOOKING-CHECKOUT-" + BOOKING_ID))
                    .thenReturn(Optional.of(Payment.builder().bookingId(BOOKING_ID).build()));

            CheckoutSessionResponse response = service.initiateStripeBookingCheckout(BOOKING_ID);

            assertThat(response.getSessionId()).isEqualTo("cs_1");
            verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
        }

        @Test
        @DisplayName("查與寫之間的極小競態：唯一索引擋下第二列 → E-6005（可重試），不是 500")
        void raceBetweenLookupAndInsert_isReportedAsRetryable() {
            bookingExists(Booking.BookingStatus.CREATED);
            stripeEnabled();
            when(paymentGatewayFactory.createCheckoutSession(eq("STRIPE"), any())).thenReturn(session("cs_1"));
            when(paymentRepository.saveAndFlush(any(Payment.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            assertThatThrownBy(() -> service.initiateStripeBookingCheckout(BOOKING_ID))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_6005));
        }
    }

    @Nested
    @DisplayName("confirmStripeBookingCheckout")
    class ConfirmStripeBookingCheckout {

        private Payment processingPaymentOf(final UUID bookingId) {
            return Payment.builder().id(PAYMENT_ID).bookingId(bookingId).status(Payment.PaymentStatus.PROCESSING)
                    .transactionId("cs_1").build();
        }

        @Test
        @DisplayName("Stripe 說已付款 → 付款 SUCCESS、訂房 PAID")
        void paid_marksBookingPaid() {
            bookingExists(Booking.BookingStatus.CREATED);
            Payment payment = processingPaymentOf(BOOKING_ID);
            when(paymentRepository.findByTransactionId("cs_1")).thenReturn(Optional.of(payment));
            when(paymentGatewayFactory.retrieveCheckoutSession("STRIPE", "cs_1")).thenReturn(
                    PaymentGatewayRequestResponse.CheckoutSessionResult.builder().sessionId("cs_1")
                            .paymentIntentId("pi_1").status("complete").paymentStatus("paid").build());

            OrderPaymentStateDto state = service.confirmStripeBookingCheckout(BOOKING_ID, "cs_1");

            verify(paymentRepository).markSuccessIfNotAlready(eq(PAYMENT_ID), eq(Payment.PaymentStatus.SUCCESS), eq("pi_1"), any());
            verify(bookingRepository).updateStatusIfCurrent(BOOKING_ID, Booking.BookingStatus.CREATED, Booking.BookingStatus.PAID);
            // 條件式 UPDATE 不經過 persistence context：必須重新整理已載入的付款實體，否則回應會是「訂房 PAID、付款 PROCESSING」
            verify(entityManager).refresh(payment);
            assertThat(state.getOrderStatus()).isEqualTo("PAID");
        }

        @Test
        @DisplayName("Stripe 說還沒付款 → 什麼都不改，回報現況")
        void unpaid_changesNothing() {
            bookingExists(Booking.BookingStatus.CREATED);
            when(paymentRepository.findByTransactionId("cs_1")).thenReturn(Optional.of(processingPaymentOf(BOOKING_ID)));
            when(paymentGatewayFactory.retrieveCheckoutSession("STRIPE", "cs_1")).thenReturn(session("cs_1"));

            OrderPaymentStateDto state = service.confirmStripeBookingCheckout(BOOKING_ID, "cs_1");

            verify(paymentRepository, never()).markSuccessIfNotAlready(any(), any(), any(), any());
            verify(bookingRepository, never()).updateStatusIfCurrent(any(), any(), any());
            verify(entityManager, never()).refresh(any());
            assertThat(state.getOrderStatus()).isEqualTo("CREATED");
            assertThat(state.getPaymentStatus()).isEqualTo("PROCESSING");
        }

        @Test
        @DisplayName("這個 session 屬於別筆訂房 → E-6000，連 Stripe 都不問")
        void sessionOfAnotherBooking_isRejected() {
            bookingExists(Booking.BookingStatus.CREATED);
            when(paymentRepository.findByTransactionId("cs_1")).thenReturn(Optional.of(processingPaymentOf(OTHER_BOOKING_ID)));

            assertThatThrownBy(() -> service.confirmStripeBookingCheckout(BOOKING_ID, "cs_1"))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_6000));

            verify(paymentGatewayFactory, never()).retrieveCheckoutSession(any(), any());
        }

        @Test
        @DisplayName("找不到這個 session 的付款 → E-6000；別人的訂房 → E-1007")
        void unknownSessionAndOwnership() {
            bookingExists(Booking.BookingStatus.CREATED);
            when(paymentRepository.findByTransactionId("cs_x")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.confirmStripeBookingCheckout(BOOKING_ID, "cs_x"))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_6000));

            when(bookingRepository.findById(BOOKING_ID))
                    .thenReturn(Optional.of(bookingOf(OTHER_USER_ID, Booking.BookingStatus.CREATED)));
            assertThatThrownBy(() -> service.confirmStripeBookingCheckout(BOOKING_ID, "cs_1"))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.E_1007));
        }

        @Test
        @DisplayName("付款早已成功 → 冪等，直接回報現況，不再問 Stripe")
        void alreadySucceeded_isIdempotent() {
            bookingExists(Booking.BookingStatus.PAID);
            Payment payment = processingPaymentOf(BOOKING_ID);
            payment.setStatus(Payment.PaymentStatus.SUCCESS);
            when(paymentRepository.findByTransactionId("cs_1")).thenReturn(Optional.of(payment));

            OrderPaymentStateDto state = service.confirmStripeBookingCheckout(BOOKING_ID, "cs_1");

            verify(paymentGatewayFactory, never()).retrieveCheckoutSession(any(), any());
            verify(paymentRepository, never()).markSuccessIfNotAlready(any(), any(), any(), any());
            assertThat(state.getPaymentStatus()).isEqualTo("SUCCESS");
        }
    }

    @Nested
    @DisplayName("markStripePaymentSucceeded：訂房付款")
    class MarkStripePaymentSucceededForBooking {

        private Payment paymentOfBooking() {
            return Payment.builder().id(PAYMENT_ID).bookingId(BOOKING_ID).status(Payment.PaymentStatus.PROCESSING)
                    .transactionId("cs_1").build();
        }

        @Test
        @DisplayName("訂房仍是待付款 → 以條件式更新轉 PAID，寫成功稽核")
        void bookingBecomesPaid() {
            when(paymentRepository.findByTransactionId("cs_1")).thenReturn(Optional.of(paymentOfBooking()));
            bookingExists(Booking.BookingStatus.CREATED);

            boolean changed = service.markStripePaymentSucceeded("cs_1", "pi_1");

            assertThat(changed).isTrue();
            verify(bookingRepository).updateStatusIfCurrent(BOOKING_ID, Booking.BookingStatus.CREATED, Booking.BookingStatus.PAID);
            verify(auditService).record(eq("STRIPE_PAYMENT_SUCCEEDED_WEBHOOK"), eq("PAYMENT"), eq(PAYMENT_ID), eq(TENANT_ID),
                    any(), eq("SUCCESS"), any());
            verify(auditService, never()).record(eq("STRIPE_PAYMENT_BOOKING_NOT_PAYABLE"), any(), any(), any(), any(), any(),
                    any());
        }

        @Test
        @DisplayName("訂房已不是待付款（買家先取消了）→ 付款照常標成功，但訂房不動，並留下「錢收了、訂房不成立」的稽核")
        void bookingNotPayable_leavesAuditTrail() {
            when(paymentRepository.findByTransactionId("cs_1")).thenReturn(Optional.of(paymentOfBooking()));
            bookingExists(Booking.BookingStatus.CANCELLED);
            when(bookingRepository.updateStatusIfCurrent(any(), any(), any())).thenReturn(0);

            boolean changed = service.markStripePaymentSucceeded("cs_1", "pi_1");

            assertThat(changed).as("付款本身確實被標成成功").isTrue();
            verify(auditService).record(eq("STRIPE_PAYMENT_BOOKING_NOT_PAYABLE"), eq("PAYMENT"), eq(PAYMENT_ID), eq(TENANT_ID),
                    eq("CANCELLED"), eq("SUCCESS"), any());
        }

        @Test
        @DisplayName("付款早已標成功（重送／另一條路徑搶先）→ 不再動訂房、不寫稽核")
        void alreadyMarked_doesNothing() {
            when(paymentRepository.findByTransactionId("cs_1")).thenReturn(Optional.of(paymentOfBooking()));
            when(paymentRepository.markSuccessIfNotAlready(any(), any(), any(), any())).thenReturn(0);

            boolean changed = service.markStripePaymentSucceeded("cs_1", "pi_1");

            assertThat(changed).isFalse();
            verify(bookingRepository, never()).updateStatusIfCurrent(any(), any(), any());
            verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
        }
    }
}
