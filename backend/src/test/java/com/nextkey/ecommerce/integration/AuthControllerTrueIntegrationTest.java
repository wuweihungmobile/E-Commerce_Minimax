package com.nextkey.ecommerce.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.LoginRequest;
import com.nextkey.ecommerce.api.dto.LogoutRequest;
import com.nextkey.ecommerce.api.dto.RefreshTokenRequest;
import com.nextkey.ecommerce.api.dto.RegisterRequest;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.RefreshTokenService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Auth Controller 真正整合測試 (IT-M03-TRUE-001 ~ IT-M03-TRUE-005)
 *
 * 使用真實的 PostgreSQL 和 Redis 進行完整的 HTTP 層測試
 * 測試範圍：
 * - US-M03-001: 會員註冊
 * - US-M03-002: 會員登入
 * - US-M03-003: JWT 刷新
 * - US-M03-004: 會員登出
 * - US-M03-005: 取得當前用戶資訊
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("IT-M03-TRUE: Auth Controller 真實整合測試")
class AuthControllerTrueIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    // 測試資料工廠方法 - 每次生成唯一的 email
    private String uniqueEmail() {
        return "test-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000) + "@example.com";
    }

    private static final String TEST_PASSWORD = "SecurePass123!";

    // ── IT-M03-TRUE-001: 會員註冊 ─────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("US-M03-001: 成功註冊新會員")
    void register_success() throws Exception {
        String email = uniqueEmail();
        RegisterRequest request = RegisterRequest.builder()
                .email(email)
                .password(TEST_PASSWORD)
                .userType("BUYER")
                .build();

        mockMvc.perform(post("/v2/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Registration successful"))
                .andExpect(jsonPath("$.data.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.userType").value("BUYER"))
                .andDo(result -> {
                    String response = result.getResponse().getContentAsString();
                    JsonNode jsonNode = objectMapper.readTree(response);
                    String userId = jsonNode.path("data").path("userId").asText();
                    System.out.println("✅ US-M03-001 PASSED: 用戶註冊成功, userId=" + userId);
                });

        // 清理
        userRepository.findByEmail(email).ifPresent(user -> userRepository.delete(user));
    }

    // ── IT-M03-TRUE-002: 會員登入 ─────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("US-M03-002: 成功登入並獲得 tokens")
    void login_success() throws Exception {
        String email = uniqueEmail();

        // 先註冊
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(email)
                .password(TEST_PASSWORD)
                .userType("BUYER")
                .build();

        mockMvc.perform(post("/v2/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // 登入
        LoginRequest loginRequest = LoginRequest.builder()
                .email(email)
                .password(TEST_PASSWORD)
                .build();

        ResultActions loginActions = mockMvc.perform(post("/v2/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));

        MvcResult result = loginActions.andReturn();
        String response = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(response);
        String accessToken = jsonNode.path("data").path("accessToken").asText();
        String refreshToken = jsonNode.path("data").path("refreshToken").asText();

        Assertions.assertNotNull(accessToken, "Access token should be generated");
        Assertions.assertNotNull(refreshToken, "Refresh token should be generated");
        System.out.println("✅ US-M03-002 PASSED: 用戶登入成功");

        // 清理
        userRepository.findByEmail(email).ifPresent(user -> userRepository.delete(user));
    }

    // ── IT-M03-TRUE-003: JWT 刷新 ─────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("US-M03-003: 使用 Refresh Token 刷新 JWT")
    void refreshToken_success() throws Exception {
        String email = uniqueEmail();

        // 1. 註冊
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(email)
                .password(TEST_PASSWORD)
                .userType("BUYER")
                .build();

        mockMvc.perform(post("/v2/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // 2. 登入
        LoginRequest loginRequest = LoginRequest.builder()
                .email(email)
                .password(TEST_PASSWORD)
                .build();

        MvcResult loginResult = mockMvc.perform(post("/v2/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String currentRefreshToken = loginJson.path("data").path("refreshToken").asText();

        // 3. 刷新 token
        RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
                .refreshToken(currentRefreshToken)
                .build();

        ResultActions refreshActions = mockMvc.perform(post("/v2/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        MvcResult refreshResult = refreshActions.andReturn();
        String refreshResponse = refreshResult.getResponse().getContentAsString();
        JsonNode refreshJson = objectMapper.readTree(refreshResponse);
        String newAccessToken = refreshJson.path("data").path("accessToken").asText();

        Assertions.assertNotNull(newAccessToken, "New access token should be generated");
        System.out.println("✅ US-M03-003 PASSED: JWT 刷新成功");

        // 清理
        userRepository.findByEmail(email).ifPresent(user -> userRepository.delete(user));
    }

    // ── IT-M03-TRUE-004: 會員登出 ─────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("US-M03-004: 成功登出，Refresh Token 被加入黑名單")
    void logout_success() throws Exception {
        String email = uniqueEmail();

        // 1. 註冊
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(email)
                .password(TEST_PASSWORD)
                .userType("BUYER")
                .build();

        mockMvc.perform(post("/v2/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // 2. 登入
        LoginRequest loginRequest = LoginRequest.builder()
                .email(email)
                .password(TEST_PASSWORD)
                .build();

        MvcResult loginResult = mockMvc.perform(post("/v2/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String currentAccessToken = loginJson.path("data").path("accessToken").asText();
        String currentRefreshToken = loginJson.path("data").path("refreshToken").asText();

        // 3. 登出
        LogoutRequest logoutRequest = LogoutRequest.builder()
                .refreshToken(currentRefreshToken)
                .build();

        mockMvc.perform(post("/v2/auth/logout")
                        .header("Authorization", "Bearer " + currentAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Logout successful"));

        System.out.println("✅ US-M03-004 PASSED: 用戶登出成功");

        // 清理
        userRepository.findByEmail(email).ifPresent(user -> userRepository.delete(user));
    }

    // ── IT-M03-TRUE-005: 取得當前用戶資訊 ─────────────────────────

    @Test
    @Order(5)
    @DisplayName("US-M03-005: 取得當前用戶資訊")
    void getCurrentUserInfo_success() throws Exception {
        String email = uniqueEmail();

        // 1. 註冊
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(email)
                .password(TEST_PASSWORD)
                .userType("BUYER")
                .build();

        mockMvc.perform(post("/v2/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // 2. 登入
        LoginRequest loginRequest = LoginRequest.builder()
                .email(email)
                .password(TEST_PASSWORD)
                .build();

        MvcResult loginResult = mockMvc.perform(post("/v2/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String currentAccessToken = loginJson.path("data").path("accessToken").asText();

        // 3. 取得當前用戶資訊
        mockMvc.perform(get("/v2/auth/me")
                        .header("Authorization", "Bearer " + currentAccessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.userType").value("BUYER"));

        System.out.println("✅ US-M03-005 PASSED: 取得當前用戶資訊成功");

        // 清理
        userRepository.findByEmail(email).ifPresent(user -> userRepository.delete(user));
    }
}