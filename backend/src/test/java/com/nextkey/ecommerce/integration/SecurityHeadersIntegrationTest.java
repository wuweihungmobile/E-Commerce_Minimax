package com.nextkey.ecommerce.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 197：{@code SecurityConfig} 安全標頭回歸測試。
 *
 * <p>PRD 16.4.1 要求「所有 Error Response」帶 {@code X-Content-Type-Options: nosniff} 與
 * {@code X-Frame-Options: DENY}，SRD 要求 CSP。標頭由 Spring Security 的 HeaderWriterFilter 寫入，
 * 若有人日後在 {@code SecurityConfig} 加上 {@code .headers(h -> h.disable())}，或把某條回應改成
 * 在過濾鏈之外產生，這些防護會悄悄消失、且不會有任何功能測試變紅。此測試走完整過濾鏈，
 * 逐一驗證各種回應類型（200／400／401／403／CORS preflight）都帶齊標頭。
 *
 * <p>HSTS：TLS 通常由應用前方的代理終止，後端收到 http，Spring 預設的 isSecure() 恆為 false 而永遠不送。
 * 決策（Sprint 198）：同時採信 X-Forwarded-Proto——偽造只會讓 http 回應多帶一個瀏覽器會忽略的標頭。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@DisplayName("IT-SEC-HDR: SecurityConfig 安全標頭")
class SecurityHeadersIntegrationTest {

    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";

    @Autowired
    private MockMvc mockMvc;

    private static void assertHardened(ResultActions actions) throws Exception {
        actions.andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", API_CSP))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    @DisplayName("IT-SEC-HDR-01: 未登入被擋（401，authenticationEntryPoint 產生）仍帶齊安全標頭")
    void unauthenticated401_hasSecurityHeaders() throws Exception {
        assertHardened(mockMvc.perform(get("/v2/auth/me"))
                .andExpect(status().isUnauthorized()));
    }

    @Test
    @WithErpSecurity(role = "BUYER")
    @DisplayName("IT-SEC-HDR-02: 權限不足（403，accessDeniedHandler 產生）仍帶齊安全標頭")
    void forbidden403_hasSecurityHeaders() throws Exception {
        assertHardened(mockMvc.perform(get("/v2/dashboard/listings"))
                .andExpect(status().isForbidden()));
    }

    @Test
    @DisplayName("IT-SEC-HDR-03: 驗證失敗（400，GlobalExceptionHandler 產生）仍帶齊安全標頭")
    void validation400_hasSecurityHeaders() throws Exception {
        assertHardened(mockMvc.perform(post("/v2/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()));
    }

    @Test
    @DisplayName("IT-SEC-HDR-04: 成功回應（200，公開端點）帶齊安全標頭")
    void success200_hasSecurityHeaders() throws Exception {
        assertHardened(mockMvc.perform(get("/ws/info"))
                .andExpect(status().isOk()));
    }

    @Test
    @DisplayName("IT-SEC-HDR-05: CORS preflight（OPTIONS）回應帶齊安全標頭")
    void corsPreflight_hasSecurityHeaders() throws Exception {
        assertHardened(mockMvc.perform(options("/v2/auth/me")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000")));
    }

    // 只涵蓋送出此標頭的網域本身，刻意不含 includeSubDomains（見 IT-SEC-HDR-11）
    private static final String HSTS = "max-age=31536000";

    @Test
    @DisplayName("IT-SEC-HDR-06: HTTPS 請求帶 Strict-Transport-Security")
    void https_hasHsts() throws Exception {
        mockMvc.perform(get("/v2/auth/me").secure(true))
                .andExpect(header().string("Strict-Transport-Security", HSTS));
    }

    @Test
    @DisplayName("IT-SEC-HDR-07: TLS 由代理終止（http + X-Forwarded-Proto: https）仍帶 HSTS")
    void behindTlsTerminatingProxy_hasHsts() throws Exception {
        mockMvc.perform(get("/v2/auth/me").header("X-Forwarded-Proto", "https"))
                .andExpect(header().string("Strict-Transport-Security", HSTS));
    }

    @Test
    @DisplayName("IT-SEC-HDR-08: 多層代理（https,http）以第一段為準，仍帶 HSTS")
    void multiHopProxy_usesFirstHop() throws Exception {
        mockMvc.perform(get("/v2/auth/me").header("X-Forwarded-Proto", "https,http"))
                .andExpect(header().string("Strict-Transport-Security", HSTS));
    }

    @Test
    @DisplayName("IT-SEC-HDR-09: 純 http（含 X-Forwarded-Proto: http）不帶 HSTS——瀏覽器不該在明文連線上被要求記住")
    void plainHttp_hasNoHsts() throws Exception {
        mockMvc.perform(get("/v2/auth/me"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
        mockMvc.perform(get("/v2/auth/me").header("X-Forwarded-Proto", "http"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    @DisplayName("IT-SEC-HDR-10: 代理標頭的邊界寫法——不分大小寫、逗號後有空白照樣採信；第一段是 http 則不送")
    void forwardedProtoEdgeCases() throws Exception {
        mockMvc.perform(get("/v2/auth/me").header("X-Forwarded-Proto", "HTTPS"))
                .andExpect(header().string("Strict-Transport-Security", HSTS));
        mockMvc.perform(get("/v2/auth/me").header("X-Forwarded-Proto", "https, http"))
                .andExpect(header().string("Strict-Transport-Security", HSTS));
        mockMvc.perform(get("/v2/auth/me").header("X-Forwarded-Proto", "http,https"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    @DisplayName("IT-SEC-HDR-11: HSTS 不含 includeSubDomains——子網域是否都能走 https 無從驗證，而該指示一年內無法反悔")
    void hsts_doesNotCoverSubdomains() throws Exception {
        mockMvc.perform(get("/v2/auth/me").secure(true))
                .andExpect(header().string("Strict-Transport-Security", not(containsString("includeSubDomains"))));
    }
}
