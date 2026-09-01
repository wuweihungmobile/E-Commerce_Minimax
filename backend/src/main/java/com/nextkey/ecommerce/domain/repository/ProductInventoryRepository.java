package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.product.ProductInventory;

/**
 * 產品庫存 Repository
 *
 * <p>Sprint 103（DEF-050）起，訂單流程的三段式庫存異動一律走本檔的條件式／相對 UPDATE，
 * 不再「載入實體 → 改欄位 → save()」。三條敘述都刻意帶 {@code version = version + 1}：
 * {@link ProductInventory} 有 {@code @Version} 樂觀鎖，M16 ERP 的進貨／盤點仍走 JPA save
 * （{@code StockMovementService}、{@code PurchaseOrderService}），若原生 UPDATE 不推進版號，
 * ERP 端就會拿著過期快照通過樂觀鎖檢查，把訂單流程剛寫入的數量整列覆蓋掉。
 */
@Repository
public interface ProductInventoryRepository extends JpaRepository<ProductInventory, UUID> {

    List<ProductInventory> findBySkuIdIn(List<UUID> skuIds);

    /**
     * 原子預扣庫存（Sprint 103，DEF-050）。
     *
     * <p>「可售量是否足夠」與「累加預扣量」寫在同一條 SQL 的 WHERE 與 SET 中，取代原本
     * {@code hasAvailableStock()} 讀 → {@code save()} 寫的兩段式操作。
     *
     * <p>修復前該窗口的實測失效模式**不是**超賣：{@code @Version} 樂觀鎖確實生效，10 張訂單
     * 併發搶 3 件庫存時只有 2 張成功、8 張收到 {@code ObjectOptimisticLockingFailureException}
     * （買家端即 500），庫存賣不完；DEF-050 原記錄的「雙雙通過檢查而超賣、寫入互相覆蓋」
     * 已由 {@code M12InventoryConcurrencyIntegrationTest} 實測推翻。改為條件式 UPDATE 後，
     * 併發請求由資料庫在單一敘述內排隊，賣得完且失敗者拿到語意明確的 E-3004。
     *
     * @return 受影響筆數；1 表示預扣成功，0 表示可售量不足**或**該 SKU 無庫存列（呼叫端須自行區分）
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE product_inventory
               SET reserved_qty = reserved_qty + :quantity,
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE sku_id = :skuId
               AND total_qty - reserved_qty >= :quantity
            """, nativeQuery = true)
    int reserveIfAvailable(@Param("skuId") UUID skuId, @Param("quantity") int quantity);

    /**
     * 原子釋放預扣（Sprint 103，DEF-050）。訂單於未付款狀態取消時呼叫。
     *
     * <p>修復前為「載入 → {@code release()} → save()」，實測 10 筆併發取消只有 2 筆成功，
     * 其餘 8 筆的釋放被樂觀鎖擋掉而**靜默失效**（呼叫端 {@code OrderService} 未捕捉此例外，
     * 但取消流程本身已完成），使 {@code reserved_qty} 殘留、那批貨從此賣不出去。
     *
     * <p>{@code GREATEST(..., 0)} 為下限保護，語意與修復前 {@code release()} 內的
     * {@code Math.max(0, ...)} 一致。
     *
     * @return 受影響筆數；0 表示該 SKU 無庫存列（未啟用庫存追蹤，既有語意為略過）
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE product_inventory
               SET reserved_qty = GREATEST(reserved_qty - :quantity, 0),
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE sku_id = :skuId
            """, nativeQuery = true)
    int releaseReservation(@Param("skuId") UUID skuId, @Param("quantity") int quantity);

    /**
     * 原子扣帳（Sprint 103，DEF-050）。付款成功時把預扣正式轉為出庫。
     *
     * <p>此處是本缺陷影響最深的一段：扣帳由 {@code PaymentStateService.deductStockSafely}
     * 包在 try/catch 中呼叫（付款已成功，不因庫存失敗而回滾），因此修復前併發付款時
     * 8/10 的樂觀鎖失敗會被**完全靜默地吞掉**——付款成立、{@code total_qty} 卻沒扣，
     * 該 SKU 帳實不符且持續可賣。這才是 DEF-050 真正通往超賣的路徑（間接且延遲發生）。
     *
     * <p>{@code total_qty} 為無條件相對遞減、{@code reserved_qty} 帶下限保護，
     * 語意與修復前 {@code deductStock()} 一致。
     *
     * @return 受影響筆數；0 表示該 SKU 無庫存列（未啟用庫存追蹤，既有語意為略過）
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE product_inventory
               SET total_qty = total_qty - :quantity,
                   reserved_qty = GREATEST(reserved_qty - :quantity, 0),
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE sku_id = :skuId
            """, nativeQuery = true)
    int deductReserved(@Param("skuId") UUID skuId, @Param("quantity") int quantity);
}
