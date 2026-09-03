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

import com.nextkey.ecommerce.api.dto.erp.StockMovementDto;
import com.nextkey.ecommerce.api.dto.erp.StockMovementRequest;
import com.nextkey.ecommerce.core.erp.StockMovementService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * ERP 庫存異動列表的顯示欄位整合測試（Sprint 117，DEF-064；真實 PostgreSQL）。
 *
 * <p><b>缺陷</b>：`/dashboard/erp/stock-movements` 的表格有「SKU／品名／參考單號」三欄，永遠顯示 `-`：
 * <ul>
 *   <li>{@code StockMovementDto} 有 {@code skuCode}／{@code productName} 兩個欄位，
 *       但 {@code StockMovementService.toDto()} <b>從來沒填過值</b>。</li>
 *   <li>{@code StockMovementRequest} <b>根本沒有 referenceNumber 欄位</b>，
 *       前端表單那格輸入送出後被 Jackson 靜默忽略。</li>
 * </ul>
 *
 * <p>使用者拍板選項 C：來源單據（系統推導、唯讀）與參考單號（店家自填、可存）兩者並存
 * ——它們是兩件不同的事，共用一欄會讓「這個單號是誰產生的」永遠說不清楚。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-M16-DISPLAY: 庫存異動列表顯示欄位（Sprint 117 / DEF-064）")
class M16ErpStockMovementDisplayIntegrationTest {

    @Autowired private StockMovementService stockMovementService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;

    @MockBean private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    private String runStamp;
    private UUID tenantId;
    private UUID userId;
    private UUID listingId;
    private UUID skuId;

    @BeforeEach
    void setUp() {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());

        runStamp = String.valueOf(System.nanoTime());
        seedTenantUserListing();
        skuId = seedSku("SKU-DISPLAY");
        TenantContext.setCurrentTenant(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("IT-M16-DISPLAY-001: 手動異動列表帶出 SKU 編號與品名（修復前兩欄恆為 null）")
    void movementList_carriesSkuCodeAndProductName() {
        stockMovementService.createManualMovement(manualRequest(5, null), userId);

        StockMovementDto row = onlyMovement();

        assertThat(row.getSkuCode()).isEqualTo("SKU-DISPLAY-" + runStamp);
        assertThat(row.getProductName()).isEqualTo("顯示測試商品");
    }

    @Test
    @DisplayName("IT-M16-DISPLAY-002: 店家填的參考單號真的被存下來（修復前被靜默丟棄）")
    void manualMovement_persistsReferenceNumber() {
        stockMovementService.createManualMovement(manualRequest(3, "盤點單 2026-09"), userId);

        assertThat(onlyMovement().getReferenceNumber()).isEqualTo("盤點單 2026-09");
    }

    @Test
    @DisplayName("IT-M16-DISPLAY-003: 手動異動沒有來源單據（sourceDocument 為 null，不得亂填）")
    void manualMovement_hasNoSourceDocument() {
        stockMovementService.createManualMovement(manualRequest(2, "手寫單號"), userId);

        StockMovementDto row = onlyMovement();
        assertThat(row.getReferenceType()).isEqualTo("MANUAL");
        assertThat(row.getSourceDocument()).isNull();
        // 店家自填的單號不得被當成來源單據——那會讓「這筆是誰產生的」說不清楚
        assertThat(row.getReferenceNumber()).isEqualTo("手寫單號");
    }

    @Test
    @DisplayName("IT-M16-DISPLAY-004: 採購收貨的來源單據是採購單號")
    void purchaseReceipt_sourceDocumentIsPoNumber() {
        // po_number 有唯一約束，而測試 DB 不會在跑次之間重置 → 帶上跑次後綴
        String poNumber = "PO-2026-" + runStamp;
        UUID poId = seedPurchaseOrder(poNumber);
        seedMovement("INBOUND", "PURCHASE_ORDER", poId);

        assertThat(onlyMovement().getSourceDocument()).isEqualTo(poNumber);
    }

    @Test
    @DisplayName("IT-M16-DISPLAY-005: 訂單異動的來源單據是訂單 id 前八碼（比照前端既有的訂單顯示慣例）")
    void orderMovement_sourceDocumentIsShortOrderId() {
        UUID orderId = UUID.randomUUID();
        seedMovement("RESERVE", "ORDER", orderId);

        assertThat(onlyMovement().getSourceDocument()).isEqualTo(orderId.toString().substring(0, 8));
    }

    @Test
    @DisplayName("IT-M16-DISPLAY-006: 依 SKU 查詢的異動記錄同樣帶齊顯示欄位（庫存明細頁用）")
    void movementsBySku_carryDisplayFields() {
        stockMovementService.createManualMovement(manualRequest(4, "單號-A"), userId);

        List<StockMovementDto> rows = stockMovementService.getMovementsBySku(skuId);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSkuCode()).isEqualTo("SKU-DISPLAY-" + runStamp);
        assertThat(rows.get(0).getProductName()).isEqualTo("顯示測試商品");
        assertThat(rows.get(0).getReferenceNumber()).isEqualTo("單號-A");
    }

    // ── 固件 ──────────────────────────────────────────────────

    private StockMovementRequest manualRequest(final int quantity, final String referenceNumber) {
        return StockMovementRequest.builder()
                .skuId(skuId)
                .movementType("ADJUST_PLUS")
                .quantity(quantity)
                .referenceNumber(referenceNumber)
                .notes("顯示欄位測試")
                .build();
    }

    private StockMovementDto onlyMovement() {
        List<StockMovementDto> rows = stockMovementService
                .getMovements(PageRequest.of(0, 50)).getContent();
        assertThat(rows).as("預期恰有一筆異動記錄").hasSize(1);
        return rows.get(0);
    }

    private void seedTenantUserListing() {
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Display Tenant")
                .slug("display-" + runStamp)
                .contactEmail("display-" + runStamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());
        tenantId = tenant.getId();

        User owner = userRepository.save(User.builder()
                .email("display-owner-" + runStamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Display Owner")
                .role(User.UserRole.STORE_OWNER)
                .status("ACTIVE")
                .tenantId(tenantId)
                .build());
        userId = owner.getId();

        listingId = listingRepository.save(Listing.builder()
                .tenant(tenant)
                .owner(owner)
                .listingType(Listing.ListingType.PRODUCT)
                .title("顯示測試商品")
                .basePrice(new BigDecimal("100.00"))
                .status(Listing.ListingStatus.ACTIVE)
                .build()).getId();
    }

    private UUID seedSku(final String skuCode) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO product_skus (id, product_listing_id, sku_code, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())
                """, id, listingId, skuCode + "-" + runStamp);
        jdbcTemplate.update("""
                INSERT INTO product_inventory (sku_id, total_qty, reserved_qty, low_stock_threshold, version, updated_at)
                VALUES (?, 100, 0, 10, 0, NOW())
                """, id);
        return id;
    }

    private UUID seedPurchaseOrder(final String poNumber) {
        UUID supplierId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO suppliers (id, tenant_id, name, email, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                """, supplierId, tenantId, "Display Supplier", "display-sup-" + runStamp + "@example.com");
        UUID poId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO purchase_orders (id, tenant_id, supplier_id, po_number, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'RECEIVED', NOW(), NOW())
                """, poId, tenantId, supplierId, poNumber);
        return poId;
    }

    /** 直接種一筆系統產生的異動（採購收貨／訂單流程的產物），只為驗證來源單據的推導。 */
    private void seedMovement(final String movementType, final String referenceType, final UUID referenceId) {
        jdbcTemplate.update("""
                INSERT INTO stock_movements (id, tenant_id, sku_id, movement_type, quantity, balance_after,
                                             reference_type, reference_id, created_at)
                VALUES (?, ?, ?, ?, 1, 0, ?, ?, NOW())
                """, UUID.randomUUID(), tenantId, skuId, movementType, referenceType, referenceId);
    }
}
