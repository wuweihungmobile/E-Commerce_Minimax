package com.nextkey.ecommerce.core.product;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.domain.model.inventory.StockMovement;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.order.OrderItem;
import com.nextkey.ecommerce.domain.model.product.ProductInventory;
import com.nextkey.ecommerce.domain.repository.ProductInventoryRepository;
import com.nextkey.ecommerce.domain.repository.StockMovementRepository;
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
 *
 * <p>Sprint 103（DEF-050）：三段操作全數改為單一敘述的原子 UPDATE。修復前每段都是
 * 「載入實體 → 改欄位 → save()」，併發下 10 筆請求只有 2 筆寫得進去，其餘被
 * {@code @Version} 樂觀鎖擋成技術性例外——預扣端讓買家看到 500 且賣不完，釋放端讓
 * 取消的庫存還不回去，扣帳端則被付款流程的 try/catch 靜默吞掉而帳實不符。
 *
 * <p>Sprint 115（DEF-065）：三段操作各補寫一筆 {@code stock_movements} 流水帳。
 * PRD §6.7.3 明訂下單產生 {@code RESERVE}、出貨產生 {@code OUTBOUND}、取消產生 {@code RELEASE}，
 * 但在此之前本類別對 {@link StockMovement} **零引用**——{@code product_inventory} 的數字會動、
 * 流水帳一筆都不會留，庫存台帳（PRD §6.7.3 列為 P0）對 C 端的所有進出完全沒有資料。
 * 這同時是 DEF-063 能存活那麼久的原因：那三型從來沒被寫入過，所以後端枚舉把它們拼成
 * {@code RESERVATION}／{@code SALE} 也沒有人發現。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductInventoryService {

    private final ProductInventoryRepository productInventoryRepository;
    private final StockMovementRepository stockMovementRepository;

    /** 訂單建立時逐項檢查庫存並預扣（reservedQty）；任一項不足即拋例外，交易回滾不留部分建立的訂單。 */
    @Transactional
    public void reserveForOrder(Order order) {
        requirePersisted(order);
        for (OrderItem item : order.getItems()) {
            if (item.getSku() == null) {
                continue;
            }
            UUID skuId = item.getSku().getId();
            // 0 筆有兩種可能：可售量不足（要擋），或該 SKU 根本沒有庫存列（未啟用追蹤，既有語意為略過）。
            // existsById 只在失敗路徑上多付一次查詢，成功路徑維持單次往返。
            if (productInventoryRepository.reserveIfAvailable(skuId, item.getQuantity()) == 0) {
                if (productInventoryRepository.existsById(skuId)) {
                    throw new BusinessException(ErrorCode.E_3004, "Insufficient stock for SKU: " + skuId);
                }
                continue;
            }
            // PRD §6.7.4：RESERVE 為 +reserved_qty，total_qty 不動
            recordMovement(order, item, StockMovement.MovementType.RESERVE, 0, item.getQuantity());
        }
    }

    /** 訂單於未付款（CREATED）狀態被取消時，釋放先前預扣的庫存。 */
    @Transactional
    public void releaseForOrder(Order order) {
        requirePersisted(order);
        for (OrderItem item : order.getItems()) {
            if (item.getSku() == null) {
                continue;
            }
            if (productInventoryRepository.releaseReservation(item.getSku().getId(), item.getQuantity()) > 0) {
                // PRD §6.7.4：RELEASE 為 -reserved_qty，total_qty 不動
                recordMovement(order, item, StockMovement.MovementType.RELEASE, 0, -item.getQuantity());
            }
        }
    }

    /** 付款成功時將預扣正式轉為扣帳（totalQty 與 reservedQty 同時扣除）。 */
    @Transactional
    public void deductForOrder(Order order) {
        requirePersisted(order);
        for (OrderItem item : order.getItems()) {
            if (item.getSku() == null) {
                continue;
            }
            if (productInventoryRepository.deductReserved(item.getSku().getId(), item.getQuantity()) > 0) {
                // PRD §6.7.4：OUTBOUND 為 -total_qty, -reserved_qty
                recordMovement(order, item, StockMovement.MovementType.OUTBOUND,
                        -item.getQuantity(), -item.getQuantity());
            }
        }
    }

    /**
     * 寫下一筆訂單流程的庫存流水帳（Sprint 115，DEF-065）。
     *
     * <p>只在庫存列確實被改動時呼叫：SKU 無庫存列（未啟用追蹤）時什麼都沒發生，
     * 寫一筆 before/after 皆為 0 的列會讓台帳出現不存在的異動。
     *
     * <p>前後數量的取法比照 {@code StockMovementService}／{@code PurchaseOrderService}
     * （Sprint 113）：after 一律回讀 DB（原子 UPDATE 後實體快照已過期），before 由
     * 「after − 本次帶號變化量」反推，兩者必然自洽。
     *
     * <p>⚠️ 例外：{@code releaseReservation}／{@code deductReserved} 對 {@code reserved_qty}
     * 帶 {@code GREATEST(..., 0)} 下限保護。在 {@code reserved_qty >= quantity} 的正常狀態下
     * 反推值精確；若下限真的生效（資料已不一致的異常狀態），{@code before_reserved_qty}
     * 記到的會是「應扣值」而非實際值。這裡刻意不為了那個異常狀態多付一次讀取——
     * 真正該處理的是讓它不發生，而不是把它記得更漂亮。
     *
     * @param totalDelta    本次對 {@code total_qty} 的帶號變化量
     * @param reservedDelta 本次對 {@code reserved_qty} 的帶號變化量
     */
    private void recordMovement(final Order order, final OrderItem item,
            final StockMovement.MovementType movementType, final int totalDelta, final int reservedDelta) {
        UUID skuId = item.getSku().getId();
        int afterTotalQty = productInventoryRepository.findTotalQtyBySkuId(skuId);
        int afterReservedQty = productInventoryRepository.findReservedQtyBySkuId(skuId);

        StockMovement movement = StockMovement.builder()
                // 🔴 走關聯物件而非 order.getTenantId()／getUserId()：那兩個是 insertable=false 的
                // 影子欄位，只有實體重新從 DB 載入時才有值。訂單剛在本交易內建立時它們是 null，
                // 而 stock_movements.tenant_id 是 NOT NULL——用影子欄位會在建單當下直接炸掉。
                .tenantId(order.getTenant().getId())
                .skuId(skuId)
                .movementType(movementType)
                .quantity(item.getQuantity())
                .beforeTotalQty(afterTotalQty - totalDelta)
                .afterTotalQty(afterTotalQty)
                .balanceAfter(afterTotalQty)
                .beforeReservedQty(afterReservedQty - reservedDelta)
                .afterReservedQty(afterReservedQty)
                .referenceType(StockMovement.ReferenceType.ORDER)
                .referenceId(order.getId())
                .orderItemId(item.getId())
                .notes("Order " + movementType.name().toLowerCase())
                .createdBy(order.getUser().getId())
                .build();

        stockMovementRepository.save(movement);
        log.debug("Recorded order stock movement: type={}, skuId={}, qty={}, orderId={}",
                movementType, skuId, item.getQuantity(), order.getId());
    }

    /**
     * 要求訂單已持久化（Sprint 115，DEF-065）。
     *
     * <p>DEF-065 修復前，{@code OrderService} 是「先 {@code reserveForOrder} 再 {@code save}」，
     * 此時 {@code order.getId()} 與每個 {@code OrderItem.getId()} 都還是 null
     * （兩者皆為 {@code @GeneratedValue}）。流水帳一旦在那個時點寫入，
     * 就是一批 {@code reference_id} 為 null 的孤兒列——查得到數字、查不到來源，等同沒記。
     *
     * <p>這裡刻意大聲失敗而非靜默容忍：呼叫順序是本功能的前提，不是可選的最佳化。
     */
    private void requirePersisted(final Order order) {
        if (order.getId() == null) {
            throw new IllegalStateException(
                    "Order must be persisted before inventory operations: stock movements need a reference_id");
        }
        for (OrderItem item : order.getItems()) {
            if (item.getSku() != null && item.getId() == null) {
                throw new IllegalStateException(
                        "Order items must be persisted before inventory operations: "
                                + "stock movements need an order_item_id");
            }
        }
    }
}
