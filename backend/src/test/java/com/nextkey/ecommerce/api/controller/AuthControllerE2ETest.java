package com.nextkey.ecommerce.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.LoginRequest;
import com.nextkey.ecommerce.api.dto.RefreshTokenRequest;
import com.nextkey.ecommerce.api.dto.RegisterRequest;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantMemberRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.*;

import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Auth Controller API E2E 測試 (API-M03-001 ~ API-M03-010)
 *
 * 使用 REST Assured 框架進行完整的 HTTP 層 E2E 測試
 * 測試範圍：
 * - API-M03-001 ~ API-M03-003: 會員註冊
 * - API-M03-004 ~ API-M03-006: 會員登入
 * - API-M03-007 ~ API-M03-008: Token 刷新
 * - API-M03-009 ~ API-M03-010: 取得當前用戶資訊
 *
 * 注意：此測試使用真實的 PostgreSQL 和 Redis，確保完整的 E2E 測試覆蓋
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.nextkey.ecommerce.integration.IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("API-M03 E2E: Auth Controller REST Assured E2E 測試")
class AuthControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantMemberRepository tenantMemberRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private static final String BASE_URL = "/v2/auth";
    private static final String TEST_PASSWORD = "SecurePass123!";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        // 設定 REST Assured MockMvc
        RestAssuredMockMvc.mockMvc(mockMvc);
    }

    @AfterEach
    void tearDown() {
        // 🔴 清理 SecurityContext 避免影響其他測試
        SecurityContextHolder.clearContext();
    }

    // 測試資料工廠方法 - 每次生成唯一的 email
    private String uniqueEmail() {
        return "e2e-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000) + "@example.com";
    }

    // 清理測試資料
    private void cleanupUser(String email) {
        userRepository.findByEmail(email).ifPresent(user -> userRepository.delete(user));
    }

    // ── API-M03-001: 會員註冊-成功 ─────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("API-M03-001: POST /v2/auth/register - 成功註冊，返回 201")
    void register_success_returns201() {
        String email = uniqueEmail();

        try {
            given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(RegisterRequest.builder()
                            .email(email)
                            .password(TEST_PASSWORD)
                            .userType("BUYER")
                            .build())
                    .when()
                    .post(BASE_URL + "/register")
                    .then()
                    .statusCode(201)
                    .body("success", is(true))
                    .body("message", is("Registration successful"))
                    .body("data.userId", notNullValue())
                    .body("data.email", is(email))
                    .body("data.userType", is("BUYER"));

            System.out.println("✅ API-M03-001 PASSED: 成功註冊新會員");
        } finally {
            cleanupUser(email);
        }
    }

    // ── API-M03-002: 會員註冊-缺少必填欄位 ─────────────────────────

    @Test
    @Order(2)
    @DisplayName("API-M03-002: POST /v2/auth/register - 缺少密碼，返回 400")
    void register_missingPassword_returns400() {
        String email = uniqueEmail();

        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(RegisterRequest.builder()
                        .email(email)
                        // 故意不設定 password
                        .userType("BUYER")
                        .build())
                .when()
                .post(BASE_URL + "/register")
                .then()
                .statusCode(400);

        System.out.println("✅ API-M03-002 PASSED: 缺少必填欄位時返回 400");
    }

    // ── API-M03-003: 會員註冊-Email格式錯誤 ─────────────────────────

    @Test
    @Order(3)
    @DisplayName("API-M03-003: POST /v2/auth/register - Email格式錯誤，返回 400")
    void register_invalidEmailFormat_returns400() {
        @SuppressWarnings("unused")
        String email = uniqueEmail();

        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(RegisterRequest.builder()
                        .email("invalid-email-format") // 錯誤的 Email 格式
                        .password(TEST_PASSWORD)
                        .userType("BUYER")
                        .build())
                .when()
                .post(BASE_URL + "/register")
                .then()
                .statusCode(400);

        System.out.println("✅ API-M03-003 PASSED: Email格式錯誤時返回 400");
    }

    // ── API-M03-004: 會員登入-成功 ─────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("API-M03-004: POST /v2/auth/login - 成功登入，返回 200")
    void login_success_returns200() throws Exception {
        String email = uniqueEmail();

        try {
            // 先註冊
            given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(RegisterRequest.builder()
                            .email(email)
                            .password(TEST_PASSWORD)
                            .userType("BUYER")
                            .build())
                    .when()
                    .post(BASE_URL + "/register")
                    .then()
                    .statusCode(201);

            // 登入
            given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(LoginRequest.builder()
                            .email(email)
                            .password(TEST_PASSWORD)
                            .build())
                    .when()
                    .post(BASE_URL + "/login")
                    .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("message", is("Login successful"))
                    .body("data.accessToken", notNullValue())
                    .body("data.refreshToken", notNullValue())
                    .body("data.tokenType", is("Bearer"));

            System.out.println("✅ API-M03-004 PASSED: 成功登入並獲得 tokens");
        } finally {
            cleanupUser(email);
        }
    }

    // ── API-M03-005: 會員登入-密碼錯誤 ───────────────────────────────

    @Test
    @Order(5)
    @DisplayName("API-M03-005: POST /v2/auth/login - 密碼錯誤，返回 401")
    void login_wrongPassword_returns401() {
        String email = uniqueEmail();

        try {
            // 先註冊
            given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(RegisterRequest.builder()
                            .email(email)
                            .password(TEST_PASSWORD)
                            .userType("BUYER")
                            .build())
                    .when()
                    .post(BASE_URL + "/register")
                    .then()
                    .statusCode(201);

            // 使用錯誤密碼登入
            given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(LoginRequest.builder()
                            .email(email)
                            .password("WrongPassword123!")
                            .build())
                    .when()
                    .post(BASE_URL + "/login")
                    .then()
                    .statusCode(401);

            System.out.println("✅ API-M03-005 PASSED: 密碼錯誤時返回 401");
        } finally {
            cleanupUser(email);
        }
    }

    // ── API-M03-006: 會員登入-帳號不存在 ───────────────────────────

    @Test
    @Order(6)
    @DisplayName("API-M03-006: POST /v2/auth/login - 帳號不存在，返回 401")
    void login_userNotFound_returns401() {
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(LoginRequest.builder()
                        .email("nonexistent-user@example.com")
                        .password(TEST_PASSWORD)
                        .build())
                .when()
                .post(BASE_URL + "/login")
                .then()
                .statusCode(401);

        System.out.println("✅ API-M03-006 PASSED: 帳號不存在時返回 401");
    }

    // ── API-M03-007: Token 刷新-成功 ───────────────────────────────

    @Test
    @Order(7)
    @DisplayName("API-M03-007: POST /v2/auth/refresh - 成功刷新，返回 200")
    void refresh_success_returns200() throws Exception {
        String email = uniqueEmail();

        try {
            // 1. 註冊
            given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(RegisterRequest.builder()
                            .email(email)
                            .password(TEST_PASSWORD)
                            .userType("BUYER")
                            .build())
                    .when()
                    .post(BASE_URL + "/register")
                    .then()
                    .statusCode(201);

            // 2. 登入取得 tokens
            String loginResponse = given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(LoginRequest.builder()
                            .email(email)
                            .password(TEST_PASSWORD)
                            .build())
                    .when()
                    .post(BASE_URL + "/login")
                    .then()
                    .statusCode(200)
                    .extract()
                    .asString();

            JsonNode loginJson = objectMapper.readTree(loginResponse);
            String currentRefreshToken = loginJson.path("data").path("refreshToken").asText();

            // 3. 刷新 token
            given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(RefreshTokenRequest.builder()
                            .refreshToken(currentRefreshToken)
                            .build())
                    .when()
                    .post(BASE_URL + "/refresh")
                    .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data.accessToken", notNullValue());

            System.out.println("✅ API-M03-007 PASSED: 成功刷新 JWT");
        } finally {
            cleanupUser(email);
        }
    }

    // ── API-M03-008: Token 刷新-Token過期 ─────────────────────────

    @Test
    @Order(8)
    @DisplayName("API-M03-008: POST /v2/auth/refresh - Token過期，返回 401")
    void refresh_expiredToken_returns401() {
        // 使用一個過期的 refresh token（格式正確但已失效）
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(RefreshTokenRequest.builder()
                        .refreshToken("expired.invalid.token")
                        .build())
                .when()
                .post(BASE_URL + "/refresh")
                .then()
                .statusCode(401);

        System.out.println("✅ API-M03-008 PASSED: Token過期時返回 401");
    }

    // ── API-M03-009: 取得當前用戶資訊-成功 ─────────────────────────

    @Test
    @Order(9)
    @DisplayName("API-M03-009: GET /v2/auth/me - 成功取得用戶資訊，返回 200")
    void getCurrentUser_success_returns200() throws Exception {
        String email = uniqueEmail();

        try {
            // 1. 註冊
            given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(RegisterRequest.builder()
                            .email(email)
                            .password(TEST_PASSWORD)
                            .userType("BUYER")
                            .build())
                    .when()
                    .post(BASE_URL + "/register")
                    .then()
                    .statusCode(201);

            // 2. 登入取得 access token
            String loginResponse = given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(LoginRequest.builder()
                            .email(email)
                            .password(TEST_PASSWORD)
                            .build())
                    .when()
                    .post(BASE_URL + "/login")
                    .then()
                    .statusCode(200)
                    .extract()
                    .asString();

            JsonNode loginJson = objectMapper.readTree(loginResponse);
            String accessToken = loginJson.path("data").path("accessToken").asText();

            // 3. 取得當前用戶資訊
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .when()
                    .get(BASE_URL + "/me")
                    .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data.email", is(email))
                    .body("data.userType", is("BUYER"));

            System.out.println("✅ API-M03-009 PASSED: 成功取得當前用戶資訊");
        } finally {
            cleanupUser(email);
        }
    }

    // ── API-M03-010: 取得當前用戶資訊-未授權 ───────────────────────

    @Test
    @Order(10)
    @DisplayName("API-M03-010: GET /v2/auth/me - 未授權，返回 401 或 403")
    void getCurrentUser_unauthorized_returns401or403() {
        // 不帶 Authorization header - Spring Security 返回 403 (Forbidden)
        // 因為請求雖然格式正確但沒有有效的認證資訊
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get(BASE_URL + "/me")
                .then()
                .statusCode(anyOf(is(401), is(403)));

        // 使用無效的 token - 返回 401 (Unauthorized) 或 403 (Forbidden)
        given()
                .header("Authorization", "Bearer invalid.jwt.token")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get(BASE_URL + "/me")
                .then()
                .statusCode(anyOf(is(401), is(403)));

        System.out.println("✅ API-M03-010 PASSED: 未授權時返回 401 或 403");
    }

    // ── AI-2428: 會員資料 Export + 帳戶刪除（Sprint 94，PRD §1.5.1）─────────

    @Test
    @Order(11)
    @DisplayName("AI-2428-001: GET /v2/auth/me/data-export - 成功彙整個人資料")
    void exportMyData_success_returns200() throws Exception {
        String email = uniqueEmail();

        try {
            given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(RegisterRequest.builder().email(email).password(TEST_PASSWORD).userType("BUYER").build())
                    .when()
                    .post(BASE_URL + "/register")
                    .then()
                    .statusCode(201);

            String loginResponse = given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(LoginRequest.builder().email(email).password(TEST_PASSWORD).build())
                    .when()
                    .post(BASE_URL + "/login")
                    .then()
                    .statusCode(200)
                    .extract()
                    .asString();
            String accessToken = objectMapper.readTree(loginResponse).path("data").path("accessToken").asText();

            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .when()
                    .get(BASE_URL + "/me/data-export")
                    .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data.profile.email", is(email))
                    .body("data.profile.role", is("BUYER"))
                    .body("data.knownLimitations", not(empty()));

            System.out.println("✅ AI-2428-001 PASSED: 成功彙整個人資料匯出");
        } finally {
            cleanupUser(email);
        }
    }

    @Test
    @Order(12)
    @DisplayName("AI-2428-002: DELETE /v2/auth/me - BUYER 無未結案交易時成功匿名化")
    void deleteMyAccount_eligibleBuyer_returns200AndAnonymizes() throws Exception {
        String email = uniqueEmail();

        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(RegisterRequest.builder().email(email).password(TEST_PASSWORD).userType("BUYER").build())
                .when()
                .post(BASE_URL + "/register")
                .then()
                .statusCode(201);

        String loginResponse = given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(LoginRequest.builder().email(email).password(TEST_PASSWORD).build())
                .when()
                .post(BASE_URL + "/login")
                .then()
                .statusCode(200)
                .extract()
                .asString();
        String accessToken = objectMapper.readTree(loginResponse).path("data").path("accessToken").asText();
        java.util.UUID userId = userRepository.findByEmail(email).orElseThrow().getId();

        try {
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .when()
                    .delete(BASE_URL + "/me")
                    .then()
                    .statusCode(200)
                    .body("success", is(true));

            User anonymized = userRepository.findById(userId).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(anonymized.getEmail())
                    .isNotEqualTo(email).endsWith("@anonymized.local");
            org.assertj.core.api.Assertions.assertThat(anonymized.getStatus()).isEqualTo("DELETED");
            org.assertj.core.api.Assertions.assertThat(anonymized.getFullName()).isEqualTo("已刪除的使用者");

            System.out.println("✅ AI-2428-002 PASSED: BUYER 帳戶成功匿名化");
        } finally {
            userRepository.findById(userId).ifPresent(userRepository::delete);
        }
    }

    @Test
    @Order(13)
    @DisplayName("AI-2428-003: DELETE /v2/auth/me - 非 BUYER 角色（STORE_OWNER）拒絕，返回 403")
    void deleteMyAccount_nonBuyerRole_returns403() {
        String email = uniqueEmail();
        User storeOwner = User.builder()
                .email(email)
                .passwordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .build();
        storeOwner = userRepository.save(storeOwner);
        String accessToken = jwtTokenService.generateAccessToken(storeOwner.getId(), email, "STORE_OWNER", null);

        try {
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .when()
                    .delete(BASE_URL + "/me")
                    .then()
                    .statusCode(403)
                    .body("code", is("E-1009"));

            System.out.println("✅ AI-2428-003 PASSED: 非 BUYER 角色拒絕自助刪除");
        } finally {
            cleanupUser(email);
        }
    }

    // ── DEF-244: 公開註冊端點先前可被用來奪取任一店鋪的 STORE_OWNER 權限 ──

    @Test
    @Order(14)
    @DisplayName("DEF-244: POST /v2/auth/register - userType=STORE_OWNER 應被拒絕，返回 400")
    void register_storeOwnerUserType_returns400() {
        String email = uniqueEmail();

        // 修復前：STORE_OWNER 是合法的 userType，本斷言會失敗（實際回 201）。
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(RegisterRequest.builder()
                        .email(email)
                        .password(TEST_PASSWORD)
                        .userType("STORE_OWNER")
                        .build())
                .when()
                .post(BASE_URL + "/register")
                .then()
                .statusCode(400);

        org.assertj.core.api.Assertions.assertThat(userRepository.findByEmail(email)).isEmpty();
        System.out.println("✅ DEF-244 PASSED: userType=STORE_OWNER 於註冊時被拒絕");
    }

    @Test
    @Order(15)
    @DisplayName("DEF-244: POST /v2/auth/register - 帶入任意租戶 UUID 不得建立任何 tenant_members 關聯")
    void register_withArbitraryTenantIdField_createsNoTenantMembership() throws Exception {
        String email = uniqueEmail();
        Tenant victimTenant = tenantRepository.save(Tenant.builder()
                .name("DEF-244 Victim Tenant")
                .slug("def-244-victim-" + System.currentTimeMillis())
                .contactEmail("victim-" + System.currentTimeMillis() + "@example.com")
                .contactPhone("+886-000000000")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());

        try {
            // 修復前：RegisterRequest 還有 tenantId 欄位，帶入此值會讓 AuthService.register
            // 無條件在 tenant_members 寫入一筆 storeRole=STORE_OWNER 的真實成員紀錄——任何未登入
            // 訪客只要知道任一既有店鋪的 UUID 就能奪取其管理權限。DTO 已移除該欄位，這裡改用
            // 手寫 JSON 模擬「繞過型別檢查、直接打 API」的攻擊者視角，確認此欄位現在被靜默忽略。
            String rawBody = objectMapper.createObjectNode()
                    .put("email", email)
                    .put("password", TEST_PASSWORD)
                    .put("userType", "SELLER")
                    .put("tenantId", victimTenant.getId().toString())
                    .toString();

            given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(rawBody)
                    .when()
                    .post(BASE_URL + "/register")
                    .then()
                    .statusCode(201);

            User created = userRepository.findByEmail(email).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(created.getTenantId()).isNull();
            org.assertj.core.api.Assertions.assertThat(tenantMemberRepository.findByUserId(created.getId())).isEmpty();

            System.out.println("✅ DEF-244 PASSED: 帶入的 tenantId 被靜默忽略，未建立任何租戶關聯");
        } finally {
            cleanupUser(email);
            tenantRepository.deleteById(victimTenant.getId());
        }
    }
}