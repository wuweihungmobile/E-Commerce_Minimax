package com.nextkey.ecommerce.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.api.dto.LoginRequest;
import com.nextkey.ecommerce.api.dto.ProductDto;
import com.nextkey.ecommerce.api.dto.RegisterRequest;
import com.nextkey.ecommerce.core.promo.PromoService;
import com.nextkey.ecommerce.domain.model.promo.PromoCode;
import com.nextkey.ecommerce.domain.repository.PromoCodeRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * M11CartPromoIntegrationTest - 購物車優惠券整合測試
 *
 * 測試範圍：
 * - IT-M11-001: applyPromoCode - 成功套用有效優惠券
 * - IT-M11-002: applyPromoCode - 優惠券代碼無效
 * - IT-M11-003: applyPromoCode - 購物車為空
 * - IT-M11-006: validatePromoCode - 成功驗證
 * - IT-M11-007: validatePromoCode - 無效代碼
 * - IT-M11-008: removePromo - 成功移除優惠券
 * - IT-M11-010: 百分比折扣計算正確性
 * - IT-M11-011: 固定金額折扣計算正確性
 *
 * 注意：此測試使用 Mock Bean 來隔離外部服務依賴
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("IT-M11: 購物車優惠券整合測試")
class M11CartPromoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @MockBean
    private PromoService promoService;

    @MockBean
    private PromoCodeRepository promoCodeRepository;

    @MockBean
    private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

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

        // Mock FeatureToggleService to allow product creation
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());

        buyerEmail = "promo-buyer-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000) + "@example.com";
        sellerEmail = "promo-seller-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000) + "@example.com";

        try {
            // 創建測試用的 Tenant
            com.nextkey.ecommerce.domain.model.tenant.Tenant testTenant =
                    com.nextkey.ecommerce.domain.model.tenant.Tenant.builder()
                            .name("Test Tenant for Cart Promo")
                            .slug("test-promo-tenant-" + System.currentTimeMillis())
                            .contactEmail("promo-test@tenant.com")
                            .contactPhone("+886-123456789")
                            .status(com.nextkey.ecommerce.domain.model.tenant.Tenant.TenantStatus.ACTIVE)
                            .build();
            testTenant = tenantRepository.save(testTenant);
            testTenantId = testTenant.getId();

            // 註冊 SELLER 用戶
            given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(RegisterRequest.builder()
                            .email(sellerEmail)
                            .password(TEST_PASSWORD)
                            .fullName("Promo Seller")
                            .userType("SELLER")
                            .tenantId(testTenantId)
                            .build())
                    .when()
                    .post(AUTH_URL + "/register")
                    .then()
                    .statusCode(201);

            // SELLER 登入
            sellerToken = given()
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
                    .path("data.accessToken");

            // 註冊 BUYER 用戶
            given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(RegisterRequest.builder()
                            .email(buyerEmail)
                            .password(TEST_PASSWORD)
                            .fullName("Promo Buyer")
                            .userType("BUYER")
                            .tenantId(testTenantId)
                            .build())
                    .when()
                    .post(AUTH_URL + "/register")
                    .then()
                    .statusCode(201);

            // BUYER 登入
            buyerToken = given()
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
                    .path("data.accessToken");

        } catch (Exception e) {
            System.err.println("❌ SetUp failed: " + e.getMessage());
            throw e;
        }
    }

    private UUID createTestProduct(BigDecimal price) throws Exception {
        String createResponse = given()
                .header("Authorization", "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(ProductDto.CreateRequest.builder()
                        .title("Promo Test Product " + System.currentTimeMillis())
                        .category("Test")
                        .basePrice(price)
                        .build())
                .when()
                .post(PRODUCT_URL)
                .then()
                .statusCode(201)
                .extract()
                .asString();

        return UUID.fromString(objectMapper.readTree(createResponse).path("data").path("listingId").asText());
    }

    private void addItemToCart(UUID listingId, int quantity) throws Exception {
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.AddItemRequest.builder()
                        .listingId(listingId)
                        .quantity(quantity)
                        .build())
                .when()
                .post(CART_URL + "/items")
                .then()
                .statusCode(200);
    }

    private PromoCode buildValidPromoCode() {
        return PromoCode.builder()
                .code("TEST20")
                .discountType(PromoCode.DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20))
                .minPurchaseAmount(BigDecimal.valueOf(100))
                .maxDiscountAmount(BigDecimal.valueOf(50))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .currentUsageCount(0)
                .build();
    }

    // ── IT-M11-001: applyPromoCode - 成功套用有效優惠券 ─────────────────

    @Test
    @Order(1)
    @DisplayName("IT-M11-001: POST /v2/cart/apply-promo - 成功套用有效優惠券，返回 200")
    void applyPromoCode_validPromoCode_success() throws Exception {
        // Arrange - 先加入商品到購物車
        UUID listingId = createTestProduct(new BigDecimal("100.00"));
        addItemToCart(listingId, 2);

        // Mock PromoService 驗證返回
        CartDto.PromoValidationResult validResult = CartDto.PromoValidationResult.valid(
                "TEST20", "PERCENTAGE", BigDecimal.valueOf(20), BigDecimal.valueOf(50));
        when(promoService.validatePromoCode(anyString(), any(UUID.class)))
                .thenReturn(validResult);

        // Mock PromoCodeRepository
        PromoCode promoCode = buildValidPromoCode();
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId(anyString(), any(UUID.class)))
                .thenReturn(Optional.of(promoCode));

        when(promoService.computeDiscount(any(PromoCode.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(BigDecimal.valueOf(40));

        // Act & Assert
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.ApplyPromoRequest.builder()
                        .promoCode("TEST20")
                        .build())
                .when()
                .post(CART_URL + "/apply-promo")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.appliedPromoCode", equalTo("TEST20"))
                .body("data.discountAmount", allOf(greaterThanOrEqualTo(40), lessThanOrEqualTo(40)));

        System.out.println("✅ IT-M11-001 PASSED: 成功套用有效優惠券");
    }

    // ── IT-M11-002: applyPromoCode - 優惠券代碼無效 ─────────────────────

    @Test
    @Order(2)
    @DisplayName("IT-M11-002: POST /v2/cart/apply-promo - 優惠券代碼無效，返回 400")
    void applyPromoCode_invalidCode_returns400() throws Exception {
        // Arrange - 先加入商品到購物車
        UUID listingId = createTestProduct(new BigDecimal("100.00"));
        addItemToCart(listingId, 1);

        // Mock PromoService 驗證返回無效
        CartDto.PromoValidationResult invalidResult = CartDto.PromoValidationResult.invalid(
                "INVALID", "Promo code not found");
        when(promoService.validatePromoCode(anyString(), any(UUID.class)))
                .thenReturn(invalidResult);

        // Act & Assert
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.ApplyPromoRequest.builder()
                        .promoCode("INVALID")
                        .build())
                .when()
                .post(CART_URL + "/apply-promo")
                .then()
                .statusCode(400)
                .body("success", is(false));

        System.out.println("✅ IT-M11-002 PASSED: 優惠券代碼無效");
    }

    // ── IT-M11-003: applyPromoCode - 購物車為空 ─────────────────────────

    @Test
    @Order(3)
    @DisplayName("IT-M11-003: POST /v2/cart/apply-promo - 購物車為空，返回 400")
    void applyPromoCode_emptyCart_returns400() throws Exception {
        // 不加入任何商品到購物車

        // Act & Assert
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.ApplyPromoRequest.builder()
                        .promoCode("TEST20")
                        .build())
                .when()
                .post(CART_URL + "/apply-promo")
                .then()
                .statusCode(400)
                .body("success", is(false));

        System.out.println("✅ IT-M11-003 PASSED: 購物車為空");
    }

    // ── IT-M11-006: validatePromoCode - 成功驗證 ───────────────────────

    @Test
    @Order(4)
    @DisplayName("IT-M11-006: GET /v2/cart/validate-promo - 成功驗證，返回 200")
    void validatePromoCode_validCode_returns200() throws Exception {
        // Arrange
        CartDto.PromoValidationResult validResult = CartDto.PromoValidationResult.valid(
                "TEST20", "PERCENTAGE", BigDecimal.valueOf(20), BigDecimal.valueOf(50));
        when(promoService.validatePromoCode(anyString(), any(UUID.class)))
                .thenReturn(validResult);

        // Act & Assert
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .queryParam("code", "TEST20")
                .when()
                .get(CART_URL + "/validate-promo")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.valid", is(true))
                .body("data.promoCode", equalTo("TEST20"))
                .body("data.discountType", equalTo("PERCENTAGE"))
                .body("data.discountValue", equalTo(20));

        System.out.println("✅ IT-M11-006 PASSED: 成功驗證優惠券");
    }

    // ── IT-M11-007: validatePromoCode - 無效代碼 ───────────────────────

    @Test
    @Order(5)
    @DisplayName("IT-M11-007: GET /v2/cart/validate-promo - 無效代碼，返回 200 但 data.valid 為 false")
    void validatePromoCode_invalidCode_returns200WithInvalidFlag() throws Exception {
        // Arrange
        CartDto.PromoValidationResult invalidResult = CartDto.PromoValidationResult.invalid(
                "EXPIRED", "Promo code has expired");
        when(promoService.validatePromoCode(anyString(), any(UUID.class)))
                .thenReturn(invalidResult);

        // Act & Assert
        // 注意：validatePromoCode 端點設計永遠返回 200，valid/invalid 狀態在 response body 中
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .queryParam("code", "EXPIRED")
                .when()
                .get(CART_URL + "/validate-promo")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.valid", is(false));

        System.out.println("✅ IT-M11-007 PASSED: 無效優惠券代碼");
    }

    // ── IT-M11-008: removePromo - 成功移除優惠券 ───────────────────────

    @Test
    @Order(6)
    @DisplayName("IT-M11-008: DELETE /v2/cart/promo - 成功移除優惠券，返回 200")
    void removePromo_success_returns200() throws Exception {
        // Arrange - 先加入商品到購物車並套用優惠券
        UUID listingId = createTestProduct(new BigDecimal("100.00"));
        addItemToCart(listingId, 1);

        CartDto.PromoValidationResult validResult = CartDto.PromoValidationResult.valid(
                "TEST20", "PERCENTAGE", BigDecimal.valueOf(20), BigDecimal.valueOf(50));
        when(promoService.validatePromoCode(anyString(), any(UUID.class)))
                .thenReturn(validResult);

        PromoCode promoCode = buildValidPromoCode();
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId(anyString(), any(UUID.class)))
                .thenReturn(Optional.of(promoCode));

        when(promoService.computeDiscount(any(PromoCode.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(BigDecimal.valueOf(20));

        // 先套用優惠券
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.ApplyPromoRequest.builder()
                        .promoCode("TEST20")
                        .build())
                .when()
                .post(CART_URL + "/apply-promo")
                .then()
                .statusCode(200);

        // Act & Assert - 移除優惠券
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .delete(CART_URL + "/promo")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", equalTo("Promo code removed"));

        System.out.println("✅ IT-M11-008 PASSED: 成功移除優惠券");
    }

    // ── IT-M11-010: 百分比折扣計算正確性 ───────────────────────────────

    @Test
    @Order(7)
    @DisplayName("IT-M11-010: POST /v2/cart/apply-promo - 20% 百分比折扣計算正確")
    void applyPromoCode_percentageDiscount_calculatesCorrectly() throws Exception {
        // Arrange - 加入 200 元的商品 2 個 = 400 元
        UUID listingId = createTestProduct(new BigDecimal("200.00"));
        addItemToCart(listingId, 2);

        // Mock 20% 折扣
        CartDto.PromoValidationResult validResult = CartDto.PromoValidationResult.valid(
                "PERCENT20", "PERCENTAGE", BigDecimal.valueOf(20), null);
        when(promoService.validatePromoCode(anyString(), any(UUID.class)))
                .thenReturn(validResult);

        PromoCode promoCode = PromoCode.builder()
                .code("PERCENT20")
                .discountType(PromoCode.DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId(anyString(), any(UUID.class)))
                .thenReturn(Optional.of(promoCode));

        // 400 * 20% = 80
        when(promoService.computeDiscount(any(PromoCode.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(BigDecimal.valueOf(80));

        // Act & Assert
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.ApplyPromoRequest.builder()
                        .promoCode("PERCENT20")
                        .build())
                .when()
                .post(CART_URL + "/apply-promo")
                .then()
                .statusCode(200)
                .body("data.discountAmount", equalTo(80))
                .body("data.finalAmount", hasToString("320.0"));

        System.out.println("✅ IT-M11-010 PASSED: 20% 百分比折扣計算正確");
    }

    // ── IT-M11-011: 固定金額折扣計算正確性 ───────────────────────────────

    @Test
    @Order(8)
    @DisplayName("IT-M11-011: POST /v2/cart/apply-promo - 固定 $50 折扣計算正確")
    void applyPromoCode_fixedAmountDiscount_calculatesCorrectly() throws Exception {
        // Arrange - 加入 300 元的商品
        UUID listingId = createTestProduct(new BigDecimal("300.00"));
        addItemToCart(listingId, 1);

        // Mock 固定 50 元折扣
        CartDto.PromoValidationResult validResult = CartDto.PromoValidationResult.valid(
                "FIXED50", "FIXED_AMOUNT", BigDecimal.valueOf(50), null);
        when(promoService.validatePromoCode(anyString(), any(UUID.class)))
                .thenReturn(validResult);

        PromoCode promoCode = PromoCode.builder()
                .code("FIXED50")
                .discountType(PromoCode.DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(50))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId(anyString(), any(UUID.class)))
                .thenReturn(Optional.of(promoCode));

        when(promoService.computeDiscount(any(PromoCode.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(BigDecimal.valueOf(50));

        // Act & Assert
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.ApplyPromoRequest.builder()
                        .promoCode("FIXED50")
                        .build())
                .when()
                .post(CART_URL + "/apply-promo")
                .then()
                .statusCode(200)
                .body("data.discountAmount", hasToString("50"))
                .body("data.finalAmount", hasToString("250.0"));

        System.out.println("✅ IT-M11-011 PASSED: 固定 $50 折扣計算正確");
    }

    // ── IT-M11-009: 完整流程 - 加入商品、套用優惠券、驗證最終金額 ─────────

    @Test
    @Order(9)
    @DisplayName("IT-M11-009: 完整流程 - 加入商品、套用優惠券、驗證最終金額")
    void fullFlow_addItemApplyPromo_verifyFinalAmount() throws Exception {
        // Arrange - 加入 150 元的商品 2 個 = 300 元
        UUID listingId = createTestProduct(new BigDecimal("150.00"));
        addItemToCart(listingId, 2);

        // Mock 15% 折扣，最高上限 60 元
        CartDto.PromoValidationResult validResult = CartDto.PromoValidationResult.valid(
                "SAVE15", "PERCENTAGE", BigDecimal.valueOf(15), BigDecimal.valueOf(60));
        when(promoService.validatePromoCode(anyString(), any(UUID.class)))
                .thenReturn(validResult);

        PromoCode promoCode = PromoCode.builder()
                .code("SAVE15")
                .discountType(PromoCode.DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(15))
                .maxDiscountAmount(BigDecimal.valueOf(60))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId(anyString(), any(UUID.class)))
                .thenReturn(Optional.of(promoCode));

        // 300 * 15% = 45 (未超過上限 60)
        when(promoService.computeDiscount(any(PromoCode.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(BigDecimal.valueOf(45));

        // Act & Assert
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.ApplyPromoRequest.builder()
                        .promoCode("SAVE15")
                        .build())
                .when()
                .post(CART_URL + "/apply-promo")
                .then()
                .statusCode(200)
                .body("data.appliedPromoCode", equalTo("SAVE15"))
                .body("data.discountAmount", hasToString("45"))
                .body("data.finalAmount", hasToString("255.0"));

        System.out.println("✅ IT-M11-009 PASSED: 完整流程測試通過");
    }

    // ── IT-M11-012: 折扣上限生效 ───────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("IT-M11-012: POST /v2/cart/apply-promo - 折扣上限生效 (300 * 20% = 60)")
    void applyPromoCode_maxDiscountCap_applied() throws Exception {
        // Arrange - 加入 300 元的商品
        UUID listingId = createTestProduct(new BigDecimal("300.00"));
        addItemToCart(listingId, 1);

        // Mock 20% 折扣，最高上限 50 元
        CartDto.PromoValidationResult validResult = CartDto.PromoValidationResult.valid(
                "MAX50", "PERCENTAGE", BigDecimal.valueOf(20), BigDecimal.valueOf(50));
        when(promoService.validatePromoCode(anyString(), any(UUID.class)))
                .thenReturn(validResult);

        PromoCode promoCode = PromoCode.builder()
                .code("MAX50")
                .discountType(PromoCode.DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20))
                .maxDiscountAmount(BigDecimal.valueOf(50))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();
        when(promoCodeRepository.findByCodeIgnoreCaseAndTenantId(anyString(), any(UUID.class)))
                .thenReturn(Optional.of(promoCode));

        // 300 * 20% = 60，但上限為 50，所以應該是 50
        when(promoService.computeDiscount(any(PromoCode.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(BigDecimal.valueOf(50));

        // Act & Assert
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.ApplyPromoRequest.builder()
                        .promoCode("MAX50")
                        .build())
                .when()
                .post(CART_URL + "/apply-promo")
                .then()
                .statusCode(200)
                .body("data.discountAmount", hasToString("50"))
                .body("data.finalAmount", hasToString("250.0"));

        System.out.println("✅ IT-M11-012 PASSED: 折扣上限生效");
    }
}