package com.nextkey.ecommerce.core.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.nextkey.ecommerce.api.dto.erp.PurchaseOrderCreateRequest;
import com.nextkey.ecommerce.api.dto.erp.PurchaseOrderDto;
import com.nextkey.ecommerce.api.dto.erp.PurchaseOrderReceiveRequest;
import com.nextkey.ecommerce.api.dto.erp.PurchaseOrderUpdateRequest;
import com.nextkey.ecommerce.domain.model.inventory.PurchaseOrder;
import com.nextkey.ecommerce.domain.model.inventory.PurchaseOrderItem;
import com.nextkey.ecommerce.domain.model.inventory.StockMovement;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.ProductInventory;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductInventoryRepository;
import com.nextkey.ecommerce.domain.repository.PurchaseOrderItemRepository;
import com.nextkey.ecommerce.domain.repository.PurchaseOrderRepository;
import com.nextkey.ecommerce.domain.repository.StockMovementRepository;
import com.nextkey.ecommerce.domain.repository.SupplierRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * {@link PurchaseOrderService} 單元測試（Sprint 72 US-001 + US-003）。
 *
 * <p>比照 {@code M16ErpIntegrationTest}（IT-M16-101~117）與 {@code M16ErpE2ETest}（E2E-M16-001~006）
 * 業務情境涵蓋狀態機（DRAFT→SUBMITTED→RECEIVED/PARTIALLY_RECEIVED/CANCELLED）與收貨連動庫存異動；
 * 另含 US-003：{@code createPurchaseOrder} 疑似跨租戶庫存挪用的驗證測試。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseOrderService 單元測試（Sprint 72）")
class PurchaseOrderServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private PurchaseOrderItemRepository purchaseOrderItemRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private ProductInventoryRepository productInventoryRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private FeatureToggleService featureToggleService;

    @InjectMocks
    private PurchaseOrderService purchaseOrderService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID otherTenantId = UUID.randomUUID();
    private final UUID supplierId = UUID.randomUUID();
    private final UUID listingId = UUID.randomUUID();
    private final UUID skuId = UUID.randomUUID();
    private final UUID poId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private PurchaseOrderCreateRequest.PurchaseOrderItemRequest itemRequest(final int qty, final String unitCost) {
        return PurchaseOrderCreateRequest.PurchaseOrderItemRequest.builder()
                .listingId(listingId)
                .skuId(skuId)
                .quantity(qty)
                .unitCost(new BigDecimal(unitCost))
                .build();
    }

    private Listing listingOf(final UUID tid) {
        Listing listing = new Listing();
        listing.setId(listingId);
        listing.setTenantId(tid);
        return listing;
    }

    private PurchaseOrder poOf(final PurchaseOrder.POStatus status) {
        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .id(UUID.randomUUID())
                .listingId(listingId)
                .skuId(skuId)
                .quantity(10)
                .orderedQty(10)
                .receivedQuantity(0)
                .unitCost(BigDecimal.TEN)
                .subtotal(BigDecimal.valueOf(100))
                .build();
        PurchaseOrder po = PurchaseOrder.builder()
                .id(poId)
                .tenantId(tenantId)
                .supplierId(supplierId)
                .poNumber("PO-20260706-123456")
                .status(status)
                .totalAmount(BigDecimal.valueOf(100))
                .currency("TWD")
                .items(new java.util.ArrayList<>(List.of(item)))
                .build();
        item.setPurchaseOrder(po);
        return po;
    }

    // ── createPurchaseOrder ──────────────────────────────────────

    @Test
    @DisplayName("createPurchaseOrder：正常路徑，多品項金額加總、注入租戶、DRAFT 狀態")
    void createPurchaseOrder_success_aggregatesAmountAndInjectsTenant() {
        when(supplierRepository.existsByIdAndTenantId(supplierId, tenantId)).thenReturn(true);
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listingOf(tenantId)));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> {
            PurchaseOrder po = inv.getArgument(0);
            po.setId(poId);
            return po;
        });

        PurchaseOrderCreateRequest request = PurchaseOrderCreateRequest.builder()
                .supplierId(supplierId)
                .notes("test po")
                .items(List.of(itemRequest(2, "50.00"), itemRequest(3, "20.00")))
                .build();

        ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
        PurchaseOrderDto result = purchaseOrderService.createPurchaseOrder(request, userId);

        verify(purchaseOrderRepository).save(captor.capture());
        PurchaseOrder saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getStatus()).isEqualTo(PurchaseOrder.POStatus.DRAFT);
        assertThat(saved.getCreatedBy()).isEqualTo(userId);
        // 2*50 + 3*20 = 160
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("160.00");
        assertThat(saved.getPoNumber()).matches("PO-\\d{8}-\\d{6}");

        assertThat(result.getId()).isEqualTo(poId);
        assertThat(result.getSupplierId()).isEqualTo(supplierId);
    }

    @Test
    @DisplayName("createPurchaseOrder：ERP_ENABLED 功能未啟用時拒絕建立採購單（Sprint 99，PRD §7.5）")
    void createPurchaseOrder_erpFeatureDisabled_throwsAndDoesNotSave() {
        doThrow(new BusinessException(ErrorCode.E_2004, "Feature 'ERP_ENABLED' is disabled for this tenant"))
                .when(featureToggleService).checkFeatureEnabled("ERP_ENABLED");

        PurchaseOrderCreateRequest request = PurchaseOrderCreateRequest.builder()
                .supplierId(supplierId)
                .items(List.of(itemRequest(1, "10.00")))
                .build();

        assertThatThrownBy(() -> purchaseOrderService.createPurchaseOrder(request, userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_2004));

        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createPurchaseOrder：supplier 不存在或非本租戶時拋出 E_7008")
    void createPurchaseOrder_supplierNotFound_throwsE7008() {
        when(supplierRepository.existsByIdAndTenantId(supplierId, tenantId)).thenReturn(false);

        PurchaseOrderCreateRequest request = PurchaseOrderCreateRequest.builder()
                .supplierId(supplierId)
                .items(List.of(itemRequest(1, "10.00")))
                .build();

        assertThatThrownBy(() -> purchaseOrderService.createPurchaseOrder(request, userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_7008));
    }

    // ── US-003（驗證中）：跨租戶庫存挪用 ─────────────────────────

    @Test
    @DisplayName("US-003：createPurchaseOrder 品項的 listing 屬於他租戶時應拒絕（驗證疑似跨租戶庫存挪用）")
    void createPurchaseOrder_crossTenantListing_mustBeRejected() {
        // 情境還原：攻擊者租戶（tenantId）使用「自己的」合法 supplier，
        // 但品項 listingId/skuId 指向受害租戶（otherTenantId）的商品。
        when(supplierRepository.existsByIdAndTenantId(supplierId, tenantId)).thenReturn(true);
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listingOf(otherTenantId)));

        PurchaseOrderCreateRequest request = PurchaseOrderCreateRequest.builder()
                .supplierId(supplierId)
                .items(List.of(itemRequest(5, "10.00")))
                .build();

        // 預期（安全）行為：應拒絕跨租戶品項，不得建立採購單。
        // 修復前：createPurchaseOrder 未驗證品項 listing 的租戶歸屬，本斷言會失敗，證明漏洞存在。
        assertThatThrownBy(() -> purchaseOrderService.createPurchaseOrder(request, userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_1007));

        verify(purchaseOrderRepository, never()).save(any(PurchaseOrder.class));
    }

    // ── getPurchaseOrder ─────────────────────────────────────────

    @Test
    @DisplayName("getPurchaseOrder：存在時回傳 DTO")
    void getPurchaseOrder_found_returnsDto() {
        when(purchaseOrderRepository.findByIdAndTenantId(poId, tenantId))
                .thenReturn(Optional.of(poOf(PurchaseOrder.POStatus.DRAFT)));

        PurchaseOrderDto result = purchaseOrderService.getPurchaseOrder(poId);

        assertThat(result.getId()).isEqualTo(poId);
        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("getPurchaseOrder：不存在時拋出 E_7001")
    void getPurchaseOrder_notFound_throwsE7001() {
        when(purchaseOrderRepository.findByIdAndTenantId(poId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseOrderService.getPurchaseOrder(poId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_7001));
    }

    // ── listPurchaseOrders ───────────────────────────────────────

    @Test
    @DisplayName("listPurchaseOrders：不帶 status 時依租戶分頁查詢")
    void listPurchaseOrders_withoutStatus_queriesByTenant() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<PurchaseOrder> page = new PageImpl<>(List.of(poOf(PurchaseOrder.POStatus.DRAFT)), pageable, 1);
        when(purchaseOrderRepository.findByTenantId(tenantId, pageable)).thenReturn(page);

        Page<PurchaseOrderDto> result = purchaseOrderService.listPurchaseOrders(null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("listPurchaseOrders：帶 status 時依租戶+狀態分頁查詢")
    void listPurchaseOrders_withStatus_queriesByTenantAndStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<PurchaseOrder> page = new PageImpl<>(
                List.of(poOf(PurchaseOrder.POStatus.SUBMITTED)), pageable, 1);
        when(purchaseOrderRepository.findByTenantIdAndStatus(tenantId, PurchaseOrder.POStatus.SUBMITTED, pageable))
                .thenReturn(page);

        Page<PurchaseOrderDto> result =
                purchaseOrderService.listPurchaseOrders(PurchaseOrder.POStatus.SUBMITTED, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo("SUBMITTED");
    }

    // ── updatePurchaseOrder ──────────────────────────────────────

    @Test
    @DisplayName("updatePurchaseOrder：DRAFT 狀態可更新備註")
    void updatePurchaseOrder_draftStatus_updatesNotes() {
        PurchaseOrder po = poOf(PurchaseOrder.POStatus.DRAFT);
        when(purchaseOrderRepository.findByIdAndTenantId(poId, tenantId)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrderUpdateRequest request = PurchaseOrderUpdateRequest.builder().notes("updated").build();
        PurchaseOrderDto result = purchaseOrderService.updatePurchaseOrder(poId, request);

        assertThat(result.getNotes()).isEqualTo("updated");
    }

    @Test
    @DisplayName("updatePurchaseOrder：非 DRAFT 狀態拋出 E_7002")
    void updatePurchaseOrder_nonDraftStatus_throwsE7002() {
        PurchaseOrder po = poOf(PurchaseOrder.POStatus.SUBMITTED);
        when(purchaseOrderRepository.findByIdAndTenantId(poId, tenantId)).thenReturn(Optional.of(po));

        PurchaseOrderUpdateRequest request = PurchaseOrderUpdateRequest.builder().notes("x").build();

        assertThatThrownBy(() -> purchaseOrderService.updatePurchaseOrder(poId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_7002));
    }

    // ── submitPurchaseOrder ──────────────────────────────────────

    @Test
    @DisplayName("submitPurchaseOrder：DRAFT → SUBMITTED（租戶未設定審批門檻）")
    void submitPurchaseOrder_draft_transitionsToSubmitted() {
        PurchaseOrder po = poOf(PurchaseOrder.POStatus.DRAFT);
        when(purchaseOrderRepository.findByIdAndTenantId(poId, tenantId)).thenReturn(Optional.of(po));
        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(Tenant.builder().id(tenantId).build()));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrderDto result = purchaseOrderService.submitPurchaseOrder(poId);

        assertThat(result.getStatus()).isEqualTo("SUBMITTED");
        assertThat(result.getSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("submitPurchaseOrder：非 DRAFT 狀態拋出 E_7002")
    void submitPurchaseOrder_nonDraftStatus_throwsE7002() {
        PurchaseOrder po = poOf(PurchaseOrder.POStatus.CANCELLED);
        when(purchaseOrderRepository.findByIdAndTenantId(poId, tenantId)).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> purchaseOrderService.submitPurchaseOrder(poId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_7002));
    }

    @Test
    @DisplayName("submitPurchaseOrder：金額等於門檻時仍為 SUBMITTED（僅嚴格大於才需審批）")
    void submitPurchaseOrder_amountEqualsThreshold_staysSubmitted() {
        PurchaseOrder po = poOf(PurchaseOrder.POStatus.DRAFT);
        when(purchaseOrderRepository.findByIdAndTenantId(poId, tenantId)).thenReturn(Optional.of(po));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(
                Tenant.builder().id(tenantId).purchaseOrderApprovalThreshold(BigDecimal.valueOf(100)).build()));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrderDto result = purchaseOrderService.submitPurchaseOrder(poId);

        assertThat(result.getStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    @DisplayName("submitPurchaseOrder：金額超過門檻時轉 PENDING_APPROVAL")
    void submitPurchaseOrder_amountExceedsThreshold_transitionsToPendingApproval() {
        PurchaseOrder po = poOf(PurchaseOrder.POStatus.DRAFT);
        when(purchaseOrderRepository.findByIdAndTenantId(poId, tenantId)).thenReturn(Optional.of(po));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(
                Tenant.builder().id(tenantId).purchaseOrderApprovalThreshold(BigDecimal.valueOf(50)).build()));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrderDto result = purchaseOrderService.submitPurchaseOrder(poId);

        assertThat(result.getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(result.getSubmittedAt()).isNotNull();
    }

    // ── receivePurchaseOrder ─────────────────────────────────────

    @Test
    @DisplayName("receivePurchaseOrder：全數收貨 → RECEIVED 且建立入庫異動")
    void receivePurchaseOrder_fullyReceived_setsReceivedAndCreatesMovement() {
        PurchaseOrder po = poOf(PurchaseOrder.POStatus.SUBMITTED);
        UUID itemId = po.getItems().get(0).getId();
        when(purchaseOrderRepository.findByIdAndTenantId(poId, tenantId)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductInventory inventory = ProductInventory.builder().skuId(skuId).totalQty(50).reservedQty(0).build();
        when(productInventoryRepository.findById(skuId)).thenReturn(Optional.of(inventory));
        when(productInventoryRepository.save(any(ProductInventory.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        when(stockMovementRepository.save(movementCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrderReceiveRequest request = PurchaseOrderReceiveRequest.builder()
                .items(List.of(PurchaseOrderReceiveRequest.ReceiveItemRequest.builder()
                        .itemId(itemId)
                        .receivedQuantity(10)
                        .build()))
                .build();

        PurchaseOrderDto result = purchaseOrderService.receivePurchaseOrder(poId, request);

        assertThat(result.getStatus()).isEqualTo("RECEIVED");
        assertThat(result.getReceivedAt()).isNotNull();

        StockMovement movement = movementCaptor.getValue();
        assertThat(movement.getTenantId()).isEqualTo(tenantId);
        assertThat(movement.getSkuId()).isEqualTo(skuId);
        assertThat(movement.getMovementType()).isEqualTo(StockMovement.MovementType.PURCHASE_RECEIPT);
        assertThat(movement.getReferenceType()).isEqualTo(StockMovement.ReferenceType.PURCHASE_ORDER);
        assertThat(movement.getReferenceId()).isEqualTo(poId);
        assertThat(movement.getBeforeTotalQty()).isEqualTo(50);
        assertThat(movement.getAfterTotalQty()).isEqualTo(60);
    }

    @Test
    @DisplayName("receivePurchaseOrder：部分收貨 → PARTIALLY_RECEIVED")
    void receivePurchaseOrder_partiallyReceived_setsPartiallyReceived() {
        PurchaseOrder po = poOf(PurchaseOrder.POStatus.SUBMITTED);
        UUID itemId = po.getItems().get(0).getId();
        when(purchaseOrderRepository.findByIdAndTenantId(poId, tenantId)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductInventory inventory = ProductInventory.builder().skuId(skuId).totalQty(50).reservedQty(0).build();
        when(productInventoryRepository.findById(skuId)).thenReturn(Optional.of(inventory));
        when(productInventoryRepository.save(any(ProductInventory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrderReceiveRequest request = PurchaseOrderReceiveRequest.builder()
                .items(List.of(PurchaseOrderReceiveRequest.ReceiveItemRequest.builder()
                        .itemId(itemId)
                        .receivedQuantity(4) // po item quantity=10，只收 4 件
                        .build()))
                .build();

        PurchaseOrderDto result = purchaseOrderService.receivePurchaseOrder(poId, request);

        assertThat(result.getStatus()).isEqualTo("PARTIALLY_RECEIVED");
    }

    @Test
    @DisplayName("receivePurchaseOrder：品項不存在時拋出 E_7007")
    void receivePurchaseOrder_itemNotFound_throwsE7007() {
        PurchaseOrder po = poOf(PurchaseOrder.POStatus.SUBMITTED);
        when(purchaseOrderRepository.findByIdAndTenantId(poId, tenantId)).thenReturn(Optional.of(po));

        PurchaseOrderReceiveRequest request = PurchaseOrderReceiveRequest.builder()
                .items(List.of(PurchaseOrderReceiveRequest.ReceiveItemRequest.builder()
                        .itemId(UUID.randomUUID())
                        .receivedQuantity(1)
                        .build()))
                .build();

        assertThatThrownBy(() -> purchaseOrderService.receivePurchaseOrder(poId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_7007));
    }

    @Test
    @DisplayName("receivePurchaseOrder：非 SUBMITTED/PARTIALLY_RECEIVED 狀態拋出 E_7002")
    void receivePurchaseOrder_invalidStatus_throwsE7002() {
        PurchaseOrder po = poOf(PurchaseOrder.POStatus.DRAFT);
        when(purchaseOrderRepository.findByIdAndTenantId(poId, tenantId)).thenReturn(Optional.of(po));

        PurchaseOrderReceiveRequest request = PurchaseOrderReceiveRequest.builder()
                .items(List.of(PurchaseOrderReceiveRequest.ReceiveItemRequest.builder()
                        .itemId(po.getItems().get(0).getId())
                        .receivedQuantity(1)
                        .build()))
                .build();

        assertThatThrownBy(() -> purchaseOrderService.receivePurchaseOrder(poId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_7002));
    }

    @Test
    @DisplayName("receivePurchaseOrder：APPROVED（SUPER_ADMIN 已核准超額採購單）可收貨")
    void receivePurchaseOrder_approvedStatus_canReceive() {
        PurchaseOrder po = poOf(PurchaseOrder.POStatus.APPROVED);
        when(purchaseOrderRepository.findByIdAndTenantId(poId, tenantId)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductInventory inventory = ProductInventory.builder().skuId(skuId).totalQty(50).reservedQty(0).build();
        when(productInventoryRepository.findById(skuId)).thenReturn(Optional.of(inventory));
        when(productInventoryRepository.save(any(ProductInventory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrderReceiveRequest request = PurchaseOrderReceiveRequest.builder()
                .items(List.of(PurchaseOrderReceiveRequest.ReceiveItemRequest.builder()
                        .itemId(po.getItems().get(0).getId())
                        .receivedQuantity(10)
                        .build()))
                .build();

        PurchaseOrderDto result = purchaseOrderService.receivePurchaseOrder(poId, request);

        assertThat(result.getStatus()).isEqualTo("RECEIVED");
    }

    // ── cancelPurchaseOrder ──────────────────────────────────────

    @Test
    @DisplayName("cancelPurchaseOrder：DRAFT 可取消")
    void cancelPurchaseOrder_draft_cancelsSuccessfully() {
        PurchaseOrder po = poOf(PurchaseOrder.POStatus.DRAFT);
        when(purchaseOrderRepository.findByIdAndTenantId(poId, tenantId)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrderDto result = purchaseOrderService.cancelPurchaseOrder(poId);

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("cancelPurchaseOrder：SUBMITTED 可取消")
    void cancelPurchaseOrder_submitted_cancelsSuccessfully() {
        PurchaseOrder po = poOf(PurchaseOrder.POStatus.SUBMITTED);
        when(purchaseOrderRepository.findByIdAndTenantId(poId, tenantId)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrderDto result = purchaseOrderService.cancelPurchaseOrder(poId);

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("cancelPurchaseOrder：PENDING_APPROVAL 可取消（等待審批期間可撤回）")
    void cancelPurchaseOrder_pendingApproval_cancelsSuccessfully() {
        PurchaseOrder po = poOf(PurchaseOrder.POStatus.PENDING_APPROVAL);
        when(purchaseOrderRepository.findByIdAndTenantId(poId, tenantId)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrderDto result = purchaseOrderService.cancelPurchaseOrder(poId);

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("cancelPurchaseOrder：APPROVED 可取消（已核准但尚未開始收貨）")
    void cancelPurchaseOrder_approved_cancelsSuccessfully() {
        PurchaseOrder po = poOf(PurchaseOrder.POStatus.APPROVED);
        when(purchaseOrderRepository.findByIdAndTenantId(poId, tenantId)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrderDto result = purchaseOrderService.cancelPurchaseOrder(poId);

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("cancelPurchaseOrder：REJECTED 狀態無法取消（已是終態），拋出 E_7002")
    void cancelPurchaseOrder_rejected_throwsE7002() {
        PurchaseOrder po = poOf(PurchaseOrder.POStatus.REJECTED);
        when(purchaseOrderRepository.findByIdAndTenantId(poId, tenantId)).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> purchaseOrderService.cancelPurchaseOrder(poId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_7002));
    }

    @Test
    @DisplayName("cancelPurchaseOrder：RECEIVED 狀態無法取消，拋出 E_7002")
    void cancelPurchaseOrder_receivedStatus_throwsE7002() {
        PurchaseOrder po = poOf(PurchaseOrder.POStatus.RECEIVED);
        when(purchaseOrderRepository.findByIdAndTenantId(poId, tenantId)).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> purchaseOrderService.cancelPurchaseOrder(poId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_7002));
    }
}
