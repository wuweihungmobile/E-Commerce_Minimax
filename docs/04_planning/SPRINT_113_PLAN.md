# Sprint 113 Plan — DEF-051：M16 ERP 庫存讀後寫（附帶一個與併發無關的語意缺陷）

**Sprint**: Sprint 113
**日期**: 2026-09-03
**AI 編號**: AI-2447
**主題**: 承 Sprint 103 未收尾的 ERP 側庫存寫入。1 US，2 SP。

---

## 1. 為什麼挑這一項

追蹤表活躍項目：🔴 高優先級為空，🟡 中優先級 S112 修掉 DEF-043 後剩三項：

| 項目 | 為什麼不是它 |
|------|-------------|
| DEF-044 退款不回補庫存 | 條目自身寫明「**需業務決策**：退款是否應自動回補、回補時點」 |
| DEF-021 CJK 字體 | 純外觀技術債，且 S41 已決策維持系統字體堆疊 |
| **DEF-051 ERP 庫存讀後寫** | **唯一工程能自己完成的**：修法（條件式／相對 UPDATE）在 S102／S103／S105／S106 已用過四輪，不需要任何新的產品決策 |

---

## 2. 紅燈實測：追蹤表的定性對了一半

DEF-051 記的是「只有可用性問題，**沒有資料正確性風險**」。實測結果：**併發部分完全正確，但漏掉了一個與併發無關的正確性缺陷**。

新增 `M16ErpInventoryConcurrencyIntegrationTest`（5 個案例，真實 PostgreSQL、10 執行緒、各自獨立交易），對**未修復**的程式碼跑出 4 紅 1 綠：

| 案例 | 未修復時的結果 | 說明 |
|------|--------------|------|
| `singleManualAdjustment...`（單執行緒對照組） | ✅ 通過 | 寫入路徑本身沒問題——失敗的那四個是機制問題，不是環境問題 |
| `concurrentManualAdjustmentsAllApply` | ❌ `granted=3`，7 筆 `ObjectOptimisticLockingFailureException` | 10 筆盤盈只有 3 筆進帳，其餘 7 筆**整筆消失**且操作員看到 500 |
| `concurrentManualDeductionsNeverOverdraw` | ❌ `granted=2, insufficientStock=0`，8 筆樂觀鎖 | 🔴 **`insufficientStock=0` 是本輪最有資訊量的數字**：庫存 3、10 條執行緒各扣 1，**沒有任何一筆走到「庫存不足」分支**——10 條執行緒全數通過了充足性檢查（各自讀到的都還是別人尚未扣掉的數字），最後是靠樂觀鎖擋掉 8 筆。擋是擋住了，但那道 `beforeTotalQty < quantity` 檢查在這場競賽裡**一次都沒生效過** |
| `concurrentPurchaseReceiptsAllApply` | ❌ `granted=2`，8 筆樂觀鎖 | 10 張採購單併發收同一 SKU 只有 2 張入得了帳——貨到了、庫存沒加、採購單狀態也沒推進（整筆交易回滾） |
| `manualDeductionMustNotReleaseReservedStock` | ❌ `expected: 4 but was: 1` | 🔴 **追蹤表沒寫到的這一個，是資料正確性缺陷，而且不需要併發** |

### 2.1 那個與併發無關的缺陷

`ProductInventory.deductStock()` 是**訂單出貨**的語意——同時扣 `total_qty` 與 `reserved_qty`：

```java
public void deductStock(final int quantity) {
    this.totalQty -= quantity;
    this.reservedQty = Math.max(0, this.reservedQty - quantity);   // ← 訂單出貨才該做的事
}
```

M16 ERP 的 `DAMAGE`／`TRANSFER_OUT`／`THEFT`（報廢／盤虧／調撥出庫）共用了它。但 PRD §6.7.4 的異動類型表寫得很清楚：

| 類型 | 方向 |
|------|------|
| OUTBOUND（訂單出貨） | `-total_qty, -reserved_qty` |
| ADJUST_MINUS（盤虧） | `-total_qty` |
| TRANSFER_OUT（調撥出庫） | `-total_qty` |
| SCRAP（報廢） | `-total_qty` |

**只有訂單出貨才扣預留量。** 後果是可售量被灌水：總量 10、其中 4 件已被買家訂走（可售 6），報廢 3 件後應為總量 7／預留 4／可售 3，實際變成總量 7／預留 1／**可售 6**——那 3 件已經賣掉的貨被放回可售池，直接通往超賣。這條路徑**單執行緒就會發生**，與 DEF-051 的併發前提無關，所以過去四輪掃描都沒碰到它（掃描找的是讀後寫樣式）。

> 這是「樣式比對找對了位置、卻沒看那段程式碼實際在做什麼」的又一個實例（承 S103 教訓）。差別在於：這次不是推錯後果，而是**站在正確的位置上，看漏了旁邊那一行**。

---

## 3. 修法

### 3.1 三條敘述進 `ProductInventoryRepository`

```java
increaseTotalQty(skuId, qty)               // UPDATE ... SET total_qty = total_qty + :q, version = version + 1
decreaseTotalQtyIfSufficient(skuId, qty)   // UPDATE ... SET total_qty = total_qty - :q ... WHERE total_qty >= :q
findTotalQtyBySkuId(skuId)                 // SELECT COALESCE(total_qty, 0) —— 供流水帳回讀
```

三個決定值得記下來：

1. **扣減敘述只動 `total_qty`**，不碰 `reserved_qty`（見 §2.1）。
2. **條件寫 `total_qty >= :quantity` 而非可售量** `total_qty - reserved_qty`：維持修復前以總量為準的既有語意——報廢／盤虧針對的是**實體庫存**，被預留的那幾件同樣可能破損。這是刻意不改的部分。
3. **仍帶 `version = version + 1`**：ERP 改為原生 UPDATE 後，S103 那條「ERP 會拿過期快照覆蓋訂單流程」的路已經不存在，但規則保留——版號欄位存在就必須維護，否則下一個載入實體的呼叫端會拿到看似有效的過期版號。

### 3.2 流水帳的前後數量改為回讀

`stock_movements.before_total_qty` / `after_total_qty` 修復前取自實體快照。改成原子 UPDATE 後那份快照已過期，因此：

```java
int delta = applyMovementType(skuId, movementType, quantity);   // 執行 UPDATE，回傳帶號變化量
int afterTotalQty = currentTotalQty(skuId);                     // 回讀
int beforeTotalQty = afterTotalQty - delta;                     // 由 after 反推，兩者必然自洽
```

**為什麼回讀是安全的**：回讀與 UPDATE 在同一交易內，該列的行鎖尚未釋放，其他交易改不動——讀到的必然是本次操作的結果。這也是 `concurrentManualAdjustmentsAllApply` 能斷言「10 筆異動的 `after_total_qty` 恰好走完 1..10、不重複不跳號」的原因：修復後這條斷言成立，代表流水帳與實際庫存**逐筆對得上**，而不只是最終總數對。

### 3.3 `ProductInventory` 不再有任何數量異動方法

`addStock()` / `deductStock()` 在本輪後成為零呼叫死碼，**移除**（與 S101 移除 `computeDiscount` 舊多載、S102 移除 `incrementUsageCount`、S103 移除 `reserve()`/`release()` 同一個「大聲失敗」理由）。`deductStock()` 另有一個非移除不可的理由：**它就是 §2.1 那個缺陷的來源**，留著等於留一個讓下一個人再踩一次的入口。

至此 `product_inventory` 的**所有**數量寫入都在 `ProductInventoryRepository` 的原生 UPDATE 中，各自帶著自己的語意與條件。

---

## 4. 驗證

| # | 內容 | 結果 |
|---|------|------|
| ① | 新測試 × **未修復**程式碼 | **4 failed / 1 passed**（見 §2 表；1 個通過的是刻意留的單執行緒對照組） |
| ② | 新測試 × 已修復程式碼 | **5 passed**（35.07s） |
| ③ | ERP 單元測試改寫後 | `StockMovementServiceTest` 18 passed（+1 守衛）、`PurchaseOrderServiceTest` 25 passed |
| ④ | 後端全量回歸 `mvn -o verify` | 單元 **1025 passed / 0 failed**；整合見下方守門段落 |

### 4.1 既有單元測試為什麼要改寫而不是新增

`StockMovementServiceTest` 原本斷言「`result.getAfterTotalQty()` 等於 100+20」——那是在 **mock 掉的 Repository 之上**驗證記憶體物件的算術，缺陷存在時照樣全綠（Sprint 97「固件繞過同一段邏輯」的又一實例）。改寫後改為斷言**派送行為**（`verify(...).increaseTotalQty(skuId, 20)`），數量正確性交給整合測試。

另新增一個守衛案例 `createManualMovement_neverWritesThroughEntityOrOrderFlowQuery`：

```java
verify(productInventoryRepository, never()).save(any(ProductInventory.class));      // 不得退回讀後寫
verify(productInventoryRepository, never()).deductReserved(any(UUID.class), anyInt()); // 不得沿用訂單出貨敘述
```

第二條是針對 §2.1 的：`deductReserved` 會連 `reserved_qty` 一起扣，ERP 一旦改用它，就是把剛修掉的缺陷換個寫法搬回來。

---

## 5. 範圍外：本輪查證過但刻意不動的

### 5.1 🆕 DEF-063（新記錄）：ERP 手動異動 UI 有 5/7 個選項後端不認識

查 §2.1 時順手核對前後端的異動類型枚舉，發現兩邊根本不是同一組值：

| 前端 `StockMovementType`（`services/erp/stockMovement.ts`） | 後端 `StockMovement.MovementType` | 送出後 |
|---|---|---|
| `INBOUND` / `OUTBOUND` / `ADJUST_PLUS` / `ADJUST_MINUS` / `SCRAP` | 不存在 | `valueOf()` 拋 `IllegalArgumentException` → **E_7005「Invalid movement type」** |
| `TRANSFER_IN` / `TRANSFER_OUT` | 同名 | 正常 |

前端用的是 **PRD §6.7.4 的名稱**，後端枚舉用的是另一組（`PURCHASE_RECEIPT`／`SALE`／`ADJUSTMENT`／`DAMAGE`／`THEFT`／`RETURN`），中間沒有任何轉換層——`StockMovementRequest.movementType` 是 `String`，直接進 `MovementType.valueOf()`。所以 `/dashboard/erp/stock-movements/new` 的下拉選單裡，**7 個選項有 5 個必定失敗**（預設選中的 `INBOUND` 就是其中之一）。

**不在本輪修**：要往哪邊對齊是產品決策——改後端枚舉要動 `stock_movements.movement_type` 的既有資料（需要 migration），改前端則會讓 UI 標籤與 PRD 用語脫節。記為 DEF-063 交使用者拍板。

### 5.2 `RETURN` 型別是靜默 no-op

`createManualMovement` 的 `switch` 對 `RETURN` 落到 `default` → 不動庫存，卻照樣寫一筆 `isInbound()` 為真的異動記錄（`before == after`）。PRD §6.7.4 的類型表沒有 `RETURN`，語意要由使用者定義，一併記入 DEF-063。

### 5.3 沒有動的

- **ERP `InventoryService`**：全為 `@Transactional(readOnly = true)` 查詢，無寫入。
- **`stock_movements.before_reserved_qty` / `after_reserved_qty`**：兩個欄位存在但 ERP 從未填值（一直是 null）。填它是新功能，不是本輪的缺陷修復（Rule 3）。
- **`receivePurchaseOrder` 對 `PurchaseOrderItem.receivedQuantity` 的讀後寫**：同一張採購單的併發收貨確實有窗口，但那是**採購單自己的列**、與 `product_inventory` 無關，且業務上「兩個人同時收同一張單」與「多張單收同一個 SKU」風險不同級。本輪測試刻意讓每條執行緒收**自己的**採購單，把競爭點單獨留在庫存列上。若要處理，需要自己的紅燈。

---

## 6. 下一輪建議

| 優先 | 項目 | 說明 |
|------|------|------|
| 1 | 🟡 **DEF-063**（本輪新記錄） | ERP 手動異動 UI 5/7 選項必定失敗——**使用者可見、必然發生**，但要往哪邊對齊需拍板 |
| 2 | 🟡 DEF-044 | 退款回補庫存，需業務決策 |
| 3 | 🟡 DEF-021 | CJK 字體，S41 已決策維持現狀 |

至此 `product_inventory` 的讀後寫全數清空（S103 訂單流程 + S113 ERP），S102 起的「服務層對數值欄位讀後寫」系列告一段落。
