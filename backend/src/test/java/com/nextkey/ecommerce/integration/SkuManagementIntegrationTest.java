package com.nextkey.ecommerce.integration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.filter.UserPrincipal;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductInventoryRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SKU 管理與採購單收貨整合測試（Sprint 178）。
 *
 * <p>背景：全庫先前沒有任何程式碼會建立 {@code ProductSku}——{@code product_skus}/
 * {@code product_inventory} 在正式環境完全無法產生資料，導致依賴 SKU 的既有 ERP 庫存子系統
 * （低庫存預警、DEF-050 三段式原子庫存操作、採購單收貨入庫）實質上從未真正運作過，
 * 只靠既有測試（如 {@link M16ErpIntegrationTest}）直接用 {@code JdbcTemplate} 塞資料才「看起來」
 * 綠燈。本測試刻意**不**用 raw SQL 塞 SKU，而是走真正的
 * {@code POST /v2/products/{listingId}/skus} 端點，證明「建立 SKU → 建立採購單 → 送出 →
 * 收貨」整條鏈路現在可以端到端運作，且 {@code product_inventory.total_qty} 真的會增加。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("IT-SKU: 商品規格（SKU）管理與採購單收貨整合測試")
class SkuManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private ProductInventoryRepository productInventoryRepository;

    private static final UUID TENANT_ID = UUID.fromString("6f1a0000-0000-4000-8000-000000000178");
    private static final UUID USER_ID = UUID.fromString("6f1a0000-0000-4000-8000-000000000179");

    private static UUID testListingId;

    @BeforeAll
    static void setUpTestData(@Autowired TenantRepository tenantRepository,
            @Autowired UserRepository userRepository,
            @Autowired ListingRepository listingRepo,
            @Autowired JdbcTemplate jdbcTemplate) {
        Integer tenantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenants WHERE id = ?", Integer.class, TENANT_ID);
        if (tenantCount == null || tenantCount == 0) {
            jdbcTemplate.update(
                    "INSERT INTO tenants (id, name, slug, status, description, contact_email, contact_phone, "
                            + "connect_onboarding_status, connect_charges_enabled, connect_payouts_enabled, "
                            + "metadata, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW())",
                    TENANT_ID, "Test Tenant for SKU Management", "sku-mgmt-" + System.currentTimeMillis(),
                    "ACTIVE", "Sprint 178 SKU management test tenant", "sku-test@tenant.com", "+886-123456789",
                    "NOT_STARTED", false, false, "{}");
        }

        Integer toggleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenant_feature_toggles WHERE tenant_id = ? AND feature_key = ?",
                Integer.class, TENANT_ID, "ERP_ENABLED");
        if (toggleCount == null || toggleCount == 0) {
            jdbcTemplate.update(
                    "INSERT INTO tenant_feature_toggles (id, tenant_id, feature_key, is_enabled, created_at, updated_at) "
                            + "VALUES (?, ?, ?, true, NOW(), NOW())",
                    UUID.randomUUID(), TENANT_ID, "ERP_ENABLED");
        }

        if (userRepository.findById(USER_ID).isEmpty()) {
            com.nextkey.ecommerce.domain.model.user.User owner =
                    com.nextkey.ecommerce.domain.model.user.User.builder()
                            .id(USER_ID)
                            .email("sku-owner-" + System.currentTimeMillis() + "@example.com")
                            .passwordHash("dummy")
                            .fullName("Test Store Owner")
                            .role(com.nextkey.ecommerce.domain.model.user.User.UserRole.STORE_OWNER)
                            .status("ACTIVE")
                            .tenantId(TENANT_ID)
                            .build();
            userRepository.save(owner);
        }

        Listing listing = Listing.builder()
                .tenantId(TENANT_ID)
                .ownerId(USER_ID)
                .listingType(Listing.ListingType.PRODUCT)
                .title("Sprint 178 Test Product")
                .description("SKU management regression test product")
                .basePrice(BigDecimal.valueOf(199))
                .currency("TWD")
                .status(Listing.ListingStatus.ACTIVE)
                .build();
        listing = listingRepo.save(listing);
        testListingId = listing.getId();
        // Listing.tenantId 為 insertable=false 影子欄位（DEF-041），JPA save 不寫 tenant_id
        jdbcTemplate.update("UPDATE listings SET tenant_id = ? WHERE id = ?", TENANT_ID, testListingId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 同時具備 ERP Controller 需要的角色權限（{@code STORE_OWNER}）與 ProductController 新增
     * SKU 端點需要的細粒度權限碼（{@code product:read}/{@code product:update}）——兩者是不同的
     * 權限字串體系（見 {@link WithErpSecurity} 與 {@code M01ProductIntegrationTest.authAs}），
     * 本測試橫跨兩個 Controller，故手動合併兩邊都需要的權限，而非套用只涵蓋單邊的既有測試註解。
     */
    private Authentication authAs() {
        UserPrincipal principal = new UserPrincipal(USER_ID, "sku-owner@example.com", "STORE_OWNER",
                TENANT_ID.toString());
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("STORE_OWNER"));
        authorities.add(new SimpleGrantedAuthority("ROLE_STORE_OWNER"));
        authorities.add(new SimpleGrantedAuthority("product:read"));
        authorities.add(new SimpleGrantedAuthority("product:update"));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    @Test
    @DisplayName("建立 SKU → 建立採購單 → 送出 → 收貨，product_inventory.total_qty 真的會增加")
    void createSkuThenReceivePurchaseOrder_actuallyIncreasesInventory() throws Exception {
        Authentication auth = authAs();

        // 1) 透過真正的端點建立 SKU（不是 raw SQL）
        String createSkuBody = objectMapper.writeValueAsString(java.util.Map.of(
                "skuCode", "SKU-IT-178-" + System.currentTimeMillis(),
                "specName", "標準款"));

        String skuResponse = mockMvc.perform(post("/v2/products/" + testListingId + "/skus")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSkuBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalQty").value(0))
                .andExpect(jsonPath("$.data.availableQty").value(0))
                .andReturn().getResponse().getContentAsString();

        JsonNode skuJson = objectMapper.readTree(skuResponse).path("data");
        UUID skuId = UUID.fromString(skuJson.path("id").asText());

        // 建立時應一併建立 total_qty=0 的庫存列（而非完全沒有列）
        assertThat(productInventoryRepository.findById(skuId)).isPresent();
        assertThat(productInventoryRepository.findById(skuId).get().getTotalQty()).isZero();

        // 2) 建立供應商
        String supplierBody = objectMapper.writeValueAsString(java.util.Map.of("name", "IT-178 Supplier"));
        String supplierResponse = mockMvc.perform(post("/v2/dashboard/suppliers")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplierBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID supplierId = UUID.fromString(objectMapper.readTree(supplierResponse).path("data").path("id").asText());

        // 3) listing-options 選擇器應該要能看到剛建立的 SKU（驗證 ErpController 的串接）
        mockMvc.perform(get("/v2/dashboard/purchase-orders/listing-options")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + testListingId + "')].skus[0].id").value(skuId.toString()));

        // 4) 建立採購單，品項帶入剛剛建立的真實 skuId
        String createPoBody = objectMapper.writeValueAsString(java.util.Map.of(
                "supplierId", supplierId.toString(),
                "items", List.of(java.util.Map.of(
                        "listingId", testListingId.toString(),
                        "skuId", skuId.toString(),
                        "quantity", 10,
                        "unitCost", 50.0))));

        String poResponse = mockMvc.perform(post("/v2/dashboard/purchase-orders")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPoBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();

        JsonNode poJson = objectMapper.readTree(poResponse).path("data");
        UUID poId = UUID.fromString(poJson.path("id").asText());
        UUID itemId = UUID.fromString(poJson.path("items").get(0).path("id").asText());

        // 5) 送出採購單（DRAFT → SUBMITTED，測試租戶無審批門檻設定，不會落入 PENDING_APPROVAL）
        mockMvc.perform(put("/v2/dashboard/purchase-orders/" + poId + "/submit")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        // 6) 確認收貨：這是修復前恆為 no-op 的那一步——item.getSkuId() 先前永遠是 null，
        // receivePurchaseOrder 的 `item.getSkuId() != null` 判斷式永遠不成立，
        // createInboundMovement 從未被呼叫，product_inventory.total_qty 紋風不動。
        String receiveBody = objectMapper.writeValueAsString(java.util.Map.of(
                "items", List.of(java.util.Map.of("itemId", itemId.toString(), "receivedQuantity", 10))));

        mockMvc.perform(put("/v2/dashboard/purchase-orders/" + poId + "/receive")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiveBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECEIVED"));

        // 7) 真正的迴歸驗證：庫存確實增加了，而不是靜默維持 0
        Integer totalQty = productInventoryRepository.findTotalQtyBySkuId(skuId);
        assertThat(totalQty).isEqualTo(10);
    }

    @Test
    @DisplayName("建立 SKU：代碼重複回傳 409（E-3005）")
    void createSku_duplicateSkuCode_returns409() throws Exception {
        Authentication auth = authAs();
        String skuCode = "SKU-IT-178-DUP-" + System.currentTimeMillis();

        mockMvc.perform(post("/v2/products/" + testListingId + "/skus")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("skuCode", skuCode))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v2/products/" + testListingId + "/skus")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("skuCode", skuCode))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("E-3005"));
    }
}
