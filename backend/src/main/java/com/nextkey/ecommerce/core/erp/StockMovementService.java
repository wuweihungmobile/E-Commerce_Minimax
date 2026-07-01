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
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.ProductInventory;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
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
    private final ListingRepository listingRepository;

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

        // 驗證租戶擁有此 SKU 所屬 listing（租戶隔離，DEF-017 修復；比照 NotificationTemplateService 慣例）
        UUID productListingId = inventory.getSku().getProductListingId();
        Listing listing = listingRepository.findById(productListingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_3003,
                        String.format("Listing not found for SKU: skuId=%s", skuId)));
        // null-safe：listing 租戶為空（資料異常）或不符當前租戶 → 拒絕（避免 NPE→500）
        if (!tenantId.equals(listing.getTenantId())) {
            throw new BusinessException(ErrorCode.E_1007, "SKU does not belong to current tenant");
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
}