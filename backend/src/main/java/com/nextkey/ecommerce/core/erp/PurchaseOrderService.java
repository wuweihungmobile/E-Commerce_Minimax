package com.nextkey.ecommerce.core.erp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.erp.PurchaseOrderCreateRequest;
import com.nextkey.ecommerce.api.dto.erp.PurchaseOrderDto;
import com.nextkey.ecommerce.api.dto.erp.PurchaseOrderReceiveRequest;
import com.nextkey.ecommerce.api.dto.erp.PurchaseOrderUpdateRequest;
import com.nextkey.ecommerce.domain.model.erp.Supplier;
import com.nextkey.ecommerce.domain.model.inventory.PurchaseOrder;
import com.nextkey.ecommerce.domain.model.inventory.PurchaseOrderItem;
import com.nextkey.ecommerce.domain.model.inventory.StockMovement;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductInventoryRepository;
import com.nextkey.ecommerce.domain.repository.ProductSkuRepository;
import com.nextkey.ecommerce.domain.repository.PurchaseOrderItemRepository;
import com.nextkey.ecommerce.domain.repository.PurchaseOrderRepository;
import com.nextkey.ecommerce.domain.repository.StockMovementRepository;
import com.nextkey.ecommerce.domain.repository.SupplierRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * 採購單 Service
 * PRD §9.15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    @SuppressWarnings("unused")
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final ProductSkuRepository productSkuRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ListingRepository listingRepository;
    private final TenantRepository tenantRepository;
    private final FeatureToggleService featureToggleService;

    // PO Number generation
    private static final int PO_NUMBER_MIN = 100000;
    private static final int PO_NUMBER_MAX = 999999;

    /**
     * 建立採購單 (DRAFT)
     */
    @Transactional
    public PurchaseOrderDto createPurchaseOrder(final PurchaseOrderCreateRequest request, final UUID userId) {
        featureToggleService.checkFeatureEnabled("ERP_ENABLED");

        UUID tenantId = TenantContext.getCurrentTenant();

        // 驗證供應商存在且屬於該 tenant
        validateSupplier(request.getSupplierId(), tenantId);

        // 產生 PO Number: PO-{YYYYMMDD}-{random6}
        String poNumber = generatePoNumber();

        // 建立 PurchaseOrder
        PurchaseOrder purchaseOrder = PurchaseOrder.builder()
                .tenantId(tenantId)
                .supplierId(request.getSupplierId())
                .poNumber(poNumber)
                .status(PurchaseOrder.POStatus.DRAFT)
                .expectedDeliveryDate(request.getExpectedDeliveryDate())
                .notes(request.getNotes())
                .currency("TWD")
                .createdBy(userId)
                .build();

        // 建立 Items
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PurchaseOrderCreateRequest.PurchaseOrderItemRequest itemRequest : request.getItems()) {
            // 驗證品項的 listing 屬於當前租戶（DEF-027 修復，比照 StockMovementService 的 DEF-017 模式，
            // 避免以他租戶的 listing/SKU 建立採購單，收貨時把庫存挪用到他租戶商品上）
            validateListingOwnership(itemRequest.getListingId(), tenantId);
            // DEF-231：先前只驗證 listingId 屬於呼叫者租戶，未驗證 skuId 是否真的屬於這個 listingId，
            // 可用自己的 listingId 搭配他租戶的真實 SKU UUID（SKU 列表端點本身刻意公開可查）建立採購單，
            // 收貨時把庫存挪用到他租戶的 SKU 上。skuId 為選填（品項可能尚未建立規格），故僅在有值時檢查。
            if (itemRequest.getSkuId() != null) {
                validateSkuOwnership(itemRequest.getSkuId(), itemRequest.getListingId());
            }

            BigDecimal subtotal = itemRequest.getUnitCost().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

            PurchaseOrderItem item = PurchaseOrderItem.builder()
                    .purchaseOrder(purchaseOrder)
                    .listingId(itemRequest.getListingId())
                    .skuId(itemRequest.getSkuId())
                    .quantity(itemRequest.getQuantity())
                    .orderedQty(itemRequest.getQuantity())
                    .receivedQuantity(0)
                    .unitCost(itemRequest.getUnitCost())
                    .subtotal(subtotal)
                    .build();

            purchaseOrder.getItems().add(item);
            totalAmount = totalAmount.add(subtotal);
        }

        purchaseOrder.setTotalAmount(totalAmount);

        PurchaseOrder saved = purchaseOrderRepository.save(purchaseOrder);
        log.info("Created purchase order: id={}, poNumber={}, tenantId={}", saved.getId(), poNumber, tenantId);

        return toDto(saved);
    }

    /**
     * 取得採購單詳情
     */
    @Transactional(readOnly = true)
    public PurchaseOrderDto getPurchaseOrder(final UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();

        PurchaseOrder po = findByIdAndTenantId(id, tenantId);
        return toDto(po);
    }

    /**
     * 分頁列出採購單
     */
    @Transactional(readOnly = true)
    public Page<PurchaseOrderDto> listPurchaseOrders(final PurchaseOrder.POStatus status, final Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenant();

        Page<PurchaseOrder> orders;
        if (status != null) {
            orders = purchaseOrderRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else {
            orders = purchaseOrderRepository.findByTenantId(tenantId, pageable);
        }

        Map<UUID, String> supplierNames = supplierRepository
                .findAllById(orders.getContent().stream()
                        .map(PurchaseOrder::getSupplierId)
                        .distinct()
                        .collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getName));

        return orders.map(po -> toDto(po, supplierNames.get(po.getSupplierId())));
    }

    /**
     * 更新採購單（僅 DRAFT 狀態可更新）
     */
    @Transactional
    public PurchaseOrderDto updatePurchaseOrder(final UUID id, final PurchaseOrderUpdateRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();

        PurchaseOrder po = findByIdAndTenantIdForUpdate(id, tenantId);

        if (!po.canSubmit()) {
            throw new BusinessException(ErrorCode.E_7002,
                    String.format("Cannot update PO in status: %s", po.getStatus()));
        }

        if (request.getNotes() != null) {
            po.setNotes(request.getNotes());
        }

        if (request.getExpectedDeliveryDate() != null) {
            po.setExpectedDeliveryDate(request.getExpectedDeliveryDate());
        }

        PurchaseOrder updated = purchaseOrderRepository.save(po);
        log.info("Updated purchase order: id={}, tenantId={}", id, tenantId);

        return toDto(updated);
    }

    /**
     * 提交採購單 (DRAFT → SUBMITTED，若金額超過租戶設定的審批門檻則轉 PENDING_APPROVAL)
     * Sprint 85：PRD §6.7.2 採購審批金額上限機制。
     */
    @Transactional
    public PurchaseOrderDto submitPurchaseOrder(final UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();

        PurchaseOrder po = findByIdAndTenantIdForUpdate(id, tenantId);

        if (!po.canSubmit()) {
            throw new BusinessException(ErrorCode.E_7002,
                    String.format("Cannot submit PO in status: %s", po.getStatus()));
        }

        po.setStatus(exceedsApprovalThreshold(tenantId, po.getTotalAmount())
                ? PurchaseOrder.POStatus.PENDING_APPROVAL
                : PurchaseOrder.POStatus.SUBMITTED);
        po.setSubmittedAt(Instant.now());

        PurchaseOrder updated = purchaseOrderRepository.save(po);
        log.info("Submitted purchase order: id={}, tenantId={}, status={}", id, tenantId, updated.getStatus());

        return toDto(updated);
    }

    /**
     * 判斷採購金額是否超過租戶設定的審批門檻。門檻為 null 代表該租戶未啟用此機制。
     */
    private boolean exceedsApprovalThreshold(final UUID tenantId, final BigDecimal totalAmount) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));
        BigDecimal threshold = tenant.getPurchaseOrderApprovalThreshold();
        return threshold != null && totalAmount != null && totalAmount.compareTo(threshold) > 0;
    }

    /**
     * 確認收貨 (SUBMITTED/PARTIALLY_RECEIVED → RECEIVED/PARTIALLY_RECEIVED)
     */
    @Transactional
    public PurchaseOrderDto receivePurchaseOrder(final UUID id, final PurchaseOrderReceiveRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();

        PurchaseOrder po = findByIdAndTenantIdForUpdate(id, tenantId);

        if (!po.canReceive()) {
            throw new BusinessException(ErrorCode.E_7002,
                    String.format("Cannot receive PO in status: %s", po.getStatus()));
        }

        // 處理每個收貨項目
        for (PurchaseOrderReceiveRequest.ReceiveItemRequest receiveItem : request.getItems()) {
            PurchaseOrderItem item = po.getItems().stream()
                    .filter(i -> i.getId().equals(receiveItem.getItemId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.E_7007,
                            String.format("Purchase order item not found: %s", receiveItem.getItemId())));

            int newReceivedQty = item.getReceivedQuantity() + receiveItem.getReceivedQuantity();
            // DEF-070：收貨數量（含累計歷次收貨）不得超過訂購量，否則庫存被虛增且狀態機失真
            if (newReceivedQty > item.getQuantity()) {
                throw new BusinessException(ErrorCode.E_7009,
                        String.format("Received quantity exceeds ordered quantity: itemId=%s, ordered=%d, received=%d",
                                item.getId(), item.getQuantity(), newReceivedQty));
            }
            item.setReceivedQuantity(newReceivedQty);

            // 建立庫存異動 (INBOUND)
            if (receiveItem.getReceivedQuantity() > 0 && item.getSkuId() != null) {
                createInboundMovement(tenantId, item, receiveItem.getReceivedQuantity(), po.getId());
            }
        }

        // 判定新狀態
        boolean allReceived = po.getItems().stream()
                .allMatch(i -> i.getReceivedQuantity() >= i.getQuantity());
        po.setStatus(allReceived ? PurchaseOrder.POStatus.RECEIVED : PurchaseOrder.POStatus.PARTIALLY_RECEIVED);

        if (allReceived) {
            po.setReceivedAt(Instant.now());
        }

        PurchaseOrder updated = purchaseOrderRepository.save(po);
        log.info("Received purchase order: id={}, status={}, tenantId={}", id, updated.getStatus(), tenantId);

        return toDto(updated);
    }

    /**
     * 取消採購單 (DRAFT/SUBMITTED → CANCELLED)
     */
    @Transactional
    public PurchaseOrderDto cancelPurchaseOrder(final UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();

        PurchaseOrder po = findByIdAndTenantIdForUpdate(id, tenantId);

        if (!po.canCancel()) {
            throw new BusinessException(ErrorCode.E_7002,
                    String.format("Cannot cancel PO in status: %s", po.getStatus()));
        }

        po.setStatus(PurchaseOrder.POStatus.CANCELLED);

        PurchaseOrder updated = purchaseOrderRepository.save(po);
        log.info("Cancelled purchase order: id={}, tenantId={}", id, tenantId);

        return toDto(updated);
    }

    /**
     * 建立入庫異動
     *
     * <p>Sprint 113（DEF-051）：入庫改為單一敘述的原子 UPDATE。修復前是「載入實體 →
     * {@code addStock()} → save()」的讀後寫，10 張採購單併發收同一 SKU 實測只有 2 張入得了帳，
     * 其餘 8 張撞 {@code ObjectOptimisticLockingFailureException} 而整筆交易回滾——貨已經到了、
     * 系統沒入庫、採購單狀態也沒推進，操作員只看到 500。
     *
     * <p>0 筆即代表該 SKU 沒有庫存列，與修復前 {@code findById(...).orElseThrow()} 的 E_3003 同義，
     * 且省掉一次往返。
     */
    private void createInboundMovement(final UUID tenantId, final PurchaseOrderItem item, final int receivedQty, final UUID poId) {
        if (productInventoryRepository.increaseTotalQty(item.getSkuId(), receivedQty) == 0) {
            throw new BusinessException(ErrorCode.E_3003,
                    String.format("SKU not found: %s", item.getSkuId()));
        }

        // 前後數量回讀 DB：本交易對該列的行鎖尚未釋放，讀到的必然是本次入庫的結果
        int afterTotalQty = productInventoryRepository.findTotalQtyBySkuId(item.getSkuId());
        int beforeTotalQty = afterTotalQty - receivedQty;

        StockMovement movement = StockMovement.builder()
                .tenantId(tenantId)
                .skuId(item.getSkuId())
                .movementType(StockMovement.MovementType.INBOUND)
                .quantity(receivedQty)
                .beforeTotalQty(beforeTotalQty)
                .afterTotalQty(afterTotalQty)
                .balanceAfter(afterTotalQty)
                .referenceType(StockMovement.ReferenceType.PURCHASE_ORDER)
                .referenceId(poId)
                .orderItemId(item.getId())
                .notes("Purchase order receipt")
                .build();

        stockMovementRepository.save(movement);
        log.debug("Created inbound movement: skuId={}, qty={}, poId={}", item.getSkuId(), receivedQty, poId);
    }

    /**
     * 驗證供應商
     */
    private void validateSupplier(final UUID supplierId, final UUID tenantId) {
        if (!supplierRepository.existsByIdAndTenantId(supplierId, tenantId)) {
            throw new BusinessException(ErrorCode.E_7008,
                    String.format("Supplier not found or inactive: %s", supplierId));
        }
    }

    /**
     * 驗證品項的 listing 屬於當前租戶（DEF-027 修復）
     */
    private void validateListingOwnership(final UUID listingId, final UUID tenantId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_3000,
                        String.format("Listing not found: %s", listingId)));
        if (!tenantId.equals(listing.getTenantId())) {
            throw new BusinessException(ErrorCode.E_1007, "Listing does not belong to current tenant");
        }
    }

    /**
     * 驗證品項的 SKU 屬於同一品項已驗證過的 listing（DEF-231 修復）。
     *
     * <p>與 {@link ProductSkuService#updateSku} 對「SKU 存在但不屬於此 listing」的既有回應方式一致：
     * 一律回傳 E_3003「SKU not found」，不區分「真的不存在」與「存在但屬於別的 listing／租戶」，
     * 避免藉由錯誤訊息差異洩漏他租戶 SKU 是否存在。
     */
    private void validateSkuOwnership(final UUID skuId, final UUID listingId) {
        if (!productSkuRepository.existsByIdAndProductListingId(skuId, listingId)) {
            throw new BusinessException(ErrorCode.E_3003, "SKU not found: " + skuId);
        }
    }

    /**
     * 產生 PO Number
     */
    private String generatePoNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int random = ThreadLocalRandom.current().nextInt(PO_NUMBER_MIN, PO_NUMBER_MAX);
        return String.format("PO-%s-%d", date, random);
    }

    /**
     * 依 ID 和 Tenant 取得 PO
     */
    private PurchaseOrder findByIdAndTenantId(final UUID id, final UUID tenantId) {
        return purchaseOrderRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_7001,
                        String.format("Purchase order not found: id=%s, tenantId=%s", id, tenantId)));
    }

    /**
     * 依 ID 和 Tenant 取得 PO（悲觀鎖，DEF-126/127/128/129：供 update/submit/receive/cancel
     * 四個寫入方法使用，序列化「讀狀態→驗證→改欄位→save()」複合操作，見
     * {@link PurchaseOrderRepository#findByIdAndTenantIdForUpdate}）。
     */
    private PurchaseOrder findByIdAndTenantIdForUpdate(final UUID id, final UUID tenantId) {
        return purchaseOrderRepository.findByIdAndTenantIdForUpdate(id, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_7001,
                        String.format("Purchase order not found: id=%s, tenantId=%s", id, tenantId)));
    }

    /**
     * 轉換為 DTO
     */
    private PurchaseOrderDto toDto(final PurchaseOrder po) {
        String supplierName = supplierRepository.findById(po.getSupplierId())
                .map(Supplier::getName)
                .orElse(null);
        return toDto(po, supplierName);
    }

    private PurchaseOrderDto toDto(final PurchaseOrder po, final String supplierName) {
        return PurchaseOrderDto.builder()
                .id(po.getId())
                .tenantId(po.getTenantId())
                .poNumber(po.getPoNumber())
                .supplierId(po.getSupplierId())
                .supplierName(supplierName)
                .status(po.getStatus() != null ? po.getStatus().name() : null)
                .totalAmount(po.getTotalAmount())
                .currency(po.getCurrency())
                .expectedDeliveryDate(po.getExpectedDeliveryDate() != null ? po.getExpectedDeliveryDate().toString() : null)
                .notes(po.getNotes())
                .items(po.getItems().stream()
                        .map(this::toItemDto)
                        .collect(Collectors.toList()))
                .submittedAt(po.getSubmittedAt())
                .receivedAt(po.getReceivedAt())
                .reviewedBy(po.getReviewedBy())
                .reviewedAt(po.getReviewedAt())
                .rejectionReason(po.getRejectionReason())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    private PurchaseOrderDto.PurchaseOrderItemDto toItemDto(PurchaseOrderItem item) {
        return PurchaseOrderDto.PurchaseOrderItemDto.builder()
                .id(item.getId())
                .listingId(item.getListingId())
                .skuId(item.getSkuId())
                .skuCode(item.getSkuCode())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .receivedQuantity(item.getReceivedQuantity())
                .unitPrice(item.getUnitCost())
                .subtotal(item.getSubtotal())
                .build();
    }
}