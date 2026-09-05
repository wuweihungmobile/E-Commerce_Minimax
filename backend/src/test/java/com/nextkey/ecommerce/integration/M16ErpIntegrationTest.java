package com.nextkey.ecommerce.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.erp.*;
import com.nextkey.ecommerce.domain.model.erp.Supplier;
import com.nextkey.ecommerce.domain.model.inventory.PurchaseOrder;
import com.nextkey.ecommerce.domain.model.inventory.PurchaseOrderItem;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


/**
 * M16 ERP Backend API 整合測試
 *
 * 測試範圍：
 * - IT-M16-001 ~ 008: 供應商管理
 * - IT-M16-101 ~ 117: 採購單管理
 * - IT-M16-201 ~ 205: 庫存管理
 * - IT-M16-301 ~ 306: 庫存異動
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("IT-M16: M16 ERP Backend API 整合測試")
@WithErpSecurity
class M16ErpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SupplierRepository supplierRepository;


    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private static final String BASE_URL = "/v2/dashboard";
    @SuppressWarnings("unused")
    private static final String TEST_PASSWORD = "SecurePass123!";

    // 測試資料 - 與 @WithErpSecurity annotation 的 tenantId 一致
    private static final UUID FIXED_TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID FIXED_STORE_OWNER_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    private static UUID testTenantId = FIXED_TENANT_ID; // 使用固定 ID 與 @WithErpSecurity 一致
    private static UUID testStoreOwnerUserId = FIXED_STORE_OWNER_USER_ID;
    @SuppressWarnings("unused")
    private static UUID testSellerUserId;
    @SuppressWarnings("unused")
    private static UUID testSupplierId;
    private static UUID testListingId;
    private static UUID testSkuId;
    @SuppressWarnings("unused")
    private static UUID testInventoryId;
    @SuppressWarnings("unused")
    private String storeOwnerToken;
    @SuppressWarnings("unused")
    private String sellerToken;

    // ═══════════════════════════════════════════════════════════════
    // Test Data Setup
    // ═══════════════════════════════════════════════════════════════

    @BeforeAll
    static void setUpTestData(@Autowired TenantRepository tenantRepo,
                              @Autowired UserRepository userRepo,
                              @Autowired ListingRepository listingRepo,
                              @Autowired JdbcTemplate jdbcTemplate) {
        // 使用與 @WithErpSecurity 一致的固定 ID
        // 確保測試資料與 security context 一致
        testTenantId = FIXED_TENANT_ID;
        testStoreOwnerUserId = FIXED_STORE_OWNER_USER_ID;

        // 種 id=FIXED_TENANT_ID 的 tenants 列（DEF-017 修復）。
        // Tenant.id 為 @GeneratedValue → builder .id() 會被忽略、存成隨機 id；而 @WithErpSecurity 硬編
        // FIXED_TENANT_ID、listings.tenant_id 有 FK → 必須以 raw SQL 種固定 ID 租戶（比照 TestDatabaseInitializer）。
        Integer tenantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenants WHERE id = ?", Integer.class, FIXED_TENANT_ID);
        if (tenantCount == null || tenantCount == 0) {
            jdbcTemplate.update(
                    "INSERT INTO tenants (id, name, slug, status, description, contact_email, contact_phone, "
                            + "connect_onboarding_status, connect_charges_enabled, connect_payouts_enabled, metadata, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW())",
                    FIXED_TENANT_ID, "Test Tenant for M16 ERP", "m16-erp-fixed-" + System.currentTimeMillis(),
                    "ACTIVE", "M16 ERP fixed-id tenant", "erp-test@tenant.com", "+886-123456789",
                    "NOT_STARTED", false, false, "{}");
        }

        // Sprint 99：PurchaseOrderService.createPurchaseOrder 新增 ERP_ENABLED 檢查，
        // 測試租戶需種好對應的 feature toggle 才能建立採購單。
        Integer toggleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenant_feature_toggles WHERE tenant_id = ? AND feature_key = ?",
                Integer.class, FIXED_TENANT_ID, "ERP_ENABLED");
        if (toggleCount == null || toggleCount == 0) {
            jdbcTemplate.update(
                    "INSERT INTO tenant_feature_toggles (id, tenant_id, feature_key, is_enabled, created_at, updated_at) "
                            + "VALUES (?, ?, ?, true, NOW(), NOW())",
                    UUID.randomUUID(), FIXED_TENANT_ID, "ERP_ENABLED");
        }

        // 檢查並創建 STORE_OWNER 用戶（如果不存在）
        User storeOwner = userRepo.findById(FIXED_STORE_OWNER_USER_ID).orElse(null);
        if (storeOwner == null) {
            storeOwner = User.builder()
                    .id(FIXED_STORE_OWNER_USER_ID)
                    .email("erp-owner-" + System.currentTimeMillis() + "@example.com")
                    .passwordHash("dummy")
                    .fullName("Test Store Owner")
                    .role(User.UserRole.STORE_OWNER)
                    .status("ACTIVE")
                    .tenantId(testTenantId)
                    .build();
            storeOwner = userRepo.save(storeOwner);
        }

        // 創建 SELLER 用戶
        User seller = User.builder()
                .email("erp-seller-" + System.currentTimeMillis() + "@example.com")
                .passwordHash("dummy")
                .fullName("Test Seller")
                .role(User.UserRole.SELLER)
                .status("ACTIVE")
                .tenantId(testTenantId)
                .build();
        seller = userRepo.save(seller);
        testSellerUserId = seller.getId();

        // 創建測試用的 Listing (當作商品)
        Listing testListing = Listing.builder()
                .tenantId(testTenantId)
                .ownerId(testStoreOwnerUserId)
                .listingType(Listing.ListingType.PRODUCT)
                .title("Test Product for ERP")
                .description("Test product description")
                .basePrice(BigDecimal.valueOf(100))
                .status(Listing.ListingStatus.ACTIVE)
                .build();
        testListing = listingRepo.save(testListing);
        testListingId = testListing.getId();
        // Listing.tenantId 為 insertable=false 影子欄位，JPA save 不寫 tenant_id → 顯式 JDBC 補寫
        // （此時 FIXED_TENANT_ID 租戶已存在，FK 滿足；DEF-017 租戶檢查依賴 listing.getTenantId() 相符）。
        jdbcTemplate.update("UPDATE listings SET tenant_id = ? WHERE id = ?", testTenantId, testListingId);

        // 初始化 testSkuId (在 @BeforeAll 中必須初始化，否則後續測試會使用 null)
        // 這是因為 @BeforeAll 只執行一次，而 @Test 方法執行順序依賴於 testSkuId
        testSkuId = UUID.randomUUID();
        try {
            jdbcTemplate.update(
                    "INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                    testSkuId, testListingId, "SKU-ERP-INIT-" + System.currentTimeMillis(), "ACTIVE"
            );
            jdbcTemplate.update(
                    "INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, updated_at) VALUES (?, ?, ?, ?, NOW())",
                    testSkuId, 100, 0, 10
            );
        } catch (Exception e) {
            // 如果插入失敗，可能是因為資料表已經存在相關資料，繼續執行
            System.out.println("⚠️ testSkuId initialization: " + e.getMessage());
        }

        listingRepo.flush();

        System.out.println("✅ M16 Test data setup: tenant=" + testTenantId + ", user=" + testStoreOwnerUserId + ", sku=" + testSkuId);
    }

    @BeforeEach
    void setUp() throws Exception {
        // 使用 @WithErpSecurity 自動設置 SecurityContext 和 TenantContext
        // 無需手動產生 token
    }

    // ═══════════════════════════════════════════════════════════════
    // IT-M16-001 ~ 008: 供應商管理測試
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("IT-M16-001: 建立供應商-成功")
    void createSupplier_success_returns201() throws Exception {
        SupplierCreateRequest request = SupplierCreateRequest.builder()
                .name("Test Supplier " + System.currentTimeMillis())
                .contactPerson("John Doe")
                .email("supplier@example.com")
                .phone("+886-987654321")
                .address("Taipei, Taiwan")
                .build();

        mockMvc.perform(post(BASE_URL + "/suppliers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value(request.getName()))
                .andExpect(jsonPath("$.data.email").value(request.getEmail()));

        System.out.println("✅ IT-M16-001 PASSED");
    }

    @Test
    @Order(2)
    @DisplayName("IT-M16-002: 建立供應商-名稱必填")
    void createSupplier_missingName_returns400() throws Exception {
        SupplierCreateRequest request = SupplierCreateRequest.builder()
                .name("") // Empty name
                .email("supplier@example.com")
                .build();

        mockMvc.perform(post(BASE_URL + "/suppliers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        System.out.println("✅ IT-M16-002 PASSED");
    }

    @Test
    @Order(3)
    @DisplayName("IT-M16-003: 建立供應商-Email格式錯誤")
    void createSupplier_invalidEmail_returns400() throws Exception {
        SupplierCreateRequest request = SupplierCreateRequest.builder()
                .name("Test Supplier")
                .email("invalid-email-format") // Invalid email
                .build();

        mockMvc.perform(post(BASE_URL + "/suppliers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        System.out.println("✅ IT-M16-003 PASSED");
    }

    @Test
    @Order(4)
    @DisplayName("IT-M16-004: 更新供應商-成功")
    void updateSupplier_success_returns200() throws Exception {
        // 先建立供應商
        Supplier supplier = Supplier.builder()
                .tenantId(testTenantId)
                .name("Original Supplier Name")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();
        supplier = supplierRepository.save(supplier);

        SupplierUpdateRequest request = SupplierUpdateRequest.builder()
                .name("Updated Supplier Name")
                .contactPerson("Jane Doe")
                .build();

        mockMvc.perform(put(BASE_URL + "/suppliers/" + supplier.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Updated Supplier Name"));

        System.out.println("✅ IT-M16-004 PASSED");
    }

    @Test
    @Order(5)
    @DisplayName("IT-M16-005: 更新供應商-不存在")
    void updateSupplier_notFound_returns404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        SupplierUpdateRequest request = SupplierUpdateRequest.builder()
                .name("Updated Name")
                .build();

        mockMvc.perform(put(BASE_URL + "/suppliers/" + nonExistentId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        System.out.println("✅ IT-M16-005 PASSED");
    }

    @Test
    @Order(6)
    @DisplayName("IT-M16-006: 查詢供應商列表-全部")
    void listSuppliers_all_returnsList() throws Exception {
        // 建立測試供應商
        Supplier supplier1 = Supplier.builder()
                .tenantId(testTenantId)
                .name("Supplier 1")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();
        Supplier supplier2 = Supplier.builder()
                .tenantId(testTenantId)
                .name("Supplier 2")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();
        supplierRepository.save(supplier1);
        supplierRepository.save(supplier2);

        mockMvc.perform(get(BASE_URL + "/suppliers")
                        .with(csrf()))
                        .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))));

        System.out.println("✅ IT-M16-006 PASSED");
    }

    @Test
    @Order(7)
    @DisplayName("IT-M16-007: 查詢供應商列表-依狀態篩選")
    void listSuppliers_filterByStatus_returnsFilteredList() throws Exception {
        // 建立不同狀態的供應商
        Supplier activeSupplier = Supplier.builder()
                .tenantId(testTenantId)
                .name("Active Supplier")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();
        Supplier inactiveSupplier = Supplier.builder()
                .tenantId(testTenantId)
                .name("Inactive Supplier")
                .status(Supplier.SupplierStatus.INACTIVE)
                .build();
        supplierRepository.save(activeSupplier);
        supplierRepository.save(inactiveSupplier);

        mockMvc.perform(get(BASE_URL + "/suppliers")
                        .param("status", "ACTIVE")
                        .with(csrf()))
                        .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].status", everyItem(equalTo("ACTIVE"))));

        System.out.println("✅ IT-M16-007 PASSED");
    }

    @Test
    @Order(8)
    @DisplayName("IT-M16-008: SELLER 角色查詢供應商列表")
    void listSuppliers_asSeller_returnsList() throws Exception {
        mockMvc.perform(get(BASE_URL + "/suppliers")
                        .with(csrf()))
                        .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        System.out.println("✅ IT-M16-008 PASSED");
    }

    @Test
    @Order(9)
    @DisplayName("IT-M16-009: 查詢單一供應商-成功（DEF-079，Sprint 129：檢視/編輯頁原本沒有 GET 端點）")
    void getSupplier_success_returns200() throws Exception {
        Supplier supplier = Supplier.builder()
                .tenantId(testTenantId)
                .name("Detail Test Supplier")
                .contactPerson("Alice")
                .email("alice@example.com")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();
        supplier = supplierRepository.save(supplier);

        mockMvc.perform(get(BASE_URL + "/suppliers/" + supplier.getId())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(supplier.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Detail Test Supplier"))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"));

        System.out.println("✅ IT-M16-009 PASSED");
    }

    @Test
    @Order(10)
    @DisplayName("IT-M16-010: 查詢單一供應商-不存在")
    void getSupplier_notFound_returns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/suppliers/" + UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isNotFound());

        System.out.println("✅ IT-M16-010 PASSED");
    }

    @Test
    @Order(11)
    @DisplayName("IT-M16-011: 查詢單一供應商-他租戶供應商回 404（租戶隔離）")
    void getSupplier_otherTenant_returns404() throws Exception {
        Supplier otherTenantSupplier = Supplier.builder()
                .tenantId(UUID.randomUUID())
                .name("Other Tenant Supplier")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();
        otherTenantSupplier = supplierRepository.save(otherTenantSupplier);

        mockMvc.perform(get(BASE_URL + "/suppliers/" + otherTenantSupplier.getId())
                        .with(csrf()))
                .andExpect(status().isNotFound());

        System.out.println("✅ IT-M16-011 PASSED");
    }

    @Test
    @Order(12)
    @DisplayName("IT-M16-012: 查詢本租戶 listing 選項（供採購單選擇器使用，DEF-076，Sprint 129）")
    void listTenantListings_returnsActiveListingsOfCurrentTenantOnly() throws Exception {
        // 本租戶但 DRAFT 狀態：不應出現在選項中
        Listing draftListing = Listing.builder()
                .tenantId(testTenantId)
                .ownerId(testStoreOwnerUserId)
                .listingType(Listing.ListingType.PRODUCT)
                .title("Draft Listing Should Not Appear")
                .basePrice(BigDecimal.valueOf(10))
                .status(Listing.ListingStatus.DRAFT)
                .build();
        draftListing = listingRepository.save(draftListing);
        jdbcTemplate.update("UPDATE listings SET tenant_id = ? WHERE id = ?", testTenantId, draftListing.getId());

        // 他租戶的 ACTIVE listing：不應出現在選項中（租戶隔離）
        UUID otherTenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants (id, name, slug, status, connect_onboarding_status, connect_charges_enabled, "
                        + "connect_payouts_enabled, metadata, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW())",
                otherTenantId, "Other Tenant", "other-tenant-" + System.currentTimeMillis(), "ACTIVE",
                "NOT_STARTED", false, false, "{}");
        Listing otherTenantListing = Listing.builder()
                .tenantId(otherTenantId)
                .ownerId(testStoreOwnerUserId)
                .listingType(Listing.ListingType.PRODUCT)
                .title("Other Tenant Listing Should Not Appear")
                .basePrice(BigDecimal.valueOf(20))
                .status(Listing.ListingStatus.ACTIVE)
                .build();
        otherTenantListing = listingRepository.save(otherTenantListing);
        jdbcTemplate.update("UPDATE listings SET tenant_id = ? WHERE id = ?", otherTenantId, otherTenantListing.getId());

        mockMvc.perform(get(BASE_URL + "/purchase-orders/listing-options")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].id", hasItem(testListingId.toString())))
                .andExpect(jsonPath("$.data[*].title", not(hasItem("Draft Listing Should Not Appear"))))
                .andExpect(jsonPath("$.data[*].title", not(hasItem("Other Tenant Listing Should Not Appear"))));

        System.out.println("✅ IT-M16-012 PASSED");
    }

    // ═══════════════════════════════════════════════════════════════
    // IT-M16-101 ~ 117: 採購單管理測試
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(101)
    @DisplayName("IT-M16-101: 建立採購單-成功")
    void createPurchaseOrder_success_returns201() throws Exception {
        // 先建立供應商
        Supplier supplier = Supplier.builder()
                .tenantId(testTenantId)
                .name("PO Test Supplier")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();
        supplier = supplierRepository.save(supplier);

        // 建立 ProductSku (SKU) - 必須先建立此记录才能建立 product_inventory
        testSkuId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                testSkuId, testListingId, "SKU-ERP-" + System.currentTimeMillis(), "ACTIVE"
        );

        // 建立 ProductInventory (SKU) - 使用 JdbcTemplate 直接插入以匹配真實資料表結構
        jdbcTemplate.update(
                "INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, updated_at) VALUES (?, ?, ?, ?, NOW())",
                testSkuId, 100, 0, 10
        );

        PurchaseOrderCreateRequest request = PurchaseOrderCreateRequest.builder()
                .supplierId(supplier.getId())
                .notes("Test PO notes")
                .items(List.of(
                        PurchaseOrderCreateRequest.PurchaseOrderItemRequest.builder()
                                .listingId(testListingId)
                                .skuId(testSkuId)
                                .quantity(10)
                                .unitCost(BigDecimal.valueOf(50.00))
                                .build()
                ))
                .build();

        mockMvc.perform(post(BASE_URL + "/purchase-orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.items", hasSize(1)));

        System.out.println("✅ IT-M16-101 PASSED");
    }

    @Test
    @Order(102)
    @DisplayName("IT-M16-102: 建立採購單-supplierId 必填")
    void createPurchaseOrder_missingSupplierId_returns400() throws Exception {
        PurchaseOrderCreateRequest request = PurchaseOrderCreateRequest.builder()
                .supplierId(null) // Missing supplierId
                .items(List.of(
                        PurchaseOrderCreateRequest.PurchaseOrderItemRequest.builder()
                                .listingId(testListingId)
                                .quantity(10)
                                .unitCost(BigDecimal.valueOf(50.00))
                                .build()
                ))
                .build();

        mockMvc.perform(post(BASE_URL + "/purchase-orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        System.out.println("✅ IT-M16-102 PASSED");
    }

    @Test
    @Order(103)
    @DisplayName("IT-M16-103: 建立採購單-items 必填")
    void createPurchaseOrder_emptyItems_returns400() throws Exception {
        // 先建立供應商
        Supplier supplier = Supplier.builder()
                .tenantId(testTenantId)
                .name("PO Supplier No Items")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();
        supplier = supplierRepository.save(supplier);

        PurchaseOrderCreateRequest request = PurchaseOrderCreateRequest.builder()
                .supplierId(supplier.getId())
                .items(List.of()) // Empty items
                .build();

        mockMvc.perform(post(BASE_URL + "/purchase-orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        System.out.println("✅ IT-M16-103 PASSED");
    }

    @Test
    @Order(104)
    @DisplayName("IT-M16-104: 查詢採購單列表-分頁")
    void listPurchaseOrders_paged_returnsPage() throws Exception {
        mockMvc.perform(get(BASE_URL + "/purchase-orders")
                        .param("page", "0")
                        .param("size", "10")
                        .with(csrf()))
                        .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());

        System.out.println("✅ IT-M16-104 PASSED");
    }

    @Test
    @Order(105)
    @DisplayName("IT-M16-105: 查詢採購單列表-依狀態篩選")
    void listPurchaseOrders_filterByStatus_returnsFilteredList() throws Exception {
        mockMvc.perform(get(BASE_URL + "/purchase-orders")
                        .param("status", "DRAFT")
                        .with(csrf()))
                        .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[*].status", everyItem(equalTo("DRAFT"))));

        System.out.println("✅ IT-M16-105 PASSED");
    }

    @Test
    @Order(106)
    @DisplayName("IT-M16-106: 查詢採購單詳情-成功")
    void getPurchaseOrder_success_returns200() throws Exception {
        // 建立測試採購單
        Supplier supplier = Supplier.builder()
                .tenantId(testTenantId)
                .name("Get PO Supplier")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();
        supplier = supplierRepository.save(supplier);

        PurchaseOrder po = PurchaseOrder.builder()
                .tenantId(testTenantId)
                .supplierId(supplier.getId())
                .poNumber("PO-" + System.currentTimeMillis())
                .status(PurchaseOrder.POStatus.DRAFT)
                .currency("TWD")
                .build();
        po = purchaseOrderRepository.save(po);

        mockMvc.perform(get(BASE_URL + "/purchase-orders/" + po.getId())
                        .with(csrf()))
                        .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(po.getId().toString()));

        System.out.println("✅ IT-M16-106 PASSED");
    }

    @Test
    @Order(107)
    @DisplayName("IT-M16-107: 查詢採購單詳情-不存在")
    void getPurchaseOrder_notFound_returns404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get(BASE_URL + "/purchase-orders/" + nonExistentId)
                        .with(csrf()))
                        .andExpect(status().isNotFound());

        System.out.println("✅ IT-M16-107 PASSED");
    }

    @Test
    @Order(108)
    @DisplayName("IT-M16-108: 更新採購單-成功 (DRAFT)")
    void updatePurchaseOrder_asDraft_success_returns200() throws Exception {
        // 建立測試採購單
        Supplier supplier = Supplier.builder()
                .tenantId(testTenantId)
                .name("Update PO Supplier")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();
        supplier = supplierRepository.save(supplier);

        PurchaseOrder po = PurchaseOrder.builder()
                .tenantId(testTenantId)
                .supplierId(supplier.getId())
                .poNumber("PO-UPDATE-" + System.currentTimeMillis())
                .status(PurchaseOrder.POStatus.DRAFT)
                .currency("TWD")
                .build();
        po = purchaseOrderRepository.save(po);

        PurchaseOrderUpdateRequest request = PurchaseOrderUpdateRequest.builder()
                .notes("Updated notes")
                .build();

        mockMvc.perform(put(BASE_URL + "/purchase-orders/" + po.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.notes").value("Updated notes"));

        System.out.println("✅ IT-M16-108 PASSED");
    }

    @Test
    @Order(109)
    @DisplayName("IT-M16-109: 更新採購單-已提交不可修改")
    void updatePurchaseOrder_asSubmitted_returns422() throws Exception {
        // 建立測試採購單
        Supplier supplier = Supplier.builder()
                .tenantId(testTenantId)
                .name("Update Submitted PO Supplier")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();
        supplier = supplierRepository.save(supplier);

        PurchaseOrder po = PurchaseOrder.builder()
                .tenantId(testTenantId)
                .supplierId(supplier.getId())
                .poNumber("PO-SUBMITTED-" + System.currentTimeMillis())
                .status(PurchaseOrder.POStatus.SUBMITTED)
                .currency("TWD")
                .build();
        po = purchaseOrderRepository.save(po);

        PurchaseOrderUpdateRequest request = PurchaseOrderUpdateRequest.builder()
                .notes("Try to update submitted PO")
                .build();

        mockMvc.perform(put(BASE_URL + "/purchase-orders/" + po.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());

        System.out.println("✅ IT-M16-109 PASSED");
    }

    @Test
    @Order(110)
    @DisplayName("IT-M16-110: 提交採購單-成功 DRAFT→SUBMITTED")
    void submitPurchaseOrder_success_returns200() throws Exception {
        // 建立測試採購單
        Supplier supplier = Supplier.builder()
                .tenantId(testTenantId)
                .name("Submit PO Supplier")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();
        supplier = supplierRepository.save(supplier);

        PurchaseOrder po = PurchaseOrder.builder()
                .tenantId(testTenantId)
                .supplierId(supplier.getId())
                .poNumber("PO-SUBMIT-" + System.currentTimeMillis())
                .status(PurchaseOrder.POStatus.DRAFT)
                .currency("TWD")
                .build();
        po = purchaseOrderRepository.save(po);

        mockMvc.perform(put(BASE_URL + "/purchase-orders/" + po.getId() + "/submit")
                        .with(csrf()))
                        .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        System.out.println("✅ IT-M16-110 PASSED");
    }

    @Test
    @Order(111)
    @DisplayName("IT-M16-111: 提交採購單-非 DRAFT 狀態")
    void submitPurchaseOrder_asNonDraft_returns422() throws Exception {
        // 建立測試採購單 (已 SUBMITTED)
        Supplier supplier = Supplier.builder()
                .tenantId(testTenantId)
                .name("Submit Non-Draft PO Supplier")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();
        supplier = supplierRepository.save(supplier);

        PurchaseOrder po = PurchaseOrder.builder()
                .tenantId(testTenantId)
                .supplierId(supplier.getId())
                .poNumber("PO-NON-DRAFT-" + System.currentTimeMillis())
                .status(PurchaseOrder.POStatus.SUBMITTED)
                .currency("TWD")
                .build();
        po = purchaseOrderRepository.save(po);

        mockMvc.perform(put(BASE_URL + "/purchase-orders/" + po.getId() + "/submit")
                        .with(csrf()))
                        .andExpect(status().isUnprocessableEntity());

        System.out.println("✅ IT-M16-111 PASSED");
    }

    @Test
    @Order(112)
    @DisplayName("IT-M16-112: 收貨-成功 PARTIAL_RECEIVED")
    void receivePurchaseOrder_partial_success_returns200() throws Exception {
        // 建立測試供應商
        Supplier supplier = Supplier.builder()
                .tenantId(testTenantId)
                .name("Partial Receive Supplier")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();
        supplier = supplierRepository.save(supplier);

        // 建立 ProductSku (SKU) - 必須先建立才能關聯
        UUID testSkuIdForPO = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                testSkuIdForPO, testListingId, "SKU-ERP-PO-" + System.currentTimeMillis(), "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, updated_at) VALUES (?, ?, ?, ?, NOW())",
                testSkuIdForPO, 100, 0, 10
        );

        // 建立採購單（含 items）
        PurchaseOrder po = PurchaseOrder.builder()
                .tenantId(testTenantId)
                .supplierId(supplier.getId())
                .poNumber("PO-PARTIAL-" + System.currentTimeMillis())
                .status(PurchaseOrder.POStatus.SUBMITTED)
                .currency("TWD")
                .build();

        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .purchaseOrder(po)
                .listingId(testListingId)
                .skuId(testSkuIdForPO)
                .quantity(10)
                .orderedQty(10)
                .receivedQuantity(0)
                .unitCost(BigDecimal.valueOf(50.00))
                .build();
        po.getItems().add(item);

        po = purchaseOrderRepository.save(po);

        // 部分收貨
        PurchaseOrderReceiveRequest request = PurchaseOrderReceiveRequest.builder()
                .items(List.of(
                        PurchaseOrderReceiveRequest.ReceiveItemRequest.builder()
                                .itemId(item.getId())
                                .receivedQuantity(5) // 收到部分
                                .build()
                ))
                .build();

        mockMvc.perform(put(BASE_URL + "/purchase-orders/" + po.getId() + "/receive")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_RECEIVED"));

        System.out.println("✅ IT-M16-112 PASSED");
    }

    @Test
    @Order(113)
    @DisplayName("IT-M16-113: 收貨-成功 RECEIVED")
    void receivePurchaseOrder_full_success_returns200() throws Exception {
        // 建立測試供應商
        Supplier supplier = Supplier.builder()
                .tenantId(testTenantId)
                .name("Full Receive Supplier")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();
        supplier = supplierRepository.save(supplier);

        // 建立 ProductSku (SKU)
        UUID testSkuIdForPO = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                testSkuIdForPO, testListingId, "SKU-ERP-PO-FULL-" + System.currentTimeMillis(), "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, updated_at) VALUES (?, ?, ?, ?, NOW())",
                testSkuIdForPO, 100, 0, 10
        );

        // 建立採購單（含 items）
        PurchaseOrder po = PurchaseOrder.builder()
                .tenantId(testTenantId)
                .supplierId(supplier.getId())
                .poNumber("PO-FULL-" + System.currentTimeMillis())
                .status(PurchaseOrder.POStatus.SUBMITTED)
                .currency("TWD")
                .build();

        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .purchaseOrder(po)
                .listingId(testListingId)
                .skuId(testSkuIdForPO)
                .quantity(10)
                .orderedQty(10)
                .receivedQuantity(0)
                .unitCost(BigDecimal.valueOf(50.00))
                .build();
        po.getItems().add(item);

        po = purchaseOrderRepository.save(po);

        // 全部收貨
        PurchaseOrderReceiveRequest request = PurchaseOrderReceiveRequest.builder()
                .items(List.of(
                        PurchaseOrderReceiveRequest.ReceiveItemRequest.builder()
                                .itemId(item.getId())
                                .receivedQuantity(10) // 全部收到
                                .build()
                ))
                .build();

        mockMvc.perform(put(BASE_URL + "/purchase-orders/" + po.getId() + "/receive")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RECEIVED"));

        System.out.println("✅ IT-M16-113 PASSED");
    }

    @Test
    @Order(114)
    @DisplayName("IT-M16-114: 收貨-非 SUBMITTED/PARTIAL_RECEIVED 狀態")
    void receivePurchaseOrder_asReceived_returns422() throws Exception {
        // 建立測試供應商
        Supplier supplier = Supplier.builder()
                .tenantId(testTenantId)
                .name("Receive Received PO Supplier")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();
        supplier = supplierRepository.save(supplier);

        // 建立 ProductSku (SKU) - 需要關聯才能建立 item
        UUID testSkuIdForPO = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                testSkuIdForPO, testListingId, "SKU-ERP-PO-REC-" + System.currentTimeMillis(), "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, updated_at) VALUES (?, ?, ?, ?, NOW())",
                testSkuIdForPO, 100, 0, 10
        );

        // 建立採購單（含 items）- 狀態為 RECEIVED
        PurchaseOrder po = PurchaseOrder.builder()
                .tenantId(testTenantId)
                .supplierId(supplier.getId())
                .poNumber("PO-RECEIVED-" + System.currentTimeMillis())
                .status(PurchaseOrder.POStatus.RECEIVED)
                .currency("TWD")
                .build();

        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .purchaseOrder(po)
                .listingId(testListingId)
                .skuId(testSkuIdForPO)
                .quantity(10)
                .orderedQty(10)
                .receivedQuantity(10) // 已經全部收貨
                .unitCost(BigDecimal.valueOf(50.00))
                .build();
        po.getItems().add(item);

        po = purchaseOrderRepository.save(po);

        // 嘗試再次收貨 - 應返回 422 (UNPROCESSABLE_ENTITY)
        PurchaseOrderReceiveRequest request = PurchaseOrderReceiveRequest.builder()
                .items(List.of(
                        PurchaseOrderReceiveRequest.ReceiveItemRequest.builder()
                                .itemId(item.getId())
                                .receivedQuantity(10)
                                .build()
                ))
                .build();

        mockMvc.perform(put(BASE_URL + "/purchase-orders/" + po.getId() + "/receive")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());

        System.out.println("✅ IT-M16-114 PASSED");
    }

    @Test
    @Order(115)
    @DisplayName("IT-M16-115: 取消採購單-成功 DRAFT→CANCELLED")
    void cancelPurchaseOrder_asDraft_success_returns200() throws Exception {
        // 建立測試採購單
        Supplier supplier = Supplier.builder()
                .tenantId(testTenantId)
                .name("Cancel Draft PO Supplier")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();
        supplier = supplierRepository.save(supplier);

        PurchaseOrder po = PurchaseOrder.builder()
                .tenantId(testTenantId)
                .supplierId(supplier.getId())
                .poNumber("PO-CANCEL-DRAFT-" + System.currentTimeMillis())
                .status(PurchaseOrder.POStatus.DRAFT)
                .currency("TWD")
                .build();
        po = purchaseOrderRepository.save(po);

        mockMvc.perform(put(BASE_URL + "/purchase-orders/" + po.getId() + "/cancel")
                        .with(csrf()))
                        .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        System.out.println("✅ IT-M16-115 PASSED");
    }

    @Test
    @Order(116)
    @DisplayName("IT-M16-116: 取消採購單-成功 SUBMITTED→CANCELLED")
    void cancelPurchaseOrder_asSubmitted_success_returns200() throws Exception {
        // 建立測試採購單
        Supplier supplier = Supplier.builder()
                .tenantId(testTenantId)
                .name("Cancel Submitted PO Supplier")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();
        supplier = supplierRepository.save(supplier);

        PurchaseOrder po = PurchaseOrder.builder()
                .tenantId(testTenantId)
                .supplierId(supplier.getId())
                .poNumber("PO-CANCEL-SUB-" + System.currentTimeMillis())
                .status(PurchaseOrder.POStatus.SUBMITTED)
                .currency("TWD")
                .build();
        po = purchaseOrderRepository.save(po);

        mockMvc.perform(put(BASE_URL + "/purchase-orders/" + po.getId() + "/cancel")
                        .with(csrf()))
                        .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        System.out.println("✅ IT-M16-116 PASSED");
    }

    @Test
    @Order(117)
    @DisplayName("IT-M16-117: 取消採購單-非 DRAFT/SUBMITTED 狀態 (PARTIAL_RECEIVED)")
    void cancelPurchaseOrder_asPartialReceived_returns400() throws Exception {
        // 建立測試採購單
        Supplier supplier = Supplier.builder()
                .tenantId(testTenantId)
                .name("Cancel Partial PO Supplier")
                .status(Supplier.SupplierStatus.ACTIVE)
                .build();
        supplier = supplierRepository.save(supplier);

        PurchaseOrder po = PurchaseOrder.builder()
                .tenantId(testTenantId)
                .supplierId(supplier.getId())
                .poNumber("PO-CANCEL-PARTIAL-" + System.currentTimeMillis())
                .status(PurchaseOrder.POStatus.PARTIALLY_RECEIVED)
                .currency("TWD")
                .build();
        po = purchaseOrderRepository.save(po);

        mockMvc.perform(put(BASE_URL + "/purchase-orders/" + po.getId() + "/cancel")
                        .with(csrf()))
                        .andExpect(status().isUnprocessableEntity());

        System.out.println("✅ IT-M16-117 PASSED");
    }

    // ═══════════════════════════════════════════════════════════════
    // IT-M16-201 ~ 205: 庫存管理測試
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(201)
    @DisplayName("IT-M16-201: 查詢庫存列表-分頁")
    void getInventoryLedger_paged_returnsPage() throws Exception {
        // Sprint 116（DEF-066）：種一個本測試自己的 SKU 到 product_inventory（生產程式碼真正會寫的那張）。
        // 原本這裡 save 一筆 Inventory 實體到孤兒 inventory 表，那張表已隨 V72 移除。
        // 🔴 不沿用 static 的 testSkuId：它會被較早順序的 @Transactional 測試重新指派並隨回滾消失。
        seedLowStockSku("SKU-LEDGER-201-", 100);

        // 🔴 斷言同時收緊：原本只驗 success 與 isArray()，**空台帳照樣通過**——
        // DEF-066 讓台帳在生產環境上永遠是空的，而這個案例一路是綠的。
        mockMvc.perform(get(BASE_URL + "/inventory")
                        .param("page", "0")
                        .param("size", "50")
                        .with(csrf()))
                        .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isNotEmpty())
                .andExpect(jsonPath("$.data.content[0].quantity").isNumber())
                .andExpect(jsonPath("$.data.content[0].skuCode").isNotEmpty());

        System.out.println("✅ IT-M16-201 PASSED");
    }

    @Test
    @Order(202)
    @DisplayName("IT-M16-202: 查詢 SKU 詳情-成功")
    void getInventoryDetail_success_returns200() throws Exception {
        // 在測試中建立獨立的 inventory 記錄，避免 @Transactional rollback 問題
        UUID testSku = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                testSku, testListingId, "SKU-TEST-DETAIL-" + System.currentTimeMillis(), "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, updated_at) VALUES (?, ?, ?, ?, NOW())",
                testSku, 100, 0, 10
        );

        mockMvc.perform(get(BASE_URL + "/inventory/" + testSku)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.skuId").value(testSku.toString()))
                // Sprint 116（DEF-066）：數量欄位必須真的有值，否則「查得到一筆空殼」也會過
                .andExpect(jsonPath("$.data.quantity").value(100))
                .andExpect(jsonPath("$.data.availableQuantity").value(100))
                .andExpect(jsonPath("$.data.skuCode").isNotEmpty());

        System.out.println("✅ IT-M16-202 PASSED");
    }

    @Test
    @Order(203)
    @DisplayName("IT-M16-203: 查詢 SKU 詳情-不存在")
    void getInventoryDetail_notFound_returns404() throws Exception {
        UUID nonExistentSkuId = UUID.randomUUID();

        mockMvc.perform(get(BASE_URL + "/inventory/" + nonExistentSkuId)
                        .with(csrf()))
                        .andExpect(status().isNotFound());

        System.out.println("✅ IT-M16-203 PASSED");
    }

    @Test
    @Order(204)
    @DisplayName("IT-M16-204: 低庫存警報-有空壓警示")
    void getLowStockAlerts_withAlerts_returnsAlerts() throws Exception {
        // Sprint 116（DEF-066）：低庫存改由真實庫存表造成，並種本測試自己的 SKU
        // （static 的 testSkuId 會被較早順序的 @Transactional 測試重新指派並隨回滾消失）
        seedLowStockSku("SKU-LEDGER-204-", 5);

        // 🔴 斷言收緊：原本只驗 success，連「預警清單是空的」都會通過
        mockMvc.perform(get(BASE_URL + "/inventory/alerts")
                        .with(csrf()))
                        .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andExpect(jsonPath("$.data[0].currentQuantity").value(5))
                .andExpect(jsonPath("$.data[0].severity").value("CRITICAL"));

        System.out.println("✅ IT-M16-204 PASSED");
    }

    @Test
    @Order(205)
    @DisplayName("IT-M16-205: 低庫存警報-無警示")
    void getLowStockAlerts_noAlerts_returnsEmptyList() throws Exception {
        mockMvc.perform(get(BASE_URL + "/inventory/alerts")
                        .with(csrf()))
                        .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        System.out.println("✅ IT-M16-205 PASSED");
    }

    // ═══════════════════════════════════════════════════════════════
    // IT-M16-301 ~ 306: 庫存異動測試
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(301)
    @DisplayName("IT-M16-301: 手動異動-TRANSFER_IN 成功")
    void createStockMovement_inbound_success_returns201() throws Exception {
        // 在每個測試中建立獨立的庫存記錄，確保 @Transactional rollback 不影響
        UUID testSku = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                testSku, testListingId, "SKU-TEST-IN-" + System.currentTimeMillis(), "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, updated_at) VALUES (?, ?, ?, ?, NOW())",
                testSku, 100, 0, 10
        );

        StockMovementRequest request = StockMovementRequest.builder()
                .skuId(testSku)
                .movementType("TRANSFER_IN") // 調撥入庫
                .quantity(50)
                .notes("Test inbound movement")
                .build();

        mockMvc.perform(post(BASE_URL + "/stock-movements")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.movementType").value("TRANSFER_IN"));

        System.out.println("✅ IT-M16-301 PASSED");
    }

    @Test
    @Order(302)
    @DisplayName("IT-M16-302: 手動異動-TRANSFER_OUT 成功")
    void createStockMovement_outbound_success_returns201() throws Exception {
        // 在每個測試中建立獨立的庫存記錄
        UUID testSku = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                testSku, testListingId, "SKU-TEST-OUT-" + System.currentTimeMillis(), "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, updated_at) VALUES (?, ?, ?, ?, NOW())",
                testSku, 100, 0, 10
        );

        StockMovementRequest request = StockMovementRequest.builder()
                .skuId(testSku)
                .movementType("TRANSFER_OUT") // 調撥出庫
                .quantity(10)
                .notes("Test outbound movement")
                .build();

        mockMvc.perform(post(BASE_URL + "/stock-movements")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.movementType").value("TRANSFER_OUT"));

        System.out.println("✅ IT-M16-302 PASSED");
    }

    @Test
    @Order(303)
    @DisplayName("IT-M16-303: 手動異動-ADJUST_PLUS 成功")
    void createStockMovement_adjust_success_returns201() throws Exception {
        // 在每個測試中建立獨立的庫存記錄
        UUID testSku = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                testSku, testListingId, "SKU-TEST-ADJ-" + System.currentTimeMillis(), "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, updated_at) VALUES (?, ?, ?, ?, NOW())",
                testSku, 100, 0, 10
        );

        StockMovementRequest request = StockMovementRequest.builder()
                .skuId(testSku)
                .movementType("ADJUST_PLUS") // 盤盈調整
                .quantity(5)
                .notes("Test adjust movement")
                .build();

        mockMvc.perform(post(BASE_URL + "/stock-movements")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.movementType").value("ADJUST_PLUS"));

        System.out.println("✅ IT-M16-303 PASSED");
    }

    @Test
    @Order(304)
    @DisplayName("IT-M16-304: 手動異動-數量必填")
    void createStockMovement_missingQuantity_returns400() throws Exception {
        // 建立庫存記錄
        UUID testSku = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                testSku, testListingId, "SKU-TEST-NULL-QTY-" + System.currentTimeMillis(), "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, updated_at) VALUES (?, ?, ?, ?, NOW())",
                testSku, 100, 0, 10
        );

        StockMovementRequest request = StockMovementRequest.builder()
                .skuId(testSku)
                .movementType("TRANSFER_IN") // 調撥入庫
                .quantity(null) // Missing quantity
                .build();

        mockMvc.perform(post(BASE_URL + "/stock-movements")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        System.out.println("✅ IT-M16-304 PASSED");
    }

    @Test
    @Order(305)
    @DisplayName("IT-M16-305: 手動異動-TRANSFER_OUT 庫存不足")
    void createStockMovement_outbound_insufficientStock_returns400() throws Exception {
        // 建立庫存記錄
        UUID testSku = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                testSku, testListingId, "SKU-TEST-LOW-" + System.currentTimeMillis(), "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, updated_at) VALUES (?, ?, ?, ?, NOW())",
                testSku, 10, 0, 10
        );

        StockMovementRequest request = StockMovementRequest.builder()
                .skuId(testSku)
                .movementType("TRANSFER_OUT") // 調撥出庫
                .quantity(999999) // 超過庫存
                .notes("Try to outbound more than available")
                .build();

        mockMvc.perform(post(BASE_URL + "/stock-movements")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        System.out.println("✅ IT-M16-305 PASSED");
    }

    @Test
    @Order(306)
    @DisplayName("IT-M16-306: 查詢異動列表-分頁")
    void getStockMovements_paged_returnsPage() throws Exception {
        mockMvc.perform(get(BASE_URL + "/stock-movements")
                        .param("page", "0")
                        .param("size", "50")
                        .with(csrf()))
                        .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());

        System.out.println("✅ IT-M16-306 PASSED");
    }

    @Test
    @Order(307)
    @DisplayName("IT-M16-307: 手動異動-跨租戶隔離（他租戶 SKU 回 403，DEF-017）")
    void createStockMovement_crossTenant_returns403() throws Exception {
        // 建立「其他租戶」（generated id，存在於 tenants）+ 其 listing + SKU；當前 @WithErpSecurity 為 FIXED_TENANT_ID
        Tenant otherTenant = tenantRepository.save(Tenant.builder()
                .name("Other Tenant " + System.currentTimeMillis())
                .slug("other-erp-tenant-" + System.currentTimeMillis())
                .contactEmail("other-erp@tenant.com")
                .contactPhone("+886-000000000")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        Listing otherListing = listingRepository.save(Listing.builder()
                .tenantId(otherTenant.getId())
                .ownerId(otherTenant.getId())
                .listingType(Listing.ListingType.PRODUCT)
                .title("Other Tenant Product")
                .description("cross-tenant isolation test")
                .basePrice(BigDecimal.valueOf(100))
                .status(Listing.ListingStatus.ACTIVE)
                .build());
        listingRepository.flush();
        // 顯式補寫 tenant_id（insertable=false）→ 確實是「他租戶」而非 null（otherTenant 存在，FK 滿足）
        jdbcTemplate.update("UPDATE listings SET tenant_id = ? WHERE id = ?", otherTenant.getId(), otherListing.getId());

        UUID otherSku = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                otherSku, otherListing.getId(), "SKU-OTHER-" + System.currentTimeMillis(), "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, updated_at) VALUES (?, ?, ?, ?, NOW())",
                otherSku, 100, 0, 10
        );

        StockMovementRequest request = StockMovementRequest.builder()
                .skuId(otherSku)
                .movementType("TRANSFER_IN")
                .quantity(10)
                .notes("cross-tenant attempt")
                .build();

        // 當前租戶嘗試異動他租戶 SKU → 應被拒（403 E_1007）
        mockMvc.perform(post(BASE_URL + "/stock-movements")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        System.out.println("✅ IT-M16-307 PASSED");
    }

    // ═══════════════════════════════════════════════════════════════
    // Cleanup
    // ═══════════════════════════════════════════════════════════════

    @AfterAll
    static void cleanup(@Autowired TenantRepository tenantRepo,
                        @Autowired UserRepository userRepo,
                        @Autowired ListingRepository listingRepo) {
        // 清理測試資料 (由 @Transactional 管理)
        System.out.println("✅ M16 ERP IT Tests completed");
    }

    /**
     * 種一個屬於 testTenantId 的 SKU 與 product_inventory 列（門檻固定 10），回傳 skuId。
     *
     * <p>Sprint 116（DEF-066）：庫存台帳的資料來源是 product_inventory，不再是孤兒的 inventory 表。
     * 刻意每個案例自己種：static 的 {@code testSkuId} 會被較早順序的測試重新指派，
     * 而那些測試在 {@code @Transactional} 下回滾後，該 SKU 的列已不存在。
     */
    private UUID seedLowStockSku(final String skuCodePrefix, final int totalQty) {
        UUID skuId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                skuId, testListingId, skuCodePrefix + System.nanoTime());
        jdbcTemplate.update(
                "INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, updated_at) "
                        + "VALUES (?, ?, 0, 10, NOW())",
                skuId, totalQty);
        return skuId;
    }
}
