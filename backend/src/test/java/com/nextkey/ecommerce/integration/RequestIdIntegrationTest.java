package com.nextkey.ecommerce.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DEF-280（Sprint 198）：PRD §16.4.1 要求所有回應帶 {@code X-Request-ID}，錯誤回應的內容並須能對回同一個 ID。
 *
 * <p>這個 ID 存在的目的是讓使用者回報「我看到這個錯誤」時能直接對到後端日誌，所以最要緊的是
 * 「回應標頭」與「錯誤內容裡的 requestId」是同一個值，而且每一種產生錯誤的路徑
 * （Security 401／403、Controller 驗證 400）都成立——這些路徑分別由不同的程式碼寫出回應。
 * 走完整過濾鏈驗證，才能證明 {@code RequestIdFilter} 確實排在 Spring Security 之前。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@DisplayName("IT-REQID: X-Request-ID")
class RequestIdIntegrationTest {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mockMvc;

    private static String headerOf(final MvcResult result) {
        return result.getResponse().getHeader("X-Request-ID");
    }

    @Test
    @DisplayName("IT-REQID-01: 未登入 401（authenticationEntryPoint 產生）：標頭有值，且與內容的 requestId 相同")
    void unauthenticated401_headerMatchesBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/v2/auth/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(headerOf(result)).matches(UUID_PATTERN);
        assertThat(bodyRequestId(result)).isEqualTo(headerOf(result));
    }

    @Test
    @WithErpSecurity(role = "BUYER")
    @DisplayName("IT-REQID-02: 權限不足 403（accessDeniedHandler 產生）：內容的 requestId 等於標頭")
    void forbidden403_headerMatchesBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/v2/dashboard/listings"))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(bodyRequestId(result)).isNotBlank().isEqualTo(headerOf(result));
    }

    @Test
    @DisplayName("IT-REQID-03: 驗證失敗 400（GlobalExceptionHandler 產生）：內容的 requestId 等於標頭")
    void validation400_headerMatchesBody() throws Exception {
        MvcResult result = mockMvc.perform(post("/v2/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(bodyRequestId(result)).isNotBlank().isEqualTo(headerOf(result));
    }

    @Test
    @DisplayName("IT-REQID-04: 成功回應（200）也帶標頭")
    void success200_hasHeader() throws Exception {
        mockMvc.perform(get("/ws/info"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", matchesPattern(UUID_PATTERN)));
    }

    @Test
    @DisplayName("IT-REQID-05: 每個請求的 ID 都不同（不可殘留上一個請求的值）")
    void eachRequestGetsDistinctId() throws Exception {
        String first = headerOf(mockMvc.perform(get("/v2/auth/me")).andReturn());
        String second = headerOf(mockMvc.perform(get("/v2/auth/me")).andReturn());

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("IT-REQID-06: 呼叫端帶入的合法 ID 會沿用；不安全的（含空白）會被丟棄重新產生")
    void inboundIdIsKeptOnlyWhenSafe() throws Exception {
        mockMvc.perform(get("/v2/auth/me").header("X-Request-ID", "trace-from-frontend-42"))
                .andExpect(header().string("X-Request-ID", "trace-from-frontend-42"))
                .andExpect(jsonPath("$.requestId").value("trace-from-frontend-42"));

        MockHttpServletResponse unsafe = mockMvc.perform(get("/v2/auth/me").header("X-Request-ID", "bad value"))
                .andReturn().getResponse();
        assertThat(unsafe.getHeader("X-Request-ID")).matches(UUID_PATTERN);
    }

    @Test
    @DisplayName("IT-REQID-07: 跨源請求會暴露 X-Request-ID（否則瀏覽器端的前端讀不到）")
    void corsExposesRequestIdHeader() throws Exception {
        MockHttpServletResponse actual = mockMvc.perform(get("/v2/auth/me").header("Origin", "http://localhost:3000"))
                .andReturn().getResponse();
        assertThat(actual.getHeader("Access-Control-Expose-Headers")).contains("X-Request-ID");

        mockMvc.perform(options("/v2/auth/me")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().exists("X-Request-ID"));
    }

    private static String bodyRequestId(final MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return new ObjectMapper().readTree(body).path("requestId").asText();
    }
}
