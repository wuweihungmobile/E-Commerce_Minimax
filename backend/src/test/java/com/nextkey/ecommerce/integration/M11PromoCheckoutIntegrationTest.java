package com.nextkey.ecommerce.integration;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.api.dto.LoginRequest;
import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.api.dto.ProductDto;
import com.nextkey.ecommerce.api.dto.RegisterRequest;
import com.nextkey.ecommerce.domain.model.promo.PromoCode;
import com.nextkey.ecommerce.domain.model.promo.PromoCodeUsage;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.PromoCodeRepository;
import com.nextkey.ecommerce.domain.repository.PromoCodeUsageRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;

import io.restassured.module.mockmvc.RestAssuredMockMvc;

/**
 * 優惠券結帳完整迴路整合測試（Sprint 100 US-001，真實 DB + 真實 JWT）。
 *
 * <p>刻意**不** mock {@code PromoCodeRepository}/{@code PromoService}——既有的
 * {@code M11CartPromoIntegrationTest} 以 {@code @MockBean} 隔離了這兩者，因此它驗證的是
 * 購物車端點的行為，而非優惠券資料真的有被寫入與計數。Sprint 97 的教訓正是「所有相關測試
 * 都用固件繞過同一段業務邏輯」本身就是缺口存活的原因，故本測試全程走真實資料庫，
 * 驗證修復後的完整迴路：
 *
 * <pre>
 *   套用券 → 下單（金額扣折扣、額度佔用、購物車清券）
 *          → 同買家再用同張券（每人限用擋下）
 *          → 取消訂單（額度退還、用券紀錄轉 REVOKED）
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@DisplayName("IT-M11-PROMO: 優惠券結帳完整迴路（Sprint 100）")
class M11PromoCheckoutIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private PromoCodeRepository promoCodeRepository;
    @Autowired private PromoCodeUsageRepository promoCodeUsageRepository;

    @MockBean private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    private static final String CART_URL = "/v2/cart";
    private static final String ORDER_URL = "/v2/orders";
    private static final String PRODUCT_URL = "/v2/products";
    private static final String AUTH_URL = "/v2/auth";
    private static final String TEST_PASSWORD = "SecurePass123!";

    /** 商品單價 100 x 2 = 小計 200；券固定折 100。 */
    private static final BigDecimal UNIT_PRICE = new BigDecimal("100.00");
    private static final BigDecimal DISCOUNT = new BigDecimal("100.00");

    private String buyerToken;
    private String sellerToken;
    private UUID tenantId;
    private UUID buyerId;
    private String promoCodeStr;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());

        long stamp = System.currentTimeMillis();
        String sellerEmail = "promo-checkout-seller-" + stamp + "@example.com";
        String buyerEmail = "promo-checkout-buyer-" + stamp + "@example.com";

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Promo Checkout Tenant")
                .slug("promo-checkout-tenant-" + stamp)
                .contactEmail("promo-checkout@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        tenantId = tenant.getId();

        sellerToken = registerAndLogin(sellerEmail, "Promo Seller", "SELLER");
        buyerToken = registerAndLogin(buyerEmail, "Promo Buyer", "BUYER");
        buyerId = UUID.fromString(given()
                .header("Authorization", "Bearer " + buyerToken)
                .when().get(AUTH_URL + "/me")
                .then().statusCode(200)
                .extract().path("data.userId"));

        // 真實寫入 promo_codes：關聯以 tenant 物件設定（tenantId 為 insertable=false 的影子欄位）
        promoCodeStr = "CHECKOUT" + (stamp % 100000);
        promoCodeRepository.save(PromoCode.builder()
                .tenant(tenant)
                .code(promoCodeStr)
                .discountType(PromoCode.DiscountType.FIXED_AMOUNT)
                .discountValue(DISCOUNT)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .maxUsageCount(5)
                .currentUsageCount(0)
                .maxUsagePerUser(1)
                .isActive(true)
                .build());
    }

    private String registerAndLogin(final String email, final String fullName, final String userType) {
        given().contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(RegisterRequest.builder()
                        .email(email).password(TEST_PASSWORD).fullName(fullName)
                        .userType(userType).tenantId(tenantId).build())
                .when().post(AUTH_URL + "/register")
                .then().statusCode(201);

        return given().contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(LoginRequest.builder().email(email).password(TEST_PASSWORD).build())
                .when().post(AUTH_URL + "/login")
                .then().statusCode(200)
                .extract().path("data.accessToken");
    }

    private UUID createProductAndAddToCart() throws Exception {
        String created = given()
                .header("Authorization", "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(ProductDto.CreateRequest.builder()
                        .title("Promo Checkout Product " + System.nanoTime())
                        .category("Test")
                        .basePrice(UNIT_PRICE)
                        .build())
                .when().post(PRODUCT_URL)
                .then().statusCode(201)
                .extract().asString();

        UUID listingId = UUID.fromString(
                objectMapper.readTree(created).path("data").path("listingId").asText());

        given().header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.AddItemRequest.builder().listingId(listingId).quantity(2).build())
                .when().post(CART_URL + "/items")
                .then().statusCode(200);

        return listingId;
    }

    private void applyPromo() {
        given().header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(CartDto.ApplyPromoRequest.builder().promoCode(promoCodeStr).build())
                .when().post(CART_URL + "/apply-promo")
                .then().statusCode(200);
    }

    private String placeOrderExpecting(final int expectedStatus) {
        return given().header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(OrderDto.CreateRequest.builder()
                        .orderType("PRODUCT")
                        .shippingAddress("台北市信義區測試路 1 號")
                        .shippingRecipientName("測試收件人")
                        .shippingPhone("0912345678")
                        .build())
                .when().post(ORDER_URL)
                .then().statusCode(expectedStatus)
                .extract().asString();
    }

    @Test
    @DisplayName("完整迴路：套券下單扣折扣 → 額度佔用 → 每人限用擋第二單 → 取消退還額度")
    void promoCheckoutFullLoop() throws Exception {
        // ── 1. 套用優惠券並下單 ──────────────────────────────
        createProductAndAddToCart();
        applyPromo();

        String orderJson = placeOrderExpecting(201);
        var orderNode = objectMapper.readTree(orderJson).path("data");
        UUID orderId = UUID.fromString(orderNode.path("id").asText());

        BigDecimal totalAmount = new BigDecimal(orderNode.path("totalAmount").asText());
        BigDecimal shippingFee = new BigDecimal(orderNode.path("shippingFee").asText());
        BigDecimal discountAmount = new BigDecimal(orderNode.path("discountAmount").asText());

        // 小計 200 - 折扣 100 + 運費 = 應付金額（修復前為 200 + 運費，折扣完全消失）
        assertThat(discountAmount).isEqualByComparingTo(DISCOUNT);
        assertThat(orderNode.path("promoCode").asText()).isEqualTo(promoCodeStr);
        assertThat(totalAmount).isEqualByComparingTo(
                UNIT_PRICE.multiply(BigDecimal.valueOf(2)).subtract(DISCOUNT).add(shippingFee));

        // ── 2. 額度真的被佔用（修復前 current_usage_count 永遠是 0）──
        PromoCode afterOrder = promoCodeRepository
                .findByCodeIgnoreCaseAndTenantId(promoCodeStr, tenantId).orElseThrow();
        assertThat(afterOrder.getCurrentUsageCount()).isEqualTo(1);

        List<PromoCodeUsage> usages = promoCodeUsageRepository
                .findByOrderIdAndStatus(orderId, PromoCodeUsage.UsageStatus.ACTIVE);
        assertThat(usages).hasSize(1);
        assertThat(usages.get(0).getUserId()).isEqualTo(buyerId);
        assertThat(usages.get(0).getPromoCodeId()).isEqualTo(afterOrder.getId());

        // ── 3. 同一買家再用同一張券 → 每人限用擋下（修復前 max_usage_per_user 零讀取）──
        createProductAndAddToCart();
        applyPromo();
        placeOrderExpecting(400);

        // ── 4. 取消訂單 → 額度退還（PRD §2630）──────────────
        given().header("Authorization", "Bearer " + buyerToken)
                .queryParam("reason", "integration test")
                .when().post(ORDER_URL + "/" + orderId + "/cancel")
                .then().statusCode(200);

        PromoCode afterCancel = promoCodeRepository
                .findByCodeIgnoreCaseAndTenantId(promoCodeStr, tenantId).orElseThrow();
        assertThat(afterCancel.getCurrentUsageCount()).isZero();
        assertThat(promoCodeUsageRepository
                .findByOrderIdAndStatus(orderId, PromoCodeUsage.UsageStatus.ACTIVE)).isEmpty();
    }
}
