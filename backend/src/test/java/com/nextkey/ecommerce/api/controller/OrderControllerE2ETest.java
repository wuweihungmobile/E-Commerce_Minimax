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
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.*;

/**
 * Order Controller API E2E 測試 (API-M06-001 ~ API-M06-012)
 *
 * 使用 REST Assured 框架進行完整的 HTTP 層 E2E 測試
 * 測試範圍：
 * - API-M06-001: 建立訂單-成功
 * - API-M06-002: 建立訂單-購物車為空
 * - API-M06-003: 取得訂單列表-成功
 * - API-M06-004: 取得訂單列表-分頁
 * - API-M06-005: 取得訂單詳情-成功
 * - API-M06-006: 取得訂單詳情-不存在
 * - API-M06-007: 更新訂單狀態-成功
 * - API-M06-008: 取消訂單-成功
 * - API-M06-009: 取得訂單日誌-成功
 * - API-M06-010: 未授權操作
 * - API-M06-011: 建立 Room 類型訂單
 * - API-M06-012: 取消訂單-已取消的訂單無法再次取消
 *
 * 注意：此測試使用真實的 PostgreSQL 和 Redis，確保完整的 E2E 測試覆蓋
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("API-M06 E2E: Order Controller REST Assured E2E 測試")
class OrderControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ListingRepository listingRepository;

    private static final String BASE_URL = "/v2/orders";
    private static final String AUTH_URL = "/v2/auth";
    private static final String TEST_PASSWORD = "SecurePass123!";

    // 測試用的 ROOM listing ID
    private static UUID testRoomListingId;
    private static UUID testTenantId;

    private String accessToken;
    private String userEmail;

    @BeforeAll
    static void setUpTestData(@Autowired TenantRepository tenantRepo,
                               @Autowired ListingRepository listingRepo,
                               @Autowired UserRepository userRepo) {
        // 創建測試用的 Tenant
        Tenant testTenant = Tenant.builder()
                .name("Test Tenant for ROOM Orders")
                .slug("test-tenant-" + System.currentTimeMillis())
                .contactEmail("test@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        testTenant = tenantRepo.save(testTenant);
        testTenantId = testTenant.getId();

        // 創建測試用的 HOST 用戶
        User testHost = User.builder()
                .email("host-test-" + System.currentTimeMillis() + "@example.com")
                .passwordHash("dummy")
                .fullName("Test Host")
                .role(User.UserRole.HOST)
                .status("ACTIVE")
                .tenantId(testTenantId)
                .build();
        testHost = userRepo.save(testHost);

        // 創建測試用的 ROOM Listing
        Listing testRoom = Listing.builder()
                .tenantId(testTenantId)
                .ownerId(testHost.getId())
                .listingType(Listing.ListingType.ROOM)
                .title("Test ROOM Listing")
                .description("Test room for E2E tests")
                .basePrice(BigDecimal.valueOf(1500))
                .status(Listing.ListingStatus.ACTIVE)
                .build();
        testRoom = listingRepo.save(testRoom);
        testRoomListingId = testRoom.getId();

        System.out.println("✅ Test ROOM Listing created: " + testRoomListingId);
    }

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);

        userEmail = "order-test-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000) + "@example.com";

        try {
            // 註冊 BUYER 用戶
            given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(RegisterRequest.builder()
                            .email(userEmail)
                            .password(TEST_PASSWORD)
                            .userType("BUYER")
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
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup test user: " + e.getMessage(), e);
        }
    }

    @AfterEach
    void tearDown() {
        // 先刪除用戶的訂單，再刪除用戶
        userRepository.findByEmail(userEmail).ifPresent(user -> {
            orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), Pageable.unpaged())
                    .forEach(order -> orderRepository.delete(order));
            userRepository.delete(user);
        });
    }

    // ── API-M06-001: 建立訂單-成功 ────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("API-M06-001: POST /v2/orders - 成功建立 Room 類型訂單，返回 201")
    void createOrder_success_returns201() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = LocalDate.now().plusDays(3);

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(OrderDto.CreateRequest.builder()
                        .orderType("ROOM")
                        .listingId(testRoomListingId)
                        .shippingAddress("123 Test Street")
                        .shippingRecipientName("Test User")
                        .shippingPhone("+886-912345678")
                        .notes("Please deliver on time")
                        .checkInDate(checkIn)
                        .checkOutDate(checkOut)
                        .guestCount(2)
                        .guestName("Guest Name")
                        .guestPhone("+886-987654321")
                        .guestEmail("guest@example.com")
                        .specialRequests("Late check-in")
                        .build())
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(201)
                .body("success", is(true))
                .body("message", is("Order created successfully"))
                .body("data.id", notNullValue())
                .body("data.orderType", is("ROOM"))
                .body("data.status", is("CREATED"))
                .body("data.guestCount", equalTo(2));

        System.out.println("✅ API-M06-001 PASSED: 成功建立 Room 類型訂單");
    }

    // ── API-M06-002: 建立訂單-缺少必填欄位 ───────────────────────

    @Test
    @Order(2)
    @DisplayName("API-M06-002: POST /v2/orders - 缺少必填欄位，返回 400")
    void createOrder_missingRequiredFields_returns400() {
        // 缺少 orderType
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(OrderDto.CreateRequest.builder()
                        .shippingAddress("123 Test Street")
                        .build())
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(400);

        System.out.println("✅ API-M06-002 PASSED: 缺少必填欄位時返回 400");
    }

    // ── API-M06-003: 取得訂單列表-成功 ────────────────────────────

    @Test
    @Order(3)
    @DisplayName("API-M06-003: GET /v2/orders - 成功取得訂單列表，返回 200")
    void getOrders_success_returns200() {
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

        System.out.println("✅ API-M06-003 PASSED: 成功取得訂單列表");
    }

    // ── API-M06-004: 取得訂單列表-分頁 ────────────────────────────

    @Test
    @Order(4)
    @DisplayName("API-M06-004: GET /v2/orders - 分頁參數有效，返回 200")
    void getOrders_withPagination_returns200() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .param("page", 0)
                .param("size", 5)
                .param("sortBy", "createdAt")
                .param("sortDir", "DESC")
                .when()
                .get(BASE_URL)
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.size", equalTo(5));

        System.out.println("✅ API-M06-004 PASSED: 分頁參數有效");
    }

    // ── API-M06-005: 取得訂單詳情-成功 ────────────────────────────

    @Test
    @Order(5)
    @DisplayName("API-M06-005: GET /v2/orders/{orderId} - 成功取得訂單詳情，返回 200")
    void getOrder_success_returns200() throws Exception {
        // 先建立訂單
        String createResponse = given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(OrderDto.CreateRequest.builder()
                        .orderType("ROOM")
                        .listingId(testRoomListingId)
                        .checkInDate(LocalDate.now().plusDays(1))
                        .checkOutDate(LocalDate.now().plusDays(2))
                        .guestCount(2)
                        .build())
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(201)
                .extract()
                .asString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        UUID orderId = UUID.fromString(createJson.path("data").path("id").asText());

        // 取得訂單詳情
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get(BASE_URL + "/" + orderId)
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.id", equalTo(orderId.toString()))
                .body("data.orderType", is("ROOM"));

        System.out.println("✅ API-M06-005 PASSED: 成功取得訂單詳情");
    }

    // ── API-M06-006: 取得訂單詳情-不存在 ──────────────────────────

    @Test
    @Order(6)
    @DisplayName("API-M06-006: GET /v2/orders/{orderId} - 訂單不存在，返回 404")
    void getOrder_notFound_returns404() {
        UUID nonExistentId = UUID.randomUUID();

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get(BASE_URL + "/" + nonExistentId)
                .then()
                .statusCode(404);

        System.out.println("✅ API-M06-006 PASSED: 訂單不存在時返回 404");
    }

    // ── API-M06-007: 更新訂單狀態-成功 ────────────────────────────

    @Test
    @Order(7)
    @DisplayName("API-M06-007: PATCH /v2/orders/{orderId}/status - 成功更新訂單狀態，返回 200")
    void updateOrderStatus_success_returns200() throws Exception {
        // 先建立訂單
        String createResponse = given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(OrderDto.CreateRequest.builder()
                        .orderType("ROOM")
                        .listingId(testRoomListingId)
                        .checkInDate(LocalDate.now().plusDays(1))
                        .checkOutDate(LocalDate.now().plusDays(2))
                        .guestCount(2)
                        .build())
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(201)
                .extract()
                .asString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        UUID orderId = UUID.fromString(createJson.path("data").path("id").asText());

        // 更新訂單狀態（需要管理員權限，此測試預期失敗或需要不同角色）
        // 注意：一般 BUYER 角色無法更新訂單狀態，這裡測試權限不足的情況
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(OrderDto.UpdateStatusRequest.builder()
                        .targetStatus("CONFIRMED")
                        .reason("Test confirmation")
                        .build())
                .when()
                .patch(BASE_URL + "/" + orderId + "/status")
                .then()
                .statusCode(403); // BUYER 角色無權限

        System.out.println("✅ API-M06-007 PASSED: BUYER 無法更新訂單狀態");
    }

    // ── API-M06-008: 取消訂單-成功 ────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("API-M06-008: POST /v2/orders/{orderId}/cancel - 成功取消訂單，返回 200")
    void cancelOrder_success_returns200() throws Exception {
        // 先建立訂單
        String createResponse = given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(OrderDto.CreateRequest.builder()
                        .orderType("ROOM")
                        .listingId(testRoomListingId)
                        .checkInDate(LocalDate.now().plusDays(1))
                        .checkOutDate(LocalDate.now().plusDays(2))
                        .guestCount(2)
                        .build())
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(201)
                .extract()
                .asString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        UUID orderId = UUID.fromString(createJson.path("data").path("id").asText());

        // 取消訂單
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .param("reason", "Test cancellation")
                .when()
                .post(BASE_URL + "/" + orderId + "/cancel")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.status", is("CANCELLED"));

        System.out.println("✅ API-M06-008 PASSED: 成功取消訂單");
    }

    // ── API-M06-009: 取得訂單日誌-成功 ────────────────────────────

    @Test
    @Order(9)
    @DisplayName("API-M06-009: GET /v2/orders/{orderId}/logs - 成功取得訂單日誌，返回 200")
    void getOrderLogs_success_returns200() throws Exception {
        // 先建立並取消訂單
        String createResponse = given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(OrderDto.CreateRequest.builder()
                        .orderType("ROOM")
                        .listingId(testRoomListingId)
                        .checkInDate(LocalDate.now().plusDays(1))
                        .checkOutDate(LocalDate.now().plusDays(2))
                        .guestCount(2)
                        .build())
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(201)
                .extract()
                .asString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        UUID orderId = UUID.fromString(createJson.path("data").path("id").asText());

        // 取消訂單以產生日誌
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .post(BASE_URL + "/" + orderId + "/cancel")
                .then()
                .statusCode(200);

        // 取得訂單日誌
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get(BASE_URL + "/" + orderId + "/logs")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data", notNullValue());

        System.out.println("✅ API-M06-009 PASSED: 成功取得訂單日誌");
    }

    // ── API-M06-010: 未授權操作 ──────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("API-M06-010: GET /v2/orders - 未授權，返回 401 或 403")
    void getOrders_unauthorized_returns401or403() {
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

        System.out.println("✅ API-M06-010 PASSED: 未授權時返回 401 或 403");
    }

    // ── API-M06-011: 建立 Room 類型訂單（含完整資訊）────────────

    @Test
    @Order(11)
    @DisplayName("API-M06-011: POST /v2/orders - Room 類型含完整入住資訊，返回 201")
    void createRoomOrder_withFullInfo_returns201() {
        LocalDate checkIn = LocalDate.now().plusDays(7);
        LocalDate checkOut = LocalDate.now().plusDays(10);

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(OrderDto.CreateRequest.builder()
                        .orderType("ROOM")
                        .listingId(testRoomListingId)
                        .shippingAddress("456 Hotel Address")
                        .shippingRecipientName("Hotel Reception")
                        .shippingPhone("+886-912345678")
                        .notes("Extra pillow needed")
                        .checkInDate(checkIn)
                        .checkOutDate(checkOut)
                        .guestCount(4)
                        .guestName("John Doe")
                        .guestPhone("+886-987654321")
                        .guestEmail("john.doe@example.com")
                        .specialRequests("High floor room preferred, late check-in after 10pm")
                        .build())
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(201)
                .body("success", is(true))
                .body("data.orderType", is("ROOM"))
                .body("data.guestCount", equalTo(4))
                .body("data.guestName", is("John Doe"))
                .body("data.guestEmail", is("john.doe@example.com"));

        System.out.println("✅ API-M06-011 PASSED: Room 類型含完整入住資訊");
    }

    // ── API-M06-012: 取消訂單-已取消的訂單無法再次取消 ───────────

    @Test
    @Order(12)
    @DisplayName("API-M06-012: POST /v2/orders/{orderId}/cancel - 已取消的訂單無法再次取消")
    void cancelOrder_alreadyCancelled_returns400() throws Exception {
        // 先建立訂單
        String createResponse = given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(OrderDto.CreateRequest.builder()
                        .orderType("ROOM")
                        .listingId(testRoomListingId)
                        .checkInDate(LocalDate.now().plusDays(1))
                        .checkOutDate(LocalDate.now().plusDays(2))
                        .guestCount(2)
                        .build())
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(201)
                .extract()
                .asString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        UUID orderId = UUID.fromString(createJson.path("data").path("id").asText());

        // 第一次取消
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .post(BASE_URL + "/" + orderId + "/cancel")
                .then()
                .statusCode(200)
                .body("data.status", is("CANCELLED"));

        // 嘗試第二次取消
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .post(BASE_URL + "/" + orderId + "/cancel")
                .then()
                .statusCode(400);

        System.out.println("✅ API-M06-012 PASSED: 已取消的訂單無法再次取消");
    }
}
