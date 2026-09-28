package com.nextkey.ecommerce.api.controller;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.LoginRequest;
import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.api.dto.PaymentDto;
import com.nextkey.ecommerce.api.dto.RegisterRequest;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantFeatureToggle;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.domain.repository.TenantFeatureToggleRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.integration.IntegrationTestConfiguration;

import io.restassured.module.mockmvc.RestAssuredMockMvc;

/**
 * 啟用 Stripe 後，Mock 付款入口不可再把訂單標成已付款（Sprint 216，DEF-299）。
 *
 * <p>Mock 付款不收錢就建立一筆成功的付款並把訂單轉 PAID。Phase 1（未啟用真實金流）這是設計；
 * 一旦啟用 Stripe，還能呼叫就等於買家不付錢拿商品。舊版 {@code POST /v2/payments} 買家本來就能呼叫，
 * {@code POST /v2/orders/{id}/pay} 則在 DEF-298 修正付款權限後買家也能呼叫，所以兩者都要擋。
 *
 * <p>對照組（未啟用 Stripe 時同一個買家能付款）證明被擋的原因是開關，不是權限或測試鷹架。
 * 測試買家登入前先歸屬專用租戶，開關只設在該租戶上，不動共用的系統租戶。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("DEF-299：啟用 Stripe 時 Mock 付款入口一律拒絕")
class MockPaymentUnderStripeIntegrationTest {

    private static final String STRIPE_PAYMENT_ENABLED = "STRIPE_PAYMENT_ENABLED";
    private static final String TEST_PASSWORD = "SecurePass123!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private ListingRepository listingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private TenantFeatureToggleRepository featureToggleRepository;

    private Tenant tenant;
    private UUID roomListingId;
    private String buyerToken;

    @BeforeAll
    void setUpTenantAndRoom() {
        tenant = tenantRepository.save(Tenant.builder()
                .name("Stripe Mock Guard Tenant")
                .slug("stripe-mock-guard-" + System.currentTimeMillis())
                .contactEmail("stripe-mock-guard@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());

        User host = userRepository.save(User.builder()
                .email("stripe-guard-host-" + System.currentTimeMillis() + "@example.com")
                .passwordHash("dummy")
                .fullName("Guard Host")
                .role(User.UserRole.HOST)
                .status("ACTIVE")
                .tenantId(tenant.getId())
                .build());

        roomListingId = listingRepository.save(Listing.builder()
                .tenantId(tenant.getId())
                .ownerId(host.getId())
                .listingType(Listing.ListingType.ROOM)
                .title("Stripe Guard ROOM")
                .description("Room for DEF-299")
                .basePrice(BigDecimal.valueOf(1500))
                .status(Listing.ListingStatus.ACTIVE)
                .build()).getId();
    }

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        RestAssuredMockMvc.mockMvc(mockMvc);
        buyerToken = registerBuyerInTenantAndLogin();
    }

    @AfterEach
    void tearDown() {
        featureToggleRepository.findByTenantIdAndFeatureKey(tenant.getId(), STRIPE_PAYMENT_ENABLED)
                .ifPresent(featureToggleRepository::delete);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("對照組：未啟用 Stripe 時買家可 Mock 付款（CREATED→PAID）")
    void mockPayWorksWhenStripeDisabled() {
        UUID orderId = createRoomOrder();

        given().header("Authorization", "Bearer " + buyerToken)
                .when().post("/v2/orders/" + orderId + "/pay")
                .then().statusCode(200)
                .body("data.orderStatus", is("PAID"));
    }

    @Test
    @DisplayName("啟用 Stripe：POST /v2/orders/{id}/pay 回 422 E-6004，訂單維持 CREATED、沒有成功付款")
    void orderMockPayRejectedWhenStripeEnabled() {
        UUID orderId = createRoomOrder();
        enableStripe();

        given().header("Authorization", "Bearer " + buyerToken)
                .when().post("/v2/orders/" + orderId + "/pay")
                .then().statusCode(422)
                .body("code", is("E-6004"));

        assertNotPaid(orderId);
    }

    @Test
    @DisplayName("啟用 Stripe：POST /v2/orders/{id}/pay/fail 同樣回 422 E-6004，不留下 Mock 付款紀錄")
    void orderMockPayFailureRejectedWhenStripeEnabled() {
        UUID orderId = createRoomOrder();
        enableStripe();

        given().header("Authorization", "Bearer " + buyerToken)
                .when().post("/v2/orders/" + orderId + "/pay/fail")
                .then().statusCode(422)
                .body("code", is("E-6004"));

        assertThat(paymentRepository.existsByOrderIdAndStatus(orderId, Payment.PaymentStatus.FAILED)).isFalse();
    }

    @Test
    @DisplayName("啟用 Stripe：舊版 POST /v2/payments 回 422 E-6004，訂單維持 CREATED、沒有成功付款")
    void legacyPaymentEndpointRejectedWhenStripeEnabled() {
        UUID orderId = createRoomOrder();
        enableStripe();

        given().header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(PaymentDto.PaymentRequest.builder()
                        .orderId(orderId)
                        .paymentMethod(PaymentDto.PaymentMethod.CREDIT_CARD)
                        .build())
                .when().post("/v2/payments")
                .then().statusCode(422)
                .body("code", is("E-6004"));

        assertNotPaid(orderId);
    }

    private void enableStripe() {
        featureToggleRepository.save(TenantFeatureToggle.builder()
                .tenant(tenant)
                .featureKey(STRIPE_PAYMENT_ENABLED)
                .isEnabled(true)
                .build());
    }

    private void assertNotPaid(final UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CREATED);
        assertThat(paymentRepository.existsByOrderIdAndStatus(orderId, Payment.PaymentStatus.SUCCESS)).isFalse();
    }

    /** 買家在登入前歸屬專用租戶，使 JWT 與 TenantContext 都落在該租戶（開關依 TenantContext 判斷）。 */
    private String registerBuyerInTenantAndLogin() {
        String email = "stripe-guard-buyer-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000)
                + "@example.com";
        given().contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(RegisterRequest.builder().email(email).password(TEST_PASSWORD).userType("BUYER").build())
                .when().post("/v2/auth/register")
                .then().statusCode(201);

        User buyer = userRepository.findByEmail(email).orElseThrow();
        buyer.setTenantId(tenant.getId());
        userRepository.save(buyer);

        return given().contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(LoginRequest.builder().email(email).password(TEST_PASSWORD).build())
                .when().post("/v2/auth/login")
                .then().statusCode(200)
                .body("data.user.tenantId", is(tenant.getId().toString()))
                .extract().path("data.accessToken");
    }

    private UUID createRoomOrder() {
        String response = given().header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(OrderDto.CreateRequest.builder()
                        .orderType("ROOM")
                        .listingId(roomListingId)
                        .shippingAddress("N/A")
                        .shippingRecipientName("Guard Buyer")
                        .shippingPhone("+886-912345678")
                        .checkInDate(LocalDate.now().plusDays(1))
                        .checkOutDate(LocalDate.now().plusDays(3))
                        .guestCount(2)
                        .guestName("Guard Buyer")
                        .guestPhone("+886-987654321")
                        .guestEmail("guard-buyer@example.com")
                        .build())
                .when().post("/v2/orders")
                .then().statusCode(201)
                .body("data.status", is("CREATED"))
                .extract().asString();
        try {
            return UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
