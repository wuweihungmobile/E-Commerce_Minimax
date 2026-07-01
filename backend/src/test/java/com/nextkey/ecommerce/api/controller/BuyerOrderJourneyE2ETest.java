package com.nextkey.ecommerce.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.LoginRequest;
import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.api.dto.RegisterRequest;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.*;

/**
 * 買家訂單旅程 E2E（AI-1501 自動化，Sprint 31 US-002）。
 *
 * 涵蓋 S29 前端實際使用的買家核心路徑（ROOM 訂單，免購物車）：
 * - 建立訂單（CREATED）→ Mock 付款（CREATED→PAID）→ 付款狀態查詢
 * - 已付款訂單出現在買家自己的訂單列表
 * - 訂單取消的擁有權隔離（他人不可取消，行為驗證：訂單仍為 PAID）
 *
 * 註：完整跨模組 journey（PRODUCT + 物流 + 評價）需 cart/SKU 與賣家角色，
 * 各模組已有獨立 E2E；本測試聚焦買家可觀測核心 + 擁有權隔離，低迭代風險。
 * 發現：getOrder 無擁有權檢查（IDOR）→ 記 DEF-018（修法涉廣用方法，待完整守門處理）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.nextkey.ecommerce.integration.IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("買家訂單旅程 E2E: 下單→付款→列表→取消隔離")
class BuyerOrderJourneyE2ETest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrderRepository orderRepository;

    private static final String BASE_URL = "/v2/orders";
    private static final String AUTH_URL = "/v2/auth";
    private static final String TEST_PASSWORD = "SecurePass123!";

    private static UUID testRoomListingId;
    private static UUID testTenantId;

    private String accessToken;
    private String userEmail;
    private static UUID sharedOrderId;

    @BeforeAll
    static void setUpTestData(@Autowired TenantRepository tenantRepo,
                               @Autowired ListingRepository listingRepo,
                               @Autowired UserRepository userRepo) {
        Tenant testTenant = Tenant.builder()
                .name("Buyer Journey Tenant")
                .slug("buyer-journey-" + System.currentTimeMillis())
                .contactEmail("buyer-journey@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        testTenant = tenantRepo.save(testTenant);
        testTenantId = testTenant.getId();

        User testHost = User.builder()
                .email("host-journey-" + System.currentTimeMillis() + "@example.com")
                .passwordHash("dummy")
                .fullName("Journey Host")
                .role(User.UserRole.HOST)
                .status("ACTIVE")
                .tenantId(testTenantId)
                .build();
        testHost = userRepo.save(testHost);

        Listing testRoom = Listing.builder()
                .tenantId(testTenantId)
                .ownerId(testHost.getId())
                .listingType(Listing.ListingType.ROOM)
                .title("Journey Test ROOM")
                .description("Room for buyer journey E2E")
                .basePrice(BigDecimal.valueOf(1500))
                .status(Listing.ListingStatus.ACTIVE)
                .build();
        testRoom = listingRepo.save(testRoom);
        testRoomListingId = testRoom.getId();
    }

    private String registerAndLogin(String email) throws Exception {
        given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(RegisterRequest.builder().email(email).password(TEST_PASSWORD).userType("BUYER").build())
                .when().post(AUTH_URL + "/register")
                .then().statusCode(201);

        String loginResponse = given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(LoginRequest.builder().email(email).password(TEST_PASSWORD).build())
                .when().post(AUTH_URL + "/login")
                .then().statusCode(200).extract().asString();

        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String token = loginJson.path("data").path("accessToken").asText();
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setTenantId(testTenantId);
            userRepository.save(user);
        });
        return token;
    }

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.clearContext();
        RestAssuredMockMvc.mockMvc(mockMvc);
        userEmail = "buyer-a-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000) + "@example.com";
        accessToken = registerAndLogin(userEmail);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private UUID createRoomOrder(String token) {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = LocalDate.now().plusDays(3);
        String response = given()
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(OrderDto.CreateRequest.builder()
                        .orderType("ROOM")
                        .listingId(testRoomListingId)
                        .shippingAddress("N/A")
                        .shippingRecipientName("Buyer A")
                        .shippingPhone("+886-912345678")
                        .checkInDate(checkIn)
                        .checkOutDate(checkOut)
                        .guestCount(2)
                        .guestName("Buyer A")
                        .guestPhone("+886-987654321")
                        .guestEmail("buyer-a@example.com")
                        .build())
                .when().post(BASE_URL)
                .then().statusCode(201)
                .body("success", is(true))
                .body("data.status", is("CREATED"))
                .extract().asString();
        try {
            return UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @Order(1)
    @DisplayName("買家建立 ROOM 訂單並 Mock 付款（CREATED→PAID）")
    void buyerCreatesAndPaysRoomOrder() {
        UUID orderId = createRoomOrder(accessToken);
        sharedOrderId = orderId;

        // Mock 付款成功
        given()
                .header("Authorization", "Bearer " + accessToken)
                .when().post(BASE_URL + "/" + orderId + "/pay")
                .then().statusCode(200)
                .body("success", is(true))
                .body("data.orderStatus", is("PAID"))
                .body("data.paymentStatus", is("SUCCESS"))
                .body("data.canPay", is(false));

        // 付款狀態查詢
        given()
                .header("Authorization", "Bearer " + accessToken)
                .when().get(BASE_URL + "/" + orderId + "/payment")
                .then().statusCode(200)
                .body("data.orderStatus", is("PAID"))
                .body("data.paymentStatus", is("SUCCESS"));
    }

    @Test
    @Order(2)
    @DisplayName("已付款訂單出現在買家自己的訂單列表")
    void paidOrderAppearsInBuyerList() {
        UUID orderId = createRoomOrder(accessToken);
        given()
                .header("Authorization", "Bearer " + accessToken)
                .when().post(BASE_URL + "/" + orderId + "/pay")
                .then().statusCode(200);

        given()
                .header("Authorization", "Bearer " + accessToken)
                .when().get(BASE_URL + "?page=0&size=20")
                .then().statusCode(200)
                .body("success", is(true))
                .body("data.content.id", hasItem(orderId.toString()));
    }

    @Test
    @Order(3)
    @DisplayName("擁有權隔離：他人不可取消買家訂單（訂單仍維持原狀態）")
    void otherBuyerCannotCancelOrder() throws Exception {
        UUID orderId = createRoomOrder(accessToken);

        // 另一位買家 B
        String otherEmail = "buyer-b-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000) + "@example.com";
        String otherToken = registerAndLogin(otherEmail);

        // B 嘗試取消 A 的訂單 → 不應成功
        given()
                .header("Authorization", "Bearer " + otherToken)
                .when().post(BASE_URL + "/" + orderId + "/cancel")
                .then().statusCode(anyOf(is(400), is(403), is(409)))
                .body("success", is(false));

        // 行為驗證：A 的訂單未被取消（仍為 CREATED）
        given()
                .header("Authorization", "Bearer " + accessToken)
                .when().get(BASE_URL + "/" + orderId)
                .then().statusCode(200)
                .body("data.status", is("CREATED"));
    }
}
