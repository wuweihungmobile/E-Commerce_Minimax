package com.nextkey.ecommerce.core.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.domain.model.payment.ProcessedStripeEvent;
import com.nextkey.ecommerce.domain.repository.ProcessedStripeEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PaymentWebhookService 單元測試（Sprint 51 US-001 / AI-2411，Phase B）。
 *
 * 驗證 webhook 事件解析 + dispatch + 去重：
 * - checkout.session.completed（paid）→ markStripePaymentSucceeded
 * - payment_intent.payment_failed → markStripePaymentFailed
 * - 事件重送（已處理）→ skip（不 dispatch、不重複 save）
 * - 未知事件 → 不 dispatch，但記錄已處理
 * - checkout.session.completed 未付款 → 不標記成功
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentWebhookService: Stripe webhook 事件處理（AI-2411）")
class PaymentWebhookServiceTest {

    @Mock private ProcessedStripeEventRepository processedStripeEventRepository;
    @Mock private PaymentStateService paymentStateService;

    private PaymentWebhookService service;

    @BeforeEach
    void setUp() {
        service = new PaymentWebhookService(new ObjectMapper(), processedStripeEventRepository, paymentStateService);
    }

    @Test
    @DisplayName("UT-WH-001: checkout.session.completed（paid）→ markStripePaymentSucceeded + 記錄事件")
    void checkoutSessionCompletedPaid_marksSucceeded() {
        String payload = """
                {"id":"evt_1","type":"checkout.session.completed",
                 "data":{"object":{"id":"cs_1","payment_status":"paid","payment_intent":"pi_1"}}}
                """;
        when(processedStripeEventRepository.existsById("evt_1")).thenReturn(false);

        service.handleEvent(payload);

        verify(paymentStateService).markStripePaymentSucceeded("cs_1", "pi_1");
        verify(processedStripeEventRepository).save(any(ProcessedStripeEvent.class));
    }

    @Test
    @DisplayName("UT-WH-002: payment_intent.payment_failed → markStripePaymentFailed")
    void paymentIntentFailed_marksFailed() {
        String payload = """
                {"id":"evt_2","type":"payment_intent.payment_failed",
                 "data":{"object":{"id":"pi_2"}}}
                """;
        when(processedStripeEventRepository.existsById("evt_2")).thenReturn(false);

        service.handleEvent(payload);

        verify(paymentStateService).markStripePaymentFailed("pi_2");
        verify(processedStripeEventRepository).save(any(ProcessedStripeEvent.class));
    }

    @Test
    @DisplayName("UT-WH-003: 事件重送（已處理）→ skip（不 dispatch、不重複 save）")
    void duplicateEvent_skips() {
        String payload = """
                {"id":"evt_1","type":"checkout.session.completed",
                 "data":{"object":{"id":"cs_1","payment_status":"paid","payment_intent":"pi_1"}}}
                """;
        when(processedStripeEventRepository.existsById("evt_1")).thenReturn(true);

        service.handleEvent(payload);

        verify(paymentStateService, never()).markStripePaymentSucceeded(any(), any());
        verify(processedStripeEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-WH-004: 未知事件型別 → 不 dispatch，但記錄已處理")
    void unknownEvent_recordedNoDispatch() {
        String payload = """
                {"id":"evt_3","type":"invoice.paid","data":{"object":{"id":"in_1"}}}
                """;
        when(processedStripeEventRepository.existsById("evt_3")).thenReturn(false);

        service.handleEvent(payload);

        verify(paymentStateService, never()).markStripePaymentSucceeded(any(), any());
        verify(paymentStateService, never()).markStripePaymentFailed(any());
        verify(processedStripeEventRepository).save(any(ProcessedStripeEvent.class));
    }

    @Test
    @DisplayName("UT-WH-005: checkout.session.completed 未付款（unpaid）→ 不標記成功")
    void checkoutSessionCompletedUnpaid_noMark() {
        String payload = """
                {"id":"evt_4","type":"checkout.session.completed",
                 "data":{"object":{"id":"cs_4","payment_status":"unpaid","payment_intent":"pi_4"}}}
                """;
        when(processedStripeEventRepository.existsById("evt_4")).thenReturn(false);

        service.handleEvent(payload);

        verify(paymentStateService, never()).markStripePaymentSucceeded(any(), any());
        verify(processedStripeEventRepository).save(any(ProcessedStripeEvent.class));
    }

    @Test
    @DisplayName("UT-WH-006: charge.refunded → markStripeRefunded（pi + refund id）（AI-2412）")
    void chargeRefunded_marksRefunded() {
        String payload = """
                {"id":"evt_5","type":"charge.refunded",
                 "data":{"object":{"id":"ch_1","payment_intent":"pi_5",
                   "refunds":{"data":[{"id":"re_5"}]}}}}
                """;
        when(processedStripeEventRepository.existsById("evt_5")).thenReturn(false);

        service.handleEvent(payload);

        verify(paymentStateService).markStripeRefunded("pi_5", "re_5");
        verify(processedStripeEventRepository).save(any(ProcessedStripeEvent.class));
    }
}
