package com.nextkey.ecommerce.core.payment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.core.tenant.TenantStripeConnectService;
import com.nextkey.ecommerce.domain.model.payment.ProcessedStripeEvent;
import com.nextkey.ecommerce.domain.repository.ProcessedStripeEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Stripe webhook 事件處理（Sprint 51 AI-2411，Phase B；Sprint 53 AI-2413 Phase D-1 擴充 account.updated）。
 * 解析（已驗簽的）事件 payload → 事件 id 去重 → dispatch 至權威狀態更新（與回跳路徑共用核心）。
 *
 * 冪等雙層：(1) 事件 id 去重（processed_stripe_events）(2) 狀態轉移冪等（PaymentStateService/TenantStripeConnectService）。
 * 處理付款成功（checkout.session.completed）+ 失敗（payment_intent.payment_failed）+ 退款（charge.refunded）
 * + Connect 帳戶狀態同步（account.updated，Phase D-1）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private final ObjectMapper objectMapper;
    private final ProcessedStripeEventRepository processedStripeEventRepository;
    private final PaymentStateService paymentStateService;
    private final TenantStripeConnectService tenantStripeConnectService;

    /**
     * 處理 Stripe webhook 事件（payload 已於 controller 驗簽）。
     * 任何情況皆不拋例外（controller 回 2xx 避免 Stripe 無限重送）。
     */
    @Transactional
    public void handleEvent(String payload) {
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            log.warn("Stripe webhook: invalid payload JSON: {}", e.getMessage());
            return;
        }

        String eventId = root.path("id").asText(null);
        String eventType = root.path("type").asText(null);
        if (eventId == null || eventType == null) {
            log.warn("Stripe webhook: payload missing id/type");
            return;
        }

        // 事件去重（防 Stripe 重送重複副作用）
        if (processedStripeEventRepository.existsById(eventId)) {
            log.info("Stripe webhook: event already processed, skip: id={}, type={}", eventId, eventType);
            return;
        }

        JsonNode obj = root.path("data").path("object");
        dispatch(eventType, obj);

        processedStripeEventRepository.save(ProcessedStripeEvent.builder()
                .eventId(eventId).eventType(eventType).build());
    }

    private void dispatch(String eventType, JsonNode obj) {
        switch (eventType) {
            case "checkout.session.completed" -> {
                String sessionId = obj.path("id").asText(null);
                String paymentStatus = obj.path("payment_status").asText(null);
                String paymentIntentId = obj.path("payment_intent").asText(null);
                if ("paid".equalsIgnoreCase(paymentStatus)) {
                    paymentStateService.markStripePaymentSucceeded(sessionId, paymentIntentId);
                } else {
                    log.info("Stripe webhook: checkout.session.completed not paid: session={}, status={}",
                            sessionId, paymentStatus);
                }
            }
            case "payment_intent.payment_failed" -> {
                String paymentIntentId = obj.path("id").asText(null);
                paymentStateService.markStripePaymentFailed(paymentIntentId);
            }
            case "charge.refunded" -> {
                // charge 物件的 payment_intent 為關聯的 pi id；refunds.data[0].id 為 refund id
                String paymentIntentId = obj.path("payment_intent").asText(null);
                String refundId = obj.path("refunds").path("data").path(0).path("id").asText(null);
                paymentStateService.markStripeRefunded(paymentIntentId, refundId);
            }
            case "account.updated" -> {
                // Sprint 53（AI-2413 Phase D-1）：Connect 帳戶 KYC/啟用狀態變更，回填 tenant。
                String accountId = obj.path("id").asText(null);
                boolean chargesEnabled = obj.path("charges_enabled").asBoolean(false);
                boolean payoutsEnabled = obj.path("payouts_enabled").asBoolean(false);
                boolean detailsSubmitted = obj.path("details_submitted").asBoolean(false);
                tenantStripeConnectService.syncAccountStatusFromWebhook(
                        accountId, chargesEnabled, payoutsEnabled, detailsSubmitted);
            }
            default -> log.info("Stripe webhook: unhandled event type: {}", eventType);
        }
    }
}
