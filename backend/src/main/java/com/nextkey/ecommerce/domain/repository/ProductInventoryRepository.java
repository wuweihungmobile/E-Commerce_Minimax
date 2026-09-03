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
 * 不再「載入實體 → 改欄位 → save()」。Sprint 113（DEF-051）把 M16 ERP 的手動異動與採購入庫
 * （{@code StockMovementService}、{@code PurchaseOrderService}）一併收攏進來，至此**所有**
 * {@code product_inventory} 的寫入都在本檔，實體上不再留任何數量 setter 型的異動入口。
 *
 * <p>每條敘述都刻意帶 {@code version = version + 1}：{@link ProductInventory} 有
 * {@code @Version} 樂觀鎖，若原生 UPDATE 不推進版號，任何仍持有實體快照的路徑就會通過樂觀鎖
 * 檢查而整列覆蓋別人剛寫入的數量。這在 Sprint 103 是為了防 ERP 覆蓋訂單流程；ERP 改為原生
 * UPDATE 後這條路已不存在，但規則保留——版號欄位存在就必須維護，否則下一個載入實體的呼叫端
 * 會拿到看似有效的過期版號。
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

    /**
     * 原子增加總量（Sprint 113，DEF-051）。M16 ERP 的採購收貨入庫與盤盈／調撥入庫共用。
     *
     * <p>取代 {@code ProductInventory.addStock()} 的「載入 → 加 → save()」。修復前 10 張採購單
     * 併發收同一 SKU，實測只有 2 張入得了帳，其餘 8 張以
     * {@code ObjectOptimisticLockingFailureException} 收場——貨已經到了、系統卻沒入庫，
     * 且採購單狀態同時沒推進（整筆交易回滾），操作員只看到 500。
     *
     * @return 受影響筆數；0 表示該 SKU 無庫存列
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE product_inventory
               SET total_qty = total_qty + :quantity,
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE sku_id = :skuId
            """, nativeQuery = true)
    int increaseTotalQty(@Param("skuId") UUID skuId, @Param("quantity") int quantity);

    /**
     * 原子扣減總量，庫存不足則不動（Sprint 113，DEF-051）。M16 ERP 的報廢／盤虧／調撥出庫共用。
     *
     * <p>「總量是否足夠」與「扣減」原本分屬兩次往返，併發下形同虛設：實測庫存 3 遇上 10 筆
     * 併發扣減，10 條執行緒**全數通過**充足性檢查（{@code insufficientStock=0}，各自讀到的都還是
     * 別人尚未扣掉的數字），最後靠樂觀鎖擋下 8 筆而非靠庫存檢查——擋是擋住了，但操作員收到的是
     * 技術性例外而非「庫存不足」。
     *
     * <p>🔴 **刻意只動 {@code total_qty}，不碰 {@code reserved_qty}**：PRD §6.7.4 明訂
     * ADJUST_MINUS／TRANSFER_OUT／SCRAP 皆為 {@code -total_qty}，只有 OUTBOUND（訂單出貨）
     * 才是 {@code -total_qty, -reserved_qty}。修復前 ERP 誤用共用的 {@code deductStock()}
     * 而連預留量一起扣，等於把已被買家訂走的貨重新放回可售池（見
     * {@code M16ErpInventoryConcurrencyIntegrationTest.manualDeductionMustNotReleaseReservedStock}）。
     *
     * <p>條件寫成 {@code total_qty >= :quantity} 而非可售量 {@code total_qty - reserved_qty}：
     * 維持修復前以總量為準的既有語意——報廢／盤虧針對的是實體庫存，被預留的那幾件同樣可能破損。
     *
     * @return 受影響筆數；1 表示扣減成功，0 表示總量不足**或**該 SKU 無庫存列
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE product_inventory
               SET total_qty = total_qty - :quantity,
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE sku_id = :skuId
               AND total_qty >= :quantity
            """, nativeQuery = true)
    int decreaseTotalQtyIfSufficient(@Param("skuId") UUID skuId, @Param("quantity") int quantity);

    /**
     * 讀取當前總量（Sprint 113，DEF-051）。供 {@code stock_movements} 記錄異動前後數量之用。
     *
     * <p>刻意用原生純量查詢而非實體 getter：改為原子 UPDATE 後，持久化上下文裡的實體快照
     * 早於那筆寫入，拿它填流水帳會記到過期的數字。純量查詢不經一級快取，且緊接在同一交易的
     * UPDATE 之後執行——該列的行鎖尚未釋放，其他交易改不動，因此讀到的必然是本次操作的結果。
     */
    @Query(value = "SELECT COALESCE(total_qty, 0) FROM product_inventory WHERE sku_id = :skuId",
            nativeQuery = true)
    Integer findTotalQtyBySkuId(@Param("skuId") UUID skuId);

    /**
     * 讀取當前預留量（Sprint 115，DEF-065）。供訂單流程的 {@code RESERVE}／{@code OUTBOUND}／
     * {@code RELEASE} 流水帳記錄異動前後的 {@code reserved_qty} 之用。
     *
     * <p>與 {@link #findTotalQtyBySkuId} 同一套理由：原子 UPDATE 之後實體快照已過期，
     * 必須以不經一級快取的原生純量查詢回讀；讀取緊接在同一交易的 UPDATE 之後，
     * 該列的行鎖尚未釋放，故拿到的必然是本次操作的結果。
     */
    @Query(value = "SELECT COALESCE(reserved_qty, 0) FROM product_inventory WHERE sku_id = :skuId",
            nativeQuery = true)
    Integer findReservedQtyBySkuId(@Param("skuId") UUID skuId);
}
