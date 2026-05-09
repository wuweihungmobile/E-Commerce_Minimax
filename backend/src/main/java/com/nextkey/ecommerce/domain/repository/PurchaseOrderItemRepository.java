package com.nextkey.ecommerce.domain.repository;

import com.nextkey.ecommerce.domain.model.inventory.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 採購單明細 Repository
 * PRD §8.1.5
 */
@Repository
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, UUID> {

    /**
     * 依採購單 ID 取得明細列表
     */
    List<PurchaseOrderItem> findByPurchaseOrderId(UUID purchaseOrderId);

    /**
     * 依 SKU 取得採購單明細
     */
    List<PurchaseOrderItem> findBySkuId(UUID skuId);
}