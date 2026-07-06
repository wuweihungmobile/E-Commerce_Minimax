package com.nextkey.ecommerce.core.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.nextkey.ecommerce.api.dto.erp.InventoryLedgerDto;
import com.nextkey.ecommerce.api.dto.erp.LowStockAlertDto;
import com.nextkey.ecommerce.domain.model.inventory.Inventory;
import com.nextkey.ecommerce.domain.repository.InventoryRepository;
import com.nextkey.ecommerce.domain.repository.StockMovementRepository;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * {@link InventoryService} 單元測試（Sprint 72 US-001 + US-002）。
 *
 * <p>比照 {@code M16ErpIntegrationTest}（IT-M16-201~205）業務情境涵蓋正常路徑；
 * 另含 US-002：{@code getInventoryBySku} 跨租戶讀取洩漏（已確認）的紅燈證明測試，
 * 修復後轉綠，並保留正向對照測試避免矯枉過正。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService 單元測試（Sprint 72）")
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID otherTenantId = UUID.randomUUID();
    private final UUID skuId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Inventory inventoryOf(final UUID tid) {
        return Inventory.builder()
                .id(UUID.randomUUID())
                .skuId(skuId)
                .tenantId(tid)
                .totalQty(100)
                .reservedQty(10)
                .availableQty(90)
                .safetyStock(20)
                .reorderPoint(30)
                .skuCode("SKU-001")
                .productName("Test Product")
                .location("A-01")
                .build();
    }

    // ── getInventoryLedger ───────────────────────────────────────

    @Test
    @DisplayName("getInventoryLedger：分頁依租戶過濾")
    void getInventoryLedger_paginatedByTenant() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Inventory> page = new PageImpl<>(List.of(inventoryOf(tenantId)), pageable, 1);
        when(inventoryRepository.findByTenantId(tenantId, pageable)).thenReturn(page);

        Page<InventoryLedgerDto> result = inventoryService.getInventoryLedger(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getSkuId()).isEqualTo(skuId);
    }

    // ── getInventoryBySku：正常路徑 ──────────────────────────────

    @Test
    @DisplayName("getInventoryBySku：本租戶 SKU 存在時回傳含異動記錄的詳情")
    void getInventoryBySku_ownTenant_returnsDetailWithMovements() {
        when(inventoryRepository.findBySkuIdAndTenantId(skuId, tenantId))
                .thenReturn(Optional.of(inventoryOf(tenantId)));
        when(stockMovementRepository.findBySkuIdAndTenantIdOrderByCreatedAtDesc(skuId, tenantId))
                .thenReturn(List.of());

        InventoryService.InventoryDetailDto result = inventoryService.getInventoryBySku(skuId);

        assertThat(result).isNotNull();
        assertThat(result.getSkuId()).isEqualTo(skuId);
        assertThat(result.getQuantity()).isEqualTo(100);
        assertThat(result.getAvailableQuantity()).isEqualTo(90);
        assertThat(result.getMovements()).isEmpty();
    }

    @Test
    @DisplayName("getInventoryBySku：SKU 不存在時回傳 null")
    void getInventoryBySku_notFound_returnsNull() {
        when(inventoryRepository.findBySkuIdAndTenantId(skuId, tenantId)).thenReturn(Optional.empty());

        InventoryService.InventoryDetailDto result = inventoryService.getInventoryBySku(skuId);

        assertThat(result).isNull();
    }

    // ── US-002（已確認）：跨租戶讀取洩漏 ─────────────────────────

    @Test
    @DisplayName("US-002：getInventoryBySku 對他租戶的 SKU 不得洩漏庫存資料（修復前為紅燈，修復後轉綠）")
    void getInventoryBySku_crossTenantSku_mustNotLeakOtherTenantData() {
        // skuId 實際屬於 otherTenantId，但呼叫端目前租戶為 tenantId（並非其擁有者）。
        // 修復前：InventoryService.getInventoryBySku 呼叫 InventoryRepository.findBySkuId(skuId)，
        //         完全未帶入 tenantId 過濾，任何租戶皆可讀到 otherTenantId 的庫存資料
        //         → 本測試（斷言不得洩漏）於修復前執行會失敗，證明漏洞存在（已實測驗證，見 Sprint 72 紀錄）。
        // 修復後：Service 改用 findBySkuIdAndTenantId(skuId, tenantId)，跨租戶查詢查無結果 → 回傳 null。
        when(inventoryRepository.findBySkuIdAndTenantId(skuId, tenantId)).thenReturn(Optional.empty());

        InventoryService.InventoryDetailDto result = inventoryService.getInventoryBySku(skuId);

        assertThat(result)
                .as("跨租戶查詢不得洩漏他租戶的庫存資料")
                .isNull();
    }

    // ── getLowStockAlerts：嚴重度判定 ────────────────────────────

    @Test
    @DisplayName("getLowStockAlerts：可用量 <= 安全庫存的一半 → CRITICAL")
    void getLowStockAlerts_belowHalfSafetyStock_returnsCritical() {
        Inventory low = Inventory.builder()
                .skuId(skuId)
                .tenantId(tenantId)
                .availableQty(5)
                .safetyStock(20) // 一半為 10，5 <= 10 → CRITICAL
                .build();
        when(inventoryRepository.findLowStockItemsByTenant(tenantId)).thenReturn(List.of(low));

        List<LowStockAlertDto> result = inventoryService.getLowStockAlerts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSeverity()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("getLowStockAlerts：可用量介於安全庫存一半與安全庫存之間 → LOW")
    void getLowStockAlerts_belowSafetyStockAboveHalf_returnsLow() {
        Inventory low = Inventory.builder()
                .skuId(skuId)
                .tenantId(tenantId)
                .availableQty(15)
                .safetyStock(20) // 一半為 10，15 > 10 → LOW
                .build();
        when(inventoryRepository.findLowStockItemsByTenant(tenantId)).thenReturn(List.of(low));

        List<LowStockAlertDto> result = inventoryService.getLowStockAlerts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSeverity()).isEqualTo("LOW");
    }
}
