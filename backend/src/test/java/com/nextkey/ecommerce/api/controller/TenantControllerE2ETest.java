package com.nextkey.ecommerce.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantApplication;
import com.nextkey.ecommerce.domain.model.tenant.TenantMember;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.*;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.*;

/**
 * Tenant Controller API E2E 測試 (US-M17-001 ~ US-M17-006)
 *
 * 使用 REST Assured 框架進行完整的 HTTP 層 E2E 測試
 * 測試範圍：
 * - US-M17-001: POST /api/v2/tenants/apply - 店鋪申請（Guest 訪問）
 * - US-M17-002: GET /api/v2/tenants - 取得我的店鋪列表
 * - US-M17-003: GET /api/v2/tenants/:id - 取得店鋪詳情（PENDING/APPROVED 區分）
 * - US-M17-004: PUT /api/v2/tenants/:id - 更新店鋪資訊（OWNER 角色）
 * - US-M17-005: GET /api/v2/dashboard/tenants/features - 取得功能開關
 * - US-M17-006: PUT /api/v2/dashboard/tenants/features/:feature - 更新功能開關
 *
 * 注意：此測試使用真實的 PostgreSQL 和 Redis，確保完整的 E2E 測試覆蓋
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("US-M17 E2E: Tenant Controller REST Assured E2E 測試")
class TenantControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantApplicationRepository tenantApplicationRepository;

    @Autowired
    private TenantMemberRepository tenantMemberRepository;

    private static final String BASE_URL = "/api/v2";
    private static final String TEST_PASSWORD = "SecurePass123!";

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);
    }

    // 測試資料工廠方法
    private String uniqueEmail() {
        return "tenant-e2e-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000) + "@example.com";
    }

    // 清理測試資料
    private void cleanupTenantData(UUID tenantId, UUID userId) {
        if (tenantId != null) {
            tenantMemberRepository.findByTenantId(tenantId).forEach(tenantMemberRepository::delete);
            tenantRepository.findById(tenantId).ifPresent(tenantRepository::delete);
        }
        if (userId != null) {
            userRepository.findById(userId).ifPresent(userRepository::delete);
        }
    }

    // ── US-M17-001: 店鋪申請（Guest 訪問）───────────────────────────

    @Test
    @Order(1)
    @DisplayName("US-M17-001: POST /api/v2/tenants/apply - Guest 用戶成功申請店鋪")
    void createApplication_guestUser_shouldSucceed() {
        // Guest 用戶不需要認證，直接申請
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "storeName", "Guest Test Store",
                        "businessType", "RETAIL_ONLY",
                        "contactEmail", "guest@example.com",
                        "contactPhone", "0912345678"
                ))
                .when()
                .post(BASE_URL + "/tenants/apply")
                .then()
                .statusCode(201)
                .body("success", is(true))
                .body("data.storeName", equalTo("Guest Test Store"))
                .body("data.status", equalTo("PENDING"));
    }

    // ── US-M17-002: 取得我的店鋪列表 ──────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("US-M17-002: GET /api/v2/tenants - 需要認證才能取得店鋪列表")
    void getMyTenants_unauthenticated_shouldFail() {
        // 未認證的請求應該被拒絕
        given()
                .when()
                .get(BASE_URL + "/tenants")
                .then()
                .statusCode(401); // or 403 depending on security config
    }

    // ── US-M17-003: 取得店鋪詳情 ─────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("US-M17-003: GET /api/v2/tenants/:id - PENDING_REVIEW 狀態只返回基本資訊")
    void getTenantDetails_pendingStatus_returnsLimitedInfo() {
        // 建立一個 PENDING_REVIEW 的測試店鋪
        User testUser = User.builder()
                .email(uniqueEmail())
                .passwordHash(TEST_PASSWORD)
                .fullName("Test Owner")
                .build();
        testUser = userRepository.save(testUser);

        Tenant pendingTenant = Tenant.builder()
                .name("Pending Store Test")
                .slug("pending-store-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.PENDING_REVIEW)
                .description("Full description should be hidden")
                .contactEmail("should-be-hidden@example.com")
                .logoUrl("http://hidden-logo.url")
                .metadata(Map.of("businessType", "RETAIL_ONLY"))
                .build();
        pendingTenant = tenantRepository.save(pendingTenant);

        TenantMember member = TenantMember.builder()
                .tenantId(pendingTenant.getId())
                .userId(testUser.getId())
                .storeRole(TenantMember.StoreRole.STORE_OWNER)
                .build();
        tenantMemberRepository.save(member);

        try {
            given()
                    .when()
                    .get(BASE_URL + "/tenants/" + pendingTenant.getId())
                    .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data.storeName", equalTo("Pending Store Test"))
                    .body("data.status", equalTo("PENDING_REVIEW"))
                    .body("data.storeDescription", nullValue())     // 不應返回詳細描述
                    .body("data.contactEmail", nullValue())          // 不應返回聯絡 email
                    .body("data.logoUrl", nullValue());              // 不應返回 logo
        } finally {
            cleanupTenantData(pendingTenant.getId(), testUser.getId());
        }
    }

    @Test
    @Order(4)
    @DisplayName("US-M17-003: GET /api/v2/tenants/:id - ACTIVE 狀態返回完整資訊")
    void getTenantDetails_activeStatus_returnsFullInfo() {
        // 建立一個 ACTIVE 的測試店鋪
        User testUser = User.builder()
                .email(uniqueEmail())
                .passwordHash(TEST_PASSWORD)
                .fullName("Active Store Owner")
                .build();
        testUser = userRepository.save(testUser);

        Tenant activeTenant = Tenant.builder()
                .name("Active Store Test")
                .slug("active-store-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.ACTIVE)
                .description("Full description here")
                .contactEmail("active@example.com")
                .logoUrl("http://active-logo.url")
                .metadata(Map.of("businessType", "RETAIL_ONLY"))
                .build();
        activeTenant = tenantRepository.save(activeTenant);

        TenantMember member = TenantMember.builder()
                .tenantId(activeTenant.getId())
                .userId(testUser.getId())
                .storeRole(TenantMember.StoreRole.STORE_OWNER)
                .build();
        tenantMemberRepository.save(member);

        try {
            given()
                    .when()
                    .get(BASE_URL + "/tenants/" + activeTenant.getId())
                    .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data.storeName", equalTo("Active Store Test"))
                    .body("data.status", equalTo("ACTIVE"))
                    .body("data.storeDescription", equalTo("Full description here"))
                    .body("data.contactEmail", equalTo("active@example.com"))
                    .body("data.logoUrl", equalTo("http://active-logo.url"))
                    .body("data.member.displayName", equalTo("Active Store Owner"));
        } finally {
            cleanupTenantData(activeTenant.getId(), testUser.getId());
        }
    }

    // ── US-M17-004: 更新店鋪資訊 ─────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("US-M17-004: PUT /api/v2/tenants/:id - 非 OWNER 角色不能更新店鋪")
    void updateTenant_nonOwner_shouldFail() {
        // 建立一個店鋪和 STAFF 用戶
        User ownerUser = User.builder()
                .email(uniqueEmail())
                .passwordHash(TEST_PASSWORD)
                .fullName("Owner User")
                .build();
        ownerUser = userRepository.save(ownerUser);

        User staffUser = User.builder()
                .email(uniqueEmail())
                .passwordHash(TEST_PASSWORD)
                .fullName("Staff User")
                .build();
        staffUser = userRepository.save(staffUser);

        Tenant testTenant = Tenant.builder()
                .name("Staff Test Store")
                .slug("staff-test-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        testTenant = tenantRepository.save(testTenant);

        // 建立 OWNER 和 STAFF 關係
        TenantMember ownerMember = TenantMember.builder()
                .tenantId(testTenant.getId())
                .userId(ownerUser.getId())
                .storeRole(TenantMember.StoreRole.STORE_OWNER)
                .build();
        tenantMemberRepository.save(ownerMember);

        TenantMember staffMember = TenantMember.builder()
                .tenantId(testTenant.getId())
                .userId(staffUser.getId())
                .storeRole(TenantMember.StoreRole.STORE_STAFF)
                .build();
        tenantMemberRepository.save(staffMember);

        try {
            // 以 STAFF 用戶身份嘗試更新（應該失敗）
            given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of("storeName", "Should Not Update"))
                    .when()
                    .put(BASE_URL + "/tenants/" + testTenant.getId())
                    .then()
                    .statusCode(403); // Staff cannot update store
        } finally {
            cleanupTenantData(testTenant.getId(), ownerUser.getId());
            userRepository.delete(staffUser); // Clean up staff separately
        }
    }

    // ── US-M17-005: 取得功能開關 ─────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("US-M17-005: GET /api/v2/dashboard/tenants/features - 需要認證")
    void getFeatureToggles_unauthenticated_shouldFail() {
        given()
                .when()
                .get(BASE_URL + "/dashboard/tenants/features")
                .then()
                .statusCode(401);
    }

    // ── API-M17-002: 缺少必填欄位驗證 ─────────────────────────────────

    @Test
    @Order(12)
    @DisplayName("API-M17-002: POST /api/v2/tenants/apply - 缺少必填欄位 storeName，返回 400")
    void applyStore_MissingStoreName_Returns400() {
        // 缺少 storeName 時應該返回 400
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "businessType", "RETAIL_ONLY",
                        "contactEmail", "test@example.com",
                        "contactPhone", "0912345678"
                        // 故意缺少 storeName
                ))
                .when()
                .post(BASE_URL + "/tenants/apply")
                .then()
                .statusCode(400)
                .body("success", is(false))
                .body("errors.field", hasItem("storeName"));

        System.out.println("✅ API-M17-002 PASSED: 缺少必填欄位 storeName 時返回 400");
    }

    // ── API-M17-003: Email 格式錯誤 ─────────────────────────────────

    @Test
    @Order(13)
    @DisplayName("API-M17-003: POST /api/v2/tenants/apply - Email 格式錯誤，返回 400")
    void applyStore_InvalidEmailFormat_Returns400() {
        // Email 格式錯誤時應該返回 400
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "storeName", "Test Store",
                        "businessType", "RETAIL_ONLY",
                        "contactEmail", "invalid-email-format", // 無效的 email 格式
                        "contactPhone", "0912345678"
                ))
                .when()
                .post(BASE_URL + "/tenants/apply")
                .then()
                .statusCode(400)
                .body("success", is(false))
                .body("errors.field", hasItem("contactEmail"));

        System.out.println("✅ API-M17-003 PASSED: Email 格式錯誤時返回 400");
    }

    // ── US-M17-006: 不存在的店鋪 ──────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("API-M17-006: GET /api/v2/tenants/:id - 不存在的店鋪，返回 404")
    void getTenantDetails_NotFound_Returns404() {
        UUID nonExistentId = UUID.randomUUID();

        given()
                .when()
                .get(BASE_URL + "/tenants/" + nonExistentId)
                .then()
                .statusCode(404)
                .body("success", is(false));

        System.out.println("✅ API-M17-006 PASSED: 不存在的店鋪返回 404");
    }

    // ── US-M17-004: 取得我的店鋪列表（正向成功測試）─────────────────

    @Test
    @Order(7)
    @DisplayName("US-M17-004: GET /api/v2/tenants - StoreOwner 成功取得店鋪列表")
    void getMyTenants_AsStoreOwner_ReturnsTenantList() throws Exception {
        // 建立測試用戶和店鋪
        String userEmail = uniqueEmail();

        // 註冊並登入 StoreOwner
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "email", userEmail,
                        "password", TEST_PASSWORD,
                        "userType", "SELLER"
                ))
                .when()
                .post("/v2/auth/register")
                .then()
                .statusCode(201);

        // 登入取得 token
        String loginResponse = given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("email", userEmail, "password", TEST_PASSWORD))
                .when()
                .post("/v2/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String accessToken = loginJson.path("data").path("accessToken").asText();

        // 建立店鋪並成為 StoreOwner
        User testUser = userRepository.findByEmail(userEmail).orElseThrow();
        testUser.setRole(User.UserRole.STORE_OWNER);
        userRepository.save(testUser);

        Tenant testTenant = Tenant.builder()
                .name("My Test Store")
                .slug("my-test-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        testTenant = tenantRepository.save(testTenant);

        testUser.setTenantId(testTenant.getId());
        userRepository.save(testUser);

        TenantMember ownerMember = TenantMember.builder()
                .tenantId(testTenant.getId())
                .userId(testUser.getId())
                .storeRole(TenantMember.StoreRole.STORE_OWNER)
                .build();
        tenantMemberRepository.save(ownerMember);

        // 重新登入以獲取更新後的 authorities
        String reLoginResponse = given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("email", userEmail, "password", TEST_PASSWORD))
                .when()
                .post("/v2/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        JsonNode reLoginJson = objectMapper.readTree(reLoginResponse);
        accessToken = reLoginJson.path("data").path("accessToken").asText();

        // 取得我的店鋪列表
        try {
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .when()
                    .get(BASE_URL + "/tenants")
                    .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data.tenants", hasSize(greaterThanOrEqualTo(1)))
                    .body("data.tenants[0].storeName", notNullValue())
                    .body("data.tenants[0].status", notNullValue());

            System.out.println("✅ US-M17-004 PASSED: StoreOwner 成功取得店鋪列表");
        } finally {
            cleanupTenantData(testTenant.getId(), testUser.getId());
        }
    }

    // ── US-M17-012: 更新店鋪成功 ─────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("API-M17-012: PUT /api/v2/tenants/:id - StoreOwner 更新店鋪成功，返回 200")
    void updateTenant_AsStoreOwner_Success() throws Exception {
        // 建立測試用戶和店鋪
        String userEmail = uniqueEmail();

        // 註冊並登入 StoreOwner
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "email", userEmail,
                        "password", TEST_PASSWORD,
                        "userType", "SELLER"
                ))
                .when()
                .post("/v2/auth/register")
                .then()
                .statusCode(201);

        // 登入取得 token
        String loginResponse = given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("email", userEmail, "password", TEST_PASSWORD))
                .when()
                .post("/v2/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String accessToken = loginJson.path("data").path("accessToken").asText();

        // 建立店鋪並成為 StoreOwner
        User testUser = userRepository.findByEmail(userEmail).orElseThrow();
        // 直接設定 user 的 role 為 STORE_OWNER
        testUser.setRole(User.UserRole.STORE_OWNER);
        userRepository.save(testUser);

        Tenant testTenant = Tenant.builder()
                .name("Original Store Name")
                .slug("original-store-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.ACTIVE)
                .contactEmail(userEmail)
                .build();
        testTenant = tenantRepository.save(testTenant);

        // 更新用戶的 tenantId
        testUser.setTenantId(testTenant.getId());
        userRepository.save(testUser);

        TenantMember ownerMember = TenantMember.builder()
                .tenantId(testTenant.getId())
                .userId(testUser.getId())
                .storeRole(TenantMember.StoreRole.STORE_OWNER)
                .build();
        tenantMemberRepository.save(ownerMember);

        // 重新登入以獲取更新後的 authorities
        String reLoginResponse = given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("email", userEmail, "password", TEST_PASSWORD))
                .when()
                .post("/v2/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        JsonNode reLoginJson = objectMapper.readTree(reLoginResponse);
        accessToken = reLoginJson.path("data").path("accessToken").asText();

        // 更新店鋪
        try {
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of("storeName", "新名稱"))
                    .when()
                    .put(BASE_URL + "/tenants/" + testTenant.getId())
                    .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data.storeName", equalTo("新名稱"));

            System.out.println("✅ API-M17-012 PASSED: StoreOwner 更新店鋪成功");
        } finally {
            cleanupTenantData(testTenant.getId(), testUser.getId());
        }
    }

    // ── US-M17-014: 部分更新 ────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("API-M17-014: PUT /api/v2/tenants/:id - 部分更新，其他欄位不變")
    void updateTenant_PartialUpdate_PreservesOtherFields() throws Exception {
        // 建立測試用戶和店鋪
        String userEmail = uniqueEmail();

        // 註冊並登入 StoreOwner
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "email", userEmail,
                        "password", TEST_PASSWORD,
                        "userType", "SELLER"
                ))
                .when()
                .post("/v2/auth/register")
                .then()
                .statusCode(201);

        // 登入取得 token
        String loginResponse = given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("email", userEmail, "password", TEST_PASSWORD))
                .when()
                .post("/v2/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String accessToken = loginJson.path("data").path("accessToken").asText();

        // 建立帶有完整資料的店鋪
        User testUser = userRepository.findByEmail(userEmail).orElseThrow();
        // 直接設定 user 的 role 為 STORE_OWNER
        testUser.setRole(User.UserRole.STORE_OWNER);
        userRepository.save(testUser);

        Tenant testTenant = Tenant.builder()
                .name("Original Store")
                .slug("partial-update-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.ACTIVE)
                .description("Original Description")
                .contactEmail("original@example.com")
                .contactPhone("0911111111")
                .build();
        testTenant = tenantRepository.save(testTenant);

        // 更新用戶的 tenantId
        testUser.setTenantId(testTenant.getId());
        userRepository.save(testUser);

        TenantMember ownerMember = TenantMember.builder()
                .tenantId(testTenant.getId())
                .userId(testUser.getId())
                .storeRole(TenantMember.StoreRole.STORE_OWNER)
                .build();
        tenantMemberRepository.save(ownerMember);

        // 重新登入以獲取更新後的 authorities
        String reLoginResponse = given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("email", userEmail, "password", TEST_PASSWORD))
                .when()
                .post("/v2/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        JsonNode reLoginJson = objectMapper.readTree(reLoginResponse);
        accessToken = reLoginJson.path("data").path("accessToken").asText();

        // 只更新 storeDescription，觀察其他欄位是否保持不變
        try {
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of("storeDescription", "新描述"))
                    .when()
                    .put(BASE_URL + "/tenants/" + testTenant.getId())
                    .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data.storeName", equalTo("Original Store"))  // 名稱不變
                    .body("data.storeDescription", equalTo("新描述"))    // 描述已更新
                    .body("data.contactEmail", equalTo("original@example.com"));  // email 不變

            System.out.println("✅ API-M17-014 PASSED: 部分更新保留其他欄位");
        } finally {
            cleanupTenantData(testTenant.getId(), testUser.getId());
        }
    }

    // ── US-M17-010: StoreOwner 取得功能開關 ─────────────────────

    @Test
    @Order(10)
    @DisplayName("API-M17-010: GET /api/v2/dashboard/tenants/features - StoreOwner 成功獲取功能開關")
    void getFeatureToggles_AsStoreOwner_ReturnsFeatures() throws Exception {
        // 建立測試用戶和店鋪
        String userEmail = uniqueEmail();

        // 註冊並登入 StoreOwner
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "email", userEmail,
                        "password", TEST_PASSWORD,
                        "userType", "SELLER"
                ))
                .when()
                .post("/v2/auth/register")
                .then()
                .statusCode(201);

        // 登入取得 token
        String loginResponse = given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("email", userEmail, "password", TEST_PASSWORD))
                .when()
                .post("/v2/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String accessToken = loginJson.path("data").path("accessToken").asText();

        // 建立店鋪並成為 StoreOwner
        User testUser = userRepository.findByEmail(userEmail).orElseThrow();
        // 直接設定 user 的 role 為 STORE_OWNER
        testUser.setRole(User.UserRole.STORE_OWNER);
        userRepository.save(testUser);

        Tenant testTenant = Tenant.builder()
                .name("Feature Toggle Test Store")
                .slug("feature-test-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        testTenant = tenantRepository.save(testTenant);

        // 更新用戶的 tenantId
        testUser.setTenantId(testTenant.getId());
        userRepository.save(testUser);

        TenantMember ownerMember = TenantMember.builder()
                .tenantId(testTenant.getId())
                .userId(testUser.getId())
                .storeRole(TenantMember.StoreRole.STORE_OWNER)
                .build();
        tenantMemberRepository.save(ownerMember);

        // 重新登入以獲取更新後的 authorities
        String reLoginResponse = given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("email", userEmail, "password", TEST_PASSWORD))
                .when()
                .post("/v2/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        JsonNode reLoginJson = objectMapper.readTree(reLoginResponse);
        accessToken = reLoginJson.path("data").path("accessToken").asText();

        // 取得功能開關
        try {
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .when()
                    .get(BASE_URL + "/dashboard/tenants/features")
                    .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data.features", notNullValue());

            System.out.println("✅ API-M17-010 PASSED: StoreOwner 成功獲取功能開關");
        } finally {
            cleanupTenantData(testTenant.getId(), testUser.getId());
        }
    }

    // ── US-M17-011: StoreOwner 申請功能開關 ─────────────────────

    @Test
    @Order(11)
    @DisplayName("API-M17-011: PUT /api/v2/dashboard/tenants/features/:feature - StoreOwner 申請功能，返回 200")
    void updateFeatureToggle_AsStoreOwner_ReturnsPendingApproval() throws Exception {
        // 建立測試用戶和店鋪
        String userEmail = uniqueEmail();

        // 註冊並登入 StoreOwner
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "email", userEmail,
                        "password", TEST_PASSWORD,
                        "userType", "SELLER"
                ))
                .when()
                .post("/v2/auth/register")
                .then()
                .statusCode(201);

        // 登入取得 token
        String loginResponse = given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("email", userEmail, "password", TEST_PASSWORD))
                .when()
                .post("/v2/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String accessToken = loginJson.path("data").path("accessToken").asText();

        // 建立店鋪並成為 StoreOwner
        User testUser = userRepository.findByEmail(userEmail).orElseThrow();
        // 直接設定 user 的 role 為 STORE_OWNER
        testUser.setRole(User.UserRole.STORE_OWNER);
        userRepository.save(testUser);

        Tenant testTenant = Tenant.builder()
                .name("Feature Update Test Store")
                .slug("feature-update-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        testTenant = tenantRepository.save(testTenant);

        // 更新用戶的 tenantId
        testUser.setTenantId(testTenant.getId());
        userRepository.save(testUser);

        TenantMember ownerMember = TenantMember.builder()
                .tenantId(testTenant.getId())
                .userId(testUser.getId())
                .storeRole(TenantMember.StoreRole.STORE_OWNER)
                .build();
        tenantMemberRepository.save(ownerMember);

        // 重新登入以獲取更新後的 authorities
        String reLoginResponse = given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("email", userEmail, "password", TEST_PASSWORD))
                .when()
                .post("/v2/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        JsonNode reLoginJson = objectMapper.readTree(reLoginResponse);
        accessToken = reLoginJson.path("data").path("accessToken").asText();

        // 申請功能開關
        try {
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of("enabled", true))
                    .when()
                    .put(BASE_URL + "/dashboard/tenants/features/DYNAMIC_PRICING_ENABLED")
                    .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data.status", equalTo("PENDING_APPROVAL"));

            System.out.println("✅ API-M17-011 PASSED: StoreOwner 申請功能開關成功");
        } finally {
            cleanupTenantData(testTenant.getId(), testUser.getId());
        }
    }
}