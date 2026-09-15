package com.nextkey.ecommerce.api.controller.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.nextkey.ecommerce.integration.IntegrationTestConfiguration;

/**
 * Sprint 160 DEF-202：SecurityConfig.authorizeHttpRequests() 先前未把
 * {@code /v2/payments/webhook/**} 列入 permitAll，落在預設規則
 * {@code .anyRequest().authenticated()} 之下——但 Stripe 伺服器回呼永遠不會帶 JWT
 * Authorization header，故 webhook 端點在修復前實測回應恆為 401，
 * {@link com.nextkey.ecommerce.core.payment.PaymentWebhookService} 雖有完整實作與
 * 單元測試，卻透過真正的 HTTP 路徑完全不可達（僅有付款成功一種事件有 return-URL 同步
 * 備援 {@code PaymentStateService.confirmStripeCheckout}，退款/Connect KYC 同步/
 * transfer 撤銷三種事件無任何備援，完全依賴此 webhook）。
 *
 * <p>鎖住兩個行為：(1) {@code /stripe} 修復後不再是 401（真正的身份驗證交由
 * {@link com.nextkey.ecommerce.infrastructure.payment.StripeSignatureVerifierService}
 * 的 HMAC-SHA256 signature 把關，非 JWT）；(2) {@code /linepay} 刻意維持 401——
 * 該端點是零 signature 驗證機制的未串接 stub，開放將產生無防護的公開端點卻無任何
 * 業務效益，故不比照 /stripe 一併放行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@DisplayName("DEF-202: Stripe webhook 端點可達性（SecurityConfig permitAll 修復）")
class StripeWebhookReachabilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /v2/payments/webhook/stripe：無 Authorization header 也不應回 401")
    void stripeWebhook_withoutAuthHeader_notUnauthorized() throws Exception {
        // 測試環境 stripe.webhook-secret 留空（見 application.yml），簽章驗證跳過（測試模式），
        // controller 直接放行進 PaymentWebhookService，故預期 200（而非驗證修復前的 401）。
        mockMvc.perform(post("/v2/payments/webhook/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"payment_intent.succeeded\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /v2/payments/webhook/linepay：未串接 stub，維持需要驗證（刻意行為）")
    void linePayWebhook_withoutAuthHeader_staysUnauthorized() throws Exception {
        mockMvc.perform(post("/v2/payments/webhook/linepay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
