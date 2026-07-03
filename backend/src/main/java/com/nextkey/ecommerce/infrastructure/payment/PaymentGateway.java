package com.nextkey.ecommerce.infrastructure.payment;

/**
 * 支付統一介面
 * 定義所有支付方式的共同行為
 */
public interface PaymentGateway {

    /**
     * 建立支付意圖 (Payment Intent)
     */
    PaymentGatewayRequestResponse.PaymentIntentResult createPaymentIntent(PaymentGatewayRequestResponse.PaymentIntentRequest request);

    /**
     * 確認支付
     */
    PaymentGatewayRequestResponse.PaymentConfirmResult confirmPayment(PaymentGatewayRequestResponse.PaymentConfirmRequest request);

    /**
     * 處理退款
     */
    PaymentGatewayRequestResponse.RefundResult processRefund(PaymentGatewayRequestResponse.RefundRequest request);

    /**
     * 查詢支付狀態
     */
    PaymentGatewayRequestResponse.PaymentStatusResult getPaymentStatus(String transactionId);

    /**
     * 取得網關類型
     */
    String getGatewayType();

    /**
     * 建立 Checkout Session（hosted Checkout，Sprint 50 AI-2410）。
     * 預設不支援（僅 Stripe gateway 覆寫）；hosted Checkout 為 Stripe 特化能力。
     */
    default PaymentGatewayRequestResponse.CheckoutSessionResult createCheckoutSession(
            PaymentGatewayRequestResponse.CheckoutSessionRequest request) {
        throw new UnsupportedOperationException("Checkout session not supported by " + getGatewayType());
    }

    /**
     * 查詢 Checkout Session 狀態（回跳後回填用，Sprint 50 AI-2410）。
     */
    default PaymentGatewayRequestResponse.CheckoutSessionResult retrieveCheckoutSession(String sessionId) {
        throw new UnsupportedOperationException("Checkout session not supported by " + getGatewayType());
    }
}