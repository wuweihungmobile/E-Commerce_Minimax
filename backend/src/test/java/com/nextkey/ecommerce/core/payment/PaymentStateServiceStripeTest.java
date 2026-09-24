package com.nextkey.ecommerce.core.payment;

import com.nextkey.ecommerce.api.dto.payment.CheckoutSessionResponse;
import com.nextkey.ecommerce.api.dto.payment.OrderPaymentStateDto;
import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.product.ProductInventoryService;
import com.nextkey.ecommerce.core.settlement.SettlementAdjustmentService;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.order.OrderStateLog;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PaymentStateService 真實金流（Stripe Checkout）單元測試（Sprint 50 US-001 / AI-2410）。
 *
 * 驗證 Phase A：
 * - initiateStripeCheckout：toggle 開啟 → 建 PROCESSING 付款 + 回 session url；toggle 關閉 → E_6002
 * - confirmStripeCheckout：paid → Payment SUCCESS + Order PAID；已 SUCCESS → 冪等（不重複 retrieve）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentStateService: Stripe Checkout（AI-2410）")
class PaymentStateServiceStripeTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private FeatureToggleService featureToggleService;
    @Mock private PaymentGatewayFactory paymentGatewayFactory;
    @Mock private SettlementAdjustmentService settlementAdjustmentService;
    @Mock private ProductInventoryService productInventoryService;
    @Mock private OrderStateLogRepository orderStateLogRepository;
    @Mock private AuditService auditService;

    private PaymentStateService service;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        service = new PaymentStateService(paymentRepository, orderRepository, bookingRepository,
                featureToggleService, paymentGatewayFactory, settlementAdjustmentService, productInventoryService,
                orderStateLogRepository, auditService);
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:3000");
        TenantContext.setCurrentUser(USER_ID);
        // 🔴 DEF-136：refundOrderPayment / markStripePaymentSucceeded 併發防護預設「佔用成功」，
        // 個別測試如需驗證衝突拒絕情境可覆寫
        when(paymentRepository.applyRefundIfUnchanged(any(), any(), any(), any())).thenReturn(1);
        when(paymentRepository.markSuccessIfNotAlready(any(), any(), any(), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Order createdOrder() {
        Order order = Order.builder()
                .userId(USER_ID)
                .status(Order.OrderStatus.CREATED)
                .totalAmount(BigDecimal.valueOf(1500))
                .currency("TWD")
                .build();
        order.setId(ORDER_ID);
        return order;
    }

    @Test
    @DisplayName("UT-PAY-STRIPE-001: initiate toggle 開啟 → 建 PROCESSING 付款 + 回 session url")
    void initiate_toggleOn_createsProcessingAndReturnsUrl() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(createdOrder()));
        when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(true);
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS)).thenReturn(false);
        when(paymentGatewayFactory.createCheckoutSession(eq("STRIPE"), any()))
                .thenReturn(PaymentGatewayRequestResponse.CheckoutSessionResult.builder()
                        .sessionId("cs_test_1").sessionUrl("https://checkout.stripe.com/c/pay/cs_test_1")
                        .paymentIntentId("pi_1").status("open").paymentStatus("unpaid").build());

        CheckoutSessionResponse resp = service.initiateStripeCheckout(ORDER_ID);

        assertThat(resp.getSessionUrl()).isEqualTo("https://checkout.stripe.com/c/pay/cs_test_1");
        assertThat(resp.getSessionId()).isEqualTo("cs_test_1");
        // 建立 PROCESSING 付款記錄（DEF-136：改用 saveAndFlush 做併發防護的原子佔位）
        verify(paymentRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(p ->
                p.getStatus() == Payment.PaymentStatus.PROCESSING
                        && p.getPaymentMethod() == Payment.PaymentMethod.STRIPE
                        && "cs_test_1".equals(p.getStripeSessionId())));
    }

    @Test
    @DisplayName("🔴 DEF-136: initiate 併發搶佔（idempotency_key 唯一索引衝突）-> 不重複寫入，"
            + "仍回傳 Stripe 冪等回傳的 session（與贏家相同）")
    void initiate_concurrentClaim_stillReturnsSameSessionWithoutDuplicateInsert() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(createdOrder()));
        when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(true);
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS)).thenReturn(false);
        when(paymentGatewayFactory.createCheckoutSession(eq("STRIPE"), any()))
                .thenReturn(PaymentGatewayRequestResponse.CheckoutSessionResult.builder()
                        .sessionId("cs_test_1").sessionUrl("https://checkout.stripe.com/c/pay/cs_test_1")
                        .paymentIntentId("pi_1").status("open").paymentStatus("unpaid").build());
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        CheckoutSessionResponse resp = service.initiateStripeCheckout(ORDER_ID);

        // Stripe 自己的 idempotency key 已保證兩邊拿回同一個 session，本地衝突不應影響回應內容
        assertThat(resp.getSessionUrl()).isEqualTo("https://checkout.stripe.com/c/pay/cs_test_1");
        assertThat(resp.getSessionId()).isEqualTo("cs_test_1");
    }

    @Test
    @DisplayName("UT-PAY-STRIPE-002: initiate toggle 關閉 → E_6002（不建 session）")
    void initiate_toggleOff_throwsE6002() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(createdOrder()));
        when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(false);

        assertThatThrownBy(() -> service.initiateStripeCheckout(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_6002));
        verify(paymentGatewayFactory, never()).createCheckoutSession(any(), any());
    }

    @Test
    @DisplayName("UT-PAY-STRIPE-003: confirm paid → Payment SUCCESS + Order PAID")
    void confirm_paid_updatesSuccessAndPaid() {
        Order order = createdOrder();
        Payment processing = Payment.builder().orderId(ORDER_ID)
                .paymentMethod(Payment.PaymentMethod.STRIPE).amount(BigDecimal.valueOf(1500)).currency("TWD")
                .status(Payment.PaymentStatus.PROCESSING).transactionId("cs_test_1").stripeSessionId("cs_test_1")
                .build();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS))
                .thenReturn(Optional.empty());
        when(paymentGatewayFactory.retrieveCheckoutSession("STRIPE", "cs_test_1"))
                .thenReturn(PaymentGatewayRequestResponse.CheckoutSessionResult.builder()
                        .sessionId("cs_test_1").paymentIntentId("pi_1").status("complete").paymentStatus("paid").build());
        when(paymentRepository.findByTransactionId("cs_test_1")).thenReturn(Optional.of(processing));

        OrderPaymentStateDto state = service.confirmStripeCheckout(ORDER_ID, "cs_test_1");

        // DEF-136：markStripePaymentSucceeded 改以原子 UPDATE（markSuccessIfNotAlready）落地狀態轉換，
        // 不再對 mock 出來的 entity 做 setter 呼叫，故驗證改為斷言該原子 UPDATE 確實被正確參數呼叫。
        verify(paymentRepository).markSuccessIfNotAlready(eq(processing.getId()),
                eq(Payment.PaymentStatus.SUCCESS), eq("pi_1"), any());
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.PAID);
        assertThat(state.getOrderStatus()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("UT-PAY-STRIPE-004: confirm 已 SUCCESS → 冪等（不再 retrieve）")
    void confirm_alreadySuccess_idempotent() {
        Order order = createdOrder();
        order.setStatus(Order.OrderStatus.PAID);
        Payment success = Payment.builder().orderId(ORDER_ID).status(Payment.PaymentStatus.SUCCESS)
                .transactionId("cs_test_1").build();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(success));

        service.confirmStripeCheckout(ORDER_ID, "cs_test_1");

        verify(paymentGatewayFactory, never()).retrieveCheckoutSession(any(), any());
    }

    private Order paidOrder() {
        Order order = Order.builder().userId(USER_ID).status(Order.OrderStatus.PAID)
                .totalAmount(BigDecimal.valueOf(1500)).currency("TWD").build();
        order.setId(ORDER_ID);
        return order;
    }

    @Test
    @DisplayName("UT-PAY-STRIPE-005: refund toggle 開 + STRIPE → 呼叫 Stripe Refund + REFUNDED + refund id")
    void refund_toggleOnStripe_realRefund() {
        Order order = paidOrder();
        Payment success = Payment.builder().orderId(ORDER_ID).paymentMethod(Payment.PaymentMethod.STRIPE)
                .amount(BigDecimal.valueOf(1500)).currency("TWD").status(Payment.PaymentStatus.SUCCESS)
                .transactionId("cs_test_1").stripePaymentIntentId("pi_1").build();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(success));
        when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(true);
        when(paymentGatewayFactory.processRefund("STRIPE", "pi_1", BigDecimal.valueOf(1500), "customer"))
                .thenReturn(PaymentGatewayRequestResponse.RefundResult.builder()
                        .success(true).refundId("re_1").status("succeeded").build());

        service.refundOrderPayment(ORDER_ID, null, "customer");

        verify(paymentGatewayFactory).processRefund("STRIPE", "pi_1", BigDecimal.valueOf(1500), "customer");
        assertThat(success.getStatus()).isEqualTo(Payment.PaymentStatus.REFUNDED);
        assertThat(success.getStripeRefundId()).isEqualTo("re_1");
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.REFUNDED);
        // Sprint 135（DEF-111）：退款驅動的 Order 狀態轉換須落地到既有 order_state_log
        org.mockito.ArgumentCaptor<OrderStateLog> logCaptor = org.mockito.ArgumentCaptor.forClass(OrderStateLog.class);
        verify(orderStateLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getToStatus()).isEqualTo("REFUNDED");
        assertThat(logCaptor.getValue().getReason()).isEqualTo("customer");
    }

    @Test
    @DisplayName("UT-PAY-STRIPE-006: refund toggle 關 → mock 退款（不呼叫 gateway）")
    void refund_toggleOff_mockRefund() {
        Order order = paidOrder();
        Payment success = Payment.builder().orderId(ORDER_ID).paymentMethod(Payment.PaymentMethod.MOCK)
                .amount(BigDecimal.valueOf(1500)).currency("TWD").status(Payment.PaymentStatus.SUCCESS)
                .transactionId("MOCK-1").build();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(success));
        when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(false);

        service.refundOrderPayment(ORDER_ID, null, "customer");

        verify(paymentGatewayFactory, never()).processRefund(any(), any(), any(), any());
        assertThat(success.getStatus()).isEqualTo(Payment.PaymentStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("UT-PAY-STRIPE-007: markStripeRefunded（webhook）→ REFUNDED + refund id + Order REFUNDED")
    void markStripeRefunded_updates() {
        Order order = paidOrder();
        Payment success = Payment.builder().orderId(ORDER_ID).paymentMethod(Payment.PaymentMethod.STRIPE)
                .status(Payment.PaymentStatus.SUCCESS).stripePaymentIntentId("pi_1").build();
        when(paymentRepository.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(success));
        when(paymentRepository.markRefundedIfNotAlready(any(), any(Payment.PaymentStatus.class), any()))
                .thenReturn(1);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        boolean updated = service.markStripeRefunded("pi_1", "re_2");

        assertThat(updated).isTrue();
        verify(paymentRepository).markRefundedIfNotAlready(any(), eq(Payment.PaymentStatus.REFUNDED), eq("re_2"));
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.REFUNDED);
        // Sprint 135（DEF-111）：webhook 驅動，changedBy=null（無使用者情境）
        org.mockito.ArgumentCaptor<OrderStateLog> logCaptor = org.mockito.ArgumentCaptor.forClass(OrderStateLog.class);
        verify(orderStateLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getToStatus()).isEqualTo("REFUNDED");
        assertThat(logCaptor.getValue().getChangedBy()).isNull();
    }

    @Test
    @DisplayName("UT-PAY-STRIPE-008: 部分退款（金額 < 剩餘）→ PARTIALLY_REFUNDED，Order 狀態不變（AI-2415）")
    void refund_partialAmount_staysPartiallyRefunded() {
        Order order = paidOrder();
        Payment success = Payment.builder().orderId(ORDER_ID).paymentMethod(Payment.PaymentMethod.STRIPE)
                .amount(BigDecimal.valueOf(1500)).currency("TWD").status(Payment.PaymentStatus.SUCCESS)
                .transactionId("cs_test_1").stripePaymentIntentId("pi_1").build();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(success));
        when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(true);
        when(paymentGatewayFactory.processRefund("STRIPE", "pi_1", BigDecimal.valueOf(500), "damaged item"))
                .thenReturn(PaymentGatewayRequestResponse.RefundResult.builder()
                        .success(true).refundId("re_partial_1").status("succeeded").build());

        service.refundOrderPayment(ORDER_ID, BigDecimal.valueOf(500), "damaged item");

        assertThat(success.getStatus()).isEqualTo(Payment.PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(success.getRefundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.PAID);
    }

    @Test
    @DisplayName("Sprint 86: refundOrderPayment 成功後呼叫 SettlementAdjustmentService.handleOrderRefund")
    void refund_success_invokesSettlementAdjustmentHook() {
        Order order = paidOrder();
        Payment success = Payment.builder().orderId(ORDER_ID).paymentMethod(Payment.PaymentMethod.STRIPE)
                .amount(BigDecimal.valueOf(1500)).currency("TWD").status(Payment.PaymentStatus.SUCCESS)
                .transactionId("cs_test_1").stripePaymentIntentId("pi_1").build();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(success));
        when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(true);
        when(paymentGatewayFactory.processRefund("STRIPE", "pi_1", BigDecimal.valueOf(500), "damaged item"))
                .thenReturn(PaymentGatewayRequestResponse.RefundResult.builder()
                        .success(true).refundId("re_partial_1").status("succeeded").build());

        service.refundOrderPayment(ORDER_ID, BigDecimal.valueOf(500), "damaged item");

        verify(settlementAdjustmentService).handleOrderRefund(
                order.getTenantId(), order.getId(), order.getCreatedAt(), BigDecimal.valueOf(500));
    }

    @Test
    @DisplayName("Sprint 86: SettlementAdjustmentService 拋例外不影響已完成的退款主流程")
    void refund_settlementHookThrows_doesNotFailRefund() {
        Order order = paidOrder();
        Payment success = Payment.builder().orderId(ORDER_ID).paymentMethod(Payment.PaymentMethod.STRIPE)
                .amount(BigDecimal.valueOf(1500)).currency("TWD").status(Payment.PaymentStatus.SUCCESS)
                .transactionId("cs_test_1").stripePaymentIntentId("pi_1").build();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(success));
        when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(true);
        when(paymentGatewayFactory.processRefund("STRIPE", "pi_1", BigDecimal.valueOf(500), "damaged item"))
                .thenReturn(PaymentGatewayRequestResponse.RefundResult.builder()
                        .success(true).refundId("re_partial_1").status("succeeded").build());
        org.mockito.Mockito.doThrow(new RuntimeException("settlement lookup failed"))
                .when(settlementAdjustmentService).handleOrderRefund(any(), any(), any(), any());

        service.refundOrderPayment(ORDER_ID, BigDecimal.valueOf(500), "damaged item");

        assertThat(success.getStatus()).isEqualTo(Payment.PaymentStatus.PARTIALLY_REFUNDED);
    }

    @Test
    @DisplayName("UT-PAY-STRIPE-009: 第二次部分退款補足全額 → 轉 REFUNDED + Order REFUNDED（AI-2415）")
    void refund_secondPartialCompletesFullAmount() {
        Order order = paidOrder();
        Payment partiallyRefunded = Payment.builder().orderId(ORDER_ID).paymentMethod(Payment.PaymentMethod.STRIPE)
                .amount(BigDecimal.valueOf(1500)).currency("TWD").status(Payment.PaymentStatus.PARTIALLY_REFUNDED)
                .refundedAmount(BigDecimal.valueOf(500))
                .transactionId("cs_test_1").stripePaymentIntentId("pi_1").build();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.PARTIALLY_REFUNDED))
                .thenReturn(Optional.of(partiallyRefunded));
        when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(true);
        when(paymentGatewayFactory.processRefund("STRIPE", "pi_1", BigDecimal.valueOf(1000), "second refund"))
                .thenReturn(PaymentGatewayRequestResponse.RefundResult.builder()
                        .success(true).refundId("re_partial_2").status("succeeded").build());

        service.refundOrderPayment(ORDER_ID, BigDecimal.valueOf(1000), "second refund");

        assertThat(partiallyRefunded.getStatus()).isEqualTo(Payment.PaymentStatus.REFUNDED);
        assertThat(partiallyRefunded.getRefundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500));
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("UT-PAY-STRIPE-010: 退款金額超過剩餘可退額度 → E_6009，不呼叫 gateway（AI-2415）")
    void refund_amountExceedsRemaining_throws() {
        Order order = paidOrder();
        Payment success = Payment.builder().orderId(ORDER_ID).paymentMethod(Payment.PaymentMethod.STRIPE)
                .amount(BigDecimal.valueOf(1500)).currency("TWD").status(Payment.PaymentStatus.SUCCESS)
                .transactionId("cs_test_1").stripePaymentIntentId("pi_1").build();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(success));

        assertThatThrownBy(() -> service.refundOrderPayment(ORDER_ID, BigDecimal.valueOf(2000), "too much"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_6009);
        verify(paymentGatewayFactory, never()).processRefund(any(), any(), any(), any());
    }

    @Test
    @DisplayName("UT-PAY-STRIPE-011: 退款金額為零或負數 → E_6009（AI-2415）")
    void refund_nonPositiveAmount_throws() {
        Order order = paidOrder();
        Payment success = Payment.builder().orderId(ORDER_ID).paymentMethod(Payment.PaymentMethod.STRIPE)
                .amount(BigDecimal.valueOf(1500)).currency("TWD").status(Payment.PaymentStatus.SUCCESS)
                .transactionId("cs_test_1").stripePaymentIntentId("pi_1").build();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(success));

        assertThatThrownBy(() -> service.refundOrderPayment(ORDER_ID, BigDecimal.ZERO, "zero"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_6009);
    }

    @Test
    @DisplayName("Sprint 192（DEF-267）: 退款金額超過 2 位小數（500.005）→ E_6009，不佔用額度、不呼叫 gateway")
    void refund_subCentAmount_throwsBeforeAnyMoneyMoves() {
        // Stripe 換算 amount × 100 後以 longValue() 截斷（500.005 → 50000 分 = 500.00），
        // 而 payments.refunded_amount 是 NUMERIC(12,2) 四捨五入（500.005 → 500.01）：
        // 同一筆退款兩邊帳差一分，且 500.005 累計到全額時狀態判斷與實際落庫值也會不一致。
        Order order = paidOrder();
        Payment success = Payment.builder().orderId(ORDER_ID).paymentMethod(Payment.PaymentMethod.STRIPE)
                .amount(BigDecimal.valueOf(1500)).currency("TWD").status(Payment.PaymentStatus.SUCCESS)
                .transactionId("cs_test_1").stripePaymentIntentId("pi_1").build();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(success));

        assertThatThrownBy(() -> service.refundOrderPayment(ORDER_ID, new BigDecimal("500.005"), "sub-cent"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_6009);
        verify(paymentRepository, never()).applyRefundIfUnchanged(any(), any(), any(), any());
        verify(paymentGatewayFactory, never()).processRefund(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Sprint 192（DEF-267）: 退款金額 500.500（僅尾端補零，數值仍為 2 位小數）→ 允許，不可誤擋")
    void refund_trailingZeroScale_isAccepted() {
        Order order = paidOrder();
        Payment success = Payment.builder().orderId(ORDER_ID).paymentMethod(Payment.PaymentMethod.STRIPE)
                .amount(BigDecimal.valueOf(1500)).currency("TWD").status(Payment.PaymentStatus.SUCCESS)
                .transactionId("cs_test_1").stripePaymentIntentId("pi_1").build();
        BigDecimal amount = new BigDecimal("500.500");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(success));
        when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(true);
        when(paymentGatewayFactory.processRefund("STRIPE", "pi_1", amount, "trailing zero"))
                .thenReturn(PaymentGatewayRequestResponse.RefundResult.builder()
                        .success(true).refundId("re_tz_1").status("succeeded").build());

        service.refundOrderPayment(ORDER_ID, amount, "trailing zero");

        assertThat(success.getStatus()).isEqualTo(Payment.PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(success.getRefundedAmount()).isEqualByComparingTo("500.50");
    }

    @Test
    @DisplayName("🔴 DEF-136: 併發搶佔（compare-and-swap 影響 0 列）-> 拒絕本次退款，絕不呼叫 Stripe")
    void refund_concurrentClaim_rejectsWithoutCallingStripe() {
        Order order = paidOrder();
        Payment success = Payment.builder().orderId(ORDER_ID).paymentMethod(Payment.PaymentMethod.STRIPE)
                .amount(BigDecimal.valueOf(1500)).currency("TWD").status(Payment.PaymentStatus.SUCCESS)
                .transactionId("cs_test_1").stripePaymentIntentId("pi_1").build();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, Payment.PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(success));
        when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(true);
        // 模擬另一個併發的退款請求已搶先改變 refundedAmount，本次 compare-and-swap 影響 0 列
        when(paymentRepository.applyRefundIfUnchanged(any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.refundOrderPayment(ORDER_ID, BigDecimal.valueOf(500), "concurrent"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_6009);

        verify(paymentGatewayFactory, never()).processRefund(any(), any(), any(), any());
        verify(settlementAdjustmentService, never()).handleOrderRefund(any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }
}
