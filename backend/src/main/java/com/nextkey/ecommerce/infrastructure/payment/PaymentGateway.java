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

    /**
     * 建立 Stripe Connect Express 帳戶（Sprint 53 AI-2413 Phase D-1）。
     * 預設不支援（僅 Stripe gateway 覆寫）；Connect 為 Stripe 特化能力。
     */
    default PaymentGatewayRequestResponse.ConnectAccountResult createConnectAccount(String email) {
        throw new UnsupportedOperationException("Connect account not supported by " + getGatewayType());
    }

    /**
     * 建立 Stripe Connect account link（onboarding 導轉 URL，Sprint 53 AI-2413 Phase D-1）。
     */
    default PaymentGatewayRequestResponse.AccountLinkResult createAccountLink(
            String accountId, String refreshUrl, String returnUrl) {
        throw new UnsupportedOperationException("Connect account link not supported by " + getGatewayType());
    }

    /**
     * 查詢 Stripe Connect 帳戶最新狀態（charges/payouts/details_submitted，Sprint 53 AI-2413 Phase D-1）。
     */
    default PaymentGatewayRequestResponse.ConnectAccountResult getConnectAccountStatus(String accountId) {
        throw new UnsupportedOperationException("Connect account status not supported by " + getGatewayType());
    }

    /**
     * 將結算單審核通過後的淨額轉給賣家 Connect 帳戶（Sprint 80 AI-2416 Phase D-2）。
     * Separate charges and transfers 模式：付款時仍 100% 進平台帳戶，本方法為事後分步轉帳。
     *
     * @param destinationAccountId 賣家 Stripe Connect 帳戶 id（{@code Tenant.stripeConnectAccountId}）
     * @param amountInCents 轉帳金額（最小貨幣單位，如分）
     * @param currency 幣別（如 twd）
     * @param sourceReferenceId 供對帳追溯的來源識別碼（如 settlementStatementId）
     */
    default PaymentGatewayRequestResponse.TransferResult createTransfer(
            String destinationAccountId, long amountInCents, String currency, String sourceReferenceId) {
        throw new UnsupportedOperationException("Transfer not supported by " + getGatewayType());
    }
}