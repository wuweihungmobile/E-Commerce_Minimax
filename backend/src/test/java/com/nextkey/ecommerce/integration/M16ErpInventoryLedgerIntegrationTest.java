package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.api.dto.erp.InventoryLedgerDto;
import com.nextkey.ecommerce.api.dto.erp.LowStockAlertDto;
import com.nextkey.ecommerce.core.cms.listing.ListingCardService;
import com.nextkey.ecommerce.core.erp.InventoryService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * ERP 庫存台帳整合測試（Sprint 116，DEF-066；真實 PostgreSQL）。
 *
 * <p><b>缺陷</b>：系統有兩張庫存表。真正的數字全在 {@code product_inventory}——訂單預扣／扣帳／釋放、
 * ERP 手動異動、採購收貨都寫這張；但 ERP 的庫存台帳列表／明細／低庫存預警與賣場商品卡讀的是
 * {@code inventory}，而**該表沒有任何生產程式碼寫入**。`V50` 的檔頭註解自承它是「entity 存在但沒有
 * 建表 migration，導致 ddl-auto=validate 失敗」才補建的空殼。生產環境上這三個端點永遠回空。
 *
 * <p><b>為什麼一直沒被發現</b>：既有的 M16 整合測試自己 {@code INSERT INTO inventory} 再查，
 * 於是測試全綠而生產全空——S97「所有相關測試都用固件繞過同一段邏輯」的又一個實例。
 * 本測試因此**刻意只寫入 {@code product_inventory}**（生產程式碼真正會寫的那張），
 * 不碰 {@code inventory}：這是本測試與既有測試唯一但決定性的差別。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-M16-LEDGER: ERP 庫存台帳讀真實庫存表（Sprint 116 / DEF-066）")
class M16ErpInventoryLedgerIntegrationTest {

    @Autowired private InventoryService inventoryService;
    @Autowired private ListingCardService listingCardService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;

    @MockBean private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    /** 每次跑次的唯一後綴：sku_code 有唯一約束，而測試 DB 不會在跑次之間重置。 */
    private String runStamp;
    private UUID tenantId;
    private UUID listingId;
    private UUID otherTenantId;
    private UUID otherListingId;

    @BeforeEach
    void setUp() {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());

        long stamp = System.nanoTime();
        runStamp = String.valueOf(stamp);
        tenantId = seedTenant("ledger-a-" + stamp);
        listingId = seedListing(tenantId, "台帳測試商品", "ledger-a-" + stamp);
        otherTenantId = seedTenant("ledger-b-" + stamp);
        otherListingId = seedListing(otherTenantId, "他租戶商品", "ledger-b-" + stamp);

        TenantContext.setCurrentTenant(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("IT-M16-LEDGER-001: 庫存台帳列表反映 product_inventory 的真實數字與品名")
    void inventoryLedger_readsProductInventory() {
        UUID skuId = givenSkuWithProductInventory(listingId, "SKU-L001", 100, 30);

        List<InventoryLedgerDto> rows = inventoryService
                .getInventoryLedger(PageRequest.of(0, 50)).getContent();

        assertThat(rows).hasSize(1);
        InventoryLedgerDto row = rows.get(0);
        assertThat(row.getSkuId()).isEqualTo(skuId);
        assertThat(row.getQuantity()).isEqualTo(100);
        assertThat(row.getReservedQuantity()).isEqualTo(30);
        assertThat(row.getAvailableQuantity()).isEqualTo(70);
        // 台帳沒有品名與 SKU 編號就只是一排 UUID，等同不能用
        assertThat(row.getSkuCode()).isEqualTo(code("SKU-L001"));
        assertThat(row.getProductName()).isEqualTo("台帳測試商品");
    }

    @Test
    @DisplayName("IT-M16-LEDGER-002: 庫存明細反映真實數字，並帶出該 SKU 的異動記錄")
    void inventoryDetail_readsProductInventoryAndMovements() {
        UUID skuId = givenSkuWithProductInventory(listingId, "SKU-L002", 80, 5);
        givenMovement(skuId, "INBOUND", 80);
        givenMovement(skuId, "OUTBOUND", 5);

        InventoryService.InventoryDetailDto detail = inventoryService.getInventoryBySku(skuId);

        assertThat(detail).isNotNull();
        assertThat(detail.getQuantity()).isEqualTo(80);
        assertThat(detail.getReservedQuantity()).isEqualTo(5);
        assertThat(detail.getAvailableQuantity()).isEqualTo(75);
        assertThat(detail.getSkuCode()).isEqualTo(code("SKU-L002"));
        assertThat(detail.getProductName()).isEqualTo("台帳測試商品");
        assertThat(detail.getMovements()).hasSize(2);
    }

    @Test
    @DisplayName("IT-M16-LEDGER-003: 可售量低於門檻時列入低庫存預警")
    void lowStockAlerts_readProductInventory() {
        // 門檻 10、可售 3 → 低於門檻一半，屬 CRITICAL
        givenSkuWithProductInventory(listingId, "SKU-L003", 3, 0);
        // 可售 50 遠高於門檻，不該出現
        givenSkuWithProductInventory(listingId, "SKU-L003B", 50, 0);

        List<LowStockAlertDto> alerts = inventoryService.getLowStockAlerts();

        assertThat(alerts).hasSize(1);
        LowStockAlertDto alert = alerts.get(0);
        assertThat(alert.getSkuCode()).isEqualTo(code("SKU-L003"));
        assertThat(alert.getProductName()).isEqualTo("台帳測試商品");
        assertThat(alert.getCurrentQuantity()).isEqualTo(3);
        assertThat(alert.getLowStockThreshold()).isEqualTo(10);
        assertThat(alert.getSeverity()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("IT-M16-LEDGER-004: 租戶隔離——他租戶的 SKU 不得出現在台帳、明細或預警（DEF-026 回歸）")
    void tenantIsolation_otherTenantSkuNotVisible() {
        givenSkuWithProductInventory(listingId, "SKU-MINE", 100, 0);
        UUID otherSkuId = givenSkuWithProductInventory(otherListingId, "SKU-OTHER", 5, 0);

        assertThat(inventoryService.getInventoryLedger(PageRequest.of(0, 50)).getContent())
                .extracting(InventoryLedgerDto::getSkuCode)
                .containsExactly(code("SKU-MINE"));
        assertThat(inventoryService.getInventoryBySku(otherSkuId)).isNull();
        assertThat(inventoryService.getLowStockAlerts())
                .extracting(LowStockAlertDto::getSkuCode)
                .doesNotContain(code("SKU-OTHER"));
    }

    @Test
    @DisplayName("IT-M16-LEDGER-005: 最近進出庫時間由流水帳推導（Sprint 115 補齊流水帳後才有意義）")
    void lastInboundOutbound_derivedFromMovements() {
        UUID skuId = givenSkuWithProductInventory(listingId, "SKU-L005", 60, 0);
        givenMovement(skuId, "INBOUND", 60);
        givenMovement(skuId, "OUTBOUND", 2);

        InventoryLedgerDto row = inventoryService
                .getInventoryLedger(PageRequest.of(0, 50)).getContent().get(0);

        assertThat(row.getLastInboundDate()).isNotNull();
        assertThat(row.getLastOutboundDate()).isNotNull();
    }

    @Test
    @DisplayName("IT-M16-LEDGER-006: 賣場商品卡的有無庫存反映真實庫存（買家端）")
    void listingCard_readsProductInventory() {
        givenSkuWithProductInventory(listingId, "SKU-L006", 40, 10);

        var card = listingCardService.getListingCard(listingId);

        assertThat(card.getAvailability()).isNotNull();
        assertThat(card.getAvailability().getInStock()).isTrue();
        assertThat(card.getAvailability().getAvailableQty()).isEqualTo(30);
    }

    // ── 固件：刻意只寫 product_inventory，不碰 inventory ──────────

    private UUID seedTenant(final String slug) {
        return tenantRepository.save(Tenant.builder()
                .name("Ledger Tenant " + slug)
                .slug(slug)
                .contactEmail(slug + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build()).getId();
    }

    private UUID seedListing(final UUID ownerTenantId, final String title, final String slug) {
        Tenant tenant = tenantRepository.findById(ownerTenantId).orElseThrow();
        User owner = userRepository.save(User.builder()
                .email(slug + "-owner@example.com")
                .passwordHash("dummy")
                .fullName("Ledger Owner")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(ownerTenantId)
                .build());
        return listingRepository.save(Listing.builder()
                .tenant(tenant)
                .owner(owner)
                .listingType(Listing.ListingType.PRODUCT)
                .title(title)
                .basePrice(new BigDecimal("100.00"))
                .status(Listing.ListingStatus.ACTIVE)
                .build()).getId();
    }

    /** 只寫 {@code product_inventory}——生產程式碼真正會寫的那張表。 */
    private UUID givenSkuWithProductInventory(final UUID ownerListingId, final String skuCode,
            final int totalQty, final int reservedQty) {
        UUID skuId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())
                """, skuId, ownerListingId, code(skuCode));
        jdbcTemplate.update("""
                INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, version, updated_at)
                VALUES (?, ?, ?, 10, 0, NOW())
                """, skuId, totalQty, reservedQty);
        return skuId;
    }

    /** 加上跑次後綴的 sku_code；斷言與固件共用同一個轉換，維持精確比對。 */
    private String code(final String base) {
        return base + "-" + runStamp;
    }

    private void givenMovement(final UUID skuId, final String movementType, final int quantity) {
        jdbcTemplate.update("""
                INSERT INTO stock_movements (id, tenant_id, sku_id, movement_type, quantity, balance_after,
                                             reference_type, created_at)
                VALUES (?, ?, ?, ?, ?, 0, 'ORDER', NOW())
                """, UUID.randomUUID(), tenantId, skuId, movementType, quantity);
    }
}
