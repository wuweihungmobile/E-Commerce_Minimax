package com.nextkey.ecommerce.infrastructure.payment;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 統一支付網關工廠
 * 根據支付方式選擇對應的網關
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentGatewayFactory {

    private final StripePaymentGateway stripePaymentGateway;
    private final LinePayPaymentGateway linePayPaymentGateway;
    private final MockPaymentGateway mockPaymentGateway;

    /**
     * 根據支付方式取得網關
     */
    public PaymentGateway getGateway(String paymentMethod) {
        if (paymentMethod == null) {
            return mockPaymentGateway;
        }

        return switch (paymentMethod.toUpperCase()) {
            case "STRIPE", "CREDIT_CARD" -> stripePaymentGateway;
            case "LINE_PAY" -> linePayPaymentGateway;
            default -> mockPaymentGateway;
        };
    }

    /**
     * 創建支付意圖
     */
    public PaymentGatewayRequestResponse.PaymentIntentResult createPaymentIntent(
            String paymentMethod,
            UUID orderId,
            UUID bookingId,
            BigDecimal amount,
            String currency) {

        PaymentGatewayRequestResponse.PaymentIntentRequest request =
                new PaymentGatewayRequestResponse.PaymentIntentRequest(
                        orderId, bookingId, amount, currency, paymentMethod, null,
                        generateIdempotencyKey(orderId, bookingId));

        return getGateway(paymentMethod).createPaymentIntent(request);
    }

    /**
     * 確認支付
     */
    public PaymentGatewayRequestResponse.PaymentConfirmResult confirmPayment(
            String paymentMethod,
            String transactionId,
            String paymentIntentId) {

        PaymentGatewayRequestResponse.PaymentConfirmRequest request =
                new PaymentGatewayRequestResponse.PaymentConfirmRequest(
                        transactionId, paymentIntentId, paymentMethod, null);

        return getGateway(paymentMethod).confirmPayment(request);
    }

    /**
     * 處理退款
     */
    public PaymentGatewayRequestResponse.RefundResult processRefund(
            String paymentMethod,
            String transactionId,
            BigDecimal amount,
            String reason) {

        PaymentGatewayRequestResponse.RefundRequest request =
                PaymentGatewayRequestResponse.RefundRequest.builder()
                        .transactionId(transactionId)
                        .amount(amount)
                        .reason(reason)
                        .idempotencyKey("refund-" + transactionId)
                        .build();

        return getGateway(paymentMethod).processRefund(request);
    }

    /**
     * 查詢支付狀態
     */
    public PaymentGatewayRequestResponse.PaymentStatusResult getPaymentStatus(
            String paymentMethod, String transactionId) {
        return getGateway(paymentMethod).getPaymentStatus(transactionId);
    }

    /**
     * 建立 Checkout Session（hosted Checkout，Sprint 50 AI-2410）
     */
    public PaymentGatewayRequestResponse.CheckoutSessionResult createCheckoutSession(
            String paymentMethod,
            PaymentGatewayRequestResponse.CheckoutSessionRequest request) {
        return getGateway(paymentMethod).createCheckoutSession(request);
    }

    /**
     * 查詢 Checkout Session 狀態
     */
    public PaymentGatewayRequestResponse.CheckoutSessionResult retrieveCheckoutSession(
            String paymentMethod, String sessionId) {
        return getGateway(paymentMethod).retrieveCheckoutSession(sessionId);
    }

    private String generateIdempotencyKey(UUID orderId, UUID bookingId) {
        if (orderId != null) {
            return "ORDER-" + orderId.toString();
        } else if (bookingId != null) {
            return "BOOKING-" + bookingId.toString();
        }
        return "PAY-" + UUID.randomUUID().toString();
    }
}