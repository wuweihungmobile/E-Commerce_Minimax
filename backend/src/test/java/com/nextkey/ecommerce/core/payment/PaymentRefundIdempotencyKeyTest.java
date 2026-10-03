package com.nextkey.ecommerce.core.payment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.core.settlement.SettlementAdjustmentService;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.OrderStateLogRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.infrastructure.payment.LinePayPaymentGateway;
import com.nextkey.ecommerce.infrastructure.payment.MockPaymentGateway;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayFactory;
import com.nextkey.ecommerce.infrastructure.payment.StripePaymentGateway;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import com.stripe.Stripe;

/**
 * Stripe 退款冪等鍵測試（Sprint 207，DEF-288）。
 *
 * <p>為什麼不 mock {@code PaymentGatewayFactory}：冪等鍵是在 factory 內組出來的，而既有的
 * {@code PaymentStateServiceStripeTest} 把 factory 整個換成 mock，所以那組測試（含
 * 「第二次部分退款」UT-PAY-STRIPE-009）從頭到尾看不到「Stripe 實際收到的鍵是什麼」。這裡讓
 * service → factory → 真實 {@code StripePaymentGateway} 全走真的，只在 HTTP 層以 WireMock 攔截，
 * 直接檢查 Stripe 收到的 {@code Idempotency-Key} 標頭。
 *
 * <p>斷言的性質：本檔驗證的是「我們送出的鍵」（可在本機驗證），不驗證 Stripe 對鍵的反應。
 * 依 Stripe 文件：同一個鍵搭配相同參數會回傳第一次的結果（不再建立新退款），搭配不同參數則回錯誤，
 * 鍵約 24 小時後失效——這部分我們沒有金鑰、未對真實 Stripe 實測。
 *
 * <p>正確的鍵要同時滿足兩件事：
 * <ul>
 *   <li>不同的邏輯退款（累計已退金額不同）→ 不同的鍵，否則第二次部分退款會被 Stripe 當成重送；</li>
 *   <li>同一筆邏輯退款的重試（Stripe 失敗、本地交易回滾後再送）→ 相同的鍵，Stripe 才能替我們去重。</li>
 * </ul>
 */
@WireMockTest
@DisplayName("Stripe 退款冪等鍵（Sprint 207 / DEF-288）")
class PaymentRefundIdempotencyKeyTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String REFUNDS_PATH = "/v1/refunds";

    private PaymentStateService service;
    private Payment payment;
    private Order order;
    private FeatureToggleService featureToggleService;

    @BeforeEach
    void setUp(final WireMockRuntimeInfo wri) {
        Stripe.overrideApiBase("http://localhost:" + wri.getHttpPort());

        StripePaymentGateway stripeGateway = new StripePaymentGateway(mock(PaymentRepository.class));
        ReflectionTestUtils.setField(stripeGateway, "stripeApiKey", "sk_test_placeholder");
        PaymentGatewayFactory factory = new PaymentGatewayFactory(
                stripeGateway, mock(LinePayPaymentGateway.class), mock(MockPaymentGateway.class));

        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        featureToggleService = mock(FeatureToggleService.class);

        service = new PaymentStateService(paymentRepository, orderRepository, mock(BookingRepository.class),
                featureToggleService, factory, mock(SettlementAdjustmentService.class),
                mock(OrderStateLogRepository.class), mock(AuditService.class), mock(PaymentStoreGuard.class));
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:3000");
        TenantContext.setCurrentUser(USER_ID);

        order = Order.builder().userId(USER_ID).status(Order.OrderStatus.PAID)
                .totalAmount(BigDecimal.valueOf(1500)).currency("TWD").build();
        order.setId(ORDER_ID);
        payment = Payment.builder().orderId(ORDER_ID).paymentMethod(Payment.PaymentMethod.STRIPE)
                .amount(BigDecimal.valueOf(1500)).currency("TWD").status(Payment.PaymentStatus.SUCCESS)
                .transactionId("cs_test_1").stripePaymentIntentId("pi_1").build();

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        // 依 payment 當下的狀態回傳，讓「第一次退款後變 PARTIALLY_REFUNDED」自然接到第二次呼叫
        when(paymentRepository.findByOrderIdAndStatus(eq(ORDER_ID), any())).thenAnswer(invocation ->
                invocation.getArgument(1) == payment.getStatus() ? Optional.of(payment) : Optional.empty());
        when(paymentRepository.applyRefundIfUnchanged(any(), any(), any(), any())).thenReturn(1);
        when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        Stripe.overrideApiBase("https://api.stripe.com");
    }

    @Test
    @DisplayName("UT-REFUND-KEY-001: 同一筆付款連續兩次「同金額」部分退款 → 送給 Stripe 的冪等鍵必須不同")
    void successivePartialRefundsOfSameAmount_useDistinctKeys() {
        stubStripeRefundSuccess();

        service.refundOrderPayment(ORDER_ID, BigDecimal.valueOf(500), "first");
        service.refundOrderPayment(ORDER_ID, BigDecimal.valueOf(500), "second");

        // 這是「只用金額當鍵」的偷懶修法也會踩到的情境：兩次都是 500，只有累計已退金額（0→500、500→1000）不同
        assertThat(sentIdempotencyKeys()).hasSize(2).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("UT-REFUND-KEY-002: 同一筆付款連續兩次「不同金額」部分退款 → 冪等鍵必須不同")
    void successivePartialRefundsOfDifferentAmounts_useDistinctKeys() {
        stubStripeRefundSuccess();

        service.refundOrderPayment(ORDER_ID, BigDecimal.valueOf(500), "first");
        service.refundOrderPayment(ORDER_ID, BigDecimal.valueOf(700), "second");

        assertThat(sentIdempotencyKeys()).hasSize(2).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("UT-REFUND-KEY-003: Stripe 失敗後重試同一筆退款 → 冪等鍵必須相同（讓 Stripe 替我們去重）")
    void retryOfSameLogicalRefund_reusesTheSameKey() {
        // 第一次 Stripe 回 400 → 本地丟例外、交易整體回滾（payment 記憶體狀態不變）；第二次成功
        stubFor(post(urlEqualTo(REFUNDS_PATH)).inScenario("refund-retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"boom\"}}"))
                .willSetStateTo("recovered"));
        stubFor(post(urlEqualTo(REFUNDS_PATH)).inScenario("refund-retry")
                .whenScenarioStateIs("recovered")
                .willReturn(refundSucceededResponse()));

        assertThatThrownBy(() -> service.refundOrderPayment(ORDER_ID, BigDecimal.valueOf(500), "attempt"))
                .isNotNull();
        service.refundOrderPayment(ORDER_ID, BigDecimal.valueOf(500), "retry");

        List<String> keys = sentIdempotencyKeys();
        assertThat(keys).hasSize(2);
        assertThat(keys.get(0)).isEqualTo(keys.get(1));
    }

    @Test
    @DisplayName("UT-REFUND-KEY-004（Sprint 226）: 自動退款（系統，沒有登入使用者）＋STRIPE_PAYMENT_ENABLED 已關閉 → "
            + "真實 Stripe gateway 仍收到恰好一次全額退款請求，帶這筆 payment_intent 與冪等鍵")
    void automaticRefund_sendsTheRefundToStripeEvenWhenTheToggleIsOff() {
        TenantContext.clear();
        order.setStatus(Order.OrderStatus.REFUNDING);
        when(featureToggleService.isFeatureEnabled("STRIPE_PAYMENT_ENABLED")).thenReturn(false);
        stubStripeRefundSuccess();

        service.refundOrderPaymentAsSystem(ORDER_ID, "Automatic refund: order cancelled");

        var sent = findAll(postRequestedFor(urlEqualTo(REFUNDS_PATH)));
        assertThat(sent).as("錢已經在 Stripe，不能只在本地標成已退款").hasSize(1);
        assertThat(sent.get(0).getBodyAsString()).contains("payment_intent=pi_1");
        assertThat(sent.get(0).getHeader("Idempotency-Key")).isEqualTo("refund-pi_1-0.00-1500.00");
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.REFUNDED);
    }

    private void stubStripeRefundSuccess() {
        stubFor(post(urlEqualTo(REFUNDS_PATH)).willReturn(refundSucceededResponse()));
    }

    private ResponseDefinitionBuilder refundSucceededResponse() {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"re_test\",\"object\":\"refund\",\"amount\":50000,\"currency\":\"twd\","
                        + "\"payment_intent\":\"pi_1\",\"status\":\"succeeded\"}");
    }

    private List<String> sentIdempotencyKeys() {
        return findAll(postRequestedFor(urlEqualTo(REFUNDS_PATH))).stream()
                .map(request -> request.getHeader("Idempotency-Key"))
                .toList();
    }
}
