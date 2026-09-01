package com.nextkey.ecommerce.core.product;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.order.OrderItem;
import com.nextkey.ecommerce.domain.model.product.ProductSku;
import com.nextkey.ecommerce.domain.repository.ProductInventoryRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * ProductInventoryService 單元測試（Sprint 88，AI-2422；Sprint 103 改寫）。
 *
 * <p>驗證 reserve-at-creation / deduct-at-payment / release-on-cancel 三段式庫存操作的
 * **派送行為**：哪些品項會被略過、原子敘述收到什麼參數、0 筆回傳如何被解讀。
 *
 * <p>Sprint 103（DEF-050）起，數量的實際變化不再由本檔驗證——它已下沉到 SQL，
 * mock 掉 Repository 的測試無從觀察，硬要驗只會變成「用固件回放自己寫的 stub」。
 * 併發正確性與數量結果一律由 {@code M12InventoryConcurrencyIntegrationTest} 以真實
 * PostgreSQL 驗證。本檔刻意保留 {@code never()).save(...)} 斷言，作為「不得退回讀後寫」的守衛。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductInventoryService 單元測試（Sprint 88；Sprint 103 改寫）")
class ProductInventoryServiceTest {

    @Mock
    private ProductInventoryRepository productInventoryRepository;

    private ProductInventoryService service;

    private static final UUID SKU_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProductInventoryService(productInventoryRepository);
    }

    private ProductSku sku(UUID id) {
        return ProductSku.builder().id(id).skuCode("SKU-" + id).build();
    }

    private OrderItem itemWithSku(ProductSku sku, int quantity) {
        return OrderItem.builder().sku(sku).quantity(quantity).build();
    }

    private Order orderWithItems(OrderItem... items) {
        return Order.builder().items(List.of(items)).build();
    }

    @Test
    @DisplayName("reserveForOrder：以品項數量呼叫原子預扣，成功時不再讀取實體")
    void reserveForOrder_sufficientStock_reservesAtomically() {
        when(productInventoryRepository.reserveIfAvailable(SKU_ID, 3)).thenReturn(1);
        Order order = orderWithItems(itemWithSku(sku(SKU_ID), 3));

        service.reserveForOrder(order);

        verify(productInventoryRepository).reserveIfAvailable(SKU_ID, 3);
        // 守衛：一旦有人把實作改回「findById → 改欄位 → save」，DEF-050 的讀後寫窗口就回來了
        verify(productInventoryRepository, never()).findById(any());
        verify(productInventoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("reserveForOrder：原子預扣回 0 且庫存列存在 → 判定為庫存不足，拋 E_3004")
    void reserveForOrder_insufficientStock_throws() {
        when(productInventoryRepository.reserveIfAvailable(SKU_ID, 5)).thenReturn(0);
        when(productInventoryRepository.existsById(SKU_ID)).thenReturn(true);
        Order order = orderWithItems(itemWithSku(sku(SKU_ID), 5));

        assertThatThrownBy(() -> service.reserveForOrder(order))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_3004);
    }

    @Test
    @DisplayName("reserveForOrder：原子預扣回 0 但庫存列不存在 → 視為未啟用追蹤，放行不拋例外")
    void reserveForOrder_noInventoryRow_skipsCheck() {
        when(productInventoryRepository.reserveIfAvailable(SKU_ID, 999)).thenReturn(0);
        when(productInventoryRepository.existsById(SKU_ID)).thenReturn(false);
        Order order = orderWithItems(itemWithSku(sku(SKU_ID), 999));

        service.reserveForOrder(order);

        verify(productInventoryRepository).existsById(SKU_ID);
    }

    @Test
    @DisplayName("reserveForOrder：訂單項目無 SKU（如 ROOM）時略過，完全不碰庫存")
    void reserveForOrder_itemWithoutSku_skipsGracefully() {
        Order order = orderWithItems(OrderItem.builder().sku(null).quantity(1).build());

        service.reserveForOrder(order);

        verify(productInventoryRepository, never()).reserveIfAvailable(any(), anyInt());
    }

    @Test
    @DisplayName("releaseForOrder：以品項數量呼叫原子釋放，不讀取也不寫回實體")
    void releaseForOrder_releasesAtomically() {
        Order order = orderWithItems(itemWithSku(sku(SKU_ID), 4));

        service.releaseForOrder(order);

        verify(productInventoryRepository).releaseReservation(SKU_ID, 4);
        verify(productInventoryRepository, never()).findById(any());
        verify(productInventoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("deductForOrder：以品項數量呼叫原子扣帳，不讀取也不寫回實體")
    void deductForOrder_deductsAtomically() {
        Order order = orderWithItems(itemWithSku(sku(SKU_ID), 3));

        service.deductForOrder(order);

        verify(productInventoryRepository).deductReserved(SKU_ID, 3);
        verify(productInventoryRepository, never()).findById(any());
        verify(productInventoryRepository, never()).save(any());
    }
}
