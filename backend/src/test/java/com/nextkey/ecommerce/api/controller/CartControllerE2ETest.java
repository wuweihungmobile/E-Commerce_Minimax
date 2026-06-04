package com.nextkey.ecommerce.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.CartDto;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.*;

/**
 * Cart Controller API E2E 測試 (API-M07-001 ~ API-M07-012)
 *
 * 使用 REST Assured 框架進行完整的 HTTP 層 E2E 測試
 * 測試範圍：
 * - API-M07-001: 取得購物車-成功
 * - API-M07-002: 加入購物車-成功
 * - API-M07-003: 加入購物車-缺少必填欄位
 * - API-M07-004: 更新購物車項目-成功
 * - API-M07-005: 更新購物車項目-數量為零
 * - API-M07-006: 移除購物車項目-成功
 * - API-M07-007: 清空購物車-成功
 * - API-M07-008: 取得購物車項目數量-成功
 * - API-M07-009: 未授權操作
 * - API-M07-010: 加入多個商品到購物車
 * - API-M07-011: 更新不存在的購物車項目
 * - API-M07-012: 移除不存在的購物車項目
 *
 * 注意：此測試使用真實的 PostgreSQL 和 Redis，確保完整的 E2E 測試覆蓋
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.nextkey.ecommerce.integration.IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("API-M07 E2E: Cart Controller REST Assured E2E 測試")
class CartControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private static final String CART_URL = "/v2/cart";
    private static final String PRODUCT_URL = "/v2/products";
    private static final String AUTH_URL = "/v2/auth";
    private static final String TEST_PASSWORD = "SecurePass123!";

    private String buyerToken;
    private String sellerToken;
    private String buyerEmail;
    private String sellerEmail;
    private static UUID testTenantId;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);

        buyerEmail = "cart-buyer-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000) + "@example.com";
        sellerEmail = "cart-seller-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000) + "@example.com";

        try {
            // 創建測試用的 Tenant
            com.nextkey.ecommerce.domain.model.tenant.Tenant testTenant =
                    com.nextkey.ecommerce.domain.model.tenant.Tenant.builder()
                            .name("Test Tenant for Cart E2E")
                            .slug("test-cart-tenant-" + System.currentTimeMillis())
                            .contactEmail("cart-test@tenant.com")
                            .contactPhone("+886-123456789")
                            .status(com.nextkey.ecommerce.domain.model.tenant.Tenant.TenantStatus.ACTIVE)
                            .build();
            testTenant = tenantRepository.save(testTenant);
            testTenantId = testTenant.getId();

            // 註冊 SELLER 用戶（用於建立商品）
            given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(RegisterRequest.builder()
                            .email(sellerEmail)
                            .password(TEST_PASSWORD)
                            .userType("SELLER")
                            .build())
                    .when()
                    .post(AUTH_URL + "/register")
                    .then()
                    .statusCode(201);

            // 登入取得 SELLER token
            String sellerLoginResponse = given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(LoginRequest.builder()
                            .email(sellerEmail)
                            .password(TEST_PASSWORD)
                            .build())
                    .when()
                    .post(AUTH_URL + "/login")
                    .then()
                    .statusCode(200)
                    .extract()
                    .asString();

            JsonNode sellerLoginJson = objectMapper.readTree(sellerLoginResponse);
            sellerToken = sellerLoginJson.path("data").path("accessToken").asText();

            // 註冊 BUYER 用戶（用於購物車操作）
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
            String buyerLoginResponse = given()
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

            JsonNode buyerLoginJson = objectMapper.readTree(buyerLoginResponse);
            buyerToken = buyerLoginJson.path("data").path("accessToken").asText();

            // 更新 SELLER 和 BUYER 用戶的 tenantId
            userRepository.findByEmail(sellerEmail).ifPresent(user -> {
                user.setTenantId(testTenantId);
                userRepository.save(user);
            });
            userRepository.findByEmail(buyerEmail).ifPresent(user -> {
                user.setTenantId(testTenantId);
                userRepository.save(user);
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup test user: " + e.getMessage(), e);
        }
    }

    @AfterEach
    void tearDown() {
        // 先清空購物車（確保測試之間的數據隔離）
        try {
            given()
                    .header("Authorization", "Bearer " + buyerToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .when()
                    .delete(CART_URL)
                    .then()
                    .statusCode(anyOf(is(200), is(404))); // 404 表示購物車本來就是空的
        } catch (Exception e) {
            // 忽略錯誤，繼續清理用戶
            System.out.println("Warning: Failed to clear cart during teardown: " + e.getMessage());
        }

        // 清理測試用戶
        userRepository.findByEmail(buyerEmail).ifPresent(user -> userRepository.delete(user));
        userRepository.findByEmail(sellerEmail).ifPresent(user -> userRepository.delete(user));

        // 清理測試 Tenant
        if (testTenantId != null) {
            tenantRepository.findById(testTenantId).ifPresent(tenant -> tenantRepository.delete(tenant));
        }
    }

    // ── API-M07-001: 取得購物車-成功 ──────────────────────────────

    @Test
    @Order(1)
    @DisplayName("API-M07-001: GET /v2/cart - 成功取得空購物車，返回 200")
    void getCart_emptyCart_returns200() {
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get(CART_URL)
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.userId", notNullValue())
                .body("data.items", notNullValue())
                .body("data.totalItems", equalTo(0))
                .body("data.totalAmount", equalTo(0));

        System.out.println("✅ API-M07-001 PASSED: 成功取得空購物車");
    }

    // ── API-M07-002: 加入購物車-成功 ─────────────────────────────

    @Test
    @Order(2)
    @DisplayName("API-M07-002: POST /v2/cart/items - 成功加入商品到購物車，返回 200")
    void addItem_success_returns200() throws Exception {
        // 先用 SELLER 建立一個商品
        String createResponse = given()
                .header("Authorization", "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(ProductDto.CreateRequest.builder()
                        .title("Test Product for Cart")
                        .category("Electronics")
                        .basePrice(new BigDecimal("599.99"))
                        .build())
                .when()
                .post(PRODUCT_URL)
                .then()
                .statusCode(201)
                .extract()
                .asString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        UUID listingId = UUID.fromString(createJson.path("data").path("listingId").asText());

        // 加入購物車
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.AddItemRequest.builder()
                        .listingId(listingId)
                        .quantity(2)
                        .build())
                .when()
                .post(CART_URL + "/items")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", is("Item added to cart"))
                .body("data.item.listingId", equalTo(listingId.toString()))
                .body("data.item.quantity", equalTo(2))
                .body("data.totalItemsInCart", greaterThanOrEqualTo(2));

        System.out.println("✅ API-M07-002 PASSED: 成功加入商品到購物車");
    }

    // ── API-M07-003: 加入購物車-缺少必填欄位 ─────────────────────

    @Test
    @Order(3)
    @DisplayName("API-M07-003: POST /v2/cart/items - 缺少必填欄位，返回 400")
    void addItem_missingRequiredFields_returns400() throws Exception {
        // 先用 SELLER 建立一個商品
        String createResponse = given()
                .header("Authorization", "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(ProductDto.CreateRequest.builder()
                        .title("Test Product")
                        .category("Electronics")
                        .basePrice(new BigDecimal("99.99"))
                        .build())
                .when()
                .post(PRODUCT_URL)
                .then()
                .statusCode(201)
                .extract()
                .asString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        UUID listingId = UUID.fromString(createJson.path("data").path("listingId").asText());

        // 缺少 listingId
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.AddItemRequest.builder()
                        .quantity(1)
                        .build())
                .when()
                .post(CART_URL + "/items")
                .then()
                .statusCode(400);

        // 缺少 quantity
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.AddItemRequest.builder()
                        .listingId(listingId)
                        .build())
                .when()
                .post(CART_URL + "/items")
                .then()
                .statusCode(400);

        // quantity 小於 1
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.AddItemRequest.builder()
                        .listingId(listingId)
                        .quantity(0)
                        .build())
                .when()
                .post(CART_URL + "/items")
                .then()
                .statusCode(400);

        System.out.println("✅ API-M07-003 PASSED: 缺少必填欄位時返回 400");
    }

    // ── API-M07-004: 更新購物車項目-成功 ─────────────────────────

    @Test
    @Order(4)
    @DisplayName("API-M07-004: PUT /v2/cart/items/{listingId} - 成功更新購物車項目數量，返回 200")
    void updateItem_success_returns200() throws Exception {
        // 先用 SELLER 建立商品並加入購物車
        String createResponse = given()
                .header("Authorization", "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(ProductDto.CreateRequest.builder()
                        .title("Product to Update")
                        .category("Electronics")
                        .basePrice(new BigDecimal("199.99"))
                        .build())
                .when()
                .post(PRODUCT_URL)
                .then()
                .statusCode(201)
                .extract()
                .asString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        UUID listingId = UUID.fromString(createJson.path("data").path("listingId").asText());

        // 加入購物車
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.AddItemRequest.builder()
                        .listingId(listingId)
                        .quantity(1)
                        .build())
                .when()
                .post(CART_URL + "/items")
                .then()
                .statusCode(200);

        // 更新數量
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.UpdateItemRequest.builder()
                        .quantity(5)
                        .build())
                .when()
                .put(CART_URL + "/items/" + listingId)
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.quantity", equalTo(5));

        System.out.println("✅ API-M07-004 PASSED: 成功更新購物車項目數量");
    }

    // ── API-M07-005: 更新購物車項目-數量為零 ─────────────────────

    @Test
    @Order(5)
    @DisplayName("API-M07-005: PUT /v2/cart/items/{listingId} - 數量為零應返回 400")
    void updateItem_zeroQuantity_returns400() throws Exception {
        // 先用 SELLER 建立商品並加入購物車
        String createResponse = given()
                .header("Authorization", "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(ProductDto.CreateRequest.builder()
                        .title("Zero Quantity Test")
                        .category("Electronics")
                        .basePrice(new BigDecimal("99.99"))
                        .build())
                .when()
                .post(PRODUCT_URL)
                .then()
                .statusCode(201)
                .extract()
                .asString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        UUID listingId = UUID.fromString(createJson.path("data").path("listingId").asText());

        // 加入購物車
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.AddItemRequest.builder()
                        .listingId(listingId)
                        .quantity(2)
                        .build())
                .when()
                .post(CART_URL + "/items")
                .then()
                .statusCode(200);

        // 嘗試將數量更新為 0（應失敗）
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.UpdateItemRequest.builder()
                        .quantity(0)
                        .build())
                .when()
                .put(CART_URL + "/items/" + listingId)
                .then()
                .statusCode(400);

        System.out.println("✅ API-M07-005 PASSED: 數量為零時返回 400");
    }

    // ── API-M07-006: 移除購物車項目-成功 ─────────────────────────

    @Test
    @Order(6)
    @DisplayName("API-M07-006: DELETE /v2/cart/items/{listingId} - 成功移除購物車項目，返回 200")
    void removeItem_success_returns200() throws Exception {
        // 先用 SELLER 建立商品並加入購物車
        String createResponse = given()
                .header("Authorization", "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(ProductDto.CreateRequest.builder()
                        .title("Product to Remove")
                        .category("Electronics")
                        .basePrice(new BigDecimal("49.99"))
                        .build())
                .when()
                .post(PRODUCT_URL)
                .then()
                .statusCode(201)
                .extract()
                .asString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        UUID listingId = UUID.fromString(createJson.path("data").path("listingId").asText());

        // 加入購物車
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.AddItemRequest.builder()
                        .listingId(listingId)
                        .quantity(1)
                        .build())
                .when()
                .post(CART_URL + "/items")
                .then()
                .statusCode(200);

        // 移除購物車項目
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .delete(CART_URL + "/items/" + listingId)
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", is("Item removed from cart"));

        System.out.println("✅ API-M07-006 PASSED: 成功移除購物車項目");
    }

    // ── API-M07-007: 清空購物車-成功 ─────────────────────────────

    @Test
    @Order(7)
    @DisplayName("API-M07-007: DELETE /v2/cart - 成功清空購物車，返回 200")
    void clearCart_success_returns200() throws Exception {
        // 用 SELLER 加入多個商品到購物車
        for (int i = 0; i < 3; i++) {
            String createResponse = given()
                    .header("Authorization", "Bearer " + sellerToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(ProductDto.CreateRequest.builder()
                            .title("Cart Product " + i)
                            .category("Electronics")
                            .basePrice(new BigDecimal("99.99"))
                            .build())
                    .when()
                    .post(PRODUCT_URL)
                    .then()
                    .statusCode(201)
                    .extract()
                    .asString();

            JsonNode createJson = objectMapper.readTree(createResponse);
            UUID listingId = UUID.fromString(createJson.path("data").path("listingId").asText());

            given()
                    .header("Authorization", "Bearer " + buyerToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(CartDto.AddItemRequest.builder()
                            .listingId(listingId)
                            .quantity(1)
                            .build())
                    .when()
                    .post(CART_URL + "/items")
                    .then()
                    .statusCode(200);
        }

        // 清空購物車
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .delete(CART_URL)
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", is("Cart cleared"));

        // 驗證購物車已清空
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get(CART_URL)
                .then()
                .statusCode(200)
                .body("data.totalItems", equalTo(0))
                .body("data.items", hasSize(0));

        System.out.println("✅ API-M07-007 PASSED: 成功清空購物車");
    }

    // ── API-M07-008: 取得購物車項目數量-成功 ─────────────────────

    @Test
    @Order(8)
    @DisplayName("API-M07-008: GET /v2/cart/count - 成功取得購物車項目數量，返回 200")
    void getCartCount_success_returns200() throws Exception {
        // 先用 SELLER 加入商品到購物車
        String createResponse = given()
                .header("Authorization", "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(ProductDto.CreateRequest.builder()
                        .title("Count Test Product")
                        .category("Electronics")
                        .basePrice(new BigDecimal("29.99"))
                        .build())
                .when()
                .post(PRODUCT_URL)
                .then()
                .statusCode(201)
                .extract()
                .asString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        UUID listingId = UUID.fromString(createJson.path("data").path("listingId").asText());

        // 加入 3 個到購物車
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.AddItemRequest.builder()
                        .listingId(listingId)
                        .quantity(3)
                        .build())
                .when()
                .post(CART_URL + "/items")
                .then()
                .statusCode(200);

        // 取得購物車數量
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get(CART_URL + "/count")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data", greaterThanOrEqualTo(3));

        System.out.println("✅ API-M07-008 PASSED: 成功取得購物車項目數量");
    }

    // ── API-M07-009: 未授權操作 ──────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("API-M07-009: GET /v2/cart - 未授權，返回 401 或 403")
    void getCart_unauthorized_returns401or403() {
        // 不帶 Authorization header
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get(CART_URL)
                .then()
                .statusCode(anyOf(is(401), is(403)));

        // 使用無效的 token
        given()
                .header("Authorization", "Bearer invalid.jwt.token")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get(CART_URL)
                .then()
                .statusCode(anyOf(is(401), is(403)));

        System.out.println("✅ API-M07-009 PASSED: 未授權時返回 401 或 403");
    }

    // ── API-M07-010: 加入多個商品到購物車 ────────────────────────

    @Test
    @Order(10)
    @DisplayName("API-M07-010: POST /v2/cart/items - 加入多個不同商品到購物車")
    void addMultipleItems_success() throws Exception {
        UUID[] listingIds = new UUID[3];

        // 用 SELLER 建立 3 個商品
        for (int i = 0; i < 3; i++) {
            String createResponse = given()
                    .header("Authorization", "Bearer " + sellerToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(ProductDto.CreateRequest.builder()
                            .title("Multi Product " + i)
                            .category("Category" + i)
                            .basePrice(new BigDecimal(100 + i * 10).setScale(2, RoundingMode.HALF_UP))
                            .build())
                    .when()
                    .post(PRODUCT_URL)
                    .then()
                    .statusCode(201)
                    .extract()
                    .asString();

            JsonNode createJson = objectMapper.readTree(createResponse);
            listingIds[i] = UUID.fromString(createJson.path("data").path("listingId").asText());
        }

        // 加入第一個商品
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.AddItemRequest.builder()
                        .listingId(listingIds[0])
                        .quantity(1)
                        .build())
                .when()
                .post(CART_URL + "/items")
                .then()
                .statusCode(200);

        // 加入第二個商品
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.AddItemRequest.builder()
                        .listingId(listingIds[1])
                        .quantity(2)
                        .build())
                .when()
                .post(CART_URL + "/items")
                .then()
                .statusCode(200);

        // 加入第三個商品
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.AddItemRequest.builder()
                        .listingId(listingIds[2])
                        .quantity(3)
                        .build())
                .when()
                .post(CART_URL + "/items")
                .then()
                .statusCode(200);

        // 驗證購物車有 3 個不同商品，共 6 個項目
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get(CART_URL)
                .then()
                .statusCode(200)
                .body("data.items", hasSize(3))
                .body("data.totalItems", equalTo(6));

        System.out.println("✅ API-M07-010 PASSED: 成功加入多個商品到購物車");
    }

    // ── API-M07-011: 更新不存在的購物車項目 ──────────────────────

    @Test
    @Order(11)
    @DisplayName("API-M07-011: PUT /v2/cart/items/{listingId} - 更新不存在的項目")
    void updateNonExistentItem_returns404() {
        UUID nonExistentId = UUID.randomUUID();

        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.UpdateItemRequest.builder()
                        .quantity(5)
                        .build())
                .when()
                .put(CART_URL + "/items/" + nonExistentId)
                .then()
                .statusCode(404);

        System.out.println("✅ API-M07-011 PASSED: 更新不存在的項目返回 404");
    }

    // ── API-M07-012: 移除不存在的購物車項目 ──────────────────────

    @Test
    @Order(12)
    @DisplayName("API-M07-012: DELETE /v2/cart/items/{listingId} - 移除不存在的項目")
    void removeNonExistentItem_returns404() {
        UUID nonExistentId = UUID.randomUUID();

        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .delete(CART_URL + "/items/" + nonExistentId)
                .then()
                .statusCode(404);

        System.out.println("✅ API-M07-012 PASSED: 移除不存在的項目返回 404");
    }
}
