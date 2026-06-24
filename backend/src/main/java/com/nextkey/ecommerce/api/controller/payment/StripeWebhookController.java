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
 */
@Slf4j
@RestController
@RequestMapping("/v2/payments/webhook")
public class StripeWebhookController {

    /**
     * Stripe Webhook 簽章驗證密鑰
     * 從 application.yml 的 {@code stripe.webhook-secret} 注入；
     * 留空表示未啟用 signature 驗證（測試模式）。
     */
    private final String stripeWebhookSecret;

    public StripeWebhookController(
            @Value("${stripe.webhook-secret:}") String stripeWebhookSecret) {
        this.stripeWebhookSecret = stripeWebhookSecret;
    }

    /**
     * 處理 Stripe Webhook 通知
     *
     * 注意：生產環境需要驗證 Stripe Signature
     * @param payload Stripe 發送的 webhook payload
     * @param stripeSignature Stripe 簽名 header
     * @return 處理結果
     */
    @PostMapping("/stripe")
    public ResponseEntity<ApiResponse<String>> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature) {

        log.info("Received Stripe webhook: signature={}, payload={}",
                stripeSignature != null ? "present" : "missing",
                payload.length() > 100 ? payload.substring(0, 100) + "..." : payload);

        // 生產環境需要驗證 Stripe Signature（驗證邏輯在 try 外，確保 BusinessException 不被吞掉）
        if (stripeWebhookSecret != null && !stripeWebhookSecret.isEmpty()) {
            if (stripeSignature == null || stripeSignature.isEmpty()) {
                log.error("Stripe webhook missing signature - signature verification required");
                throw new BusinessException(ErrorCode.E_9001, "Missing Stripe signature");
            }
            // Phase 3: 實作完整 signature 驗證 (使用 Stripe SDK)
            log.debug("Signature verification enabled but pending Phase 3 implementation");
        } else {
            log.warn("Stripe webhook secret not configured - running in test mode");
        }

        try {
            // 解析事件類型和處理
            // 實際實作會解析 payload 並根據事件類型處理
            // 這裡僅預留介面，具體邏輯根據業務需求擴展

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
            // LinePay Webhook 處理預留
            // 實際實作根據 LinePay 文件擴展

            return ResponseEntity.ok(ApiResponse.success("Webhook received", "OK"));

        } catch (RuntimeException e) {
            log.error("Failed to process LinePay webhook: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.E_9000, "Webhook processing failed: " + e.getMessage());
        }
    }
}