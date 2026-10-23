package com.nextkey.ecommerce.api.controller;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.LoginRequest;
import com.nextkey.ecommerce.api.dto.RegisterRequest;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.PricingRule;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.PricingRuleRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.shared.constants.AppConstants;

import io.restassured.module.mockmvc.RestAssuredMockMvc;

/**
 * 沒有店鋪的呼叫者不得碰到系統租戶名下的定價規則與定價日曆預覽（Sprint 303，DEF-411，真實 PostgreSQL，
 * 走完整 HTTP＋JWT＋權限＋租戶過濾鏈）。
 *
 * <p>沒有加入任何店鋪的使用者（一般買家、沒有店鋪的 ADMIN，以及平台管理員）的租戶不是 null，而是系統租戶佔位值
 * （{@link AppConstants#SYSTEM_TENANT_ID}），全體共用。定價端點（{@code /v2/dashboard/pricing/*}）的讀取只要求
 * {@code room:read} 或 {@code product:read}——BUYER 兩個都持有——而擁有權檢查是「資源的租戶 == 呼叫者的租戶」，
 * 於是任一買家對「租戶也是系統租戶」的規則與房源都等於「同租戶」。Sprint 234／235 修了客服工單、退貨、結算、
 * 成員等同型缺陷，這裡守住定價這一組。
 *
 * <p>系統租戶的規則從哪來？本類別不假設，直接量測：以<b>真實登入</b>的 SUPER_ADMIN 呼叫
 * {@code POST /v2/dashboard/pricing/rules}（{@code createRule} 以呼叫者的租戶蓋章），再看資料庫裡那一列的租戶。
 * 每個案例都附「真正的店主、平台管理員照常運作」的對照，避免守門變成「對所有人都回空」。
 *
 * <p>範圍只有讀取端點（規則列表、定價日曆預覽）。寫入端點（建立／更新／刪除／覆蓋）需要 BUYER 沒有的
 * {@code room:create}／{@code update}，其擁有權檢查的語意本輪刻意不動（沒有店鋪的 ADMIN 能否管理系統租戶的資源是權限模型決策，
 * 見追蹤表 DEF-415）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.nextkey.ecommerce.integration.IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Sprint 303（DEF-411）: 沒有店鋪的呼叫者看不到系統租戶的定價規則與定價日曆")
class PricingSystemTenantIsolationE2ETest {

    private static final String PASSWORD = "SecurePass123!";
    private static final UUID SYSTEM_TENANT_ID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
    private static final String RULES_URL = "/v2/dashboard/pricing/rules";
    private static final String CALENDAR_URL = "/v2/dashboard/pricing/calendar";
    private static final String PRICING_TOGGLE = "DYNAMIC_PRICING_ENABLED";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private PricingRuleRepository pricingRuleRepository;

    /** 沒有店鋪的一般買家（持有 room:read／product:read）。 */
    private String buyerToken;
    /** 沒有店鋪的 ADMIN（租戶內角色，租戶同樣是系統租戶佔位值）。 */
    private String storelessAdminToken;
    /** 真實登入的平台管理員（SUPER_ADMIN）。 */
    private String superAdminToken;
    /** 平台管理員登入回應裡的租戶——量測值。 */
    private String superAdminLoginTenantId;
    /** 一間真實店鋪的店主（對照組）。 */
    private String storeOwnerToken;

    private UUID systemListingId;
    private UUID storeListingId;
    private UUID systemRuleId;
    private UUID storeRuleId;

    @BeforeAll
    void seedFixtures() throws Exception {
        RestAssuredMockMvc.mockMvc(mockMvc);
        long stamp = System.currentTimeMillis();

        buyerToken = registerAndLogin("pst-buyer-" + stamp + "@example.com", null).token();

        String adminEmail = "pst-admin-" + stamp + "@example.com";
        LoginResult storelessAdmin = registerAndLogin(adminEmail, User.UserRole.ADMIN);
        storelessAdminToken = storelessAdmin.token();

        String superEmail = "pst-super-" + stamp + "@example.com";
        LoginResult superAdmin = registerAndLogin(superEmail, User.UserRole.SUPER_ADMIN);
        superAdminToken = superAdmin.token();
        superAdminLoginTenantId = superAdmin.tenantId();
        User superAdminUser = userRepository.findByEmail(superEmail).orElseThrow();

        // ── 一間真實店鋪、它的店主與房源（對照組）──
        Tenant store = tenantRepository.save(Tenant.builder()
                .name("S303 定價隔離測試店 " + stamp)
                .slug("s303-pricing-isolation-" + stamp)
                .contactEmail("s303-store-" + stamp + "@example.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        User owner = userRepository.save(User.builder()
                .email("s303-owner-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("S303 店主")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(store.getId())
                .build());
        storeOwnerToken = jwtTokenService.generateAccessToken(
                owner.getId(), owner.getEmail(), "STORE_OWNER", store.getId().toString());

        Tenant systemTenant = tenantRepository.findById(SYSTEM_TENANT_ID).orElseThrow();
        systemListingId = listingRepository.save(Listing.builder().tenant(systemTenant).owner(superAdminUser)
                .listingType(Listing.ListingType.ROOM).title("S303 平台自營房源")
                .basePrice(new BigDecimal("2000.00")).status(Listing.ListingStatus.ACTIVE).build()).getId();
        storeListingId = listingRepository.save(Listing.builder().tenant(store).owner(owner)
                .listingType(Listing.ListingType.ROOM).title("S303 店鋪房源")
                .basePrice(new BigDecimal("1500.00")).status(Listing.ListingStatus.ACTIVE).build()).getId();

        storeRuleId = pricingRuleRepository.save(PricingRule.builder()
                .tenant(store)
                .roomListingId(storeListingId)
                .ruleType(PricingRule.PricingRuleType.WEEKDAY_WEEKEND)
                .ruleName("S303 店鋪週末加價")
                .priority(5)
                .config(Map.of("weekendMultiplier", 1.2))
                .validFrom(LocalDate.now())
                .validTo(LocalDate.now().plusYears(1))
                .isActive(true)
                .build()).getId();

        // ── 系統租戶的規則：以 repository 直接蓋成系統租戶（它怎麼來的由下方「量測」案例走真實 HTTP 驗證）──
        systemRuleId = pricingRuleRepository.save(PricingRule.builder()
                .tenant(systemTenant)
                .roomListingId(systemListingId)
                .ruleType(PricingRule.PricingRuleType.WEEKDAY_WEEKEND)
                .ruleName("S303 平台自營週末加價")
                .priority(5)
                .config(Map.of("weekendMultiplier", 1.3))
                .validFrom(LocalDate.now())
                .validTo(LocalDate.now().plusYears(1))
                .isActive(true)
                .build()).getId();
    }

    @AfterAll
    void cleanUp() {
        pricingRuleRepository.deleteById(systemRuleId);
        pricingRuleRepository.deleteById(storeRuleId);
        listingRepository.deleteById(systemListingId);
        listingRepository.deleteById(storeListingId);
    }

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        RestAssuredMockMvc.mockMvc(mockMvc);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ───────────────────────── 量測：系統租戶的規則從哪來 ─────────────────────────

    @Test
    @DisplayName("量測：平台管理員登入後的租戶是系統租戶，他經 HTTP 建立的規則就蓋成系統租戶")
    void superAdminTenantIsSystemTenantAndRuleIsStampedWithIt() throws Exception {
        assertThat(superAdminLoginTenantId)
                .as("沒有店鋪歸屬的平台管理員，登入簽發的租戶退回系統租戶佔位值")
                .isEqualTo(SYSTEM_TENANT_ID.toString());

        // 測試庫預設關閉動態定價，暫時打開再還原
        Boolean previousToggle = readSystemPricingToggle();
        setSystemPricingToggle(true);
        UUID createdRuleId = null;
        try {
            String created = given().header("Authorization", "Bearer " + superAdminToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"roomListingId\":\"" + systemListingId + "\",\"ruleType\":\"SEASONAL\","
                            + "\"ruleName\":\"S303 量測用規則\",\"priority\":1,"
                            + "\"config\":{\"multiplier\":1.1},"
                            + "\"validFrom\":\"" + LocalDate.now().plusYears(3) + "\","
                            + "\"validTo\":\"" + LocalDate.now().plusYears(3).plusDays(5) + "\"}")
                    .when().post(RULES_URL)
                    .then().statusCode(201).extract().asString();
            createdRuleId = UUID.fromString(objectMapper.readTree(created).path("data").path("ruleId").asText());
            assertThat(pricingRuleRepository.findById(createdRuleId).orElseThrow().getTenantId())
                    .as("createRule 以呼叫者的租戶蓋章：管理員建的規則是系統租戶名下")
                    .isEqualTo(SYSTEM_TENANT_ID);
        } finally {
            setSystemPricingToggle(previousToggle != null && previousToggle);
            if (createdRuleId != null) {
                pricingRuleRepository.deleteById(createdRuleId);
            }
        }
    }

    // ───────────────────────── 規則列表（無參數分支） ─────────────────────────

    @Test
    @DisplayName("規則列表：沒有店鋪的買家列不出系統租戶的規則（含 activeOnly）；店主只看到自己店鋪的；平台管理員仍列得出")
    void tenantlessBuyerCannotListSystemTenantRules() {
        for (String query : new String[] {"", "?activeOnly=true"}) {
            given().header("Authorization", "Bearer " + buyerToken)
                    .when().get(RULES_URL + query)
                    .then().statusCode(200)
                    .body("data.ruleId", not(hasItem(systemRuleId.toString())))
                    .body("data.tenantId", not(hasItem(SYSTEM_TENANT_ID.toString())));
        }

        given().header("Authorization", "Bearer " + storeOwnerToken)
                .when().get(RULES_URL)
                .then().statusCode(200)
                .body("data.ruleId", hasItem(storeRuleId.toString()))
                .body("data.ruleId", not(hasItem(systemRuleId.toString())));

        given().header("Authorization", "Bearer " + superAdminToken)
                .when().get(RULES_URL)
                .then().statusCode(200)
                .body("data.ruleId", hasItem(systemRuleId.toString()));
    }

    @Test
    @DisplayName("規則列表：沒有店鋪的 ADMIN 同樣列不出系統租戶的規則")
    void tenantlessAdminCannotListSystemTenantRules() {
        given().header("Authorization", "Bearer " + storelessAdminToken)
                .when().get(RULES_URL)
                .then().statusCode(200)
                .body("data.ruleId", not(hasItem(systemRuleId.toString())));
    }

    // ───────────────────────── 規則列表（帶房源 id 的分支） ─────────────────────────

    @Test
    @DisplayName("規則列表：沒有店鋪的買家以系統租戶房源的 id 查詢 → 403；店主查自己的房源、平台管理員查系統房源照常 200")
    void tenantlessBuyerCannotListRulesOfSystemTenantListing() {
        given().header("Authorization", "Bearer " + buyerToken)
                .when().get(RULES_URL + "?roomListingId=" + systemListingId)
                .then().statusCode(403);
        given().header("Authorization", "Bearer " + buyerToken)
                .when().get(RULES_URL + "?listingId=" + systemListingId)
                .then().statusCode(403);

        given().header("Authorization", "Bearer " + storeOwnerToken)
                .when().get(RULES_URL + "?roomListingId=" + storeListingId)
                .then().statusCode(200)
                .body("data.ruleId", hasItem(storeRuleId.toString()));
        given().header("Authorization", "Bearer " + superAdminToken)
                .when().get(RULES_URL + "?roomListingId=" + systemListingId)
                .then().statusCode(200)
                .body("data.ruleId", hasItem(systemRuleId.toString()));
    }

    @Test
    @DisplayName("規則列表：店主以別人（系統租戶）房源的 id 查詢仍是 403（跨租戶本來就擋，守門不得因改寫而放寬）")
    void storeOwnerCannotListRulesOfOtherTenantsListing() {
        given().header("Authorization", "Bearer " + storeOwnerToken)
                .when().get(RULES_URL + "?roomListingId=" + systemListingId)
                .then().statusCode(403);
    }

    // ───────────────────────── 定價日曆預覽 ─────────────────────────

    @Test
    @DisplayName("日曆預覽：沒有店鋪的買家不得預覽系統租戶房源 → 403；平台管理員與店主預覽自己的照常 200")
    void tenantlessBuyerCannotPreviewSystemTenantCalendar() {
        String range = "&startDate=" + LocalDate.now().plusDays(1) + "&endDate=" + LocalDate.now().plusDays(7);

        given().header("Authorization", "Bearer " + buyerToken)
                .when().get(CALENDAR_URL + "?roomListingId=" + systemListingId + range)
                .then().statusCode(403);

        given().header("Authorization", "Bearer " + superAdminToken)
                .when().get(CALENDAR_URL + "?roomListingId=" + systemListingId + range)
                .then().statusCode(200);
        given().header("Authorization", "Bearer " + storeOwnerToken)
                .when().get(CALENDAR_URL + "?roomListingId=" + storeListingId + range)
                .then().statusCode(200);
        given().header("Authorization", "Bearer " + storeOwnerToken)
                .when().get(CALENDAR_URL + "?roomListingId=" + systemListingId + range)
                .then().statusCode(403);
    }

    // ───────────────────────── 輔助 ─────────────────────────

    private record LoginResult(String token, String tenantId) { }

    /**
     * 註冊並登入。{@code promoteTo} 不為 null 時，註冊後直接在資料庫改角色再登入——所以簽發的 token
     * 與租戶是真實登入流程算出來的，不是測試手工組的。
     */
    private LoginResult registerAndLogin(String email, User.UserRole promoteTo) throws Exception {
        given().contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(RegisterRequest.builder().email(email).password(PASSWORD).userType("BUYER").build())
                .when().post("/v2/auth/register")
                .then().statusCode(201);
        if (promoteTo != null) {
            User user = userRepository.findByEmail(email).orElseThrow();
            user.setRole(promoteTo);
            userRepository.save(user);
        }
        String loginResponse = given().contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(LoginRequest.builder().email(email).password(PASSWORD).build())
                .when().post("/v2/auth/login")
                .then().statusCode(200).extract().asString();
        JsonNode data = objectMapper.readTree(loginResponse).path("data");
        return new LoginResult(data.path("accessToken").asText(), data.path("user").path("tenantId").asText());
    }

    private Boolean readSystemPricingToggle() {
        return jdbcTemplate.queryForObject(
                "SELECT is_enabled FROM tenant_feature_toggles WHERE tenant_id = ? AND feature_key = ?",
                Boolean.class, SYSTEM_TENANT_ID, PRICING_TOGGLE);
    }

    private void setSystemPricingToggle(boolean enabled) {
        jdbcTemplate.update(
                "UPDATE tenant_feature_toggles SET is_enabled = ? WHERE tenant_id = ? AND feature_key = ?",
                enabled, SYSTEM_TENANT_ID, PRICING_TOGGLE);
    }
}
