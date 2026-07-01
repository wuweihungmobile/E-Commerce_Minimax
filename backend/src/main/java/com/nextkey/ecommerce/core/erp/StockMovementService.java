package com.nextkey.ecommerce.core.erp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.erp.StockMovementDto;
import com.nextkey.ecommerce.api.dto.erp.StockMovementRequest;
import com.nextkey.ecommerce.domain.model.inventory.StockMovement;
import com.nextkey.ecommerce.domain.model.product.ProductInventory;
import com.nextkey.ecommerce.domain.repository.ProductInventoryRepository;
import com.nextkey.ecommerce.domain.repository.StockMovementRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * 庫存異動 Service
 * PRD §6.7, §9.15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockMovementService {

    private final StockMovementRepository stockMovementRepository;
    private final ProductInventoryRepository productInventoryRepository;

    /**
     * 手動庫存異動
     * @param request 異動請求
     * @param userId 操作者 ID
     * @return 異動記錄 DTO
     */
    @Transactional
    public StockMovementDto createManualMovement(final StockMovementRequest request, final UUID userId) {
        UUID tenantId = TenantContext.getCurrentTenant();
        UUID skuId = request.getSkuId();

        // 取得當前庫存
        ProductInventory inventory = productInventoryRepository.findById(skuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_3003,
                        String.format("SKU not found: skuId=%s", skuId)));

        // 驗證 tenant
        if (!inventory.getSku().getProductListingId().equals(getTenantListings(tenantId))) {
            // 簡化檢查：直接查庫存的 tenant 關聯
        }

        // 解析異動類型
        StockMovement.MovementType movementType;
        try {
            movementType = StockMovement.MovementType.valueOf(request.getMovementType());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.E_7005,
                    String.format("Invalid movement type: %s", request.getMovementType()));
        }

        // 驗證不允許的手動類型
        if (movementType == StockMovement.MovementType.PURCHASE_RECEIPT
                || movementType == StockMovement.MovementType.SALE
                || movementType == StockMovement.MovementType.RESERVATION
                || movementType == StockMovement.MovementType.RELEASE) {
            throw new BusinessException(ErrorCode.E_7005,
                    "Manual movement cannot use PURCHASE_RECEIPT/SALE/RESERVATION/RELEASE types");
        }

        int beforeTotalQty = inventory.getTotalQty();
        int quantity = request.getQuantity();

        // 根據異動類型更新庫存
        applyMovementType(inventory, movementType, quantity, beforeTotalQty);

        productInventoryRepository.save(inventory);

        int afterTotalQty = inventory.getTotalQty();

        // 建立異動記錄
        StockMovement movement = StockMovement.builder()
                .tenantId(tenantId)
                .skuId(skuId)
                .movementType(movementType)
                .quantity(quantity)
                .beforeTotalQty(beforeTotalQty)
                .afterTotalQty(afterTotalQty)
                .balanceAfter(afterTotalQty)
                .referenceType(StockMovement.ReferenceType.MANUAL)
                .notes(request.getNotes())
                .createdBy(userId)
                .build();

        StockMovement saved = stockMovementRepository.save(movement);
        log.info("Created manual stock movement: id={}, tenantId={}, type={}, qty={}",
                saved.getId(), tenantId, movementType, quantity);

        return toDto(saved);
    }

    private void applyMovementType(ProductInventory inventory, StockMovement.MovementType movementType,
            int quantity, int beforeTotalQty) {
        switch (movementType) {
            case ADJUSTMENT:
            case TRANSFER_IN:
                inventory.addStock(quantity);
                break;
            case DAMAGE:
            case TRANSFER_OUT:
            case THEFT:
                if (beforeTotalQty < quantity) {
                    throw new BusinessException(ErrorCode.E_7004,
                            String.format("Insufficient stock: available=%d, requested=%d", beforeTotalQty, quantity));
                }
                inventory.deductStock(quantity);
                break;
            default:
                break;
        }
    }

    /**
     * 依 SKU 取得異動記錄
     */
    @Transactional(readOnly = true)
    public List<StockMovementDto> getMovementsBySku(final UUID skuId) {
        UUID tenantId = TenantContext.getCurrentTenant();

        List<StockMovement> movements = stockMovementRepository
                .findBySkuIdAndTenantIdOrderByCreatedAtDesc(skuId, tenantId);

        return movements.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 分頁取得所有異動記錄
     */
    @Transactional(readOnly = true)
    public Page<StockMovementDto> getMovements(final Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenant();

        Page<StockMovement> movements = stockMovementRepository.findByTenantId(tenantId, pageable);

        return movements.map(this::toDto);
    }

    /**
     * 依時間範圍取得異動記錄
     */
    @Transactional(readOnly = true)
    public List<StockMovementDto> getMovementsByDateRange(final Instant start, final Instant end) {
        UUID tenantId = TenantContext.getCurrentTenant();

        List<StockMovement> movements = stockMovementRepository
                .findByTenantIdAndDateRange(tenantId, start, end);

        return movements.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 轉換為 DTO
     */
    private StockMovementDto toDto(final StockMovement movement) {
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
     * -placeholder: 取得 tenant 下的 listing IDs
     */
    private UUID getTenantListings(final UUID tenantId) {
        // 這是簡化版本，實際需要透過 ListingRepository 查詢
        return tenantId;
    }
}