package com.nextkey.ecommerce.core.erp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.erp.InventoryLedgerDto;
import com.nextkey.ecommerce.api.dto.erp.LowStockAlertDto;
import com.nextkey.ecommerce.api.dto.erp.StockMovementDto;
import com.nextkey.ecommerce.domain.model.inventory.Inventory;
import com.nextkey.ecommerce.domain.repository.InventoryRepository;
import com.nextkey.ecommerce.domain.repository.StockMovementRepository;
import com.nextkey.ecommerce.shared.tenant.TenantContext;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * 庫存 Service
 * PRD §9.15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;

    // Low stock threshold multiplier
    private static final double LOW_STOCK_MULTIPLIER = 0.5;

    /**
     * 取得庫存台帳列表
     */
    @Transactional(readOnly = true)
    public Page<InventoryLedgerDto> getInventoryLedger(final Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenant();

        Page<Inventory> inventories = inventoryRepository.findByTenantId(tenantId, pageable);

        return inventories.map(this::toLedgerDto);
    }

    /**
     * 依 SKU 取得庫存詳情（含異動記錄）
     */
    @Transactional(readOnly = true)
    public InventoryDetailDto getInventoryBySku(final UUID skuId) {
        UUID tenantId = TenantContext.getCurrentTenant();

        Inventory inventory = inventoryRepository.findBySkuId(skuId)
                .orElse(null);

        if (inventory == null) {
            return null;
        }

        List<StockMovementDto> movements = stockMovementRepository
                .findBySkuIdAndTenantIdOrderByCreatedAtDesc(skuId, tenantId)
                .stream()
                .map(this::toMovementDto)
                .collect(Collectors.toList());

        return InventoryDetailDto.builder()
                .skuId(inventory.getSkuId())
                .skuCode(inventory.getSkuCode())
                .productName(inventory.getProductName())
                .quantity(inventory.getTotalQty())
                .reservedQuantity(inventory.getReservedQty())
                .availableQuantity(inventory.getAvailableQty())
                .location(inventory.getLocation())
                .reorderPoint(inventory.getReorderPoint())
                .safetyStock(inventory.getSafetyStock())
                .lastInboundDate(inventory.getLastInboundDate())
                .lastOutboundDate(inventory.getLastOutboundDate())
                .movements(movements)
                .build();
    }

    /**
     * 取得低庫存預警列表
     */
    @Transactional(readOnly = true)
    public List<LowStockAlertDto> getLowStockAlerts() {
        UUID tenantId = TenantContext.getCurrentTenant();

        List<Inventory> lowStockItems = inventoryRepository.findLowStockItemsByTenant(tenantId);

        return lowStockItems.stream()
                .map(this::toLowStockAlertDto)
                .collect(Collectors.toList());
    }

    /**
     * 轉換為庫存台帳 DTO
     */
    private InventoryLedgerDto toLedgerDto(final Inventory inventory) {
        return InventoryLedgerDto.builder()
                .skuId(inventory.getSkuId())
                .totalQty(inventory.getTotalQty())
                .reservedQty(inventory.getReservedQty())
                .availableQty(inventory.getAvailableQty())
                .lowStockThreshold(inventory.getSafetyStock())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }

    /**
     * 轉換為低庫存預警 DTO
     */
    private LowStockAlertDto toLowStockAlertDto(final Inventory inventory) {
        String severity = inventory.getAvailableQty() <= inventory.getSafetyStock() * LOW_STOCK_MULTIPLIER
                ? "CRITICAL" : "LOW";

        return LowStockAlertDto.builder()
                .skuId(inventory.getSkuId())
                .currentQty(inventory.getAvailableQty())
                .lowStockThreshold(inventory.getSafetyStock())
                .severity(severity)
                .build();
    }

    /**
     * 轉換為異動 DTO
     */
    private StockMovementDto toMovementDto(final com.nextkey.ecommerce.domain.model.inventory.StockMovement movement) {
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
        private String location;
        private Integer reorderPoint;
        private Integer safetyStock;
        private Instant lastInboundDate;
        private Instant lastOutboundDate;
        private List<StockMovementDto> movements;
    }
}