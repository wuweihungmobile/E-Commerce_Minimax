package com.nextkey.ecommerce.core.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.order.OrderItem;
import com.nextkey.ecommerce.domain.model.product.ProductInventory;
import com.nextkey.ecommerce.domain.model.product.ProductSku;
import com.nextkey.ecommerce.domain.repository.ProductInventoryRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * ProductInventoryService 單元測試（Sprint 88，AI-2422）。
 *
 * <p>驗證 reserve-at-creation / deduct-at-payment / release-on-cancel 三段式庫存操作，
 * 以及 SKU 無庫存資料列時「視為未啟用追蹤、不限量」的向下相容行為。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductInventoryService 單元測試（Sprint 88）")
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
    @DisplayName("reserveForOrder：庫存足夠時預扣 reservedQty")
    void reserveForOrder_sufficientStock_reserves() {
        ProductInventory inventory = ProductInventory.builder().skuId(SKU_ID).totalQty(10).reservedQty(0).build();
        when(productInventoryRepository.findById(SKU_ID)).thenReturn(Optional.of(inventory));
        Order order = orderWithItems(itemWithSku(sku(SKU_ID), 3));

        service.reserveForOrder(order);

        assertThat(inventory.getReservedQty()).isEqualTo(3);
        verify(productInventoryRepository).save(inventory);
    }

    @Test
    @DisplayName("reserveForOrder：庫存不足時拋 E_3004，不呼叫 save")
    void reserveForOrder_insufficientStock_throwsAndDoesNotSave() {
        ProductInventory inventory = ProductInventory.builder().skuId(SKU_ID).totalQty(2).reservedQty(0).build();
        when(productInventoryRepository.findById(SKU_ID)).thenReturn(Optional.of(inventory));
        Order order = orderWithItems(itemWithSku(sku(SKU_ID), 5));

        assertThatThrownBy(() -> service.reserveForOrder(order))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_3004);
        verify(productInventoryRepository, never()).save(inventory);
    }

    @Test
    @DisplayName("reserveForOrder：SKU 無庫存資料列時視為未啟用追蹤，略過檢查")
    void reserveForOrder_noInventoryRow_skipsCheck() {
        when(productInventoryRepository.findById(SKU_ID)).thenReturn(Optional.empty());
        Order order = orderWithItems(itemWithSku(sku(SKU_ID), 999));

        service.reserveForOrder(order);

        verify(productInventoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("reserveForOrder：訂單項目無 SKU（如 ROOM）時略過")
    void reserveForOrder_itemWithoutSku_skipsGracefully() {
        Order order = orderWithItems(OrderItem.builder().sku(null).quantity(1).build());

        service.reserveForOrder(order);

        verify(productInventoryRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("releaseForOrder：釋放先前預扣的庫存")
    void releaseForOrder_releasesReservedQty() {
        ProductInventory inventory = ProductInventory.builder().skuId(SKU_ID).totalQty(10).reservedQty(4).build();
        when(productInventoryRepository.findById(SKU_ID)).thenReturn(Optional.of(inventory));
        Order order = orderWithItems(itemWithSku(sku(SKU_ID), 4));

        service.releaseForOrder(order);

        assertThat(inventory.getReservedQty()).isEqualTo(0);
        verify(productInventoryRepository).save(inventory);
    }

    @Test
    @DisplayName("deductForOrder：付款成功後正式扣帳，totalQty 與 reservedQty 同時扣除")
    void deductForOrder_deductsTotalAndReservedQty() {
        ProductInventory inventory = ProductInventory.builder().skuId(SKU_ID).totalQty(10).reservedQty(3).build();
        when(productInventoryRepository.findById(SKU_ID)).thenReturn(Optional.of(inventory));
        Order order = orderWithItems(itemWithSku(sku(SKU_ID), 3));

        service.deductForOrder(order);

        assertThat(inventory.getTotalQty()).isEqualTo(7);
        assertThat(inventory.getReservedQty()).isEqualTo(0);
        verify(productInventoryRepository).save(inventory);
    }
}
