package com.nextkey.ecommerce.api.controller;

import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.AddressRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import io.restassured.http.ContentType;
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

import java.util.Map;
import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.*;

/**
 * Address Controller API E2E 測試（Sprint 87，PRD §14.3.1 Phase 2-B 收貨地址簿）
 *
 * 測試範圍：
 * - 新增地址（第一筆自動預設）
 * - 列表查詢（僅自己的地址）
 * - 更新地址
 * - 設為預設（清除其餘預設）
 * - 刪除地址
 * - 跨使用者操作應被拒絕（403）
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.nextkey.ecommerce.integration.IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("API-ADDR E2E: Address Controller REST Assured E2E 測試")
class AddressControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    private static final String BASE_URL = "/v2/addresses";

    private UUID buyerId;
    private String buyerToken;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        RestAssuredMockMvc.mockMvc(mockMvc);

        User buyer = User.builder()
                .email("addr-buyer-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000) + "@example.com")
                .passwordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build();
        buyer = userRepository.save(buyer);
        buyerId = buyer.getId();

        buyerToken = jwtTokenService.generateAccessToken(buyerId, buyer.getEmail(), "BUYER", null);
    }

    @AfterEach
    void tearDown() {
        addressRepository.findByUserIdOrderByIsDefaultDescUpdatedAtDesc(buyerId)
                .forEach(a -> addressRepository.deleteById(a.getId()));
        userRepository.findById(buyerId).ifPresent(userRepository::delete);
        SecurityContextHolder.clearContext();
    }

    @Test
    @Order(1)
    @DisplayName("API-ADDR-001: 新增地址成功，第一筆自動設為預設")
    void createAddress_firstAddress_success() {
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of(
                        "recipientName", "王小明",
                        "phone", "0912345678",
                        "city", "台北市",
                        "district", "中正區",
                        "addressLine", "忠孝東路一段1號"))
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(201)
                .body("success", is(true))
                .body("data.recipientName", equalTo("王小明"))
                .body("data.isDefault", is(true));
    }

    @Test
    @Order(2)
    @DisplayName("API-ADDR-002: 新增地址缺少必填欄位應回 400")
    void createAddress_missingRequiredField_returns400() {
        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("phone", "0912345678"))
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(400);
    }

    @Test
    @Order(3)
    @DisplayName("API-ADDR-003: 列表查詢僅回傳自己的地址")
    void listAddresses_returnsOwnAddressesOnly() {
        createAddressViaApi("王小明", "0912345678", "台北市", "忠孝東路一段1號");

        given()
                .header("Authorization", "Bearer " + buyerToken)
                .when()
                .get(BASE_URL)
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data", hasSize(1));
    }

    @Test
    @Order(4)
    @DisplayName("API-ADDR-004: 更新地址成功")
    void updateAddress_success() {
        String addressId = createAddressViaApi("王小明", "0912345678", "台北市", "忠孝東路一段1號");

        given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(Map.of("recipientName", "王大明"))
                .when()
                .put(BASE_URL + "/{id}", addressId)
                .then()
                .statusCode(200)
                .body("data.recipientName", equalTo("王大明"));
    }

    @Test
    @Order(5)
    @DisplayName("API-ADDR-005: 設定第二筆地址為預設後，第一筆不再是預設")
    void setDefaultAddress_clearsPreviousDefault() {
        String firstId = createAddressViaApi("王小明", "0912345678", "台北市", "忠孝東路一段1號");
        String secondId = createAddressViaApi("李小美", "0987654321", "新北市", "板橋區文化路100號");

        given()
                .header("Authorization", "Bearer " + buyerToken)
                .when()
                .put(BASE_URL + "/{id}/default", secondId)
                .then()
                .statusCode(200)
                .body("data.isDefault", is(true));

        given()
                .header("Authorization", "Bearer " + buyerToken)
                .when()
                .get(BASE_URL)
                .then()
                .statusCode(200)
                .body("data.find { it.id == '" + firstId + "' }.isDefault", is(false))
                .body("data.find { it.id == '" + secondId + "' }.isDefault", is(true));
    }

    @Test
    @Order(6)
    @DisplayName("API-ADDR-006: 刪除地址成功")
    void deleteAddress_success() {
        String addressId = createAddressViaApi("王小明", "0912345678", "台北市", "忠孝東路一段1號");

        given()
                .header("Authorization", "Bearer " + buyerToken)
                .when()
                .delete(BASE_URL + "/{id}", addressId)
                .then()
                .statusCode(200)
                .body("success", is(true));

        given()
                .header("Authorization", "Bearer " + buyerToken)
                .when()
                .get(BASE_URL)
                .then()
                .body("data", hasSize(0));
    }

    @Test
    @Order(7)
    @DisplayName("🔴 API-ADDR-007: 跨使用者更新他人地址應回 403")
    void updateAddress_crossUser_forbidden() {
        String addressId = createAddressViaApi("王小明", "0912345678", "台北市", "忠孝東路一段1號");

        User otherBuyer = User.builder()
                .email("addr-other-" + System.currentTimeMillis() + "@example.com")
                .passwordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build();
        otherBuyer = userRepository.save(otherBuyer);
        String otherToken = jwtTokenService.generateAccessToken(otherBuyer.getId(), otherBuyer.getEmail(), "BUYER", null);

        try {
            given()
                    .header("Authorization", "Bearer " + otherToken)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of("recipientName", "冒用"))
                    .when()
                    .put(BASE_URL + "/{id}", addressId)
                    .then()
                    .statusCode(403);
        } finally {
            userRepository.deleteById(otherBuyer.getId());
        }
    }

    @Test
    @Order(8)
    @DisplayName("API-ADDR-008: 未帶 Token 存取應回 401/403")
    void listAddresses_withoutToken_unauthorized() {
        given()
                .when()
                .get(BASE_URL)
                .then()
                .statusCode(anyOf(is(401), is(403)));
    }

    private String createAddressViaApi(final String recipientName, final String phone,
            final String city, final String addressLine) {
        return given()
                .header("Authorization", "Bearer " + buyerToken)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "recipientName", recipientName,
                        "phone", phone,
                        "city", city,
                        "addressLine", addressLine))
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(201)
                .extract()
                .path("data.id");
    }
}
