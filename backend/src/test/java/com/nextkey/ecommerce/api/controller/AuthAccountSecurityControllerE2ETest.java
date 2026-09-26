package com.nextkey.ecommerce.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.core.auth.AccountSecurityService;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.integration.IntegrationTestConfiguration;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * Sprint 204：忘記密碼／重設密碼／Email 驗證四個端點的 HTTP 層（FRD US-M03-006／007）。
 *
 * <p>只驗證「HTTP 層的責任」：放行規則（誰不用登入）、請求驗證（格式不合不可進到 Service，尤其不可消耗連結）、
 * 錯誤碼與狀態碼、回應不揭露資訊。Service 本身的邏輯見 {@code AccountSecurityServiceTest}，
 * token 的原子性與 TTL 見 {@code AccountTokenServiceIntegrationTest}。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@DisplayName("API-M03 (Sprint 204): 忘記密碼與 Email 驗證端點")
class AuthAccountSecurityControllerE2ETest {

    private static final String BASE = "/v2/auth";
    private static final String STRONG_PASSWORD = "NewPass123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;

    @MockBean
    private AccountSecurityService accountSecurityService;

    private String createdEmail;

    @AfterEach
    void cleanUp() {
        if (createdEmail != null) {
            userRepository.findByEmail(createdEmail).ifPresent(userRepository::delete);
        }
    }

    private String json(final Map<String, String> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("forgot：不需登入；任何 Email 都回同樣的 200 與訊息，不揭露該 Email 是否已註冊")
    void forgotPassword_isPublic_andResponseNeverVaries() throws Exception {
        for (String email : new String[] {"registered@example.com", "nobody-knows@example.com"}) {
            mockMvc.perform(post(BASE + "/password/forgot").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", email))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message")
                            .value("If the email is registered, a password reset link has been sent"));
            verify(accountSecurityService).requestPasswordReset(email);
        }
    }

    @Test
    @DisplayName("forgot：Email 格式不合 → 400 E-9000，不進到 Service")
    void forgotPassword_invalidEmail_isRejectedBeforeService() throws Exception {
        mockMvc.perform(post(BASE + "/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E-9000"));

        verify(accountSecurityService, never()).requestPasswordReset(any());
    }

    @Test
    @DisplayName("reset：不需登入；新密碼合規 → 200，且把連結 token 與新密碼原樣交給 Service")
    void resetPassword_isPublic() throws Exception {
        mockMvc.perform(post(BASE + "/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", "tok-1", "newPassword", STRONG_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(accountSecurityService).resetPassword("tok-1", STRONG_PASSWORD);
    }

    @Test
    @DisplayName("AC-M03-006 邊界：新密碼太弱 → 400 E-9000，且 Service 完全沒被呼叫（連結不會被消耗，使用者可改好再送）")
    void resetPassword_weakPassword_doesNotConsumeLink() throws Exception {
        for (String weak : new String[] {"short1A", "alllowercase1", "ALLUPPERCASE1", "NoDigitsHere"}) {
            mockMvc.perform(post(BASE + "/password/reset").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("token", "tok-1", "newPassword", weak))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("E-9000"));
        }

        verify(accountSecurityService, never()).resetPassword(any(), any());
    }

    @Test
    @DisplayName("AC-M03-006-3: 連結無效 → 400 E-1011（Service 拋出的業務例外原樣對應）")
    void resetPassword_invalidLink_returnsE1011() throws Exception {
        doThrow(new BusinessException(ErrorCode.E_1011)).when(accountSecurityService)
                .resetPassword(eq("dead-token"), any());

        mockMvc.perform(post(BASE + "/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", "dead-token", "newPassword", STRONG_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("E-1011"));
    }

    @Test
    @DisplayName("verify：不需登入（連結常在另一個瀏覽器開啟）；空白 token → 400，不進到 Service")
    void verifyEmail_isPublic_andRejectsBlankToken() throws Exception {
        mockMvc.perform(post(BASE + "/email/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", "tok-2"))))
                .andExpect(status().isOk());
        verify(accountSecurityService).verifyEmail("tok-2");

        mockMvc.perform(post(BASE + "/email/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", "  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E-9000"));
        verify(accountSecurityService, never()).verifyEmail("  ");
    }

    @Test
    @DisplayName("verify/send：未登入 → 401；已登入 → 200，且以「登入者本人」的 id 呼叫 Service（不能替別人重寄）")
    void resendVerification_requiresLogin_andUsesCallersOwnId() throws Exception {
        mockMvc.perform(post(BASE + "/email/verify/send"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E-1000"));
        verify(accountSecurityService, never()).sendEmailVerification(any());

        createdEmail = "resend-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post(BASE + "/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", createdEmail, "password", "SecurePass123"))))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post(BASE + "/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", createdEmail, "password", "SecurePass123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(body).path("data").path("accessToken").asText();
        UUID userId = UUID.fromString(objectMapper.readTree(body).path("data").path("user").path("id").asText());

        mockMvc.perform(post(BASE + "/email/verify/send").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        verify(accountSecurityService).sendEmailVerification(userId);
    }
}
