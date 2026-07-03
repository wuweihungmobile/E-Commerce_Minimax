package com.nextkey.ecommerce.api.controller.payment;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.core.payment.PaymentWebhookService;
import com.nextkey.ecommerce.infrastructure.payment.StripeSignatureVerifierService;

import lombok.extern.slf4j.Slf4j;

/**
 * Stripe Webhook 控制器
 * Phase 2-B: Stripe Webhook 事件處理
 *
 * Stripe Webhook 事件處理：
 * - payment_intent.succeeded: 支付成功
 * - payment_intent.payment_failed: 支付失敗
 * - charge.refunded: 退款成功
 * - refund.failed: 退款失敗
 *
 * 生產環境：需配置 STRIPE_WEBHOOK_SECRET 並啟用 signature 驗證
 * Phase 3: 使用 Stripe SDK Webhook.constructEvent() 解析事件型別
 */
@Slf4j
@RestController
@RequestMapping("/v2/payments/webhook")
public class StripeWebhookController {

    private final String stripeWebhookSecret;
    private final StripeSignatureVerifierService signatureVerifier;
    private final PaymentWebhookService paymentWebhookService;

    public StripeWebhookController(
            @Value("${stripe.webhook-secret:}") String stripeWebhookSecret,
            StripeSignatureVerifierService signatureVerifier,
            PaymentWebhookService paymentWebhookService) {
        this.stripeWebhookSecret = stripeWebhookSecret;
        this.signatureVerifier = signatureVerifier;
        this.paymentWebhookService = paymentWebhookService;
    }

    /**
     * 處理 Stripe Webhook 通知
     *
     * @param payload Stripe 發送的 webhook payload（raw body）
     * @param stripeSignature Stripe 簽名 header
     * @return 處理結果
     */
    @PostMapping("/stripe")
    public ResponseEntity<ApiResponse<String>> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature) {

        log.info("Received Stripe webhook: signature={}, payloadLength={}",
                stripeSignature != null ? "present" : "missing",
                payload.length());

        // 簽章驗證（測試模式 secret 空時跳過）；無效簽章會拋例外（4xx）
        signatureVerifier.verify(payload, stripeSignature, stripeWebhookSecret);

        // Phase B（AI-2411）：解析事件並權威更新狀態。處理失敗記錄但仍回 2xx，
        // 避免 Stripe 無限重送（事件去重 + 狀態冪等保障不重複副作用）。
        try {
            paymentWebhookService.handleEvent(payload);
        } catch (RuntimeException e) {
            log.error("Failed to process Stripe webhook event (returning 2xx to avoid retry storm): {}",
                    e.getMessage(), e);
        }
        return ResponseEntity.ok(ApiResponse.success("Webhook received", "OK"));
    }

    /**
     * 處理 LinePay Webhook 通知
     * Phase 2-B: LinePay 整合預留
     */
    @PostMapping("/linepay")
    public ResponseEntity<ApiResponse<String>> handleLinePayWebhook(
            @RequestBody Map<String, Object> payload) {

        log.info("Received LinePay webhook: payload={}", payload);
        // LinePay 整合預留（stub）；回 2xx 避免重送
        return ResponseEntity.ok(ApiResponse.success("Webhook received", "OK"));
    }
}
