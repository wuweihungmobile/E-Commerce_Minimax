package com.nextkey.ecommerce.core.erp;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.erp.InventoryLedgerDto;
import com.nextkey.ecommerce.api.dto.erp.LowStockAlertDto;
import com.nextkey.ecommerce.api.dto.erp.StockMovementDto;
import com.nextkey.ecommerce.domain.model.inventory.StockMovement;
import com.nextkey.ecommerce.domain.repository.ProductInventoryRepository;
import com.nextkey.ecommerce.domain.repository.ProductInventoryRepository.InventoryLedgerRow;
import com.nextkey.ecommerce.domain.repository.StockMovementRepository;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 庫存 Service
 * PRD §6.7.2「庫存台帳」「庫存預警」（P0/P1）、§9.15
 *
 * <p>🔴 Sprint 116（DEF-066）：資料來源由 {@code inventory} 表改為 {@code product_inventory}。
 * 系統有兩張庫存表，真正的數字全在 {@code product_inventory}——訂單預扣／扣帳／釋放、
 * ERP 手動異動、採購收貨都寫這張；而本 Service 修復前讀的 {@code inventory}
 * **沒有任何生產程式碼寫入**（`V50` 檔頭自承是為了讓 ddl-auto=validate 過關而補建的空殼）。
 * 後果是生產環境上 ERP 庫存台帳、庫存明細、低庫存預警**三者永遠空白**。
 *
 * <p>為什麼一直沒被發現：既有的 M16 整合測試自己 {@code INSERT INTO inventory} 再查，測試因此全綠
 * ——S97「所有相關測試都用固件繞過同一段邏輯」的又一實例。守衛見
 * {@code M16ErpInventoryLedgerIntegrationTest}，它刻意只寫 {@code product_inventory}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductInventoryRepository productInventoryRepository;
    private final StockMovementRepository stockMovementRepository;

    /** 可售量低於門檻這個比例時升級為 CRITICAL。 */
    private static final double LOW_STOCK_MULTIPLIER = 0.5;

    /** 門檻缺值時的保底（{@code product_inventory.low_stock_threshold} 的 DDL 預設亦為 10）。 */
    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 10;

    private static final Set<String> INBOUND_TYPE_NAMES =
            StockMovement.typeNames(StockMovement.INBOUND_TYPES);
    private static final Set<String> OUTBOUND_TYPE_NAMES =
            StockMovement.typeNames(StockMovement.OUTBOUND_TYPES);

    /**
     * 取得庫存台帳列表
     */
    @Transactional(readOnly = true)
    public Page<InventoryLedgerDto> getInventoryLedger(final Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenant();

        return productInventoryRepository
                .findLedgerByTenant(tenantId, INBOUND_TYPE_NAMES, OUTBOUND_TYPE_NAMES, pageable)
                .map(this::toLedgerDto);
    }

    /**
     * 依 SKU 取得庫存詳情（含異動記錄）
     */
    @Transactional(readOnly = true)
    public InventoryDetailDto getInventoryBySku(final UUID skuId) {
        UUID tenantId = TenantContext.getCurrentTenant();

        // DEF-026（Sprint 72）的租戶隔離保留：查詢本身即帶 tenant 條件，他租戶的 SKU 一律查不到。
        // 租戶歸屬經由 sku → listing 取得——product_inventory 沒有 tenant_id 欄位。
        InventoryLedgerRow row = productInventoryRepository
                .findLedgerRowBySkuIdAndTenant(skuId, tenantId, INBOUND_TYPE_NAMES, OUTBOUND_TYPE_NAMES)
                .orElse(null);

        if (row == null) {
            return null;
        }

        List<StockMovementDto> movements = stockMovementRepository
                .findBySkuIdAndTenantIdOrderByCreatedAtDesc(skuId, tenantId)
                .stream()
                .map(this::toMovementDto)
                .collect(Collectors.toList());

        return InventoryDetailDto.builder()
                .skuId(row.getSkuId())
                .skuCode(row.getSkuCode())
                .productName(row.getProductName())
                .quantity(row.getQuantity())
                .reservedQuantity(row.getReservedQuantity())
                .availableQuantity(row.getAvailableQuantity())
                .lowStockThreshold(thresholdOf(row))
                .lastInboundDate(row.getLastInboundDate())
                .lastOutboundDate(row.getLastOutboundDate())
                .movements(movements)
                .build();
    }

    /**
     * 取得低庫存預警列表
     */
    @Transactional(readOnly = true)
    public List<LowStockAlertDto> getLowStockAlerts() {
        UUID tenantId = TenantContext.getCurrentTenant();

        return productInventoryRepository
                .findLowStockByTenant(tenantId, INBOUND_TYPE_NAMES, OUTBOUND_TYPE_NAMES)
                .stream()
                .map(this::toLowStockAlertDto)
                .collect(Collectors.toList());
    }

    private int thresholdOf(final InventoryLedgerRow row) {
        return row.getLowStockThreshold() != null
                ? row.getLowStockThreshold() : DEFAULT_LOW_STOCK_THRESHOLD;
    }

    /**
     * 轉換為庫存台帳 DTO
     */
    private InventoryLedgerDto toLedgerDto(final InventoryLedgerRow row) {
        return InventoryLedgerDto.builder()
                .skuId(row.getSkuId())
                .skuCode(row.getSkuCode())
                .productName(row.getProductName())
                .quantity(row.getQuantity())
                .reservedQuantity(row.getReservedQuantity())
                .availableQuantity(row.getAvailableQuantity())
                .lowStockThreshold(thresholdOf(row))
                .lastInboundDate(row.getLastInboundDate())
                .lastOutboundDate(row.getLastOutboundDate())
                .updatedAt(row.getUpdatedAt())
                .build();
    }

    /**
     * 轉換為低庫存預警 DTO
     */
    private LowStockAlertDto toLowStockAlertDto(final InventoryLedgerRow row) {
        int threshold = thresholdOf(row);
        int available = row.getAvailableQuantity() != null ? row.getAvailableQuantity() : 0;
        String severity = available <= threshold * LOW_STOCK_MULTIPLIER ? "CRITICAL" : "LOW";

        return LowStockAlertDto.builder()
                .skuId(row.getSkuId())
                .skuCode(row.getSkuCode())
                .productName(row.getProductName())
                .currentQuantity(available)
                .lowStockThreshold(threshold)
                .severity(severity)
                .build();
    }

    /**
     * 轉換為異動 DTO
     */
    private StockMovementDto toMovementDto(final StockMovement movement) {
        return StockMovementDto.builder()
                .id(movement.getId())
                .tenantId(movement.getTenantId())
                .skuId(movement.getSkuId())
                .movementType(movement.getMovementType() != null ? movement.getMovementType().name() : null)
                .quantity(movement.getQuantity())
                .beforeTotalQty(movement.getBeforeTotalQty())
                .afterTotalQty(movement.getAfterTotalQty())
                .referenceType(movement.getReferenceType() != null ? movement.getReferenceType().name() : null)
                .referenceId(movement.getReferenceId())
                .notes(movement.getNotes())
                .createdBy(movement.getCreatedBy())
                .createdAt(movement.getCreatedAt())
                .build();
    }

    /**
     * 庫存詳情（含異動記錄）- 扁平結構
     *
     * <p>Sprint 116（DEF-066）：移除 {@code location}／{@code reorderPoint}／{@code safetyStock}。
     * 資料來源改為 {@code product_inventory} 後這三欄**沒有任何來源**，留著只會是永遠為 null 的欄位
     * ——那正是本缺陷得以長期存活的土壤。{@code product_inventory} 只有單一的低庫存門檻。
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class InventoryDetailDto {
        private UUID skuId;
        private String skuCode;
        private String productName;
        private Integer quantity;
        private Integer reservedQuantity;
        private Integer availableQuantity;
        private Integer lowStockThreshold;
        private Instant lastInboundDate;
        private Instant lastOutboundDate;
        private List<StockMovementDto> movements;
    }
}
