package com.nextkey.ecommerce.core.payment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.domain.model.payment.ProcessedStripeEvent;
import com.nextkey.ecommerce.domain.repository.ProcessedStripeEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Stripe webhook 事件處理（Sprint 51 AI-2411，Phase B）。
 * 解析（已驗簽的）事件 payload → 事件 id 去重 → dispatch 至權威狀態更新（與回跳路徑共用核心）。
 *
 * 冪等雙層：(1) 事件 id 去重（processed_stripe_events）(2) 狀態轉移冪等（PaymentStateService）。
 * 本 Sprint 處理付款成功（checkout.session.completed）+ 失敗（payment_intent.payment_failed）；
 * 退款事件（charge.refunded）留 Phase C（AI-2412）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private final ObjectMapper objectMapper;
    private final ProcessedStripeEventRepository processedStripeEventRepository;
    private final PaymentStateService paymentStateService;

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
            default -> log.info("Stripe webhook: unhandled event type: {}", eventType);
        }
    }
}
