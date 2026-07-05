package com.nextkey.ecommerce.core.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.nextkey.ecommerce.api.dto.payment.OrderPaymentStateDto;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayFactory;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayRequestResponse;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * PaymentStateService 單元測試（Sprint 67 US-001）。
 *
 * <p>背景：{@link PaymentStateService} 是金流核心（10 個 public 方法，含 Stripe 退款/對帳邏輯），
 * 先前僅 {@code PaymentStateServiceStripeTest}（Sprint 50/52/56）覆蓋
 * initiateStripeCheckout/confirmStripeCheckout/refundOrderPayment/markStripeRefunded 的 happy-path
 * 與部分退款情境（共 11 個測試），另有 {@code M07PaymentMockIntegrationTest} 透過 Controller
 * 對 mock 付款/退款流程做端對端驗證。但 getOrderPaymentState、getBookingPaymentState、
 * mockPaymentSuccess、mockPaymentFailure、markStripePaymentSucceeded（獨立驗證）、
 * markStripePaymentFailed 這 6 個方法完全沒有針對 {@link PaymentStateService} 本身的單元測試，
 * 已測方法也缺少擁有權（E_1007）、找不到資源（E_5000/E_4006/E_6000）等錯誤路徑覆蓋。
 * 本測試類別補齊上述缺口，目標為 10 個方法的完整正常/邊界/錯誤路徑覆蓋。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentStateService 單元測試（Sprint 67）")
class PaymentStateServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private FeatureToggleService featureToggleService;
    @Mock private PaymentGatewayFactory paymentGatewayFactory;

    private PaymentStateService service;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BOOKING_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void setUp() {
        service = new PaymentStateService(paymentRepository, orderRepository, bookingRepository,
                featureToggleService, paymentGatewayFactory);
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:3000");
        TenantContext.setCurrentUser(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Order orderOf(final UUID userId, final Order.OrderStatus status) {
        Order order = Order.builder()
                .userId(userId)
                .status(status)
                .totalAmount(BigDecimal.valueOf(1500))
                .currency("TWD")
                .build();
        order.setId(ORDER_ID);
        return order;
    }

    private void loginAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    // ========== getOrderPaymentState ==========

    @Nested
    @DisplayName("getOrderPaymentState")
    class GetOrderPaymentState {

        @Test
        @DisplayName("UT-PAY-STATE-001: 訂單不存在 -> E_5000")
        void orderNotFound_throwsE5000() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getOrderPaymentState(ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_5000);
        }

        @Test
        @DisplayName("UT-PAY-STATE-002: 非本人查詢他人訂單 -> E_1007")
        void otherUser_throwsE1007() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(OTHER_USER_ID, Order.OrderStatus.CREATED)));

            assertThatThrownBy(() -> service.getOrderPaymentState(ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_1007);
        }

        @Test
        @DisplayName("UT-PAY-STATE-003: 本人查詢 + 無付款記錄 -> dto 不含 payment 欄位")
        void ownerNoPayment_returnsStateWithoutPayment() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(USER_ID, Order.OrderStatus.CREATED)));
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            OrderPaymentStateDto dto = service.getOrderPaymentState(ORDER_ID);

            assertThat(dto.getOrderStatus()).isEqualTo("CREATED");
            assertThat(dto.getPaymentId()).isNull();
            assertThat(dto.getCanPay()).isTrue();
        }

        @Test
        @DisplayName("UT-PAY-STATE-004: 本人查詢 + 有付款記錄 -> dto 含 payment 資訊")
        void ownerWithPayment_returnsPaymentInfo() {
            Payment payment = Payment.builder().orderId(ORDER_ID).status(Payment.PaymentStatus.SUCCESS)
                    .transactionId("MOCK-1").build();
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(USER_ID, Order.OrderStatus.PAID)));
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));

            OrderPaymentStateDto dto = service.getOrderPaymentState(ORDER_ID);

            assertThat(dto.getPaymentStatus()).isEqualTo("SUCCESS");
            assertThat(dto.getTransactionId()).isEqualTo("MOCK-1");
        }

        @Test
        @DisplayName("UT-PAY-STATE-005: admin 查詢他人訂單 -> 放行")
        void admin_bypassesOwnership() {
            loginAsAdmin();
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(OTHER_USER_ID, Order.OrderStatus.CREATED)));
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            OrderPaymentStateDto dto = service.getOrderPaymentState(ORDER_ID);

            assertThat(dto.getOrderStatus()).isEqualTo("CREATED");
        }
    }

    // ========== getBookingPaymentState ==========

    @Nested
    @DisplayName("getBookingPaymentState")
    class GetBookingPaymentState {

        private Booking bookingOf(final Booking.BookingStatus status) {
            Booking booking = Booking.builder().userId(USER_ID).status(status).build();
            booking.setId(BOOKING_ID);
            return booking;
        }

        @Test
        @DisplayName("UT-PAY-STATE-006: 訂房不存在 -> E_4006")
        void bookingNotFound_throwsE4006() {
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getBookingPaymentState(BOOKING_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_4006);
        }

        @Test
        @DisplayName("UT-PAY-STATE-007: CREATED 狀態 + 無付款記錄 -> canPay=true，nextValidStates=PAID,CANCELLED")
        void created_noPayment_canPay() {
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(bookingOf(Booking.BookingStatus.CREATED)));
            when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.empty());

            OrderPaymentStateDto dto = service.getBookingPaymentState(BOOKING_ID);

            assertThat(dto.getCanPay()).isTrue();
            assertThat(dto.getNextValidStates()).isEqualTo("PAID,CANCELLED");
            assertThat(dto.getPaymentId()).isNull();
        }

        @Test
        @DisplayName("UT-PAY-STATE-008: 有付款記錄 -> dto 含 payment 資訊")
        void withPayment_returnsPaymentInfo() {
            Payment payment = Payment.builder().bookingId(BOOKING_ID).status(Payment.PaymentStatus.SUCCESS)
                    .transactionId("MOCK-2").paidAt(Instant.now()).build();
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(bookingOf(Booking.BookingStatus.PAID)));
            when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(payment));

            OrderPaymentStateDto dto = service.getBookingPaymentState(BOOKING_ID);

            assertThat(dto.getPaymentStatus()).isEqualTo("SUCCESS");
            assertThat(dto.getTransactionId()).isEqualTo("MOCK-2");
        }

        @Test
        @DisplayName("UT-PAY-STATE-009: PAID 狀態 -> canRefund=true，nextValidStates=CONFIRMED,CANCELLED")
        void paid_canRefund() {
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(bookingOf(Booking.BookingStatus.PAID)));
            when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.empty());

            OrderPaymentStateDto dto = service.getBookingPaymentState(BOOKING_ID);

            assertThat(dto.getCanRefund()).isTrue();
            assertThat(dto.getCanCancel()).isTrue();
            assertThat(dto.getNextValidStates()).isEqualTo("CONFIRMED,CANCELLED");
        }

        @Test
        @DisplayName("UT-PAY-STATE-010: COMPLETED 狀態（終態）-> nextValidStates 為空、不可付款/取消/退款")
        void completed_terminalState_noNextStates() {
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(bookingOf(Booking.BookingStatus.COMPLETED)));
            when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.empty());

            OrderPaymentStateDto dto = service.getBookingPaymentState(BOOKING_ID);

            assertThat(dto.getNextValidStates()).isEmpty();
            assertThat(dto.getCanPay()).isFalse();
            assertThat(dto.getCanCancel()).isFalse();
            assertThat(dto.getCanRefund()).isFalse();
        }
    }

    // ========== mockPaymentSuccess ==========

    @Nested
    @DisplayName("mockPaymentSuccess")
    class MockPaymentSuccess {

        @Test
        @DisplayName("UT-PAY-STATE-011: 訂單不存在 -> E_5000")
        void orderNotFound_throwsE5000() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.mockPaymentSuccess(ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_5000);
        }

        @Test
        @DisplayName("UT-PAY-STATE-012: 非本人 -> E_1007")
        void otherUser_throwsE1007() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(OTHER_USER_ID, Order.OrderStatus.CREATED)));

            assertThatThrownBy(() -> service.mockPaymentSuccess(ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_1007);
        }

        @Test
        @DisplayName("UT-PAY-STATE-013: 訂單狀態不可付款(已 PAID) -> E_5011")
        void statusCannotPay_throwsE5011() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(USER_ID, Order.OrderStatus.PAID)));

            assertThatThrownBy(() -> service.mockPaymentSuccess(ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_5011);
        }

        @Test
        @DisplayName("UT-PAY-STATE-014: 已有成功付款記錄 -> E_6003")
        void alreadyProcessed_throwsE6003() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(USER_ID, Order.OrderStatus.CREATED)));
            when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS)).thenReturn(true);

            assertThatThrownBy(() -> service.mockPaymentSuccess(ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_6003);
        }

        @Test
        @DisplayName("UT-PAY-STATE-015: 成功路徑 -> 建立 SUCCESS 付款 + Order 轉 PAID")
        void success_createsSuccessPaymentAndPaidOrder() {
            Order order = orderOf(USER_ID, Order.OrderStatus.CREATED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS)).thenReturn(false);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderPaymentStateDto dto = service.mockPaymentSuccess(ORDER_ID);

            assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.PAID);
            assertThat(dto.getPaymentStatus()).isEqualTo("SUCCESS");
            verify(orderRepository).save(order);
        }
    }

    // ========== mockPaymentFailure ==========

    @Nested
    @DisplayName("mockPaymentFailure")
    class MockPaymentFailure {

        @Test
        @DisplayName("UT-PAY-STATE-016: 訂單不存在 -> E_5000")
        void orderNotFound_throwsE5000() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.mockPaymentFailure(ORDER_ID, "insufficient funds"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_5000);
        }

        @Test
        @DisplayName("UT-PAY-STATE-017: 非本人 -> E_1007")
        void otherUser_throwsE1007() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(OTHER_USER_ID, Order.OrderStatus.CREATED)));

            assertThatThrownBy(() -> service.mockPaymentFailure(ORDER_ID, "insufficient funds"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_1007);
        }

        @Test
        @DisplayName("UT-PAY-STATE-018: 訂單狀態不可付款 -> E_5011")
        void statusCannotPay_throwsE5011() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(USER_ID, Order.OrderStatus.PAID)));

            assertThatThrownBy(() -> service.mockPaymentFailure(ORDER_ID, "insufficient funds"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_5011);
        }

        @Test
        @DisplayName("UT-PAY-STATE-019: 失敗路徑 -> 建立 FAILED 付款，Order 狀態不變（維持可重試）")
        void failure_createsFailedPaymentOrderUnchanged() {
            Order order = orderOf(USER_ID, Order.OrderStatus.CREATED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderPaymentStateDto dto = service.mockPaymentFailure(ORDER_ID, "insufficient funds");

            assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CREATED);
            assertThat(dto.getPaymentStatus()).isEqualTo("FAILED");
            verify(orderRepository, never()).save(any());
        }
    }

    // ========== refundOrderPayment（補齊 PaymentStateServiceStripeTest 未涵蓋的錯誤路徑） ==========

    @Nested
    @DisplayName("refundOrderPayment 錯誤路徑")
    class RefundOrderPaymentErrors {

        private Order paidOrder() {
            return orderOf(USER_ID, Order.OrderStatus.PAID);
        }

        @Test
        @DisplayName("UT-PAY-STATE-020: 訂單不存在 -> E_5000")
        void orderNotFound_throwsE5000() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.refundOrderPayment(ORDER_ID, null, "customer"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_5000);
        }

        @Test
        @DisplayName("UT-PAY-STATE-021: 非本人 -> E_1007")
        void otherUser_throwsE1007() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(OTHER_USER_ID, Order.OrderStatus.PAID)));

            assertThatThrownBy(() -> service.refundOrderPayment(ORDER_ID, null, "customer"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_1007);
        }

        @Test
        @DisplayName("UT-PAY-STATE-022: 訂單狀態不可退款(CREATED) -> E_5012")
        void statusCannotRefund_throwsE5012() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(USER_ID, Order.OrderStatus.CREATED)));

            assertThatThrownBy(() -> service.refundOrderPayment(ORDER_ID, null, "customer"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_5012);
        }

        @Test
        @DisplayName("UT-PAY-STATE-023: 找不到可退款的付款記錄 -> E_6000")
        void paymentNotFound_throwsE6000() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(paidOrder()));
            when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS))
                    .thenReturn(Optional.empty());
            when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.PARTIALLY_REFUNDED))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.refundOrderPayment(ORDER_ID, null, "customer"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_6000);
        }

        @Test
        @DisplayName("UT-PAY-STATE-024: STRIPE 付款缺少 payment intent -> E_6001（不改變付款狀態）")
        void missingPaymentIntent_throwsE6001() {
            Payment success = Payment.builder().orderId(ORDER_ID).paymentMethod(Payment.PaymentMethod.STRIPE)
                    .amount(BigDecimal.valueOf(1500)).currency("TWD").status(Payment.PaymentStatus.SUCCESS)
                    .build(); // 無 stripePaymentIntentId
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(paidOrder()));
            when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS))
                    .thenReturn(Optional.of(success));
            when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(true);

            assertThatThrownBy(() -> service.refundOrderPayment(ORDER_ID, null, "customer"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_6001);
            assertThat(success.getStatus()).isEqualTo(Payment.PaymentStatus.SUCCESS);
            verify(paymentGatewayFactory, never()).processRefund(any(), any(), any(), any());
        }

        @Test
        @DisplayName("UT-PAY-STATE-025: Stripe Refund 呼叫回傳失敗 -> E_6001（不改變付款狀態）")
        void stripeRefundFails_throwsE6001() {
            Payment success = Payment.builder().orderId(ORDER_ID).paymentMethod(Payment.PaymentMethod.STRIPE)
                    .amount(BigDecimal.valueOf(1500)).currency("TWD").status(Payment.PaymentStatus.SUCCESS)
                    .stripePaymentIntentId("pi_1").build();
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(paidOrder()));
            when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS))
                    .thenReturn(Optional.of(success));
            when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(true);
            when(paymentGatewayFactory.processRefund("STRIPE", "pi_1", BigDecimal.valueOf(1500), "customer"))
                    .thenReturn(PaymentGatewayRequestResponse.RefundResult.builder()
                            .success(false).errorMessage("card issuer declined refund").build());

            assertThatThrownBy(() -> service.refundOrderPayment(ORDER_ID, null, "customer"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_6001);
            assertThat(success.getStatus()).isEqualTo(Payment.PaymentStatus.SUCCESS);
            verify(paymentRepository, never()).save(any());
        }
    }

    // ========== markStripeRefunded（webhook）補齊邊界案例 ==========

    @Nested
    @DisplayName("markStripeRefunded 邊界案例")
    class MarkStripeRefundedEdgeCases {

        @Test
        @DisplayName("UT-PAY-STATE-026: paymentIntentId 為 null -> 回傳 false，不查詢")
        void nullPaymentIntentId_returnsFalse() {
            boolean result = service.markStripeRefunded(null, "re_1");

            assertThat(result).isFalse();
            verify(paymentRepository, never()).findByStripePaymentIntentId(any());
        }

        @Test
        @DisplayName("UT-PAY-STATE-027: 找不到付款記錄 -> 回傳 false")
        void paymentNotFound_returnsFalse() {
            when(paymentRepository.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.empty());

            boolean result = service.markStripeRefunded("pi_1", "re_1");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("UT-PAY-STATE-028: 已是 REFUNDED -> 冪等回傳 false，不覆蓋既有 refund id")
        void alreadyRefunded_idempotentFalse() {
            Payment refunded = Payment.builder().orderId(ORDER_ID).status(Payment.PaymentStatus.REFUNDED)
                    .stripeRefundId("re_old").build();
            when(paymentRepository.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(refunded));

            boolean result = service.markStripeRefunded("pi_1", "re_new");

            assertThat(result).isFalse();
            assertThat(refunded.getStripeRefundId()).isEqualTo("re_old");
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("UT-PAY-STATE-029: refundId 為 null -> 更新為 REFUNDED，但保留既有 stripeRefundId")
        void nullRefundId_keepsExistingRefundId() {
            Order order = orderOf(USER_ID, Order.OrderStatus.PAID);
            Payment success = Payment.builder().orderId(ORDER_ID).status(Payment.PaymentStatus.SUCCESS)
                    .stripeRefundId("re_prior").build();
            when(paymentRepository.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(success));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            boolean result = service.markStripeRefunded("pi_1", null);

            assertThat(result).isTrue();
            assertThat(success.getStatus()).isEqualTo(Payment.PaymentStatus.REFUNDED);
            assertThat(success.getStripeRefundId()).isEqualTo("re_prior");
        }

        @Test
        @DisplayName("UT-PAY-STATE-030: 訂單狀態不可退款（已是 REFUNDED）-> 付款仍更新，Order 不重複觸發")
        void orderCannotRefund_paymentStillUpdated() {
            Order order = orderOf(USER_ID, Order.OrderStatus.REFUNDED);
            Payment success = Payment.builder().orderId(ORDER_ID).status(Payment.PaymentStatus.SUCCESS).build();
            when(paymentRepository.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(success));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            boolean result = service.markStripeRefunded("pi_1", "re_1");

            assertThat(result).isTrue();
            assertThat(success.getStatus()).isEqualTo(Payment.PaymentStatus.REFUNDED);
            verify(orderRepository, never()).save(any());
        }
    }

    // ========== initiateStripeCheckout 邊界案例 ==========

    @Nested
    @DisplayName("initiateStripeCheckout 邊界案例")
    class InitiateStripeCheckoutEdgeCases {

        @Test
        @DisplayName("UT-PAY-STATE-031: 訂單不存在 -> E_5000")
        void orderNotFound_throwsE5000() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.initiateStripeCheckout(ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_5000);
        }

        @Test
        @DisplayName("UT-PAY-STATE-032: 非本人 -> E_1007")
        void otherUser_throwsE1007() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(OTHER_USER_ID, Order.OrderStatus.CREATED)));

            assertThatThrownBy(() -> service.initiateStripeCheckout(ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_1007);
        }

        @Test
        @DisplayName("UT-PAY-STATE-033: 訂單狀態不可付款 -> E_5011")
        void statusCannotPay_throwsE5011() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(USER_ID, Order.OrderStatus.PAID)));
            when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(true);

            assertThatThrownBy(() -> service.initiateStripeCheckout(ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_5011);
        }

        @Test
        @DisplayName("UT-PAY-STATE-034: 已有成功付款記錄 -> E_6003")
        void alreadyProcessed_throwsE6003() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(USER_ID, Order.OrderStatus.CREATED)));
            when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(true);
            when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS)).thenReturn(true);

            assertThatThrownBy(() -> service.initiateStripeCheckout(ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_6003);
            verify(paymentGatewayFactory, never()).createCheckoutSession(any(), any());
        }
    }

    // ========== confirmStripeCheckout 邊界案例 ==========

    @Nested
    @DisplayName("confirmStripeCheckout 邊界案例")
    class ConfirmStripeCheckoutEdgeCases {

        @Test
        @DisplayName("UT-PAY-STATE-035: 訂單不存在 -> E_5000")
        void orderNotFound_throwsE5000() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirmStripeCheckout(ORDER_ID, "cs_1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_5000);
        }

        @Test
        @DisplayName("UT-PAY-STATE-036: 非本人 -> E_1007")
        void otherUser_throwsE1007() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(OTHER_USER_ID, Order.OrderStatus.CREATED)));

            assertThatThrownBy(() -> service.confirmStripeCheckout(ORDER_ID, "cs_1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_1007);
        }

        @Test
        @DisplayName("UT-PAY-STATE-037: Session 尚未付款(unpaid) -> 不更新付款/訂單狀態")
        void notYetPaid_noUpdate() {
            Order order = orderOf(USER_ID, Order.OrderStatus.CREATED);
            Payment processing = Payment.builder().orderId(ORDER_ID).paymentMethod(Payment.PaymentMethod.STRIPE)
                    .status(Payment.PaymentStatus.PROCESSING).transactionId("cs_1").build();
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS))
                    .thenReturn(Optional.empty());
            when(paymentGatewayFactory.retrieveCheckoutSession("STRIPE", "cs_1"))
                    .thenReturn(PaymentGatewayRequestResponse.CheckoutSessionResult.builder()
                            .sessionId("cs_1").status("open").paymentStatus("unpaid").build());
            when(paymentRepository.findByTransactionId("cs_1")).thenReturn(Optional.of(processing));

            OrderPaymentStateDto dto = service.confirmStripeCheckout(ORDER_ID, "cs_1");

            assertThat(processing.getStatus()).isEqualTo(Payment.PaymentStatus.PROCESSING);
            assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CREATED);
            assertThat(dto.getPaymentStatus()).isEqualTo("PROCESSING");
        }

        @Test
        @DisplayName("UT-PAY-STATE-038: 付款記錄查無(edge case) -> 不拋例外，dto 無 payment 欄位")
        void paymentRecordMissing_noException() {
            Order order = orderOf(USER_ID, Order.OrderStatus.CREATED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS))
                    .thenReturn(Optional.empty());
            when(paymentGatewayFactory.retrieveCheckoutSession("STRIPE", "cs_1"))
                    .thenReturn(PaymentGatewayRequestResponse.CheckoutSessionResult.builder()
                            .sessionId("cs_1").status("open").paymentStatus("unpaid").build());
            when(paymentRepository.findByTransactionId("cs_1")).thenReturn(Optional.empty());

            OrderPaymentStateDto dto = service.confirmStripeCheckout(ORDER_ID, "cs_1");

            assertThat(dto.getPaymentId()).isNull();
            assertThat(dto.getOrderStatus()).isEqualTo("CREATED");
        }
    }

    // ========== markStripePaymentSucceeded 直接單元測試 ==========

    @Nested
    @DisplayName("markStripePaymentSucceeded")
    class MarkStripePaymentSucceeded {

        @Test
        @DisplayName("UT-PAY-STATE-039: 找不到付款記錄 -> 回傳 false")
        void paymentNotFound_returnsFalse() {
            when(paymentRepository.findByTransactionId("cs_1")).thenReturn(Optional.empty());

            boolean result = service.markStripePaymentSucceeded("cs_1", "pi_1");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("UT-PAY-STATE-040: 已是 SUCCESS -> 冪等回傳 false，不重複設定 paidAt")
        void alreadySuccess_idempotentFalse() {
            Instant firstPaidAt = Instant.now().minusSeconds(60);
            Payment success = Payment.builder().orderId(ORDER_ID).status(Payment.PaymentStatus.SUCCESS)
                    .transactionId("cs_1").paidAt(firstPaidAt).build();
            when(paymentRepository.findByTransactionId("cs_1")).thenReturn(Optional.of(success));

            boolean result = service.markStripePaymentSucceeded("cs_1", "pi_new");

            assertThat(result).isFalse();
            assertThat(success.getPaidAt()).isEqualTo(firstPaidAt);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("UT-PAY-STATE-041: paymentIntentId 為 null -> 不覆寫既有 stripePaymentIntentId")
        void nullPaymentIntentId_keepsExisting() {
            Order order = orderOf(USER_ID, Order.OrderStatus.CREATED);
            Payment processing = Payment.builder().orderId(ORDER_ID).status(Payment.PaymentStatus.PROCESSING)
                    .transactionId("cs_1").stripePaymentIntentId("pi_existing").build();
            when(paymentRepository.findByTransactionId("cs_1")).thenReturn(Optional.of(processing));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            boolean result = service.markStripePaymentSucceeded("cs_1", null);

            assertThat(result).isTrue();
            assertThat(processing.getStripePaymentIntentId()).isEqualTo("pi_existing");
            assertThat(processing.getStatus()).isEqualTo(Payment.PaymentStatus.SUCCESS);
        }

        @Test
        @DisplayName("UT-PAY-STATE-042: 付款無關聯訂單(orderId=null，如訂房付款) -> 不查詢訂單，仍回傳 true")
        void nullOrderId_noOrderLookup() {
            Payment processing = Payment.builder().bookingId(BOOKING_ID).status(Payment.PaymentStatus.PROCESSING)
                    .transactionId("cs_1").build();
            when(paymentRepository.findByTransactionId("cs_1")).thenReturn(Optional.of(processing));

            boolean result = service.markStripePaymentSucceeded("cs_1", "pi_1");

            assertThat(result).isTrue();
            assertThat(processing.getStatus()).isEqualTo(Payment.PaymentStatus.SUCCESS);
            verify(orderRepository, never()).findById(any());
        }

        @Test
        @DisplayName("UT-PAY-STATE-043: 訂單狀態不可付款(已 PAID) -> 付款仍更新，Order 不重複觸發")
        void orderCannotPay_paymentStillUpdated() {
            Order order = orderOf(USER_ID, Order.OrderStatus.PAID);
            Payment processing = Payment.builder().orderId(ORDER_ID).status(Payment.PaymentStatus.PROCESSING)
                    .transactionId("cs_1").build();
            when(paymentRepository.findByTransactionId("cs_1")).thenReturn(Optional.of(processing));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            boolean result = service.markStripePaymentSucceeded("cs_1", "pi_1");

            assertThat(result).isTrue();
            assertThat(processing.getStatus()).isEqualTo(Payment.PaymentStatus.SUCCESS);
            verify(orderRepository, never()).save(any());
        }
    }

    // ========== markStripePaymentFailed（先前完全零測試）==========

    @Nested
    @DisplayName("markStripePaymentFailed")
    class MarkStripePaymentFailed {

        @Test
        @DisplayName("UT-PAY-STATE-044: paymentIntentId 為 null -> 回傳 false，不查詢")
        void nullPaymentIntentId_returnsFalse() {
            boolean result = service.markStripePaymentFailed(null);

            assertThat(result).isFalse();
            verify(paymentRepository, never()).findByStripePaymentIntentId(any());
        }

        @Test
        @DisplayName("UT-PAY-STATE-045: 找不到付款記錄 -> 回傳 false")
        void paymentNotFound_returnsFalse() {
            when(paymentRepository.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.empty());

            boolean result = service.markStripePaymentFailed("pi_1");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("UT-PAY-STATE-046: 已是 SUCCESS（終態）-> 不覆寫為 FAILED，回傳 false")
        void alreadySuccess_notOverwritten() {
            Payment success = Payment.builder().orderId(ORDER_ID).status(Payment.PaymentStatus.SUCCESS)
                    .stripePaymentIntentId("pi_1").build();
            when(paymentRepository.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(success));

            boolean result = service.markStripePaymentFailed("pi_1");

            assertThat(result).isFalse();
            assertThat(success.getStatus()).isEqualTo(Payment.PaymentStatus.SUCCESS);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("UT-PAY-STATE-047: 已是 FAILED（終態）-> 冪等回傳 false")
        void alreadyFailed_idempotentFalse() {
            Payment failed = Payment.builder().orderId(ORDER_ID).status(Payment.PaymentStatus.FAILED)
                    .stripePaymentIntentId("pi_1").build();
            when(paymentRepository.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(failed));

            boolean result = service.markStripePaymentFailed("pi_1");

            assertThat(result).isFalse();
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("UT-PAY-STATE-048: PROCESSING -> FAILED（成功路徑）")
        void processing_marksFailed() {
            Payment processing = Payment.builder().orderId(ORDER_ID).status(Payment.PaymentStatus.PROCESSING)
                    .stripePaymentIntentId("pi_1").build();
            when(paymentRepository.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(processing));

            boolean result = service.markStripePaymentFailed("pi_1");

            assertThat(result).isTrue();
            assertThat(processing.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
            verify(paymentRepository).save(processing);
        }
    }
}
