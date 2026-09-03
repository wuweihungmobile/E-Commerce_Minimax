package com.nextkey.ecommerce.core.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.domain.model.inventory.StockMovement;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.order.OrderItem;
import com.nextkey.ecommerce.domain.model.product.ProductSku;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ProductInventoryRepository;
import com.nextkey.ecommerce.domain.repository.StockMovementRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * ProductInventoryService 單元測試（Sprint 88，AI-2422；Sprint 103／115 改寫）。
 *
 * <p>驗證 reserve-at-creation / deduct-at-payment / release-on-cancel 三段式庫存操作的
 * **派送行為**：哪些品項會被略過、原子敘述收到什麼參數、0 筆回傳如何被解讀，
 * 以及（Sprint 115／DEF-065 起）每段是否寫下對應型別的流水帳。
 *
 * <p>Sprint 103（DEF-050）起，數量的實際變化不再由本檔驗證——它已下沉到 SQL，
 * mock 掉 Repository 的測試無從觀察，硬要驗只會變成「用固件回放自己寫的 stub」。
 * 併發正確性與數量結果一律由 {@code M12InventoryConcurrencyIntegrationTest} 以真實
 * PostgreSQL 驗證；流水帳的欄位值則由 {@code M12OrderStockLedgerIntegrationTest} 驗證。
 * 本檔刻意保留 {@code never()).save(...)} 斷言，作為「不得退回讀後寫」的守衛。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductInventoryService 單元測試（Sprint 88；Sprint 103／115 改寫）")
class ProductInventoryServiceTest {

    @Mock
    private ProductInventoryRepository productInventoryRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    private ProductInventoryService service;

    private static final UUID SKU_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProductInventoryService(productInventoryRepository, stockMovementRepository);
    }

    /** 流水帳的前後數量一律回讀 DB；不是每個案例都會走到，故用 lenient 避免 strict stubs 誤報。 */
    private void givenReadBack(final int totalQty, final int reservedQty) {
        lenient().when(productInventoryRepository.findTotalQtyBySkuId(SKU_ID)).thenReturn(totalQty);
        lenient().when(productInventoryRepository.findReservedQtyBySkuId(SKU_ID)).thenReturn(reservedQty);
    }

    private ProductSku sku(UUID id) {
        return ProductSku.builder().id(id).skuCode("SKU-" + id).build();
    }

    private OrderItem itemWithSku(ProductSku sku, int quantity) {
        // Sprint 115（DEF-065）：品項必須帶 id——流水帳的 order_item_id 取自它，
        // 且 ProductInventoryService 會對未持久化的訂單大聲失敗
        return OrderItem.builder().id(UUID.randomUUID()).sku(sku).quantity(quantity).build();
    }

    /**
     * 組一張「已持久化」的訂單：帶 id，並以關聯物件（而非 insertable=false 的影子欄位）
     * 提供租戶與買家——後者是流水帳 tenant_id／created_by 的來源。
     */
    private Order orderWithItems(OrderItem... items) {
        return Order.builder()
                .id(UUID.randomUUID())
                .tenant(Tenant.builder().id(TENANT_ID).build())
                .user(User.builder().id(USER_ID).build())
                .items(List.of(items))
                .build();
    }

    /** 取出唯一一筆被寫入的流水帳。 */
    private StockMovement capturedMovement() {
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("reserveForOrder：以品項數量呼叫原子預扣，成功時不再讀取實體，並寫下 RESERVE 流水帳")
    void reserveForOrder_sufficientStock_reservesAtomically() {
        when(productInventoryRepository.reserveIfAvailable(SKU_ID, 3)).thenReturn(1);
        givenReadBack(100, 13);
        Order order = orderWithItems(itemWithSku(sku(SKU_ID), 3));

        service.reserveForOrder(order);

        verify(productInventoryRepository).reserveIfAvailable(SKU_ID, 3);
        // 守衛：一旦有人把實作改回「findById → 改欄位 → save」，DEF-050 的讀後寫窗口就回來了
        verify(productInventoryRepository, never()).findById(any());
        verify(productInventoryRepository, never()).save(any());

        // Sprint 115（DEF-065）：PRD §6.7.3 要求下單留下 RESERVE 流水帳
        StockMovement movement = capturedMovement();
        assertThat(movement.getMovementType()).isEqualTo(StockMovement.MovementType.RESERVE);
        assertThat(movement.getQuantity()).isEqualTo(3);
        assertThat(movement.getReferenceType()).isEqualTo(StockMovement.ReferenceType.ORDER);
        assertThat(movement.getReferenceId()).isEqualTo(order.getId());
        assertThat(movement.getOrderItemId()).isEqualTo(order.getItems().get(0).getId());
        assertThat(movement.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(movement.getCreatedBy()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("reserveForOrder：原子預扣回 0 且庫存列存在 → 判定為庫存不足，拋 E_3004 且不留流水帳")
    void reserveForOrder_insufficientStock_throws() {
        when(productInventoryRepository.reserveIfAvailable(SKU_ID, 5)).thenReturn(0);
        when(productInventoryRepository.existsById(SKU_ID)).thenReturn(true);
        Order order = orderWithItems(itemWithSku(sku(SKU_ID), 5));

        assertThatThrownBy(() -> service.reserveForOrder(order))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_3004);

        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("reserveForOrder：原子預扣回 0 但庫存列不存在 → 視為未啟用追蹤，放行且不留流水帳")
    void reserveForOrder_noInventoryRow_skipsCheck() {
        when(productInventoryRepository.reserveIfAvailable(SKU_ID, 999)).thenReturn(0);
        when(productInventoryRepository.existsById(SKU_ID)).thenReturn(false);
        Order order = orderWithItems(itemWithSku(sku(SKU_ID), 999));

        service.reserveForOrder(order);

        verify(productInventoryRepository).existsById(SKU_ID);
        // 沒有庫存列就沒有異動可言，寫一筆 before/after 皆為 0 的列會讓台帳出現不存在的異動
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("reserveForOrder：訂單項目無 SKU（如 ROOM）時略過，完全不碰庫存")
    void reserveForOrder_itemWithoutSku_skipsGracefully() {
        Order order = orderWithItems(OrderItem.builder().id(UUID.randomUUID()).sku(null).quantity(1).build());

        service.reserveForOrder(order);

        verify(productInventoryRepository, never()).reserveIfAvailable(any(), anyInt());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("reserveForOrder：訂單尚未持久化 → 大聲失敗，不得寫出指不回來源的孤兒流水帳")
    void reserveForOrder_transientOrder_throwsIllegalState() {
        // DEF-065 修復把 reserveForOrder 從 OrderService 的 save() 之前移到之後。若日後有人改回去，
        // order.getId()／orderItem.getId() 都是 null，流水帳會變成一批查不到來源的孤兒列。
        Order transientOrder = Order.builder()
                .tenant(Tenant.builder().id(TENANT_ID).build())
                .user(User.builder().id(USER_ID).build())
                .items(List.of(itemWithSku(sku(SKU_ID), 1)))
                .build();

        assertThatThrownBy(() -> service.reserveForOrder(transientOrder))
                .isInstanceOf(IllegalStateException.class);

        verify(productInventoryRepository, never()).reserveIfAvailable(any(), anyInt());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("releaseForOrder：以品項數量呼叫原子釋放，不讀取也不寫回實體，並寫下 RELEASE 流水帳")
    void releaseForOrder_releasesAtomically() {
        when(productInventoryRepository.releaseReservation(SKU_ID, 4)).thenReturn(1);
        givenReadBack(100, 6);
        Order order = orderWithItems(itemWithSku(sku(SKU_ID), 4));

        service.releaseForOrder(order);

        verify(productInventoryRepository).releaseReservation(SKU_ID, 4);
        verify(productInventoryRepository, never()).findById(any());
        verify(productInventoryRepository, never()).save(any());
        assertThat(capturedMovement().getMovementType()).isEqualTo(StockMovement.MovementType.RELEASE);
    }

    @Test
    @DisplayName("releaseForOrder：庫存列不存在（0 筆）→ 不留流水帳")
    void releaseForOrder_noInventoryRow_writesNoMovement() {
        when(productInventoryRepository.releaseReservation(SKU_ID, 4)).thenReturn(0);
        Order order = orderWithItems(itemWithSku(sku(SKU_ID), 4));

        service.releaseForOrder(order);

        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("deductForOrder：以品項數量呼叫原子扣帳，不讀取也不寫回實體，並寫下 OUTBOUND 流水帳")
    void deductForOrder_deductsAtomically() {
        when(productInventoryRepository.deductReserved(SKU_ID, 3)).thenReturn(1);
        givenReadBack(97, 0);
        Order order = orderWithItems(itemWithSku(sku(SKU_ID), 3));

        service.deductForOrder(order);

        verify(productInventoryRepository).deductReserved(SKU_ID, 3);
        verify(productInventoryRepository, never()).findById(any());
        verify(productInventoryRepository, never()).save(any());

        StockMovement movement = capturedMovement();
        assertThat(movement.getMovementType()).isEqualTo(StockMovement.MovementType.OUTBOUND);
        // PRD §6.7.4：OUTBOUND 同時遞減 total_qty 與 reserved_qty，兩者的前值都由 after 反推
        assertThat(movement.getBeforeTotalQty()).isEqualTo(100);
        assertThat(movement.getAfterTotalQty()).isEqualTo(97);
        assertThat(movement.getBeforeReservedQty()).isEqualTo(3);
        assertThat(movement.getAfterReservedQty()).isZero();
    }

    @Test
    @DisplayName("deductForOrder：庫存列不存在（0 筆）→ 不留流水帳")
    void deductForOrder_noInventoryRow_writesNoMovement() {
        when(productInventoryRepository.deductReserved(SKU_ID, 3)).thenReturn(0);
        Order order = orderWithItems(itemWithSku(sku(SKU_ID), 3));

        service.deductForOrder(order);

        verify(stockMovementRepository, never()).save(any());
    }
}
