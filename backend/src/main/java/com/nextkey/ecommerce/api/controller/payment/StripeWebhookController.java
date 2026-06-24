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
import com.nextkey.ecommerce.infrastructure.payment.StripeSignatureVerifierService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

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

    public StripeWebhookController(
            @Value("${stripe.webhook-secret:}") String stripeWebhookSecret,
            StripeSignatureVerifierService signatureVerifier) {
        this.stripeWebhookSecret = stripeWebhookSecret;
        this.signatureVerifier = signatureVerifier;
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

        signatureVerifier.verify(payload, stripeSignature, stripeWebhookSecret);

        try {
            return ResponseEntity.ok(ApiResponse.success("Webhook received", "OK"));
        } catch (RuntimeException e) {
            log.error("Failed to process Stripe webhook: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.E_9000, "Webhook processing failed: " + e.getMessage());
        }
    }

    /**
     * 處理 LinePay Webhook 通知
     * Phase 2-B: LinePay 整合預留
     */
    @PostMapping("/linepay")
    public ResponseEntity<ApiResponse<String>> handleLinePayWebhook(
            @RequestBody Map<String, Object> payload) {

        log.info("Received LinePay webhook: payload={}", payload);

        try {
            return ResponseEntity.ok(ApiResponse.success("Webhook received", "OK"));
        } catch (RuntimeException e) {
            log.error("Failed to process LinePay webhook: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.E_9000, "Webhook processing failed: " + e.getMessage());
        }
    }
}
