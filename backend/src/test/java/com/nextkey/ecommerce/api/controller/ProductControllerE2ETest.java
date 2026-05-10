package com.nextkey.ecommerce.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.LoginRequest;
import com.nextkey.ecommerce.api.dto.ProductDto;
import com.nextkey.ecommerce.api.dto.RegisterRequest;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.*;

/**
 * Product Controller API E2E 測試 (API-M05-001 ~ API-M05-010)
 *
 * 使用 REST Assured 框架進行完整的 HTTP 層 E2E 測試
 * 測試範圍：
 * - API-M05-001: 取得商品列表-成功
 * - API-M05-002: 取得商品列表-分頁和篩選
 * - API-M05-003: 取得商品詳情-成功
 * - API-M05-004: 取得商品詳情-不存在
 * - API-M05-005: 建立商品-成功
 * - API-M05-006: 建立商品-缺少必填欄位
 * - API-M05-007: 更新商品-成功
 * - API-M05-008: 刪除商品-成功
 * - API-M05-009: 未授權操作-需要 Bearer Token
 * - API-M05-010: 無效權限-權限不足
 *
 * 注意：此測試使用真實的 PostgreSQL 和 Redis，確保完整的 E2E 測試覆蓋
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("API-M05 E2E: Product Controller REST Assured E2E 測試")
class ProductControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private static final String BASE_URL = "/v2/products";
    private static final String AUTH_URL = "/v2/auth";
    private static final String TEST_PASSWORD = "SecurePass123!";

    // 測試用的 access token（每次測試前登入獲取）
    private String accessToken;
    private String userEmail;
    private static UUID testTenantId;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);

        // 每次測試前建立新用戶並登入獲取 token
        userEmail = "product-test-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000) + "@example.com";

        try {
            // 創建測試用的 Tenant
            com.nextkey.ecommerce.domain.model.tenant.Tenant testTenant =
                    com.nextkey.ecommerce.domain.model.tenant.Tenant.builder()
                            .name("Test Tenant for Product E2E")
                            .slug("test-product-tenant-" + System.currentTimeMillis())
                            .contactEmail("product-test@tenant.com")
                            .contactPhone("+886-123456789")
                            .status(com.nextkey.ecommerce.domain.model.tenant.Tenant.TenantStatus.ACTIVE)
                            .build();
            testTenant = tenantRepository.save(testTenant);
            testTenantId = testTenant.getId();

            // 註冊
            given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(RegisterRequest.builder()
                            .email(userEmail)
                            .password(TEST_PASSWORD)
                            .userType("SELLER")
                            .build())
                    .when()
                    .post(AUTH_URL + "/register")
                    .then()
                    .statusCode(201);

            // 登入取得 access token
            String loginResponse = given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(LoginRequest.builder()
                            .email(userEmail)
                            .password(TEST_PASSWORD)
                            .build())
                    .when()
                    .post(AUTH_URL + "/login")
                    .then()
                    .statusCode(200)
                    .extract()
                    .asString();

            JsonNode loginJson = objectMapper.readTree(loginResponse);
            accessToken = loginJson.path("data").path("accessToken").asText();

            // 更新用戶的 tenantId
            userRepository.findByEmail(userEmail).ifPresent(user -> {
                user.setTenantId(testTenantId);
                userRepository.save(user);
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup test user: " + e.getMessage(), e);
        }
    }

    @AfterEach
    void tearDown() {
        // 清理測試用戶
        userRepository.findByEmail(userEmail).ifPresent(user -> userRepository.delete(user));
    }

    // ── API-M05-001: 取得商品列表-成功 ─────────────────────────────

    @Test
    @Order(1)
    @DisplayName("API-M05-001: GET /v2/products - 成功取得商品列表，返回 200")
    void getProducts_success_returns200() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get(BASE_URL)
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.content", notNullValue())
                .body("data.pageable", notNullValue());

        System.out.println("✅ API-M05-001 PASSED: 成功取得商品列表");
    }

    // ── API-M05-002: 取得商品列表-分頁和篩選 ───────────────────────

    @Test
    @Order(2)
    @DisplayName("API-M05-002: GET /v2/products - 分頁和篩選參數有效，返回 200")
    void getProducts_withPaginationAndFilter_returns200() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .param("page", 0)
                .param("size", 10)
                .param("category", "Electronics")
                .param("brand", "Apple")
                .param("keyword", "iPhone")
                .param("sortBy", "createdAt")
                .param("sortDir", "DESC")
                .when()
                .get(BASE_URL)
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.content", notNullValue())
                .body("data.size", equalTo(10));

        System.out.println("✅ API-M05-002 PASSED: 分頁和篩選參數有效");
    }

    // ── API-M05-003: 取得商品詳情-成功 ────────────────────────────

    @Test
    @Order(3)
    @DisplayName("API-M05-003: GET /v2/products/{listingId} - 成功取得商品詳情，返回 200")
    void getProduct_success_returns200() throws Exception {
        // 先建立一個商品
        String createResponse = given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(ProductDto.CreateRequest.builder()
                        .title("Test Product for Detail")
                        .description("This is a test product description")
                        .category("Electronics")
                        .brand("TestBrand")
                        .basePrice(new BigDecimal("999.99"))
                        .coverImageUrl("https://example.com/image.jpg")
                        .weightGrams(500)
                        .dimensionsCm("10x10x10")
                        .build())
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(201)
                .extract()
                .asString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        UUID listingId = UUID.fromString(createJson.path("data").path("listingId").asText());

        // 取得商品詳情
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get(BASE_URL + "/" + listingId)
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.listingId", equalTo(listingId.toString()))
                .body("data.title", is("Test Product for Detail"))
                .body("data.category", is("Electronics"));

        System.out.println("✅ API-M05-003 PASSED: 成功取得商品詳情");
    }

    // ── API-M05-004: 取得商品詳情-不存在 ──────────────────────────

    @Test
    @Order(4)
    @DisplayName("API-M05-004: GET /v2/products/{listingId} - 商品不存在，返回 404")
    void getProduct_notFound_returns404() {
        UUID nonExistentId = UUID.randomUUID();

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get(BASE_URL + "/" + nonExistentId)
                .then()
                .statusCode(404);

        System.out.println("✅ API-M05-004 PASSED: 商品不存在時返回 404");
    }

    // ── API-M05-005: 建立商品-成功 ────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("API-M05-005: POST /v2/products - 成功建立商品，返回 201")
    void createProduct_success_returns201() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(ProductDto.CreateRequest.builder()
                        .title("New Test Product")
                        .description("Description for new test product")
                        .category("Electronics")
                        .brand("TestBrand")
                        .basePrice(new BigDecimal("299.99"))
                        .coverImageUrl("https://example.com/new-image.jpg")
                        .weightGrams(300)
                        .dimensionsCm("15x10x5")
                        .build())
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(201)
                .body("success", is(true))
                .body("message", is("Product created successfully"))
                .body("data.listingId", notNullValue())
                .body("data.title", is("New Test Product"))
                .body("data.basePrice", equalTo(299.99f))
                .body("data.status", is("ACTIVE"));

        System.out.println("✅ API-M05-005 PASSED: 成功建立商品");
    }

    // ── API-M05-006: 建立商品-缺少必填欄位 ────────────────────────

    @Test
    @Order(6)
    @DisplayName("API-M05-006: POST /v2/products - 缺少必填欄位，返回 400")
    void createProduct_missingRequiredFields_returns400() {
        // 缺少 title
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(ProductDto.CreateRequest.builder()
                        .description("Description without title")
                        .category("Electronics")
                        .basePrice(new BigDecimal("99.99"))
                        .build())
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(400);

        // 缺少 category
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(ProductDto.CreateRequest.builder()
                        .title("Product Without Category")
                        .basePrice(new BigDecimal("99.99"))
                        .build())
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(400);

        // 缺少 basePrice
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(ProductDto.CreateRequest.builder()
                        .title("Product Without Price")
                        .category("Electronics")
                        .build())
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(400);

        System.out.println("✅ API-M05-006 PASSED: 缺少必填欄位時返回 400");
    }

    // ── API-M05-007: 更新商品-成功 ────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("API-M05-007: PUT /v2/products/{listingId} - 成功更新商品，返回 200")
    void updateProduct_success_returns200() throws Exception {
        // 先建立商品
        String createResponse = given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(ProductDto.CreateRequest.builder()
                        .title("Original Title")
                        .description("Original Description")
                        .category("Electronics")
                        .brand("OriginalBrand")
                        .basePrice(new BigDecimal("199.99"))
                        .build())
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(201)
                .extract()
                .asString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        UUID listingId = UUID.fromString(createJson.path("data").path("listingId").asText());

        // 更新商品
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(ProductDto.UpdateRequest.builder()
                        .title("Updated Title")
                        .description("Updated Description")
                        .basePrice(new BigDecimal("299.99"))
                        .build())
                .when()
                .put(BASE_URL + "/" + listingId)
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", is("Product updated successfully"))
                .body("data.title", is("Updated Title"))
                .body("data.basePrice", equalTo(299.99f));

        System.out.println("✅ API-M05-007 PASSED: 成功更新商品");
    }

    // ── API-M05-008: 刪除商品-成功 ────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("API-M05-008: DELETE /v2/products/{listingId} - 成功刪除商品，返回 200")
    void deleteProduct_success_returns200() throws Exception {
        // 先建立商品
        String createResponse = given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(ProductDto.CreateRequest.builder()
                        .title("Product To Delete")
                        .category("Electronics")
                        .basePrice(new BigDecimal("99.99"))
                        .build())
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(201)
                .extract()
                .asString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        UUID listingId = UUID.fromString(createJson.path("data").path("listingId").asText());

        // 刪除商品
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .delete(BASE_URL + "/" + listingId)
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", is("Product deleted successfully"));

        System.out.println("✅ API-M05-008 PASSED: 成功刪除商品");
    }

    // ── API-M05-009: 未授權操作-需要 Bearer Token ─────────────────

    @Test
    @Order(9)
    @DisplayName("API-M05-009: GET /v2/products - 未授權，返回 401 或 403")
    void getProducts_unauthorized_returns401or403() {
        // 不帶 Authorization header
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get(BASE_URL)
                .then()
                .statusCode(anyOf(is(401), is(403)));

        // 使用無效的 token
        given()
                .header("Authorization", "Bearer invalid.jwt.token")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get(BASE_URL)
                .then()
                .statusCode(anyOf(is(401), is(403)));

        System.out.println("✅ API-M05-009 PASSED: 未授權時返回 401 或 403");
    }

    // ── API-M05-010: 無效權限-權限不足 ────────────────────────────

    @Test
    @Order(10)
    @DisplayName("API-M05-010: POST /v2/products - 使用 BUYER 角色建立商品，返回 403")
    void createProduct_withBuyerRole_returns403() throws Exception {
        // 使用 BUYER 角色登入
        String buyerEmail = "buyer-test-" + System.currentTimeMillis() + "@example.com";

        try {
            // 註冊 BUYER
            given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(RegisterRequest.builder()
                            .email(buyerEmail)
                            .password(TEST_PASSWORD)
                            .userType("BUYER")
                            .build())
                    .when()
                    .post(AUTH_URL + "/register")
                    .then()
                    .statusCode(201);

            // 登入取得 BUYER token
            String loginResponse = given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(LoginRequest.builder()
                            .email(buyerEmail)
                            .password(TEST_PASSWORD)
                            .build())
                    .when()
                    .post(AUTH_URL + "/login")
                    .then()
                    .statusCode(200)
                    .extract()
                    .asString();

            JsonNode loginJson = objectMapper.readTree(loginResponse);
            String buyerToken = loginJson.path("data").path("accessToken").asText();

            // 嘗試用 BUYER 角色建立商品
            given()
                    .header("Authorization", "Bearer " + buyerToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(ProductDto.CreateRequest.builder()
                            .title("Buyer Product Attempt")
                            .category("Electronics")
                            .basePrice(new BigDecimal("99.99"))
                            .build())
                    .when()
                    .post(BASE_URL)
                    .then()
                    .statusCode(403);

            System.out.println("✅ API-M05-010 PASSED: BUYER 角色無法建立商品");
        } finally {
            userRepository.findByEmail(buyerEmail).ifPresent(user -> userRepository.delete(user));
        }
    }
}
