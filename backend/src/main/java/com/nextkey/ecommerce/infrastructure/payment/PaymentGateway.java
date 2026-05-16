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
}