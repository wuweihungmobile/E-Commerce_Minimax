package com.nextkey.ecommerce.api.controller;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.nextkey.ecommerce.domain.model.logistics.ShippingTemplate;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ShippingTemplateRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;

import io.restassured.module.mockmvc.RestAssuredMockMvc;

/**
 * ShippingTemplate Controller E2E 測試（US-006）
 *
 * 測試範圍（AC-002 ~ AC-004）：
 * - TC-ST-E01: POST /v2/shipping-templates — SELLER 建立 FIXED 運費模板 → 200
 * - TC-ST-E02: GET /v2/shipping-templates/{id}/calculate-fee — FREE_THRESHOLD 免運計算 → shippingFee=0
 * - TC-ST-E03: POST /v2/shipping-templates — BUYER 存取 → 403
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.nextkey.ecommerce.integration.IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("API-US006 E2E: ShippingTemplate Controller REST Assured E2E 測試")
class ShippingTemplateControllerE2ETest {

    private static final String BASE_URL = "/v2/shipping-templates";
    private static final BigDecimal FIXED_AMOUNT = BigDecimal.valueOf(80);
    private static final BigDecimal FREE_THRESHOLD = BigDecimal.valueOf(500);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShippingTemplateRepository shippingTemplateRepository;

    private Tenant testTenant;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        RestAssuredMockMvc.mockMvc(mockMvc);

        testTenant = Tenant.builder()
                .name("Shipping Template Test Tenant")
                .slug("shipping-test-" + System.currentTimeMillis())
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        testTenant = tenantRepository.save(testTenant);
    }

    @AfterEach
    void tearDown() {
        shippingTemplateRepository.findByTenantId(testTenant.getId())
                .forEach(shippingTemplateRepository::delete);
        tenantRepository.delete(testTenant);
        SecurityContextHolder.clearContext();
    }

    private String createSellerToken() {
        User seller = User.builder()
                .email("seller-shipping-" + System.currentTimeMillis() + "@test.com")
                .passwordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG")
                .role(User.UserRole.SELLER)
                .status("ACTIVE")
                .build();
        seller = userRepository.save(seller);

        return jwtTokenService.generateAccessToken(
                seller.getId(),
                seller.getEmail(),
                "SELLER",
                testTenant.getId().toString()
        );
    }

    private String createBuyerToken() {
        User buyer = User.builder()
                .email("buyer-shipping-" + System.currentTimeMillis() + "@test.com")
                .passwordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build();
        buyer = userRepository.save(buyer);

        return jwtTokenService.generateAccessToken(
                buyer.getId(),
                buyer.getEmail(),
                "BUYER",
                null
        );
    }

    /**
     * TC-ST-E01: SELLER 建立 FIXED 運費模板 → 200 + TemplateResponse
     */
    @Test
    @Order(1)
    @DisplayName("TC-ST-E01: POST /v2/shipping-templates — SELLER 建立 FIXED 模板應返回 200")
    void createTemplate_asSeller_fixedType_shouldReturn200() {
        String sellerToken = createSellerToken();

        given()
                .header("Authorization", "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "name", "標準運費",
                        "feeType", "FIXED",
                        "fixedAmount", 80
                ))
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.id", notNullValue())
                .body("data.name", equalTo("標準運費"))
                .body("data.feeType", equalTo("FIXED"));

        System.out.println("✅ TC-ST-E01 PASSED: SELLER 建立 FIXED 運費模板成功");
    }

    /**
     * TC-ST-E02: GET /calculate-fee — FREE_THRESHOLD 類型，訂單金額 >= 門檻 → shippingFee = 0
     */
    @Test
    @Order(2)
    @DisplayName("TC-ST-E02: GET /v2/shipping-templates/{id}/calculate-fee — FREE_THRESHOLD 滿額免運應返回 0")
    void calculateFee_freeThresholdMet_shouldReturnZero() {
        // 直接建立模板到 DB，確保 tenantId 正確
        ShippingTemplate template = ShippingTemplate.builder()
                .tenant(testTenant)
                .name("免運門檻模板")
                .feeType(ShippingTemplate.FeeType.FREE_THRESHOLD)
                .fixedAmount(FIXED_AMOUNT)
                .freeThreshold(FREE_THRESHOLD)
                .build();
        template = shippingTemplateRepository.save(template);

        String sellerToken = createSellerToken();

        given()
                .header("Authorization", "Bearer " + sellerToken)
                .when()
                .get(BASE_URL + "/" + template.getId() + "/calculate-fee?orderAmount=500")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.shippingFee", equalTo(0))
                .body("data.feeType", equalTo("FREE_THRESHOLD"));

        System.out.println("✅ TC-ST-E02 PASSED: FREE_THRESHOLD 滿額免運計算正確（shippingFee=0）");
    }

    /**
     * TC-ST-E03: BUYER 存取 POST /v2/shipping-templates → 403
     */
    @Test
    @Order(3)
    @DisplayName("TC-ST-E03: POST /v2/shipping-templates — BUYER 角色應返回 403")
    void createTemplate_asBuyer_shouldReturn403() {
        String buyerToken = createBuyerToken();

        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "name", "買家測試模板",
                        "feeType", "FIXED",
                        "fixedAmount", 50
                ))
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(403);

        System.out.println("✅ TC-ST-E03 PASSED: BUYER 存取運費模板 API 正確返回 403");
    }
}
