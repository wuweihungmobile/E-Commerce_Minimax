package com.nextkey.ecommerce.infrastructure.payment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.stripe.Stripe;

/**
 * StripePaymentGateway 單元測試（Phase 3 — 真實 Stripe SDK + WireMock）
 *
 * 使用 WireMock 攔截 Stripe HTTP API 呼叫，驗證 SDK 整合行為。
 *
 * 測試範圍：
 * - TC-S001: createPaymentIntent 成功路徑
 * - TC-S002: createPaymentIntent 卡片拒絕（CardException → E_6006）
 * - TC-S003: createPaymentIntent 請求包含 Idempotency-Key header
 */
@WireMockTest
@DisplayName("StripePaymentGateway 單元測試（Phase 3 WireMock）")
class StripePaymentGatewayTest {

    private StripePaymentGateway gateway;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wri) {
        Stripe.overrideApiBase("http://localhost:" + wri.getHttpPort());

        gateway = new StripePaymentGateway(mock(PaymentRepository.class));
        ReflectionTestUtils.setField(gateway, "stripeApiKey", "sk_test_placeholder");
    }

    @AfterEach
    void tearDown() {
        Stripe.overrideApiBase("https://api.stripe.com");
    }

    @Test
    @DisplayName("TC-S001: createPaymentIntent 成功 — WireMock 200 返回 PaymentIntent")
    void createPaymentIntent_success_returnsPaymentIntentResult() {
        UUID orderId = UUID.randomUUID();

        stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "pi_test_success123",
                                  "object": "payment_intent",
                                  "amount": 10000,
                                  "currency": "twd",
                                  "status": "requires_payment_method",
                                  "client_secret": "pi_test_success123_secret_abc",
                                  "metadata": {"order_id": "%s"},
                                  "livemode": false
                                }
                                """.formatted(orderId))));

        PaymentGatewayRequestResponse.PaymentIntentRequest request =
                PaymentGatewayRequestResponse.PaymentIntentRequest.builder()
                        .orderId(orderId)
                        .amount(new BigDecimal("100.00"))
                        .currency("TWD")
                        .build();

        PaymentGatewayRequestResponse.PaymentIntentResult result = gateway.createPaymentIntent(request);

        assertThat(result.getTransactionId()).isEqualTo("pi_test_success123");
        assertThat(result.getPaymentIntentId()).isEqualTo("pi_test_success123");
        assertThat(result.getClientSecret()).isEqualTo("pi_test_success123_secret_abc");
        assertThat(result.getStatus()).isEqualTo("requires_payment_method");
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.getCurrency()).isEqualTo("TWD");
    }

    @Test
    @DisplayName("TC-S002: createPaymentIntent 卡片拒絕 — WireMock 402 → BusinessException E_6006")
    void createPaymentIntent_cardDeclined_throwsPaymentCardDeclinedException() {
        UUID orderId = UUID.randomUUID();

        stubFor(post(urlEqualTo("/v1/payment_intents"))
                .willReturn(aResponse()
                        .withStatus(402)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "error": {
                                    "type": "card_error",
                                    "code": "card_declined",
                                    "message": "Your card has been declined.",
                                    "decline_code": "generic_decline"
                                  }
                                }
                                """)));

        PaymentGatewayRequestResponse.PaymentIntentRequest request =
                PaymentGatewayRequestResponse.PaymentIntentRequest.builder()
                        .orderId(orderId)
                        .amount(new BigDecimal("100.00"))
                        .currency("TWD")
                        .build();

        assertThatThrownBy(() -> gateway.createPaymentIntent(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.E_6006);
                });
    }

    @Test
    @DisplayName("TC-S003: createPaymentIntent 請求包含 Idempotency-Key header（使用 orderId）")
    void createPaymentIntent_sendsIdempotencyKeyHeaderWithOrderId() {
        UUID orderId = UUID.randomUUID();

        stubFor(post(urlEqualTo("/v1/payment_intents"))
                .withHeader("Idempotency-Key", equalTo(orderId.toString()))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "pi_idem_test456",
                                  "object": "payment_intent",
                                  "amount": 5000,
                                  "currency": "twd",
                                  "status": "requires_payment_method",
                                  "client_secret": "pi_idem_test456_secret_xyz",
                                  "metadata": {},
                                  "livemode": false
                                }
                                """)));

        PaymentGatewayRequestResponse.PaymentIntentRequest request =
                PaymentGatewayRequestResponse.PaymentIntentRequest.builder()
                        .orderId(orderId)
                        .amount(new BigDecimal("50.00"))
                        .currency("TWD")
                        .build();

        PaymentGatewayRequestResponse.PaymentIntentResult result = gateway.createPaymentIntent(request);

        assertThat(result.getPaymentIntentId()).isEqualTo("pi_idem_test456");

        verify(postRequestedFor(urlEqualTo("/v1/payment_intents"))
                .withHeader("Idempotency-Key", equalTo(orderId.toString())));
    }

    @Test
    @DisplayName("TC-S004: createCheckoutSession 成功 — WireMock 200 返回 Session（AI-2410）")
    void createCheckoutSession_success_returnsSessionResult() {
        UUID orderId = UUID.randomUUID();

        stubFor(post(urlEqualTo("/v1/checkout/sessions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "cs_test_123",
                                  "object": "checkout.session",
                                  "url": "https://checkout.stripe.com/c/pay/cs_test_123",
                                  "payment_intent": "pi_test_456",
                                  "status": "open",
                                  "payment_status": "unpaid",
                                  "amount_total": 150000,
                                  "currency": "twd",
                                  "livemode": false
                                }
                                """)));

        PaymentGatewayRequestResponse.CheckoutSessionRequest request =
                PaymentGatewayRequestResponse.CheckoutSessionRequest.builder()
                        .orderId(orderId)
                        .amount(new BigDecimal("1500.00"))
                        .currency("TWD")
                        .productName("Order " + orderId)
                        .successUrl("http://localhost:3000/orders/" + orderId + "/payment/success")
                        .cancelUrl("http://localhost:3000/orders/" + orderId + "/payment/cancel")
                        .idempotencyKey("ORDER-CHECKOUT-" + orderId)
                        .build();

        PaymentGatewayRequestResponse.CheckoutSessionResult result = gateway.createCheckoutSession(request);

        assertThat(result.getSessionId()).isEqualTo("cs_test_123");
        assertThat(result.getSessionUrl()).isEqualTo("https://checkout.stripe.com/c/pay/cs_test_123");
        assertThat(result.getPaymentIntentId()).isEqualTo("pi_test_456");
        assertThat(result.getStatus()).isEqualTo("open");
        assertThat(result.getPaymentStatus()).isEqualTo("unpaid");
    }

    @Test
    @DisplayName("TC-S005: retrieveCheckoutSession 已付款 — WireMock 200 payment_status=paid（AI-2410）")
    void retrieveCheckoutSession_paid_returnsPaid() {
        stubFor(get(urlPathEqualTo("/v1/checkout/sessions/cs_test_paid"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "cs_test_paid",
                                  "object": "checkout.session",
                                  "url": "https://checkout.stripe.com/c/pay/cs_test_paid",
                                  "payment_intent": "pi_paid_789",
                                  "status": "complete",
                                  "payment_status": "paid",
                                  "livemode": false
                                }
                                """)));

        PaymentGatewayRequestResponse.CheckoutSessionResult result =
                gateway.retrieveCheckoutSession("cs_test_paid");

        assertThat(result.getSessionId()).isEqualTo("cs_test_paid");
        assertThat(result.getPaymentStatus()).isEqualTo("paid");
        assertThat(result.getStatus()).isEqualTo("complete");
        assertThat(result.getPaymentIntentId()).isEqualTo("pi_paid_789");
    }
}
