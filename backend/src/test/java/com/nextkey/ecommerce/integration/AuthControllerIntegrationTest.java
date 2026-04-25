package com.nextkey.ecommerce.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.LoginRequest;
import com.nextkey.ecommerce.api.dto.RefreshTokenRequest;
import com.nextkey.ecommerce.api.dto.RegisterRequest;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Auth Controller 整合測試 (IT-M03-001 ~ IT-M03-009)
 *
 * 使用 SpringBootTest + MockMvc 進行完整的 HTTP 層測試
 * 測試真實的 Spring Context，但不啟動外部服務（PostgreSQL/Redis 使用 Mock）
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("IT-M03: Auth Controller 整合測試")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private UserRepository userRepository;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "SecurePass123";

    @BeforeEach
    void setUp() {
        // 預設：使用者不存在
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.findByEmailAndStatus(any(), any())).thenReturn(Optional.empty());
    }

    // ── IT-M03-001: 會員註冊-成功註冊新會員 ───────────────────────

    @Test
    @DisplayName("IT-M03-001: 成功註冊新會員，返回 201")
    void register_success_returns201() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .userType("BUYER")
                .build();

        User savedUser = User.builder()
                .email(TEST_EMAIL)
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build();
        savedUser.setId(UUID.randomUUID());

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        mockMvc.perform(post("/v2/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Registration successful"))
                .andExpect(jsonPath("$.data.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.data.userType").value("BUYER"));
    }

    // ── IT-M03-002: 會員註冊-Email已被註冊 ───────────────────────

    @Test
    @DisplayName("IT-M03-002: Email已被註冊，返回 409")
    void register_emailExists_returns409() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .userType("BUYER")
                .build();

        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);

        mockMvc.perform(post("/v2/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("E-1005"));
    }

    // ── IT-M03-003: 會員註冊-密碼格式不符 ───────────────────────

    @Test
    @DisplayName("IT-M03-003: 密碼格式不符，返回 400")
    void register_invalidPassword_returns400() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email(TEST_EMAIL)
                .password("123") // 密碼太短，不符合規範
                .userType("BUYER")
                .build();

        mockMvc.perform(post("/v2/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── IT-M03-004: 會員註冊-預設角色為BUYER ────────────────────

    @Test
    @DisplayName("IT-M03-004: 不指定 userType，預設角色為 BUYER")
    void register_noUserType_defaultsToBuyer() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .build(); // 不指定 userType

        User savedUser = User.builder()
                .email(TEST_EMAIL)
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build();
        savedUser.setId(UUID.randomUUID());

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        mockMvc.perform(post("/v2/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userType").value("BUYER"));
    }

    // ── IT-M03-005: 登入-成功登入 ────────────────────────────────

    @Test
    @DisplayName("IT-M03-005: 成功登入，返回 200 和 tokens")
    void login_success_returns200WithTokens() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .build();

        UUID userId = UUID.randomUUID();
        // 使用有效的 BCrypt hash (密碼: SecurePass123)
        String validBCryptHash = "$2a$10$ZII4dn92VpUm/By6ktDBt.w28xFw6wjxYvlUwhY2s3RkxOP4WMTAa";
        User user = User.builder()
                .email(TEST_EMAIL)
                .passwordHash(validBCryptHash)
                .fullName("Test User")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build();
        user.setId(userId);

        when(userRepository.findByEmailAndStatus(TEST_EMAIL, "ACTIVE")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        mockMvc.perform(post("/v2/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    // ── IT-M03-006: 登入-錯誤密碼 ────────────────────────────────

    @Test
    @DisplayName("IT-M03-006: 錯誤密碼，返回 401")
    void login_wrongPassword_returns401() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email(TEST_EMAIL)
                .password("WrongPassword123")
                .build();

        UUID userId = UUID.randomUUID();
        // 使用有效的 BCrypt hash (密碼: SecurePass123)
        String validBCryptHash = "$2a$10$ZII4dn92VpUm/By6ktDBt.w28xFw6wjxYvlUwhY2s3RkxOP4WMTAa";
        User user = User.builder()
                .email(TEST_EMAIL)
                .passwordHash(validBCryptHash)
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build();
        user.setId(userId);

        when(userRepository.findByEmailAndStatus(TEST_EMAIL, "ACTIVE")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/v2/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ── IT-M03-007: 登入-帳號被停用 ─────────────────────────────

    @Test
    @DisplayName("IT-M03-007: 帳號被停用，返回 403")
    void login_suspendedUser_returns403() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .build();

        // SUSPENDED 用戶不會被 findByEmailAndStatus 找到
        when(userRepository.findByEmailAndStatus(TEST_EMAIL, "ACTIVE")).thenReturn(Optional.empty());

        mockMvc.perform(post("/v2/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ── IT-M03-009: JWT Refresh流程-成功刷新 ────────────────────

    @Test
    @DisplayName("IT-M03-009: 使用有效 Refresh Token 成功刷新")
    void refresh_validToken_returns200WithNewAccessToken() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email(TEST_EMAIL)
                .passwordHash("$2a$10$encodedHash")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build();
        user.setId(userId);

        String validRefreshToken = jwtTokenService.generateRefreshToken(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(validRefreshToken)
                .build();

        mockMvc.perform(post("/v2/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }
}
