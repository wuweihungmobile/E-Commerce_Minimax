package com.nextkey.ecommerce.api.controller.payment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.nextkey.ecommerce.core.payment.PaymentWebhookService;
import com.nextkey.ecommerce.infrastructure.payment.StripeSignatureVerifierService;

/**
 * DEF-262（Sprint 188）：prod 環境下 {@code stripe.webhook-secret} 留空會讓
 * {@link StripeSignatureVerifierService#verify} 靜默跳過簽章驗證，此端點又是
 * {@code permitAll()}，等同任何人皆可偽造 Stripe webhook payload。fail-fast 設計
 * 比照 DEF-251（JWT_SECRET）：寧可讓應用程式無法啟動，也不要靜默帶著這個安全缺口運行。
 */
@DisplayName("DEF-262: StripeWebhookController prod 環境 webhook-secret 缺失 fail-fast")
class StripeWebhookControllerTest {

    private final StripeSignatureVerifierService signatureVerifier =
            Mockito.mock(StripeSignatureVerifierService.class);
    private final PaymentWebhookService paymentWebhookService =
            Mockito.mock(PaymentWebhookService.class);

    @Test
    @DisplayName("🔴 DEF-262: prod profile + 空 webhook-secret → 拒絕啟動")
    void constructor_prodProfileWithBlankSecret_throwsIllegalStateException() {
        assertThatThrownBy(() -> new StripeWebhookController(
                "", "prod", signatureVerifier, paymentWebhookService))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_WEBHOOK_SECRET");
    }

    @Test
    @DisplayName("🔴 DEF-262: prod profile + null webhook-secret → 拒絕啟動")
    void constructor_prodProfileWithNullSecret_throwsIllegalStateException() {
        assertThatThrownBy(() -> new StripeWebhookController(
                null, "prod", signatureVerifier, paymentWebhookService))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_WEBHOOK_SECRET");
    }

    @Test
    @DisplayName("prod profile 混在多重 active profile 中（如 \"prod,metrics\"）仍應觸發 fail-fast")
    void constructor_prodAmongMultipleActiveProfiles_throwsIllegalStateException() {
        assertThatThrownBy(() -> new StripeWebhookController(
                "  ", "metrics,prod", signatureVerifier, paymentWebhookService))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_WEBHOOK_SECRET");
    }

    @Test
    @DisplayName("prod profile + 已設定 webhook-secret → 正常啟動")
    void constructor_prodProfileWithSecretConfigured_doesNotThrow() {
        assertThatCode(() -> new StripeWebhookController(
                "whsec_real_secret", "prod", signatureVerifier, paymentWebhookService))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("dev/test/integration-test 等非 prod profile，即使 webhook-secret 留空仍允許啟動（手動測試用途）")
    void constructor_nonProdProfileWithBlankSecret_doesNotThrow() {
        assertThatCode(() -> new StripeWebhookController(
                "", "integration-test", signatureVerifier, paymentWebhookService))
                .doesNotThrowAnyException();
        assertThatCode(() -> new StripeWebhookController(
                null, "dev", signatureVerifier, paymentWebhookService))
                .doesNotThrowAnyException();
        assertThatCode(() -> new StripeWebhookController(
                "", "", signatureVerifier, paymentWebhookService))
                .doesNotThrowAnyException();
    }
}
