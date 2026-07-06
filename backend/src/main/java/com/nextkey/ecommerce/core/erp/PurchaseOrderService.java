package com.nextkey.ecommerce.core.erp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import com.nextkey.ecommerce.domain.model.inventory.PurchaseOrder;
import com.nextkey.ecommerce.domain.model.inventory.PurchaseOrderItem;
import com.nextkey.ecommerce.domain.model.inventory.StockMovement;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.ProductInventory;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductInventoryRepository;
import com.nextkey.ecommerce.domain.repository.PurchaseOrderItemRepository;
import com.nextkey.ecommerce.domain.repository.PurchaseOrderRepository;
import com.nextkey.ecommerce.domain.repository.StockMovementRepository;
import com.nextkey.ecommerce.domain.repository.SupplierRepository;
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
    private final StockMovementRepository stockMovementRepository;
    private final ListingRepository listingRepository;

    // PO Number generation
    private static final int PO_NUMBER_MIN = 100000;
    private static final int PO_NUMBER_MAX = 999999;

    /**
     * 建立採購單 (DRAFT)
     */
    @Transactional
    public PurchaseOrderDto createPurchaseOrder(final PurchaseOrderCreateRequest request, final UUID userId) {
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

        return orders.map(this::toDto);
    }

    /**
     * 更新採購單（僅 DRAFT 狀態可更新）
     */
    @Transactional
    public PurchaseOrderDto updatePurchaseOrder(final UUID id, final PurchaseOrderUpdateRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();

        PurchaseOrder po = findByIdAndTenantId(id, tenantId);

        if (!po.canSubmit()) {
            throw new BusinessException(ErrorCode.E_7002,
                    String.format("Cannot update PO in status: %s", po.getStatus()));
        }

        if (request.getNotes() != null) {
            po.setNotes(request.getNotes());
        }

        PurchaseOrder updated = purchaseOrderRepository.save(po);
        log.info("Updated purchase order: id={}, tenantId={}", id, tenantId);

        return toDto(updated);
    }

    /**
     * 提交採購單 (DRAFT → SUBMITTED)
     */
    @Transactional
    public PurchaseOrderDto submitPurchaseOrder(final UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();

        PurchaseOrder po = findByIdAndTenantId(id, tenantId);

        if (!po.canSubmit()) {
            throw new BusinessException(ErrorCode.E_7002,
                    String.format("Cannot submit PO in status: %s", po.getStatus()));
        }

        po.setStatus(PurchaseOrder.POStatus.SUBMITTED);
        po.setSubmittedAt(Instant.now());

        PurchaseOrder updated = purchaseOrderRepository.save(po);
        log.info("Submitted purchase order: id={}, tenantId={}", id, tenantId);

        return toDto(updated);
    }

    /**
     * 確認收貨 (SUBMITTED/PARTIALLY_RECEIVED → RECEIVED/PARTIALLY_RECEIVED)
     */
    @Transactional
    public PurchaseOrderDto receivePurchaseOrder(final UUID id, final PurchaseOrderReceiveRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();

        PurchaseOrder po = findByIdAndTenantId(id, tenantId);

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
            item.setReceivedQuantity(newReceivedQty);

            // 建立庫存異動 (PURCHASE_RECEIPT)
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

        PurchaseOrder po = findByIdAndTenantId(id, tenantId);

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
     */
    private void createInboundMovement(final UUID tenantId, final PurchaseOrderItem item, final int receivedQty, final UUID poId) {
        ProductInventory inventory = productInventoryRepository.findById(item.getSkuId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_3003,
                        String.format("SKU not found: %s", item.getSkuId())));

        int beforeTotalQty = inventory.getTotalQty();
        inventory.addStock(receivedQty);
        productInventoryRepository.save(inventory);

        int afterTotalQty = inventory.getTotalQty();

        StockMovement movement = StockMovement.builder()
                .tenantId(tenantId)
                .skuId(item.getSkuId())
                .movementType(StockMovement.MovementType.PURCHASE_RECEIPT)
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
     * 轉換為 DTO
     */
    private PurchaseOrderDto toDto(final PurchaseOrder po) {
        return PurchaseOrderDto.builder()
                .id(po.getId())
                .poNumber(po.getPoNumber())
                .supplierId(po.getSupplierId())
                .status(po.getStatus() != null ? po.getStatus().name() : null)
                .totalAmount(po.getTotalAmount())
                .currency(po.getCurrency())
                .notes(po.getNotes())
                .items(po.getItems().stream()
                        .map(this::toItemDto)
                        .collect(Collectors.toList()))
                .submittedAt(po.getSubmittedAt())
                .receivedAt(po.getReceivedAt())
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