package com.nextkey.ecommerce.api.controller;

import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantMember;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.*;
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

import java.util.Map;
import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.*;

import io.restassured.http.ContentType;

/**
 * Admin Controller API E2E 測試 (API-M17-007 ~ API-M17-013)
 *
 * 使用 REST Assured 框架進行完整的 HTTP 層 E2E 測試
 * 測試範圍：
 * - API-M17-007: GET /v2/admin/tenants - 店鋪列表
 * - API-M17-008: POST /v2/admin/tenants/:id/approve - 審核通過
 * - API-M17-009: POST /v2/admin/tenants/:id/reject - 審核駁回
 * - API-M17-007-03/04: 非 PENDING 狀態/非 Admin 角色測試
 * - API-M17-008-03/04: 空白 reason/非 Admin 角色測試
 * - API-M17-010~013: StoreOwner/STORE_STAFF RBAC E2E 測試
 *
 * 注意：此測試使用真實的 PostgreSQL 和 Redis，確保完整的 E2E 測試覆蓋
 *
 * 重構说明 (Sprint 3-A):
 * - 使用 JwtTokenService 直接產生測試 token，繞過 register → update role → re-login 流程
 * - 解決 RegisterRequest.userType 不支援 ADMIN 導致的 E2E 測試失敗問題
 * - 解決 JWT authorities 無法在 E2E 測試中即時更新的問題
 *
 * ⚠️ 路徑配置說明：
 * - AdminController 使用 @RequestMapping("/v2/admin")
 * - MockMvc 環境中 request.getServletPath() 可能返回不包含 context-path 的路徑
 * - 測試中直接使用 /v2/admin/... 路徑（不含 /api prefix，因為 context-path 在測試環境不適用）
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.nextkey.ecommerce.integration.IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("API-M17 E2E: Admin Controller REST Assured E2E 測試")
class AdminControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @SuppressWarnings("unused")
    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantMemberRepository tenantMemberRepository;

    @Autowired
    private TenantFeatureToggleRepository featureToggleRepository;

    private static final String BASE_URL = "/v2/admin";
    @SuppressWarnings("unused")
    private static final String TEST_PASSWORD = "SecurePass123!";

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);
    }

    // 測試資料工廠方法
    private String uniqueEmail() {
        return "admin-e2e-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000) + "@example.com";
    }

    // ── Token 工廠方法 (Sprint 3-A 重構) ─────────────────────────────
    // 使用 JwtTokenService 直接產生測試 token，繞過 register → update role → re-login

    /**
     * 建立 SUPER_ADMIN 測試用戶並產生 token
     * 使用流程：
     * 1. 建立 User 實體（role = SUPER_ADMIN）
     * 2. 直接使用 JwtTokenService.generateAccessToken() 產生 token
     * 優勢：繞過 AuthService 的 role 解析邏輯，直接指定 role claim
     */
    private String createSuperAdminUserAndGetToken() {
        String email = uniqueEmail();

        // 直接建立 SUPER_ADMIN 用戶（User.status 是 String，不是 enum）
        User adminUser = User.builder()
                .email(email)
                .passwordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG") // BCrypt("password")
                .role(User.UserRole.SUPER_ADMIN)
                .status("ACTIVE")
                .build();
        adminUser = userRepository.save(adminUser);

        // 直接使用 JwtTokenService 產生 token（繞過 AuthService）
        return jwtTokenService.generateAccessToken(
                adminUser.getId(),
                adminUser.getEmail(),
                "SUPER_ADMIN",  // 直接指定 role
                null
        );
    }

    /**
     * 建立 BUYER 測試用戶並產生 token
     */
    private String createBuyerUserAndGetToken() {
        String email = uniqueEmail();

        User buyerUser = User.builder()
                .email(email)
                .passwordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build();
        buyerUser = userRepository.save(buyerUser);

        return jwtTokenService.generateAccessToken(
                buyerUser.getId(),
                buyerUser.getEmail(),
                "BUYER",
                null
        );
    }

    /**
     * 建立 StoreOwner 測試用戶（屬於特定租戶）並產生 token
     */
    private String createStoreOwnerUserAndGetToken(UUID tenantId) {
        String email = uniqueEmail();

        User storeOwner = User.builder()
                .email(email)
                .passwordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG")
                .role(User.UserRole.SELLER)  // StoreOwner 使用 SELLER role
                .status("ACTIVE")
                .build();
        storeOwner = userRepository.save(storeOwner);

        // 建立 TenantMember 關聯（StoreOwner 角色）- 直接使用 tenantId/userId
        TenantMember member = TenantMember.builder()
                .tenantId(tenantId)
                .userId(storeOwner.getId())
                .storeRole(TenantMember.StoreRole.STORE_OWNER)
                .build();
        tenantMemberRepository.save(member);

        return jwtTokenService.generateAccessToken(
                storeOwner.getId(),
                storeOwner.getEmail(),
                "SELLER",  // StoreOwner 對應 SELLER role
                tenantId.toString()
        );
    }

    /**
     * 建立 STORE_STAFF 測試用戶（屬於特定租戶）並產生 token
     */
    private String createStoreStaffUserAndGetToken(UUID tenantId) {
        String email = uniqueEmail();

        User storeStaff = User.builder()
                .email(email)
                .passwordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG")
                .role(User.UserRole.SELLER)
                .status("ACTIVE")
                .build();
        storeStaff = userRepository.save(storeStaff);

        // 建立 TenantMember 關聯（STORE_STAFF 角色）- 直接使用 tenantId/userId
        TenantMember member = TenantMember.builder()
                .tenantId(tenantId)
                .userId(storeStaff.getId())
                .storeRole(TenantMember.StoreRole.STORE_STAFF)
                .build();
        tenantMemberRepository.save(member);

        return jwtTokenService.generateAccessToken(
                storeStaff.getId(),
                storeStaff.getEmail(),
                "SELLER",
                tenantId.toString()
        );
    }

    // 清理測試資料
    private void cleanupTestData(UUID tenantId, UUID userId) {
        if (tenantId != null) {
            // 刪除 Feature Toggles
            featureToggleRepository.findByTenantId(tenantId).forEach(toggle -> featureToggleRepository.delete(toggle));
            // 刪除 Tenant Members
            tenantMemberRepository.findByTenantId(tenantId).forEach(member -> tenantMemberRepository.delete(member));
            // 刪除 Tenant
            tenantRepository.findById(tenantId).ifPresent(tenantRepository::delete);
        }
        if (userId != null) {
            userRepository.findById(userId).ifPresent(userRepository::delete);
        }
    }

    // ── API-M17-007: 取得店鋪列表 ─────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("API-M17-007: GET /api/v2/admin/tenants - Admin 成功取得店鋪列表")
    void getTenants_asAdmin_shouldSucceed() throws Exception {
        // 使用 token 工廠方法直接建立 SUPER_ADMIN token
        String adminToken = createSuperAdminUserAndGetToken();

        // 建立一個 PENDING_REVIEW 的測試店鋪
        Tenant testTenant = Tenant.builder()
                .name("Admin Test Store")
                .slug("admin-test-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.PENDING_REVIEW)
                .description("Test store description")
                .contactEmail("test-store@example.com")
                .build();
        testTenant = tenantRepository.save(testTenant);

        try {
            // 取得店鋪列表
            given()
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .when()
                    .get(BASE_URL + "/tenants")
                    .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data.tenants", notNullValue())
                    .body("data.totalCount", greaterThanOrEqualTo(1));

            System.out.println("✅ API-M17-007 PASSED: Admin 成功取得店鋪列表");
        } finally {
            // 清理時需要從 token 的 sub 取得 userId，但這裡我們只清理 tenant
            // user 會在測試結束後被 cleanupAll 清理
            cleanupTestData(testTenant.getId(), null);
        }
    }

    // ── API-M17-008: 審核通過租戶 ─────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("API-M17-008: POST /api/v2/admin/tenants/:id/approve - 審核通過成功")
    void approveTenant_asAdmin_shouldSucceed() throws Exception {
        // 使用 token 工廠方法直接建立 SUPER_ADMIN token
        String adminToken = createSuperAdminUserAndGetToken();

        // 建立一個 PENDING_REVIEW 的測試店鋪
        Tenant testTenant = Tenant.builder()
                .name("Approve Test Store")
                .slug("approve-test-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.PENDING_REVIEW)
                .build();
        testTenant = tenantRepository.save(testTenant);

        try {
            // 執行審核通過
            given()
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of())
                    .when()
                    .post(BASE_URL + "/tenants/" + testTenant.getId() + "/approve")
                    .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data.status", equalTo("ACTIVE"));

            System.out.println("✅ API-M17-008 PASSED: Admin 審核通過租戶成功");
        } finally {
            cleanupTestData(testTenant.getId(), null);
        }
    }

    // ── API-M17-009: 審核駁回租戶 ─────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("API-M17-009: POST /api/v2/admin/tenants/:id/reject - 審核駁回成功")
    void rejectTenant_asAdmin_shouldSucceed() throws Exception {
        // 使用 token 工廠方法直接建立 SUPER_ADMIN token
        String adminToken = createSuperAdminUserAndGetToken();

        // 建立一個 PENDING_REVIEW 的測試店鋪
        Tenant testTenant = Tenant.builder()
                .name("Reject Test Store")
                .slug("reject-test-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.PENDING_REVIEW)
                .build();
        testTenant = tenantRepository.save(testTenant);

        try {
            // 執行審核駁回
            given()
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of("reason", "資料不全"))
                    .when()
                    .post(BASE_URL + "/tenants/" + testTenant.getId() + "/reject")
                    .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data.status", equalTo("REJECTED"))
                    .body("data.reason", equalTo("資料不全"));

            System.out.println("✅ API-M17-009 PASSED: Admin 審核駁回租戶成功");
        } finally {
            cleanupTestData(testTenant.getId(), null);
        }
    }

    // ── API-M17-007-03: 非 PENDING 狀態審核 ─────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("API-M17-007-03: POST /api/v2/admin/tenants/:id/approve - 非 PENDING 狀態審核應失敗")
    void approveTenant_withNonPendingStatus_shouldReturn400() throws Exception {
        // 使用 token 工廠方法直接建立 SUPER_ADMIN token
        String adminToken = createSuperAdminUserAndGetToken();

        // 建立一個已經是 ACTIVE 狀態的測試店鋪
        Tenant testTenant = Tenant.builder()
                .name("Active Test Store")
                .slug("active-test-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        testTenant = tenantRepository.save(testTenant);

        try {
            // 嘗試審核已啟用的租戶，應收到 400 錯誤
            given()
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(ContentType.JSON)
                    .body(Map.of())
                    .when()
                    .post(BASE_URL + "/tenants/" + testTenant.getId() + "/approve")
                    .then()
                    .statusCode(400)
                    .body("success", is(false))
                    .body("code", equalTo("E-2005"))
                    .body("message", containsString("Tenant status is not PENDING_REVIEW"));

            System.out.println("✅ API-M17-007-03 PASSED: 非 PENDING 狀態審核正確返回 400");
        } finally {
            cleanupTestData(testTenant.getId(), null);
        }
    }

    // ── API-M17-007-04: 非 Admin 審核 (BUYER) ─────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("API-M17-007-04: POST /api/v2/admin/tenants/:id/approve - BUYER 角色審核應失敗")
    void approveTenant_asBuyer_shouldReturn403() throws Exception {
        // 使用 token 工廠方法直接建立 BUYER token
        String buyerToken = createBuyerUserAndGetToken();

        // 建立一個 PENDING_REVIEW 的測試店鋪
        Tenant testTenant = Tenant.builder()
                .name("Buyer Test Store")
                .slug("buyer-test-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.PENDING_REVIEW)
                .build();
        testTenant = tenantRepository.save(testTenant);

        try {
            // BUYER 用戶嘗試審核，應收到 403 錯誤
            given()
                    .header("Authorization", "Bearer " + buyerToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of())
                    .when()
                    .post(BASE_URL + "/tenants/" + testTenant.getId() + "/approve")
                    .then()
                    .statusCode(403);

            System.out.println("✅ API-M17-007-04 PASSED: BUYER 角色審核正確返回 403");
        } finally {
            cleanupTestData(testTenant.getId(), null);
        }
    }

    // ── API-M17-008-03: 空白 reason ─────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("API-M17-008-03: POST /api/v2/admin/tenants/:id/reject - 空白 reason 應失敗")
    void rejectTenant_withBlankReason_shouldReturn400() throws Exception {
        // 使用 token 工廠方法直接建立 SUPER_ADMIN token
        String adminToken = createSuperAdminUserAndGetToken();

        // 建立一個 PENDING_REVIEW 的測試店鋪
        Tenant testTenant = Tenant.builder()
                .name("Blank Reason Test Store")
                .slug("blank-reason-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.PENDING_REVIEW)
                .build();
        testTenant = tenantRepository.save(testTenant);

        try {
            // 空白 reason 應收到 400 錯誤
            given()
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of("reason", ""))
                    .when()
                    .post(BASE_URL + "/tenants/" + testTenant.getId() + "/reject")
                    .then()
                    .statusCode(400)
                    .body("success", is(false));

            System.out.println("✅ API-M17-008-03 PASSED: 空白 reason 正確返回 400");
        } finally {
            cleanupTestData(testTenant.getId(), null);
        }
    }

    // ── API-M17-008-04: 非 Admin 駁回 (BUYER) ─────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("API-M17-008-04: POST /api/v2/admin/tenants/:id/reject - BUYER 角色駁回應失敗")
    void rejectTenant_asBuyer_shouldReturn403() throws Exception {
        // 使用 token 工廠方法直接建立 BUYER token
        String buyerToken = createBuyerUserAndGetToken();

        // 建立一個 PENDING_REVIEW 的測試店鋪
        Tenant testTenant = Tenant.builder()
                .name("Buyer Reject Test Store")
                .slug("buyer-reject-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.PENDING_REVIEW)
                .build();
        testTenant = tenantRepository.save(testTenant);

        try {
            // BUYER 用戶嘗試駁回，應收到 403 錯誤
            given()
                    .header("Authorization", "Bearer " + buyerToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of("reason", "測試駁回"))
                    .when()
                    .post(BASE_URL + "/tenants/" + testTenant.getId() + "/reject")
                    .then()
                    .statusCode(403);

            System.out.println("✅ API-M17-008-04 PASSED: BUYER 角色駁回正確返回 403");
        } finally {
            cleanupTestData(testTenant.getId(), null);
        }
    }

    // ── API-M17-018: StoreOwner 呼叫 Admin approve (RBAC E2E) ─────────────────

    @Test
    @Order(8)
    @DisplayName("API-M17-018: POST /api/v2/admin/tenants/:id/approve - StoreOwner 角色應返回 403")
    void approveTenant_asStoreOwner_shouldReturn403() throws Exception {
        // 建立一個 PENDING_REVIEW 的測試店鋪
        Tenant testTenant = Tenant.builder()
                .name("StoreOwner Test Store")
                .slug("storeowner-test-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.PENDING_REVIEW)
                .build();
        testTenant = tenantRepository.save(testTenant);

        // 使用 token 工廠方法直接建立 StoreOwner token（帶有 tenantId）
        String storeOwnerToken = createStoreOwnerUserAndGetToken(testTenant.getId());

        try {
            // StoreOwner 用戶嘗試審核，應收到 403 錯誤
            given()
                    .header("Authorization", "Bearer " + storeOwnerToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of())
                    .when()
                    .post(BASE_URL + "/tenants/" + testTenant.getId() + "/approve")
                    .then()
                    .statusCode(403);

            System.out.println("✅ API-M17-010 PASSED: StoreOwner 角色審核正確返回 403");
        } finally {
            cleanupTestData(testTenant.getId(), null);
        }
    }

    // ── API-M17-019: STORE_STAFF 呼叫 Admin approve (RBAC E2E) ───────────────

    @Test
    @Order(9)
    @DisplayName("API-M17-019: POST /api/v2/admin/tenants/:id/approve - STORE_STAFF 角色應返回 403")
    void approveTenant_asStoreStaff_shouldReturn403() throws Exception {
        // 建立一個 PENDING_REVIEW 的測試店鋪
        Tenant testTenant = Tenant.builder()
                .name("StoreStaff Test Store")
                .slug("storestaff-test-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.PENDING_REVIEW)
                .build();
        testTenant = tenantRepository.save(testTenant);

        // 使用 token 工廠方法直接建立 STORE_STAFF token（帶有 tenantId）
        String storeStaffToken = createStoreStaffUserAndGetToken(testTenant.getId());

        try {
            // STORE_STAFF 用戶嘗試審核，應收到 403 錯誤
            given()
                    .header("Authorization", "Bearer " + storeStaffToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of())
                    .when()
                    .post(BASE_URL + "/tenants/" + testTenant.getId() + "/approve")
                    .then()
                    .statusCode(403);

            System.out.println("✅ API-M17-011 PASSED: STORE_STAFF 角色審核正確返回 403");
        } finally {
            cleanupTestData(testTenant.getId(), null);
        }
    }

    // ── API-M17-020: StoreOwner 呼叫 Admin reject (RBAC E2E) ─────────────────

    @Test
    @Order(10)
    @DisplayName("API-M17-020: POST /api/v2/admin/tenants/:id/reject - StoreOwner 角色應返回 403")
    void rejectTenant_asStoreOwner_shouldReturn403() throws Exception {
        // 建立一個 PENDING_REVIEW 的測試店鋪
        Tenant testTenant = Tenant.builder()
                .name("StoreOwner Reject Store")
                .slug("storeowner-reject-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.PENDING_REVIEW)
                .build();
        testTenant = tenantRepository.save(testTenant);

        // 使用 token 工廠方法直接建立 StoreOwner token（帶有 tenantId）
        String storeOwnerToken = createStoreOwnerUserAndGetToken(testTenant.getId());

        try {
            // StoreOwner 用戶嘗試駁回，應收到 403 錯誤
            given()
                    .header("Authorization", "Bearer " + storeOwnerToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of("reason", "測試駁回"))
                    .when()
                    .post(BASE_URL + "/tenants/" + testTenant.getId() + "/reject")
                    .then()
                    .statusCode(403);

            System.out.println("✅ API-M17-012 PASSED: StoreOwner 角色駁回正確返回 403");
        } finally {
            cleanupTestData(testTenant.getId(), null);
        }
    }

    // ── API-M17-022: StoreOwner 呼叫 Admin reject (RBAC E2E) ─────────────────

    @Test
    @Order(11)
    @DisplayName("API-M17-022: POST /api/v2/admin/tenants/:id/reject - StoreOwner 角色應返回 403")
    void rejectTenant_asStoreOwner_shouldReturn403_forAPI17022() throws Exception {
        // 建立一個 PENDING_REVIEW 的測試店鋪
        Tenant testTenant = Tenant.builder()
                .name("StoreOwner Reject Store")
                .slug("storeowner-reject-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.PENDING_REVIEW)
                .build();
        testTenant = tenantRepository.save(testTenant);

        // 使用 token 工廠方法直接建立 StoreOwner token（帶有 tenantId）
        String storeOwnerToken = createStoreOwnerUserAndGetToken(testTenant.getId());

        try {
            // StoreOwner 用戶嘗試駁回，應收到 403 錯誤
            given()
                    .header("Authorization", "Bearer " + storeOwnerToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of("reason", "測試駁回"))
                    .when()
                    .post(BASE_URL + "/tenants/" + testTenant.getId() + "/reject")
                    .then()
                    .statusCode(403);

            System.out.println("✅ API-M17-012 PASSED: StoreOwner 角色駁回正確返回 403");
        } finally {
            cleanupTestData(testTenant.getId(), null);
        }
    }

    // ── API-M17-023: STORE_STAFF 呼叫 Admin reject (RBAC E2E) ───────────────

    @Test
    @Order(12)
    @DisplayName("API-M17-023: POST /api/v2/admin/tenants/:id/reject - STORE_STAFF 角色應返回 403")
    void rejectTenant_asStoreStaff_shouldReturn403() throws Exception {
        // 建立一個 PENDING_REVIEW 的測試店鋪
        Tenant testTenant = Tenant.builder()
                .name("StoreStaff Reject Store")
                .slug("storestaff-reject-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.PENDING_REVIEW)
                .build();
        testTenant = tenantRepository.save(testTenant);

        // 使用 token 工廠方法直接建立 STORE_STAFF token（帶有 tenantId）
        String storeStaffToken = createStoreStaffUserAndGetToken(testTenant.getId());

        try {
            // STORE_STAFF 用戶嘗試駁回，應收到 403 錯誤
            given()
                    .header("Authorization", "Bearer " + storeStaffToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of("reason", "測試駁回"))
                    .when()
                    .post(BASE_URL + "/tenants/" + testTenant.getId() + "/reject")
                    .then()
                    .statusCode(403);

            System.out.println("✅ API-M17-013 PASSED: STORE_STAFF 角色駁回正確返回 403");
        } finally {
            cleanupTestData(testTenant.getId(), null);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // US-M17-009: Admin 更新租戶的 Feature Toggle
    // API: PUT /v2/admin/tenants/{tenantId}/features/{feature}
    // Request Body: {"enabled": true}
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * US-M17-009-01: Admin 更新 Feature Toggle 成功
     */
    @Test
    @Order(13)
    @DisplayName("US-M17-009-01: PUT /v2/admin/tenants/:id/features/:feature - Admin 更新成功返回 200")
    void testUpdateTenantFeatureToggle_asAdmin_shouldSucceed() throws Exception {
        // 建立 SUPER_ADMIN token
        String adminToken = createSuperAdminUserAndGetToken();

        // 建立測試租戶並初始化 Feature Toggle
        Tenant testTenant = Tenant.builder()
                .name("Feature Toggle Test Tenant")
                .slug("feature-toggle-test-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        testTenant = tenantRepository.save(testTenant);

        // 建立 Feature Toggle 記錄
        com.nextkey.ecommerce.domain.model.tenant.TenantFeatureToggle toggle =
                com.nextkey.ecommerce.domain.model.tenant.TenantFeatureToggle.builder()
                        .tenant(testTenant)
                        .featureKey("BOOKING_ENABLED")
                        .isEnabled(false)
                        .build();
        featureToggleRepository.save(toggle);

        try {
            // Admin 啟用 Feature Toggle
            given()
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of("enabled", true))
                    .when()
                    .put(BASE_URL + "/tenants/" + testTenant.getId() + "/features/BOOKING_ENABLED")
                    .then()
                    .statusCode(200)
                    .body("success", is(true))
                    .body("data.isEnabled", is(true));

            System.out.println("✅ US-M17-009-01 PASSED: Admin 更新 Feature Toggle 成功");
        } finally {
            cleanupTestData(testTenant.getId(), null);
        }
    }

    /**
     * US-M17-009-02: BUYER 角色更新 Feature Toggle 應返回 403
     */
    @Test
    @Order(14)
    @DisplayName("US-M17-009-02: PUT /v2/admin/tenants/:id/features/:feature - BUYER 角色應返回 403")
    void testUpdateTenantFeatureToggle_asNonAdmin_shouldReturn403() throws Exception {
        // 建立 BUYER token
        String buyerToken = createBuyerUserAndGetToken();

        // 建立測試租戶
        Tenant testTenant = Tenant.builder()
                .name("Non-Admin Feature Toggle Test")
                .slug("nonadmin-feature-test-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        testTenant = tenantRepository.save(testTenant);

        try {
            // BUYER 用戶嘗試更新 Feature Toggle，應收到 403 錯誤
            given()
                    .header("Authorization", "Bearer " + buyerToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of("enabled", true))
                    .when()
                    .put(BASE_URL + "/tenants/" + testTenant.getId() + "/features/BOOKING_ENABLED")
                    .then()
                    .statusCode(403);

            System.out.println("✅ US-M17-009-02 PASSED: BUYER 角色更新 Feature Toggle 正確返回 403");
        } finally {
            cleanupTestData(testTenant.getId(), null);
        }
    }
}