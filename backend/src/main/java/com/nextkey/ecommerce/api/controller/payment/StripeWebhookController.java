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

    /**
     * DEF-262（Sprint 188）：{@code stripe.webhook-secret} 留空時
     * {@link StripeSignatureVerifierService#verify} 會靜默跳過簽章驗證（測試模式設計，
     * 見該類別 javadoc）。此端點在 {@code SecurityConfig} 為 {@code permitAll()}（Stripe
     * 回呼不帶 JWT），若 prod 環境忘記設定 {@code STRIPE_WEBHOOK_SECRET} 環境變數，任何人皆可
     * 未經驗證直接 POST 偽造的 Stripe event payload，偽造付款成功/退款/Connect KYC 狀態——與
     * DEF-251（JWT_SECRET 預設值）同一模式。dev/test/integration-test 環境仍允許留空，
     * 以便無真實 Stripe 帳號時可手動測試（見 StripeWebhookReachabilityTest）。
     */
    public StripeWebhookController(
            @Value("${stripe.webhook-secret:}") String stripeWebhookSecret,
            @Value("${spring.profiles.active:}") String activeProfiles,
            StripeSignatureVerifierService signatureVerifier,
            PaymentWebhookService paymentWebhookService) {
        if (isProdProfile(activeProfiles) && (stripeWebhookSecret == null || stripeWebhookSecret.isBlank())) {
            throw new IllegalStateException(
                    "STRIPE_WEBHOOK_SECRET 未設定：prod 環境下這會讓 Stripe webhook 簽章驗證被靜默跳過，"
                            + "任何人皆可偽造付款成功/退款事件。請設定環境變數 STRIPE_WEBHOOK_SECRET 後再啟動。");
        }
        this.stripeWebhookSecret = stripeWebhookSecret;
        this.signatureVerifier = signatureVerifier;
        this.paymentWebhookService = paymentWebhookService;
    }

    private static boolean isProdProfile(String activeProfiles) {
        if (activeProfiles == null) {
            return false;
        }
        for (String profile : activeProfiles.split(",")) {
            if ("prod".equalsIgnoreCase(profile.trim())) {
                return true;
            }
        }
        return false;
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
