package com.nextkey.ecommerce.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantApplication;
import com.nextkey.ecommerce.domain.model.tenant.TenantMember;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantApplicationRepository;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * 開店申請 → Admin 審核 → StoreOwner 授權 端到端 E2E 測試（PRD §7.4.1，Sprint 97）
 *
 * 重新全面比對 PRD 全文發現的第五個缺口：{@code TenantService.createApplication} 只寫入
 * {@code tenant_applications} 表，{@code AdminService.approveTenant/rejectTenant} 卻只操作
 * 既有 {@code tenants} 表記錄，兩者從未串接——Buyer 提交開店申請後永遠卡住，Admin 端看不到、
 * 審不了，也不會產生真正的店鋪與 StoreOwner 授權。本測試驗證修復後的完整迴路。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.nextkey.ecommerce.integration.IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@DisplayName("IT-M17-Application: 開店申請審核端到端流程")
class TenantApplicationReviewE2ETest {

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

    @Autowired
    private JwtTokenService jwtTokenService;

    private static final String TENANT_BASE_URL = "/api/v2";
    private static final String ADMIN_BASE_URL = "/v2/admin";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        RestAssuredMockMvc.mockMvc(mockMvc);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String uniqueEmail() {
        return "tenant-app-e2e-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000) + "@example.com";
    }

    private String createBuyerUserAndGetToken(UUID[] userIdOut) {
        String email = uniqueEmail();
        User buyer = User.builder()
                .email(email)
                .passwordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build();
        buyer = userRepository.save(buyer);
        userIdOut[0] = buyer.getId();
        return jwtTokenService.generateAccessToken(buyer.getId(), buyer.getEmail(), "BUYER", null);
    }

    private String createSuperAdminUserAndGetToken() {
        String email = uniqueEmail();
        User admin = User.builder()
                .email(email)
                .passwordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG")
                .role(User.UserRole.SUPER_ADMIN)
                .status("ACTIVE")
                .build();
        admin = userRepository.save(admin);
        return jwtTokenService.generateAccessToken(admin.getId(), admin.getEmail(), "SUPER_ADMIN", null);
    }

    private String submitApplication(String buyerToken, String storeName) throws Exception {
        String responseBody = given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "storeName", storeName,
                        "businessType", "RETAIL_ONLY",
                        "contactEmail", "store@example.com",
                        "contactPhone", "0912345678"
                ))
                .when()
                .post(TENANT_BASE_URL + "/tenants/apply")
                .then()
                .statusCode(201)
                .extract().asString();
        JsonNode json = objectMapper.readTree(responseBody);
        return json.path("data").path("applicationId").asText();
    }

    @Test
    @DisplayName("IT-M17-APP-001: 完整迴路 — 申請 → Admin 列表可見 → 核准 → 真正成為 StoreOwner")
    void fullLoop_applyReviewApprove_createsRealTenantAndStoreOwner() throws Exception {
        UUID[] buyerIdHolder = new UUID[1];
        String buyerToken = createBuyerUserAndGetToken(buyerIdHolder);
        UUID buyerId = buyerIdHolder[0];
        String adminToken = createSuperAdminUserAndGetToken();

        String applicationId = submitApplication(buyerToken, "E2E Full Loop Store " + System.currentTimeMillis());

        // Admin 端可見待審核申請
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get(ADMIN_BASE_URL + "/tenant-applications")
                .then()
                .statusCode(200)
                .body("data.applications.applicationId", hasItem(applicationId));

        // Admin 核准
        String approveResponse = given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .post(ADMIN_BASE_URL + "/tenant-applications/" + applicationId + "/approve")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.status", equalTo("APPROVED"))
                .extract().asString();

        JsonNode approveJson = objectMapper.readTree(approveResponse);
        UUID tenantId = UUID.fromString(approveJson.path("data").path("tenantId").asText());

        // 驗證：真正的 Tenant 已建立為 ACTIVE
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        assertThat(tenant.getStatus()).isEqualTo(Tenant.TenantStatus.ACTIVE);

        // 驗證：申請人已成為該 Tenant 的 StoreOwner
        assertThat(tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(
                tenantId, buyerId, TenantMember.StoreRole.STORE_OWNER)).isTrue();

        // 驗證：User.role 已同步更新為 STORE_OWNER（下次登入 JWT 才會真正拿到 StoreOwner 權限）
        User approvedUser = userRepository.findById(buyerId).orElseThrow();
        assertThat(approvedUser.getRole()).isEqualTo(User.UserRole.STORE_OWNER);

        // 驗證：申請狀態已回填為 APPROVED 並關聯 tenantId
        TenantApplication application = tenantApplicationRepository.findById(UUID.fromString(applicationId)).orElseThrow();
        assertThat(application.getStatus()).isEqualTo(TenantApplication.ApplicationStatus.APPROVED);
        assertThat(application.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("IT-M17-APP-002: Admin 駁回申請 — 不建立 Tenant，僅更新申請狀態")
    void rejectApplication_doesNotCreateTenant() throws Exception {
        UUID[] buyerIdHolder = new UUID[1];
        String buyerToken = createBuyerUserAndGetToken(buyerIdHolder);
        String adminToken = createSuperAdminUserAndGetToken();

        String applicationId = submitApplication(buyerToken, "E2E Reject Store " + System.currentTimeMillis());

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("reason", "資料不完整"))
                .when()
                .post(ADMIN_BASE_URL + "/tenant-applications/" + applicationId + "/reject")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("REJECTED"));

        TenantApplication application = tenantApplicationRepository.findById(UUID.fromString(applicationId)).orElseThrow();
        assertThat(application.getStatus()).isEqualTo(TenantApplication.ApplicationStatus.REJECTED);
        assertThat(application.getTenantId()).isNull();
    }

    @Test
    @DisplayName("IT-M17-APP-003: 重複核准已審核過的申請 → 400 E-2007")
    void approveAlreadyReviewedApplication_returns400() throws Exception {
        UUID[] buyerIdHolder = new UUID[1];
        String buyerToken = createBuyerUserAndGetToken(buyerIdHolder);
        String adminToken = createSuperAdminUserAndGetToken();

        String applicationId = submitApplication(buyerToken, "E2E Double Approve Store " + System.currentTimeMillis());

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .post(ADMIN_BASE_URL + "/tenant-applications/" + applicationId + "/approve")
                .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .post(ADMIN_BASE_URL + "/tenant-applications/" + applicationId + "/approve")
                .then()
                .statusCode(400)
                .body("code", equalTo("E-2007"));
    }

    @Test
    @DisplayName("IT-M17-APP-004: Guest（未認證）申請無法被核准 → 400 E-2008")
    void approveGuestApplication_returns400() throws Exception {
        String adminToken = createSuperAdminUserAndGetToken();

        String responseBody = given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "storeName", "E2E Guest Store " + System.currentTimeMillis(),
                        "businessType", "RETAIL_ONLY",
                        "contactEmail", "guest-store@example.com",
                        "contactPhone", "0987654321"
                ))
                .when()
                .post(TENANT_BASE_URL + "/tenants/apply")
                .then()
                .statusCode(201)
                .extract().asString();
        String applicationId = objectMapper.readTree(responseBody).path("data").path("applicationId").asText();

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .post(ADMIN_BASE_URL + "/tenant-applications/" + applicationId + "/approve")
                .then()
                .statusCode(400)
                .body("code", equalTo("E-2008"));
    }

    @Test
    @DisplayName("IT-M17-APP-005: 非 SUPER_ADMIN 查詢待審核申請列表 → 403")
    void getPendingApplications_nonAdmin_returns403() {
        UUID[] buyerIdHolder = new UUID[1];
        String buyerToken = createBuyerUserAndGetToken(buyerIdHolder);

        given()
                .header("Authorization", "Bearer " + buyerToken)
                .when()
                .get(ADMIN_BASE_URL + "/tenant-applications")
                .then()
                .statusCode(403);
    }

    /**
     * 讀取平台統計的「待審核店鋪」數字（Admin Dashboard 直接顯示此欄位）。
     */
    private int readPendingTenantReviews(String adminToken) throws Exception {
        String body = given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get(ADMIN_BASE_URL + "/stats")
                .then()
                .statusCode(200)
                .extract().asString();
        return objectMapper.readTree(body).path("data").path("pendingTenantReviews").asInt();
    }

    @Test
    @DisplayName("IT-M17-APP-006: 平台統計的待審核數必須反映真正待審核的開店申請")
    void platformStats_pendingTenantReviews_reflectsPendingApplications() throws Exception {
        String adminToken = createSuperAdminUserAndGetToken();

        // 差分斷言：測試 DB 經 Flyway V7 已播入一筆 PENDING_REVIEW 租戶，
        // 直接斷言 ">= 1" 會被那筆固件矇混過關（假綠）。改為量測「送出一筆申請前後的增量」，
        // 才真正驗證統計數字與待審核申請之間的因果關係。
        int before = readPendingTenantReviews(adminToken);

        UUID[] buyerIdHolder = new UUID[1];
        String buyerToken = createBuyerUserAndGetToken(buyerIdHolder);
        submitApplication(buyerToken, "E2E Stats Store " + System.currentTimeMillis());

        // 送出一筆待審核申請後，Admin Dashboard 的待審核數必須 +1。
        // 修復前：統計數 Tenant.status=PENDING_REVIEW（生產無任何路徑會產生此狀態），恆定不變。
        int after = readPendingTenantReviews(adminToken);
        assertThat(after)
                .as("送出開店申請後，Admin Dashboard 的待審核店鋪數應該 +1")
                .isEqualTo(before + 1);
    }
}
