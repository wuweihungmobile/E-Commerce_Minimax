package com.nextkey.ecommerce.api.controller;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

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
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductRepository;
import com.nextkey.ecommerce.domain.repository.ShippingTemplateRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.shared.constants.AppConstants;

import io.restassured.module.mockmvc.RestAssuredMockMvc;

/**
 * 沒有店鋪的 SELLER／HOST 不得寫入店家層資料（Sprint 240，DEF-326，使用者於 Sprint 238 結尾拍板）。
 *
 * <p>自助註冊可以直接得到 {@code SELLER} 或 {@code HOST}，沒有審核、不建租戶；開店核准後的角色是 {@code STORE_OWNER}。
 * 所以正式環境的 SELLER／HOST 幾乎都是「還沒開店的人」，租戶落在系統租戶佔位值，卻持有商品、房源、定價、運費模板、
 * CMS、貼文的寫入權限；各寫入方法的擁有權檢查是「資源的租戶 == 呼叫者的租戶」，於是所有這類使用者與平台自營資料彼此互通
 * （可改運費模板——結帳時對所有一般買家生效、可改線上 CMS 頁面、可往系統租戶的買家目錄上架商品……）。
 *
 * <p>全程走真實的註冊、登入、JWT、權限對照表（整合測試用生產權限，見 DEF-304）：權限完全由 token 的 role 宣告決定，所以
 * 判斷的位置是簽發——沒有店鋪的 SELLER／HOST 以買家身分簽發。判斷依據是<b>租戶</b>而不是角色標籤：有真實店鋪租戶的
 * SELLER／HOST 照常運作（每個案例都有這個對照，避免守門變成「對所有 SELLER 都擋」）。
 *
 * <p>被擋下的回應是 403 {@code E-1007}；探針用的請求內容都是合法的——Spring MVC 先綁定並驗證請求內容、才進方法安全檢查，
 * 內容不合法會先得到 400，把「沒被擋」藏起來。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.nextkey.ecommerce.integration.IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Sprint 240: 沒有店鋪的 SELLER／HOST 不得寫入店家層資料（DEF-326）")
class StoreLessSellerHostE2ETest {

    private static final String PASSWORD = "SecurePass123!";
    private static final String AUTH = "/v2/auth";
    private static final UUID SYSTEM_TENANT_ID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
    private static final String DENIED = "E-1007";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ShippingTemplateRepository shippingTemplateRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final String stamp = String.valueOf(System.currentTimeMillis());

    // 沒有店鋪的真實 SELLER／HOST（註冊 → 登入，原樣）
    private JsonNode sellerLogin;
    private JsonNode hostLogin;
    // 有真實店鋪租戶的 SELLER／HOST（註冊 → 寫入租戶 → 登入）
    private JsonNode storeSellerLogin;
    private JsonNode storeHostLogin;
    private UUID storeTenantId;
    // 店主（對照）與一般買家
    private String storeOwnerToken;
    private String buyerToken;

    @BeforeAll
    void seedFixtures() throws Exception {
        RestAssuredMockMvc.mockMvc(mockMvc);

        sellerLogin = registerAndLogin("def326-seller-" + stamp + "@example.com", "SELLER", null);
        hostLogin = registerAndLogin("def326-host-" + stamp + "@example.com", "HOST", null);

        Tenant store = tenantRepository.save(Tenant.builder()
                .name("DEF-326 對照店 " + stamp)
                .slug("def326-store-" + stamp)
                .contactEmail("def326-store-" + stamp + "@example.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        storeTenantId = store.getId();
        storeSellerLogin = registerAndLogin("def326-store-seller-" + stamp + "@example.com", "SELLER", storeTenantId);
        storeHostLogin = registerAndLogin("def326-store-host-" + stamp + "@example.com", "HOST", storeTenantId);

        User owner = userRepository.save(User.builder()
                .email("def326-owner-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("DEF-326 店主")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(storeTenantId)
                .build());
        storeOwnerToken = jwtTokenService.generateAccessToken(
                owner.getId(), owner.getEmail(), "STORE_OWNER", storeTenantId.toString());

        buyerToken = registerAndLogin("def326-buyer-" + stamp + "@example.com", "BUYER", null)
                .path("data").path("accessToken").asText();
    }

    @AfterAll
    void cleanUp() {
        // 修復前這些探針會在系統租戶真的建立資料；清掉，以免污染共用的測試資料庫（修復後這些語句不會刪到任何列）
        jdbcTemplate.update("DELETE FROM cms_pages WHERE slug LIKE ?", "def326-%-" + stamp);
        jdbcTemplate.update("DELETE FROM posts WHERE title LIKE ?", "DEF326 貼文 " + stamp + "%");
        jdbcTemplate.update("DELETE FROM shipping_templates WHERE name LIKE ?", "DEF326 運費 " + stamp + "%");
        for (UUID tenantId : new UUID[] {SYSTEM_TENANT_ID, storeTenantId}) {
            var listings = listingRepository.findByTenantId(tenantId).stream()
                    .filter(l -> l.getTitle() != null && l.getTitle().startsWith("DEF326 商品 " + stamp))
                    .toList();
            productRepository.deleteAllById(listings.stream().map(l -> l.getId()).toList());
            listingRepository.deleteAll(listings);
        }
        shippingTemplateRepository.findByTenantId(storeTenantId).forEach(shippingTemplateRepository::delete);
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

    // ───────────────────────── 簽發：以什麼角色運作 ─────────────────────────

    @Test
    @DisplayName("沒有店鋪的 SELLER／HOST 登入 → 以買家身分簽發（回應與 token 的 role 都是 BUYER）；資料庫的角色不動")
    void storeLessSellerAndHostAreIssuedAsBuyer() {
        for (JsonNode login : new JsonNode[] {sellerLogin, hostLogin}) {
            String token = login.path("data").path("accessToken").asText();
            assertThat(login.path("data").path("user").path("role").asText()).isEqualTo("BUYER");
            assertThat(jwtTokenService.getRole(token)).isEqualTo("BUYER");
        }
        assertThat(userRepository.findByEmail("def326-seller-" + stamp + "@example.com").orElseThrow().getRole())
                .as("users.role 不動：身分的有效值是簽發時依租戶推導的，開店核准時才由 AdminService 改成 STORE_OWNER")
                .isEqualTo(User.UserRole.SELLER);
        assertThat(userRepository.findByEmail("def326-host-" + stamp + "@example.com").orElseThrow().getRole())
                .isEqualTo(User.UserRole.HOST);
    }

    @Test
    @DisplayName("有真實店鋪租戶的 SELLER／HOST 登入 → 角色照舊（判斷依據是租戶，不是角色標籤）")
    void sellerAndHostWithARealStoreKeepTheirRole() {
        assertThat(storeSellerLogin.path("data").path("user").path("role").asText()).isEqualTo("SELLER");
        assertThat(jwtTokenService.getRole(storeSellerLogin.path("data").path("accessToken").asText()))
                .isEqualTo("SELLER");
        assertThat(storeHostLogin.path("data").path("user").path("role").asText()).isEqualTo("HOST");
        assertThat(jwtTokenService.getRole(storeHostLogin.path("data").path("accessToken").asText()))
                .isEqualTo("HOST");
    }

    @Test
    @DisplayName("換發 token（refresh）也是同一套：沒有店鋪的 SELLER 換發後仍是買家，有店鋪的仍是 SELLER")
    void refreshAppliesTheSameRule() throws Exception {
        assertThat(refreshedRole(sellerLogin)).isEqualTo("BUYER");
        assertThat(refreshedRole(storeSellerLogin)).isEqualTo("SELLER");
    }

    @Test
    @DisplayName("GET /v2/auth/me 與簽發一致：沒有店鋪的 SELLER 是 BUYER，有店鋪的是 SELLER")
    void currentUserReportsTheEffectiveRole() {
        given().header("Authorization", "Bearer " + sellerLogin.path("data").path("accessToken").asText())
                .when().get(AUTH + "/me")
                .then().statusCode(200)
                .body("data.userType", is("BUYER"));
        given().header("Authorization", "Bearer " + storeSellerLogin.path("data").path("accessToken").asText())
                .when().get(AUTH + "/me")
                .then().statusCode(200)
                .body("data.userType", is("SELLER"));
    }

    // ───────────────────────── 沒有店鋪的 SELLER：寫入／讀取店家層資料 ─────────────────────────

    @Test
    @DisplayName("商品：沒有店鋪的 SELLER 不得建立商品（原本會在系統租戶的買家目錄上架）；有店鋪的 SELLER 與店主照常")
    void storeLessSellerCannotCreateProducts() {
        denied(sellerToken(), "post", "/v2/products", productBody("A"));

        given().header("Authorization", "Bearer " + storeSellerToken())
                .contentType(MediaType.APPLICATION_JSON_VALUE).body(productBody("B"))
                .when().post("/v2/products")
                .then().statusCode(201);
        given().header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE).body(productBody("C"))
                .when().post("/v2/products")
                .then().statusCode(201);

        assertThat(listingRepository.findByTenantId(SYSTEM_TENANT_ID))
                .as("系統租戶（買家目錄）不得出現沒有店鋪的 SELLER 建立的商品")
                .noneMatch(l -> l.getTitle() != null && l.getTitle().startsWith("DEF326 商品 " + stamp));
    }

    @Test
    @DisplayName("運費模板：沒有店鋪的 SELLER 不得建立（結帳時對所有一般買家生效）；有店鋪的 SELLER 照常")
    void storeLessSellerCannotCreateShippingTemplates() {
        denied(sellerToken(), "post", "/v2/shipping-templates", shippingTemplateBody("A"));

        given().header("Authorization", "Bearer " + storeSellerToken())
                .contentType(MediaType.APPLICATION_JSON_VALUE).body(shippingTemplateBody("B"))
                .when().post("/v2/shipping-templates")
                .then().statusCode(200);

        assertThat(shippingTemplateRepository.findByTenantId(SYSTEM_TENANT_ID))
                .as("系統租戶的運費模板會套用在所有一般買家的結帳，不得被沒有店鋪的 SELLER 新增")
                .noneMatch(t -> t.getName() != null && t.getName().startsWith("DEF326 運費 " + stamp));
    }

    @Test
    @DisplayName("CMS：沒有店鋪的 SELLER 不得建立頁面（可佔用 slug、直接改線上內容）")
    void storeLessSellerCannotCreateCmsPages() {
        denied(sellerToken(), "post", "/v2/cms/pages", Map.of(
                "title", "DEF326 頁面", "slug", "def326-page-" + stamp, "pageType", "CUSTOM"));
    }

    @Test
    @DisplayName("貼文：沒有店鋪的 SELLER／HOST 不得發布（公開的 /v2/posts?tenantId=系統租戶 看得到）")
    void storeLessSellerAndHostCannotCreatePosts() {
        Map<String, Object> body = Map.of("title", "DEF326 貼文 " + stamp, "content", "內容");
        denied(sellerToken(), "post", "/v2/dashboard/posts", body);
        denied(hostToken(), "post", "/v2/dashboard/posts", body);
    }

    @Test
    @DisplayName("ERP：沒有店鋪的 SELLER 讀不到庫存台帳與供應商；有店鋪的 SELLER 與店主照常")
    void storeLessSellerCannotReadErp() {
        for (String path : new String[] {"/v2/dashboard/suppliers", "/v2/dashboard/inventory"}) {
            denied(sellerToken(), "get", path, null);
            given().header("Authorization", "Bearer " + storeSellerToken())
                    .when().get(path).then().statusCode(200);
            given().header("Authorization", "Bearer " + storeOwnerToken)
                    .when().get(path).then().statusCode(200);
        }
    }

    @Test
    @DisplayName("賣家儀表板：沒有店鋪的 SELLER 與一般買家都不行；真正的店主（STORE_OWNER）與有店鋪的 SELLER 可以")
    void sellerDashboardIsOnlyForStores() {
        denied(sellerToken(), "get", "/v2/seller/dashboard", null);
        denied(buyerToken, "get", "/v2/seller/dashboard", null);

        given().header("Authorization", "Bearer " + storeOwnerToken)
                .when().get("/v2/seller/dashboard")
                .then().statusCode(200);
        given().header("Authorization", "Bearer " + storeSellerToken())
                .when().get("/v2/seller/dashboard")
                .then().statusCode(200);
    }

    @Test
    @DisplayName("儀表板縱深防禦：換發前簽發、仍帶 SELLER 角色卻是系統租戶的舊 token，儀表板端點自己也擋（不依賴簽發）")
    void sellerDashboardRejectsAStaleSystemTenantToken() {
        UUID userId = userRepository.findByEmail("def326-seller-" + stamp + "@example.com").orElseThrow().getId();
        String staleToken = jwtTokenService.generateAccessToken(
                userId, "def326-seller-" + stamp + "@example.com", "SELLER", SYSTEM_TENANT_ID.toString());
        denied(staleToken, "get", "/v2/seller/dashboard", null);
    }

    // ───────────────────────── 沒有店鋪的 HOST ─────────────────────────

    @Test
    @DisplayName("房源：沒有店鋪的 HOST 不得更新／管理房源；有店鋪的 HOST 通過權限檢查（找不到這間房 → 404，而不是 403）")
    void storeLessHostCannotManageRooms() {
        UUID noSuchRoom = UUID.randomUUID();
        denied(hostToken(), "put", "/v2/rooms/" + noSuchRoom, Map.of());

        given().header("Authorization", "Bearer " + storeHostToken())
                .contentType(MediaType.APPLICATION_JSON_VALUE).body(Map.of())
                .when().put("/v2/rooms/" + noSuchRoom)
                .then().statusCode(404);
    }

    // ───────────────────────── helpers ─────────────────────────

    private String sellerToken() {
        return sellerLogin.path("data").path("accessToken").asText();
    }

    private String hostToken() {
        return hostLogin.path("data").path("accessToken").asText();
    }

    private String storeSellerToken() {
        return storeSellerLogin.path("data").path("accessToken").asText();
    }

    private String storeHostToken() {
        return storeHostLogin.path("data").path("accessToken").asText();
    }

    /** 被擋下：403 且錯誤碼是「權限不足」（不是別的 403，例如功能未啟用 E-2004）。 */
    private void denied(String token, String method, String path, Object body) {
        var request = given().header("Authorization", "Bearer " + token);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON_VALUE).body(body);
        }
        var response = switch (method) {
            case "post" -> request.when().post(path);
            case "put" -> request.when().put(path);
            default -> request.when().get(path);
        };
        response.then().statusCode(403).body("code", is(DENIED));
    }

    private Map<String, Object> productBody(String tag) {
        return Map.of("title", "DEF326 商品 " + stamp + tag, "category", "test", "basePrice", 100);
    }

    private Map<String, Object> shippingTemplateBody(String tag) {
        return Map.of("name", "DEF326 運費 " + stamp + tag, "feeType", "FIXED", "fixedAmount", 60);
    }

    private String refreshedRole(JsonNode login) throws Exception {
        String refreshToken = login.path("data").path("refreshToken").asText();
        String response = given().contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("refreshToken", refreshToken))
                .when().post(AUTH + "/refresh")
                .then().statusCode(200).extract().asString();
        return objectMapper.readTree(response).path("data").path("user").path("role").asText();
    }

    /**
     * 註冊（userType 指定）、必要時把使用者寫入店鋪租戶（登入前，所以 token 帶店鋪租戶），再登入。回傳完整的登入回應。
     */
    private JsonNode registerAndLogin(String email, String userType, UUID tenantId) throws Exception {
        given().contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("email", email, "password", PASSWORD, "userType", userType))
                .when().post(AUTH + "/register")
                .then().statusCode(201);
        if (tenantId != null) {
            User user = userRepository.findByEmail(email).orElseThrow();
            user.setTenantId(tenantId);
            userRepository.save(user);
        }
        String loginResponse = given().contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("email", email, "password", PASSWORD))
                .when().post(AUTH + "/login")
                .then().statusCode(200).extract().asString();
        return objectMapper.readTree(loginResponse);
    }
}
