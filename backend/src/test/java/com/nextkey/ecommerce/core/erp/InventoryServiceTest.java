package com.nextkey.ecommerce.core.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.nextkey.ecommerce.api.dto.erp.InventoryLedgerDto;
import com.nextkey.ecommerce.api.dto.erp.LowStockAlertDto;
import com.nextkey.ecommerce.domain.repository.ProductInventoryRepository;
import com.nextkey.ecommerce.domain.repository.ProductInventoryRepository.InventoryLedgerRow;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * {@link InventoryService} 單元測試（Sprint 72 US-001＋US-002；Sprint 116／DEF-066 改寫）。
 *
 * <p>Sprint 116 起資料來源為 {@code product_inventory}，且「哪些列屬於本租戶」「可售量是否低於門檻」
 * 都下沉到 SQL 的 JOIN 與 WHERE。mock 掉 Repository 的測試無從觀察那些條件——硬要驗只會變成
 * 用固件回放自己寫的 stub（承 Sprint 103 對 {@code ProductInventoryServiceTest} 的同一判斷）。
 * 租戶隔離、真實數量、低庫存篩選一律由 {@code M16ErpInventoryLedgerIntegrationTest} 以真實
 * PostgreSQL 驗證（含 US-002 跨租戶洩漏的回歸案例）。
 *
 * <p>本檔因此只保留 mock 層級真正驗得到的東西：**DTO 對應**與**嚴重度／門檻的計算規則**。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService 單元測試（Sprint 72；Sprint 116 改寫）")
class InventoryServiceTest {

    @Mock
    private ProductInventoryRepository productInventoryRepository;

    /** Sprint 117（DEF-064）：異動記錄改由 StockMovementService 提供（含 SKU 編號與品名）。 */
    @Mock
    private StockMovementService stockMovementService;

    @InjectMocks
    private InventoryService inventoryService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID skuId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * 台帳投影的測試替身；只需回傳固定值，故用 mock 而非自訂實作類別。
     *
     * <p>🔴 呼叫端務必**先建好再放進 {@code thenReturn}**：本方法內部會對另一個 mock 做 stubbing，
     * 若寫成 {@code when(repo.xxx()).thenReturn(List.of(rowOf(...)))}，外層的 {@code when(...)}
     * 尚未完成就開始內層 stubbing，Mockito 會丟 {@code UnfinishedStubbingException}。
     */
    private InventoryLedgerRow rowOf(final int quantity, final int reserved, final Integer threshold) {
        InventoryLedgerRow row = org.mockito.Mockito.mock(InventoryLedgerRow.class);
        lenient().when(row.getSkuId()).thenReturn(skuId);
        lenient().when(row.getSkuCode()).thenReturn("SKU-001");
        lenient().when(row.getProductName()).thenReturn("測試商品");
        lenient().when(row.getQuantity()).thenReturn(quantity);
        lenient().when(row.getReservedQuantity()).thenReturn(reserved);
        lenient().when(row.getAvailableQuantity()).thenReturn(quantity - reserved);
        lenient().when(row.getLowStockThreshold()).thenReturn(threshold);
        lenient().when(row.getUpdatedAt()).thenReturn(Instant.now());
        return row;
    }

    @Test
    @DisplayName("getInventoryLedger：投影完整對應到台帳 DTO（含 SKU 編號與品名）")
    void getInventoryLedger_mapsProjectionToDto() {
        Pageable pageable = PageRequest.of(0, 20);
        InventoryLedgerRow row = rowOf(100, 30, 10);
        when(productInventoryRepository.findLedgerByTenant(eq(tenantId), anyCollection(), anyCollection(),
                eq(pageable))).thenReturn(new PageImpl<>(List.of(row)));

        List<InventoryLedgerDto> result = inventoryService.getInventoryLedger(pageable).getContent();

        assertThat(result).hasSize(1);
        InventoryLedgerDto dto = result.get(0);
        assertThat(dto.getSkuId()).isEqualTo(skuId);
        assertThat(dto.getQuantity()).isEqualTo(100);
        assertThat(dto.getReservedQuantity()).isEqualTo(30);
        assertThat(dto.getAvailableQuantity()).isEqualTo(70);
        // 台帳沒有品名與 SKU 編號就只是一排 UUID
        assertThat(dto.getSkuCode()).isEqualTo("SKU-001");
        assertThat(dto.getProductName()).isEqualTo("測試商品");
    }

    @Test
    @DisplayName("getInventoryBySku：查得到時附上該 SKU 的異動記錄")
    void getInventoryBySku_includesMovements() {
        InventoryLedgerRow row = rowOf(80, 5, 10);
        when(productInventoryRepository.findLedgerRowBySkuIdAndTenant(eq(skuId), eq(tenantId),
                anyCollection(), anyCollection())).thenReturn(Optional.of(row));
        when(stockMovementService.getMovementsBySku(skuId)).thenReturn(List.of());

        InventoryService.InventoryDetailDto detail = inventoryService.getInventoryBySku(skuId);

        assertThat(detail).isNotNull();
        assertThat(detail.getQuantity()).isEqualTo(80);
        assertThat(detail.getAvailableQuantity()).isEqualTo(75);
        assertThat(detail.getMovements()).isEmpty();
    }

    @Test
    @DisplayName("getInventoryBySku：查無資料時回傳 null（他租戶的 SKU 由 SQL 的 tenant 條件擋掉）")
    void getInventoryBySku_notFound_returnsNull() {
        when(productInventoryRepository.findLedgerRowBySkuIdAndTenant(eq(skuId), eq(tenantId),
                anyCollection(), anyCollection())).thenReturn(Optional.empty());

        assertThat(inventoryService.getInventoryBySku(skuId)).isNull();
    }

    @Test
    @DisplayName("getLowStockAlerts：可售量 <= 門檻一半 → CRITICAL")
    void getLowStockAlerts_critical() {
        InventoryLedgerRow row = rowOf(4, 0, 10); // 可售 4 <= 10 * 0.5
        when(productInventoryRepository.findLowStockByTenant(eq(tenantId), anyCollection(), anyCollection()))
                .thenReturn(List.of(row));

        List<LowStockAlertDto> alerts = inventoryService.getLowStockAlerts();

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getSeverity()).isEqualTo("CRITICAL");
        assertThat(alerts.get(0).getCurrentQuantity()).isEqualTo(4);
        assertThat(alerts.get(0).getLowStockThreshold()).isEqualTo(10);
    }

    @Test
    @DisplayName("getLowStockAlerts：可售量介於門檻一半與門檻之間 → LOW")
    void getLowStockAlerts_low() {
        InventoryLedgerRow row = rowOf(8, 0, 10); // 5 < 8 <= 10
        when(productInventoryRepository.findLowStockByTenant(eq(tenantId), anyCollection(), anyCollection()))
                .thenReturn(List.of(row));

        assertThat(inventoryService.getLowStockAlerts().get(0).getSeverity()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("getLowStockAlerts：門檻缺值時退回預設 10，不得因 null 而炸掉")
    void getLowStockAlerts_nullThreshold_fallsBackToDefault() {
        InventoryLedgerRow row = rowOf(3, 0, null);
        when(productInventoryRepository.findLowStockByTenant(eq(tenantId), anyCollection(), anyCollection()))
                .thenReturn(List.of(row));

        LowStockAlertDto alert = inventoryService.getLowStockAlerts().get(0);

        assertThat(alert.getLowStockThreshold()).isEqualTo(10);
        assertThat(alert.getSeverity()).isEqualTo("CRITICAL");
    }
}
