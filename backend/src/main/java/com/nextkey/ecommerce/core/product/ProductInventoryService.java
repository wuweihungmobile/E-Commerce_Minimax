package com.nextkey.ecommerce.core.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.order.OrderItem;
import com.nextkey.ecommerce.domain.model.product.ProductInventory;
import com.nextkey.ecommerce.domain.repository.ProductInventoryRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 商品訂單庫存服務（Sprint 88，AI-2422）。
 * 串接既有但從未被訂單流程呼叫的 {@link ProductInventory} 庫存原語（此前僅 M16 ERP
 * 採購/庫存異動模組使用），採 reserve-at-creation / deduct-at-payment / release-on-cancel
 * 三段式，避免商品訂單前端串接後同一 SKU 可被無限超賣。
 * SKU 若無對應庫存資料列，視為未啟用庫存追蹤，不限量、略過檢查（向下相容既有 SKU）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductInventoryService {

    private final ProductInventoryRepository productInventoryRepository;

    /** 訂單建立時逐項檢查庫存並預扣（reservedQty）；任一項不足即拋例外，交易回滾不留部分建立的訂單。 */
    @Transactional
    public void reserveForOrder(Order order) {
        for (OrderItem item : order.getItems()) {
            if (item.getSku() == null) {
                continue;
            }
            ProductInventory inventory = productInventoryRepository.findById(item.getSku().getId()).orElse(null);
            if (inventory == null) {
                continue;
            }
            if (!inventory.hasAvailableStock(item.getQuantity())) {
                throw new BusinessException(ErrorCode.E_3004,
                        "Insufficient stock for SKU: " + item.getSku().getId());
            }
            inventory.reserve(item.getQuantity());
            productInventoryRepository.save(inventory);
        }
    }

    /** 訂單於未付款（CREATED）狀態被取消時，釋放先前預扣的庫存。 */
    @Transactional
    public void releaseForOrder(Order order) {
        for (OrderItem item : order.getItems()) {
            if (item.getSku() == null) {
                continue;
            }
            productInventoryRepository.findById(item.getSku().getId()).ifPresent(inventory -> {
                inventory.release(item.getQuantity());
                productInventoryRepository.save(inventory);
            });
        }
    }

    /** 付款成功時將預扣正式轉為扣帳（totalQty 與 reservedQty 同時扣除）。 */
    @Transactional
    public void deductForOrder(Order order) {
        for (OrderItem item : order.getItems()) {
            if (item.getSku() == null) {
                continue;
            }
            productInventoryRepository.findById(item.getSku().getId()).ifPresent(inventory -> {
                inventory.deductStock(item.getQuantity());
                productInventoryRepository.save(inventory);
            });
        }
    }
}
