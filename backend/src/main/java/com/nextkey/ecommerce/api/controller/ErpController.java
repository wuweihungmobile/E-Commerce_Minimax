package com.nextkey.ecommerce.api.controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.erp.InventoryLedgerDto;
import com.nextkey.ecommerce.api.dto.erp.ListingOptionDto;
import com.nextkey.ecommerce.api.dto.erp.LowStockAlertDto;
import com.nextkey.ecommerce.api.dto.erp.PurchaseOrderCreateRequest;
import com.nextkey.ecommerce.api.dto.erp.PurchaseOrderDto;
import com.nextkey.ecommerce.api.dto.erp.PurchaseOrderReceiveRequest;
import com.nextkey.ecommerce.api.dto.erp.PurchaseOrderUpdateRequest;
import com.nextkey.ecommerce.api.dto.erp.StockMovementDto;
import com.nextkey.ecommerce.api.dto.erp.StockMovementRequest;
import com.nextkey.ecommerce.api.dto.erp.SupplierCreateRequest;
import com.nextkey.ecommerce.api.dto.erp.SupplierDto;
import com.nextkey.ecommerce.api.dto.erp.SupplierUpdateRequest;
import com.nextkey.ecommerce.core.erp.InventoryService;
import com.nextkey.ecommerce.core.erp.PurchaseOrderService;
import com.nextkey.ecommerce.core.erp.StockMovementService;
import com.nextkey.ecommerce.core.erp.SupplierService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.ProductSku;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductSkuRepository;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import com.nextkey.ecommerce.shared.util.PageableUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 庫存 API Controller
 * PRD §9.15
 */
@Slf4j
@RestController
// 🔴 只能是 "/v2/dashboard"，理由同 TenantController（context-path 已是 /api，DEF-061）。
@RequestMapping("/v2/dashboard")
@RequiredArgsConstructor
public class ErpController {

    private final SupplierService supplierService;
    private final PurchaseOrderService purchaseOrderService;
    private final InventoryService inventoryService;
    private final StockMovementService stockMovementService;
    private final ListingRepository listingRepository;
    private final ProductSkuRepository productSkuRepository;

    // ========== Listing Endpoints（供採購單品項選擇器使用，DEF-076） ==========

    @GetMapping("/purchase-orders/listing-options")
    @PreAuthorize("hasAuthority('STORE_OWNER') or hasAuthority('SELLER')")
    public ResponseEntity<ApiResponse<List<ListingOptionDto>>> listTenantListingsForPurchaseOrder() {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.debug("Listing tenant listings for PO picker: tenantId={}", tenantId);

        Page<Listing> page = listingRepository.findByTenantIdAndStatus(
                tenantId, Listing.ListingStatus.ACTIVE, PageRequest.of(0, 200, Sort.by("title")));
        List<ListingOptionDto> options = page.getContent().stream()
                .map(l -> ListingOptionDto.builder()
                        .id(l.getId())
                        .title(l.getTitle())
                        .basePrice(l.getBasePrice())
                        .currency(l.getCurrency())
                        // Sprint 178：附上此商品已建立的 SKU，供品項選擇器挑選；
                        // 無 SKU 的商品回傳空陣列，前端據此提示需先建立規格才能加入採購單。
                        .skus(productSkuRepository.findByProductListingId(l.getId()).stream()
                                .map(this::toSkuOption)
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(options));
    }

    private ListingOptionDto.SkuOptionDto toSkuOption(final ProductSku sku) {
        return ListingOptionDto.SkuOptionDto.builder()
                .id(sku.getId())
                .skuCode(sku.getSkuCode())
                .specName(sku.getSpecName())
                .build();
    }

    // ========== Supplier Endpoints ==========

    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('STORE_OWNER') or hasAuthority('SELLER')")
    public ResponseEntity<ApiResponse<List<SupplierDto>>> listSuppliers(
            @RequestParam(required = false) String status) {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.debug("Listing suppliers: tenantId={}, status={}", tenantId, status);

        List<SupplierDto> suppliers = supplierService.listSuppliers(status);
        return ResponseEntity.ok(ApiResponse.success(suppliers));
    }

    @GetMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('STORE_OWNER') or hasAuthority('SELLER')")
    public ResponseEntity<ApiResponse<SupplierDto>> getSupplier(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.debug("Getting supplier: id={}, tenantId={}", id, tenantId);

        SupplierDto supplier = supplierService.getSupplier(id);
        return ResponseEntity.ok(ApiResponse.success(supplier));
    }

    @PostMapping("/suppliers")
    @PreAuthorize("hasAuthority('STORE_OWNER')")
    public ResponseEntity<ApiResponse<SupplierDto>> createSupplier(
            @Valid @RequestBody SupplierCreateRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.debug("Creating supplier: tenantId={}, name={}", tenantId, request.getName());

        SupplierDto supplier = supplierService.createSupplier(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Supplier created", supplier));
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('STORE_OWNER')")
    public ResponseEntity<ApiResponse<SupplierDto>> updateSupplier(
            @PathVariable UUID id,
            @Valid @RequestBody SupplierUpdateRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.debug("Updating supplier: id={}, tenantId={}", id, tenantId);

        SupplierDto supplier = supplierService.updateSupplier(id, request);
        return ResponseEntity.ok(ApiResponse.success("Supplier updated", supplier));
    }

    // ========== Purchase Order Endpoints ==========

    @GetMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('STORE_OWNER') or hasAuthority('SELLER')")
    public ResponseEntity<ApiResponse<Page<PurchaseOrderDto>>> listPurchaseOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = TenantContext.getCurrentTenant();
        Pageable pageable = PageableUtils.of(page, size, 100, Sort.by("createdAt").descending());

        log.debug("Listing purchase orders: tenantId={}, status={}, page={}", tenantId, status, page);

        Page<PurchaseOrderDto> orders = purchaseOrderService.listPurchaseOrders(
                status != null ? com.nextkey.ecommerce.domain.model.inventory.PurchaseOrder.POStatus.valueOf(status) : null,
                pageable);

        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @PostMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('STORE_OWNER')")
    public ResponseEntity<ApiResponse<PurchaseOrderDto>> createPurchaseOrder(
            @Valid @RequestBody PurchaseOrderCreateRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();
        UUID userId = TenantContext.getCurrentUser();
        log.debug("Creating purchase order: tenantId={}, supplierId={}", tenantId, request.getSupplierId());

        PurchaseOrderDto order = purchaseOrderService.createPurchaseOrder(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Purchase order created", order));
    }

    @GetMapping("/purchase-orders/{id}")
    @PreAuthorize("hasAuthority('STORE_OWNER') or hasAuthority('SELLER')")
    public ResponseEntity<ApiResponse<PurchaseOrderDto>> getPurchaseOrder(final @PathVariable UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.debug("Getting purchase order: id={}, tenantId={}", id, tenantId);

        PurchaseOrderDto order = purchaseOrderService.getPurchaseOrder(id);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PutMapping("/purchase-orders/{id}")
    @PreAuthorize("hasAuthority('STORE_OWNER')")
    public ResponseEntity<ApiResponse<PurchaseOrderDto>> updatePurchaseOrder(
            @PathVariable UUID id,
            @Valid @RequestBody PurchaseOrderUpdateRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.debug("Updating purchase order: id={}, tenantId={}", id, tenantId);

        PurchaseOrderDto order = purchaseOrderService.updatePurchaseOrder(id, request);
        return ResponseEntity.ok(ApiResponse.success("Purchase order updated", order));
    }

    @PutMapping("/purchase-orders/{id}/submit")
    @PreAuthorize("hasAuthority('STORE_OWNER')")
    public ResponseEntity<ApiResponse<PurchaseOrderDto>> submitPurchaseOrder(final @PathVariable UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.debug("Submitting purchase order: id={}, tenantId={}", id, tenantId);

        PurchaseOrderDto order = purchaseOrderService.submitPurchaseOrder(id);
        return ResponseEntity.ok(ApiResponse.success("Purchase order submitted", order));
    }

    @PutMapping("/purchase-orders/{id}/receive")
    @PreAuthorize("hasAuthority('STORE_OWNER')")
    public ResponseEntity<ApiResponse<PurchaseOrderDto>> receivePurchaseOrder(
            @PathVariable UUID id,
            @Valid @RequestBody PurchaseOrderReceiveRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.debug("Receiving purchase order: id={}, tenantId={}", id, tenantId);

        PurchaseOrderDto order = purchaseOrderService.receivePurchaseOrder(id, request);
        return ResponseEntity.ok(ApiResponse.success("Goods received", order));
    }

    @PutMapping("/purchase-orders/{id}/cancel")
    @PreAuthorize("hasAuthority('STORE_OWNER')")
    public ResponseEntity<ApiResponse<PurchaseOrderDto>> cancelPurchaseOrder(final @PathVariable UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.debug("Cancelling purchase order: id={}, tenantId={}", id, tenantId);

        PurchaseOrderDto order = purchaseOrderService.cancelPurchaseOrder(id);
        return ResponseEntity.ok(ApiResponse.success("Purchase order cancelled", order));
    }

    // ========== Inventory Endpoints ==========

    @GetMapping("/inventory")
    @PreAuthorize("hasAuthority('STORE_OWNER') or hasAuthority('SELLER')")
    public ResponseEntity<ApiResponse<Page<InventoryLedgerDto>>> getInventoryLedger(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID tenantId = TenantContext.getCurrentTenant();
        Pageable pageable = PageableUtils.of(page, size, 100);

        log.debug("Getting inventory ledger: tenantId={}, page={}", tenantId, page);

        Page<InventoryLedgerDto> ledger = inventoryService.getInventoryLedger(pageable);
        return ResponseEntity.ok(ApiResponse.success(ledger));
    }

    @GetMapping("/inventory/{skuId}")
    @PreAuthorize("hasAuthority('STORE_OWNER') or hasAuthority('SELLER')")
    public ResponseEntity<ApiResponse<InventoryService.InventoryDetailDto>> getInventoryDetail(
            @PathVariable UUID skuId) {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.debug("Getting inventory detail: skuId={}, tenantId={}", skuId, tenantId);

        InventoryService.InventoryDetailDto detail = inventoryService.getInventoryBySku(skuId);
        if (detail == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("E-3003", "Inventory not found for SKU: " + skuId));
        }
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @GetMapping("/inventory/alerts")
    @PreAuthorize("hasAuthority('STORE_OWNER') or hasAuthority('SELLER')")
    public ResponseEntity<ApiResponse<List<LowStockAlertDto>>> getLowStockAlerts() {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.debug("Getting low stock alerts: tenantId={}", tenantId);

        List<LowStockAlertDto> alerts = inventoryService.getLowStockAlerts();
        return ResponseEntity.ok(ApiResponse.success(alerts));
    }

    // ========== Stock Movement Endpoints ==========

    @PostMapping("/stock-movements")
    @PreAuthorize("hasAuthority('STORE_OWNER')")
    public ResponseEntity<ApiResponse<StockMovementDto>> createStockMovement(
            @Valid @RequestBody StockMovementRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();
        UUID userId = TenantContext.getCurrentUser();
        log.debug("Creating stock movement: tenantId={}, skuId={}, type={}",
                tenantId, request.getSkuId(), request.getMovementType());

        StockMovementDto movement = stockMovementService.createManualMovement(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stock movement recorded", movement));
    }

    @GetMapping("/stock-movements")
    @PreAuthorize("hasAuthority('STORE_OWNER') or hasAuthority('SELLER')")
    public ResponseEntity<ApiResponse<Page<StockMovementDto>>> getStockMovements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID tenantId = TenantContext.getCurrentTenant();
        Pageable pageable = PageableUtils.of(page, size, 100, Sort.by("createdAt").descending());

        log.debug("Getting stock movements: tenantId={}, page={}", tenantId, page);

        Page<StockMovementDto> movements = stockMovementService.getMovements(pageable);
        return ResponseEntity.ok(ApiResponse.success(movements));
    }
}