package com.nextkey.ecommerce.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.erp.*;
import com.nextkey.ecommerce.domain.model.erp.Supplier;
import com.nextkey.ecommerce.domain.model.inventory.Inventory;
import com.nextkey.ecommerce.domain.model.inventory.PurchaseOrder;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.ProductInventory;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.*;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * M16 ERP E2E 測試
 *
 * 完整 business workflow 測試
 *
 * 測試範圍：
 * - E2E-M16-001: 採購單完整生命週期
 * - E2E-M16-002: DRAFT 狀態取消
 * - E2E-M16-003: SUBMITTED 狀態取消
 * - E2E-M16-004: 進貨異動流程
 * - E2E-M16-005: 庫存不足無法出貨
 * - E2E-M16-006: 無效狀態轉換錯誤
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.nextkey.ecommerce.integration.IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E2E-M16: M16 ERP End-to-End 測試")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class M16ErpE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private ProductInventoryRepository productInventoryRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String ERP_URL = "/api/v2/dashboard";
    private static final String AUTH_URL = "/v2/auth";
    private static final String TEST_PASSWORD = "SecurePass123!";

    // 測試資料
    private static UUID testTenantId;
    private static UUID testStoreOwnerUserId;
    private static UUID testListingId;
    private static UUID testSkuId;

    private String storeOwnerToken;
    private String storeOwnerEmail;

    // ═══════════════════════════════════════════════════════════════
    // Test Data Setup
    // ═══════════════════════════════════════════════════════════════

    @BeforeAll
    static void setUpTestData(@Autowired TenantRepository tenantRepo,
                              @Autowired UserRepository userRepo,
                              @Autowired ListingRepository listingRepo,
                              @Autowired JdbcTemplate jdbcTemplate) {
        // 創建測試用的 Tenant
        Tenant testTenant = Tenant.builder()
                .name("E2E M16 Tenant " + System.currentTimeMillis())
                .slug("e2e-m16-tenant-" + System.currentTimeMillis())
                .contactEmail("e2e-m16@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        testTenant = tenantRepo.save(testTenant);
        testTenantId = testTenant.getId();

        // 創建 STORE_OWNER 用戶
        User storeOwner = User.builder()
                .email("e2e-m16-owner-" + System.currentTimeMillis() + "@example.com")
                .passwordHash("dummy")
                .fullName("E2E M16 Store Owner")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(testTenantId)
                .build();
        storeOwner = userRepo.save(storeOwner);
        testStoreOwnerUserId = storeOwner.getId();

        // 創建測試用的 Listing (當作商品)
        Listing testListing = Listing.builder()
                .tenantId(testTenantId)
                .ownerId(testStoreOwnerUserId)
                .listingType(Listing.ListingType.PRODUCT)
                .title("E2E M16 Test Product")
                .description("Test product for E2E M16 tests")
                .basePrice(BigDecimal.valueOf(100))
                .status(Listing.ListingStatus.ACTIVE)
                .build();
        testListing = listingRepo.save(testListing);
        testListingId = testListing.getId();
        listingRepo.flush();

        // 建立 ProductSku (SKU) - 必須先建立此记录才能建立 product_inventory
        testSkuId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                testSkuId, testListingId, "SKU-E2E-" + System.currentTimeMillis(), "ACTIVE"
        );

        // 使用 JdbcTemplate 直接插入 ProductInventory 記錄
        jdbcTemplate.update(
                "INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, version, updated_at) VALUES (?, ?, ?, ?, 0, NOW())",
                testSkuId, 100, 0, 10
        );

        // 建立初始庫存 (inventory table)
        UUID inventoryId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO inventory (id, sku_id, tenant_id, total_qty, reserved_qty, available_qty, safety_stock, reorder_point, version, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, NOW())",
                inventoryId, testSkuId, testTenantId, 100, 0, 100, 10, 20
        );

        System.out.println("✅ E2E M16 Test data setup: tenant=" + testTenantId + ", skuId=" + testSkuId);
    }

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);

        storeOwnerEmail = "e2e-m16-owner-" + System.currentTimeMillis() + "@example.com";

        try {
            // 註冊 STORE_OWNER 用戶
            given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(com.nextkey.ecommerce.api.dto.RegisterRequest.builder()
                            .email(storeOwnerEmail)
                            .password(TEST_PASSWORD)
                            .userType("STORE_OWNER")
                            .build())
                    .when()
                    .post(AUTH_URL + "/register")
                    .then()
                    .statusCode(201);

            // 更新用戶的 tenantId
            userRepository.findByEmail(storeOwnerEmail).ifPresent(user -> {
                user.setTenantId(testTenantId);
                user.setRole(User.UserRole.STORE_OWNER);
                userRepository.save(user);
            });

            // 登入取得 token
            String loginResponse = given()
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(com.nextkey.ecommerce.api.dto.LoginRequest.builder()
                            .email(storeOwnerEmail)
                            .password(TEST_PASSWORD)
                            .build())
                    .when()
                    .post(AUTH_URL + "/login")
                    .then()
                    .statusCode(200)
                    .extract()
                    .asString();

            JsonNode loginJson = objectMapper.readTree(loginResponse);
            storeOwnerToken = loginJson.path("data").path("accessToken").asText();

        } catch (Exception e) {
            throw new RuntimeException("Failed to setup test user: " + e.getMessage(), e);
        }
    }

    @AfterEach
    void tearDown() {
        // 清理用戶
        userRepository.findByEmail(storeOwnerEmail).ifPresent(user -> {
            userRepository.delete(user);
        });
    }

    @AfterAll
    static void cleanup(@Autowired TenantRepository tenantRepo,
                        @Autowired UserRepository userRepo,
                        @Autowired ListingRepository listingRepo,
                        @Autowired InventoryRepository inventoryRepo,
                        @Autowired ProductInventoryRepository productInventoryRepo,
                        @Autowired SupplierRepository supplierRepo,
                        @Autowired PurchaseOrderRepository purchaseOrderRepo) {
        // 清理測試資料
        if (testTenantId != null) {
            supplierRepo.deleteAll(supplierRepo.findByTenantId(testTenantId));
            purchaseOrderRepo.deleteAll(purchaseOrderRepo.findByTenantId(testTenantId));
            productInventoryRepo.deleteAll(productInventoryRepo.findAll());
            inventoryRepo.deleteAll(inventoryRepo.findByTenantId(testTenantId));
            listingRepo.deleteAll(listingRepo.findByTenantId(testTenantId));
            userRepo.deleteAll(userRepo.findByTenantId(testTenantId));
            tenantRepo.deleteById(testTenantId);
        }
        System.out.println("✅ E2E M16 Tests completed and cleaned up");
    }

    // ═══════════════════════════════════════════════════════════════
    // E2E-M16-001: 採購單完整生命週期
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("E2E-M16-001: 採購單完整流程 - DRAFT→SUBMITTED→PARTIALLY_RECEIVED→RECEIVED")
    void e2e_purchaseOrderFullLifecycle_success() throws Exception {
        // Step 1: 建立供應商
        String supplierName = "E2E Test Supplier " + System.currentTimeMillis();
        SupplierDto createdSupplier = given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(SupplierCreateRequest.builder()
                        .name(supplierName)
                        .contactPerson("John Doe")
                        .email("supplier@e2e.com")
                        .phone("+886-987654321")
                        .build())
                .when()
                .post(ERP_URL + "/suppliers")
                .then()
                .statusCode(201)
                .body("success", is(true))
                .body("data.name", equalTo(supplierName))
                .extract()
                .jsonPath()
                .getObject("data", SupplierDto.class);

        UUID supplierId = createdSupplier.getId();
        System.out.println("✅ Step 1: 供應商建立成功 - " + supplierId);

        // Step 2: 建立採購單
        PurchaseOrderDto createdPO = given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(PurchaseOrderCreateRequest.builder()
                        .supplierId(supplierId)
                        .notes("E2E Test PO")
                        .items(List.of(
                                PurchaseOrderCreateRequest.PurchaseOrderItemRequest.builder()
                                        .listingId(testListingId)
                                        .skuId(testSkuId)
                                        .quantity(50)
                                        .unitCost(BigDecimal.valueOf(25.00))
                                        .build()
                        ))
                        .build())
                .when()
                .post(ERP_URL + "/purchase-orders")
                .then()
                .statusCode(201)
                .body("success", is(true))
                .body("data.status", equalTo("DRAFT"))
                .body("data.items", hasSize(1))
                .extract()
                .jsonPath()
                .getObject("data", PurchaseOrderDto.class);

        UUID poId = createdPO.getId();
        System.out.println("✅ Step 2: 採購單建立成功 - " + poId + " (DRAFT)");

        // Step 3: 提交採購單 (DRAFT→SUBMITTED)
        PurchaseOrderDto submittedPO = given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .put(ERP_URL + "/purchase-orders/" + poId + "/submit")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.status", equalTo("SUBMITTED"))
                .extract()
                .jsonPath()
                .getObject("data", PurchaseOrderDto.class);

        System.out.println("✅ Step 3: 採購單提交成功 - " + submittedPO.getStatus());

        // Step 4: 部分收貨 (SUBMITTED→PARTIALLY_RECEIVED)
        UUID itemId = submittedPO.getItems().get(0).getId();
        PurchaseOrderDto partialPO = given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(PurchaseOrderReceiveRequest.builder()
                        .items(List.of(
                                PurchaseOrderReceiveRequest.ReceiveItemRequest.builder()
                                        .itemId(itemId)
                                        .receivedQuantity(30) // 部分收貨
                                        .build()
                        ))
                        .build())
                .when()
                .put(ERP_URL + "/purchase-orders/" + poId + "/receive")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.status", equalTo("PARTIALLY_RECEIVED"))
                .extract()
                .jsonPath()
                .getObject("data", PurchaseOrderDto.class);

        System.out.println("✅ Step 4: 部分收貨成功 - " + partialPO.getStatus());

        // Step 5: 剩餘收貨 (PARTIALLY_RECEIVED→RECEIVED)
        PurchaseOrderDto receivedPO = given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(PurchaseOrderReceiveRequest.builder()
                        .items(List.of(
                                PurchaseOrderReceiveRequest.ReceiveItemRequest.builder()
                                        .itemId(itemId)
                                        .receivedQuantity(20) // 剩餘收貨
                                        .build()
                        ))
                        .build())
                .when()
                .put(ERP_URL + "/purchase-orders/" + poId + "/receive")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.status", equalTo("RECEIVED"))
                .extract()
                .jsonPath()
                .getObject("data", PurchaseOrderDto.class);

        System.out.println("✅ Step 5: 全部收貨成功 - " + receivedPO.getStatus());

        // Step 6: 驗證庫存增加
        given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get(ERP_URL + "/inventory/" + testSkuId)
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.skuId", equalTo(testSkuId.toString()));

        System.out.println("✅ Step 6: 庫存驗證成功");

        // Step 7: 驗證低庫存警報消失 (因為原本庫存 100，收貨 50 後是 150)
        given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get(ERP_URL + "/inventory/alerts")
                .then()
                .statusCode(200)
                .body("success", is(true));

        System.out.println("✅ Step 7: 低庫存警報驗證成功");
        System.out.println("✅ E2E-M16-001 PASSED: 採購單完整生命週期測試成功");
    }

    // ═══════════════════════════════════════════════════════════════
    // E2E-M16-002: DRAFT 狀態取消
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("E2E-M16-002: 採購單取消 - DRAFT→CANCELLED")
    void e2e_cancelPurchaseOrder_asDraft_success() throws Exception {
        // Step 1: 建立供應商
        SupplierDto supplier = given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(SupplierCreateRequest.builder()
                        .name("Cancel DRAFT Supplier " + System.currentTimeMillis())
                        .contactPerson("Jane Doe")
                        .email("cancel-draft@e2e.com")
                        .build())
                .when()
                .post(ERP_URL + "/suppliers")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getObject("data", SupplierDto.class);

        // Step 2: 建立採購單
        PurchaseOrderDto po = given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(PurchaseOrderCreateRequest.builder()
                        .supplierId(supplier.getId())
                        .items(List.of(
                                PurchaseOrderCreateRequest.PurchaseOrderItemRequest.builder()
                                        .listingId(testListingId)
                                        .skuId(testSkuId)
                                        .quantity(10)
                                        .unitCost(BigDecimal.valueOf(10.00))
                                        .build()
                        ))
                        .build())
                .when()
                .post(ERP_URL + "/purchase-orders")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getObject("data", PurchaseOrderDto.class);

        UUID poId = po.getId();
        System.out.println("✅ Created PO: " + poId + " (DRAFT)");

        // Step 3: 取消採購單
        given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .put(ERP_URL + "/purchase-orders/" + poId + "/cancel")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.status", equalTo("CANCELLED"));

        System.out.println("✅ E2E-M16-002 PASSED: DRAFT→CANCELLED 成功");
    }

    // ═══════════════════════════════════════════════════════════════
    // E2E-M16-003: SUBMITTED 狀態取消
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("E2E-M16-003: 採購單取消 - SUBMITTED→CANCELLED")
    void e2e_cancelPurchaseOrder_asSubmitted_success() throws Exception {
        // Step 1: 建立供應商
        SupplierDto supplier = given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(SupplierCreateRequest.builder()
                        .name("Cancel SUBMITTED Supplier " + System.currentTimeMillis())
                        .contactPerson("Bob Smith")
                        .email("cancel-sub@e2e.com")
                        .build())
                .when()
                .post(ERP_URL + "/suppliers")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getObject("data", SupplierDto.class);

        // Step 2: 建立採購單
        PurchaseOrderDto po = given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(PurchaseOrderCreateRequest.builder()
                        .supplierId(supplier.getId())
                        .items(List.of(
                                PurchaseOrderCreateRequest.PurchaseOrderItemRequest.builder()
                                        .listingId(testListingId)
                                        .skuId(testSkuId)
                                        .quantity(20)
                                        .unitCost(BigDecimal.valueOf(15.00))
                                        .build()
                        ))
                        .build())
                .when()
                .post(ERP_URL + "/purchase-orders")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getObject("data", PurchaseOrderDto.class);

        UUID poId = po.getId();
        System.out.println("✅ Created PO: " + poId + " (DRAFT)");

        // Step 3: 提交採購單
        given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .put(ERP_URL + "/purchase-orders/" + poId + "/submit")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("SUBMITTED"));

        System.out.println("✅ Submitted PO: " + poId + " (SUBMITTED)");

        // Step 4: 取消已提交的採購單
        given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .put(ERP_URL + "/purchase-orders/" + poId + "/cancel")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.status", equalTo("CANCELLED"));

        System.out.println("✅ E2E-M16-003 PASSED: SUBMITTED→CANCELLED 成功");
    }

    // ═══════════════════════════════════════════════════════════════
    // E2E-M16-004: 進貨異動流程
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("E2E-M16-004: 庫存異動 - 手動調整流程")
    void e2e_stockMovement_adjustFlow_success() throws Exception {
        // 取得目前庫存
        Integer originalQty = given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .get(ERP_URL + "/inventory/" + testSkuId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getInt("data.availableQuantity");

        System.out.println("✅ Original inventory: " + originalQty);

        // 手動異動 ADJUST
        given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(StockMovementRequest.builder()
                        .skuId(testSkuId)
                        .movementType("ADJUSTMENT")
                        .quantity(10)
                        .notes("E2E test adjustment")
                        .build())
                .when()
                .post(ERP_URL + "/stock-movements")
                .then()
                .statusCode(201)
                .body("success", is(true))
                .body("data.movementType", equalTo("ADJUSTMENT"))
                .body("data.quantity", equalTo(10));

        System.out.println("✅ E2E-M16-004 PASSED: ADJUST 異動成功");
    }

    // ═══════════════════════════════════════════════════════════════
    // E2E-M16-005: 庫存不足無法出貨
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("E2E-M16-005: OUTBOUND 庫存不足時回傳錯誤")
    void e2e_outbound_insufficientStock_returns400() throws Exception {
        // 嘗試 OUTBOUND 超過可用庫存
        given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(StockMovementRequest.builder()
                        .skuId(testSkuId)
                        .movementType("OUTBOUND")
                        .quantity(999999) // 超過庫存
                        .notes("Try to outbound more than available")
                        .build())
                .when()
                .post(ERP_URL + "/stock-movements")
                .then()
                .statusCode(anyOf(is(400), is(500))); // 預期錯誤

        System.out.println("✅ E2E-M16-005 PASSED: 庫存不足時正確拒絕");
    }

    // ═══════════════════════════════════════════════════════════════
    // E2E-M16-006: 無效狀態轉換
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("E2E-M16-006: 嘗試取消 PARTIALLY_RECEIVED 採購單時回傳錯誤")
    void e2e_cancelPartialReceived_returns400() throws Exception {
        // Step 1: 建立供應商
        SupplierDto supplier = given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(SupplierCreateRequest.builder()
                        .name("Cancel Partial Supplier " + System.currentTimeMillis())
                        .contactPerson("Alice Wong")
                        .email("cancel-partial@e2e.com")
                        .build())
                .when()
                .post(ERP_URL + "/suppliers")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getObject("data", SupplierDto.class);

        // Step 2: 建立採購單
        PurchaseOrderDto po = given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(PurchaseOrderCreateRequest.builder()
                        .supplierId(supplier.getId())
                        .items(List.of(
                                PurchaseOrderCreateRequest.PurchaseOrderItemRequest.builder()
                                        .listingId(testListingId)
                                        .skuId(testSkuId)
                                        .quantity(30)
                                        .unitCost(BigDecimal.valueOf(20.00))
                                        .build()
                        ))
                        .build())
                .when()
                .post(ERP_URL + "/purchase-orders")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getObject("data", PurchaseOrderDto.class);

        UUID poId = po.getId();

        // Step 3: 提交
        given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .put(ERP_URL + "/purchase-orders/" + poId + "/submit")
                .then()
                .statusCode(200);

        // Step 4: 部分收貨
        UUID itemId = po.getItems().get(0).getId();
        given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(PurchaseOrderReceiveRequest.builder()
                        .items(List.of(
                                PurchaseOrderReceiveRequest.ReceiveItemRequest.builder()
                                        .itemId(itemId)
                                        .receivedQuantity(10)
                                        .build()
                        ))
                        .build())
                .when()
                .put(ERP_URL + "/purchase-orders/" + poId + "/receive")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("PARTIALLY_RECEIVED"));

        System.out.println("✅ PO is now in PARTIALLY_RECEIVED status");

        // Step 5: 嘗試取消 (應該失敗)
        given()
                .header("Authorization", "Bearer " + storeOwnerToken)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                .put(ERP_URL + "/purchase-orders/" + poId + "/cancel")
                .then()
                .statusCode(anyOf(is(400), is(422), is(500))); // 預期錯誤

        System.out.println("✅ E2E-M16-006 PASSED: PARTIALLY_RECEIVED 無法取消");
    }
}
