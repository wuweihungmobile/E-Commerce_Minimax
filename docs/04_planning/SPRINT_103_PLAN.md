# Sprint 103 Plan — 商品庫存三段式操作改為原子敘述（DEF-050）

**Sprint**: Sprint 103
**日期**: 2026-09-01
**AI 編號**: AI-2437
**主題**: 修復 DEF-050——`ProductInventoryService` 的預扣／釋放／扣帳三段操作皆為「載入實體 → 改欄位 → `save()`」的讀後寫。**本輪紅燈實測推翻了 DEF-050 記錄的失效模式**：`ProductInventory` 帶 `@Version` 樂觀鎖（`PromoCode` 沒有），併發下**不會超賣**，而是 10 筆請求只有 2 筆寫得進去、其餘 8 筆變成技術性例外——買家看到 500 且庫存賣不完、取消的庫存還不回去、付款後的扣帳被靜默吞掉。

---

## 1. 缺口盤點結果

### 起點

承接 Sprint 102 修復 DEF-046 後、依 Action Item 做同類競態橫向掃描時命中的 **DEF-050**，於 `DEFERRED_ITEMS_TRACKER.md` 列為 🔴 高優先級並建議排入本 Sprint。

### 🔴 最重要的發現：DEF-050 的原始定性是錯的

DEF-050 被記錄為「與 DEF-046 **完全同型**的讀後寫，併發下單搶同一 SKU 最後一件會雙雙通過檢查而**超賣實體商品**，且兩次 `save()` 互相覆蓋使 `reservedQty` 少算」，並據此評為「**嚴重性高於 DEF-046**」。

這個定性建立在一個沒有被查證的假設上：兩者結構相同。實際上有一個決定性差異——

| | `PromoCode`（DEF-046） | `ProductInventory`（DEF-050） |
|---|---|---|
| `@Version` 樂觀鎖 | ❌ 無 | ✅ **有**（entity 與 `V1__Initial_Schema.sql` 的 `version BIGINT` 皆存在） |
| 併發寫入結果 | 全部成功、互相覆蓋 | 一筆成功、其餘 `ObjectOptimisticLockingFailureException` |

全庫僅 `Inventory` 與 `ProductInventory` 兩個 entity 有 `@Version`。因此 DEF-046 的「超發 + lost update」在此**不成立**。

### 紅燈實測（對修復前的實作實跑，非推論）

`M12InventoryConcurrencyIntegrationTest` 對未修復的實作跑出的結果，三個方法**完全一致**：

```
RaceResult[granted=2, insufficientStock=0, unexpectedFailures={ObjectOptimisticLockingFailureException=8}]
```

Hibernate 的 SQL log 直接證實機制：`update product_inventory set ... where sku_id=? and version=?`。

| 方法 | 實測失效模式 | 實際後果 |
|------|-------------|---------|
| `reserveForOrder` | 10 張訂單搶 3 件庫存 → 只有 **2 張**成功，8 張拿到樂觀鎖例外（`insufficientStock=0`，沒有任何一張是因為庫存真的不足） | **零超賣**，但 3 件庫存賣不完（少賣 1 件），且 8 位買家看到的是 500 而非「庫存不足」 |
| `releaseForOrder` | 10 筆取消 → 只有 **2 筆**釋放成功 | `reserved_qty` 殘留 8，那批貨**從此賣不出去**（呼叫端 `OrderService` 不捕捉此例外，取消流程本身已完成，故靜默失效） |
| `deductForOrder` | 10 筆付款扣帳 → 只有 **2 筆**成功 | 失敗被 `PaymentStateService.deductStockSafely` 的 try/catch **完全靜默吞掉**——付款成立、`total_qty` 沒扣，該 SKU 帳實不符且**持續可賣** |

### 修正後的嚴重性評估

DEF-050 記錄的「嚴重性高於 DEF-046（超發實體商品）」**不成立**——沒有直接超賣。但它**仍應維持高優先級**，理由與原記錄不同：

1. **`deductForOrder` 是真正通往超賣的路徑，只是間接且延遲**：付款成功而庫存未扣，該 SKU 的 `total_qty` 永遠不減，同一件實體貨可以被賣無限次。這比「一次超賣一件」更難察覺——沒有任何例外、任何 log、任何失敗訂單。
2. **`releaseForOrder` 造成庫存被永久鎖死**：商家的貨還在，系統卻認為已被預扣。
3. **`reserveForOrder` 讓買家在尖峰時段拿到 500**：這是唯一會被使用者投訴、因而最可能先被發現的一段，但也是三者中損害最小的。

**方法論教訓**：DEF-050 是在 Sprint 102 修復 DEF-046 後的橫向掃描中，靠「結構同型」的樣式比對找到的。樣式比對**找對了位置**（這三段確實都是讀後寫、確實都是缺陷），但**推錯了後果**——因為它只比對了程式碼形狀，沒有查證 entity 的併發防護。已寫入記憶：**橫向掃描命中的缺口，其嚴重性定性在紅燈實測前一律視為未確認**。

---

## 2. 技術決策

| 決策點 | 選項 | 裁定與理由 |
|--------|------|------------|
| 修法 | (A) 條件式／相對 UPDATE（複用 S102）/ (B) 保留樂觀鎖 + 加重試 / (C) 悲觀鎖 `SELECT ... FOR UPDATE` | **(A)**。(B) 需要在每個呼叫端包重試迴圈，且重試次數是新的可調參數（調太少仍失敗、調太多變成忙等），治標不治本；(C) 多一次往返而效果與 (A) 相同。(A) 同時把「可售量不足」從技術性例外變回語意明確的 `E-3004`，一併修掉 UX 缺陷 |
| 是否移除 `@Version` | 移除 / 保留 | **保留**。M16 ERP 的進貨入庫與庫存異動（`StockMovementService`、`PurchaseOrderService`）仍走 JPA `save()`，樂觀鎖是它們目前唯一的併發防護。移除等於在修一個缺陷的同時拆掉另一條路徑的護欄 |
| 原生 UPDATE 是否推進 `version` | 推進 / 不動 | **必須推進**（三條敘述都寫了 `version = version + 1`）。若不推進，ERP 端會拿著過期快照通過樂觀鎖檢查，把訂單流程剛寫入的數量**整列覆蓋**——那才會做出真正的 lost update。這是本輪最容易被忽略的一個副作用 |
| `@Modifying` 是否加 `clearAutomatically` | 加 / 不加 | **不加**（僅 `flushAutomatically = true`，與 S102 一致）。`reserveForOrder` 在 `createOrderFromCart` 中於 `orderRepository.save(order)` **之前**被呼叫，清空 persistence context 會把該交易稍早載入的 `Listing`／`ProductSku`／`User` 一併 detach，後續 lazy loading 即爆 |
| 是否一併處理 ERP 側的同型讀後寫 | 一併 / 不動 | **不動**（Rule 3）。`StockMovementService`／`PurchaseOrderService` 屬 M16 後台情境（併發特性、業務語意、失敗處理都不同），需要自己的紅燈驗證。已記錄為 DEF-051 |
| 是否修正 `deductStockSafely` 的靜默吞例外 | 修 / 不動 | **不動**（Rule 3），但已在 Repository javadoc 標註其放大效果。修好本輪之後，該路徑不再會有樂觀鎖失敗可吞；「付款成功後庫存扣帳失敗該如何補償」是獨立的業務決策（與 DEF-044 退款回補同一類問題），不在本輪範圍 |

**未召開 AskUserQuestion**：本輪沒有需要業務權衡的取捨——修法零容忍度、不新增基礎設施、不改變任何對外契約語意（`E-3004` 是既有錯誤碼，只是從「幾乎不會觸發」變成「正確觸發」）。沿用 S102 的判準：沒有可讓使用者權衡的選項時，不佔用其決策成本。

---

## 3. 實作內容

### Repository 層（`ProductInventoryRepository`）

新增三支原生 `@Modifying` 查詢，把判斷與運算全部下沉到單一敘述：

```sql
-- 預扣：可售量檢查與累加在同一條敘述內
UPDATE product_inventory
   SET reserved_qty = reserved_qty + :quantity,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE sku_id = :skuId
   AND total_qty - reserved_qty >= :quantity

-- 釋放：相對遞減，GREATEST 保下限
UPDATE product_inventory
   SET reserved_qty = GREATEST(reserved_qty - :quantity, 0), version = version + 1, ...
 WHERE sku_id = :skuId

-- 扣帳：total 無條件遞減、reserved 帶下限，語意與原 deductStock() 一致
UPDATE product_inventory
   SET total_qty = total_qty - :quantity,
       reserved_qty = GREATEST(reserved_qty - :quantity, 0), version = version + 1, ...
 WHERE sku_id = :skuId
```

### Service 層（`ProductInventoryService`）

`reserveForOrder` 的 0 筆回傳有**兩種**意義，必須區分：

```java
// 0 筆 = 可售量不足（要擋）或該 SKU 沒有庫存列（未啟用追蹤，既有語意為略過）
if (productInventoryRepository.reserveIfAvailable(skuId, item.getQuantity()) == 0
        && productInventoryRepository.existsById(skuId)) {
    throw new BusinessException(ErrorCode.E_3004, "Insufficient stock for SKU: " + skuId);
}
```

`existsById` 只在失敗路徑上多付一次查詢，成功路徑維持單次往返。`releaseForOrder`／`deductForOrder` 的 0 筆只有一種意義（無庫存列），沿用既有的「略過」語意。

### Entity 層（`ProductInventory`）

`hasAvailableStock()` / `reserve()` / `release()` **移除**——它們在全庫只有 `ProductInventoryService` 一個呼叫端，改用原子 UPDATE 後即成零呼叫死碼。刻意不保留：留著等於留一個讓人無徵兆退回讀後寫的入口（與 S101 移除 `computeDiscount` 舊多載、S102 移除 `incrementUsageCount` 同一個「大聲失敗」理由）。原處留下說明註解。

`addStock()` / `deductStock()` **保留**——M16 ERP 仍在使用。

---

## 4. 測試

### `M12InventoryConcurrencyIntegrationTest`（新增，7 個；真實 PostgreSQL + 多執行緒）

**為什麼一定要真實 DB**：既有的 `ProductInventoryServiceTest` 6 個案例**全部** mock 掉 Repository，在單執行緒中依序回放 stub——窗口根本不存在，所以這個缺陷從 Sprint 88 起就一直對測試隱形。這正是 Sprint 97 記取的「所有相關測試都用固件繞過同一段邏輯」教訓的又一個實例。

| # | 案例 | 驗證意圖 | 修復前 |
|---|------|---------|--------|
| 1 | 10 張訂單搶庫存 3 → 恰好 3 張成功、7 張收到 E-3004、DB `reserved_qty` = 3 | 不超賣、**賣得完**、失敗語意正確 | ❌ granted=2、8 個樂觀鎖例外 |
| 2 | 庫存已全數預扣 → 併發全被擋，不被推過總量 | 邊界 | ✅ 通過（讀到不足即拋，無寫入可競） |
| 3 | SKU 無庫存列 → 併發全數放行 | 未啟用追蹤的向下相容語意不得被修法改掉 | ✅ 通過（無寫入可競） |
| 4 | 同一張訂單第二項不足 → 第一項的預扣隨交易回滾 | 原生 UPDATE 同樣受交易保護，不留孤兒預扣 | ✅ 通過 |
| 5 | 10 筆取消併發釋放 → `reserved_qty` 精準歸零 | 釋放不得被覆蓋／擋掉 | ❌ granted=2、殘留 8 |
| 6 | 10 筆付款併發扣帳 → `total_qty` 與 `reserved_qty` 同步歸零 | 扣帳不得靜默漏失 | ❌ granted=2 |
| 7 | `reserved_qty` = 0 時釋放 → 不得成為負數 | 下限保護（已下沉為 SQL `GREATEST`） | ✅ 通過 |

案例 2/3/4/7 是守衛型斷言，在舊實作下本來就通過，**如實記錄**，不計入紅燈範圍（沿用 S101/S102 的紀律）。

測試資料以 JPA builder 設**關聯物件**（`.tenant(...)` / `.owner(...)`）而非影子欄位建 tenant → user → listing 鏈；`product_skus` 與 `product_inventory` 走 JdbcTemplate。第一次嘗試用 raw SQL 建 tenant 立即撞上 `connect_charges_enabled` 的 NOT NULL（`tenants` 有多個帶 NOT NULL 的旗標欄位），改回 JPA 後即通過——手寫 INSERT 要逐欄追 schema，交給 JPA 更穩。

### `ProductInventoryServiceTest`（改寫，維持 6 個）

6 個案例全數改寫（非新增）。數量的實際變化已下沉到 SQL，mock 掉 Repository 的測試無從觀察，硬要驗只會變成「用固件回放自己寫的 stub」；本檔改為驗證**派送行為**：哪些品項被略過、原子敘述收到什麼參數、0 筆如何被解讀成「不足」或「未追蹤」。

保留並強化 `never()).save(...)` / `never()).findById(...)` 斷言，作為「不得退回讀後寫」的守衛——這是 DEF-050 的核心意圖。

---

## 5. 驗證結果

### 開發-編譯-測試循環

依 CLAUDE.md 強制規則逐步執行：測試先行（紅燈實測）→ Repository 改完即 `mvn -o compile`（通過）→ Service 改完即編譯 → Entity 移除死碼即編譯 → 單元測試改完即跑（6 全綠）→ 併發整合測試單獨跑（7 全綠）→ 全量回歸。無累積開發。

### 紅燈 → 綠燈

| 階段 | 結果 |
|------|------|
| 紅燈（修復前，實跑） | **7 個案例中 3 個失敗**，三者皆為 `granted=2 / unexpectedFailures={ObjectOptimisticLockingFailureException=8}` |
| 綠燈（修復後） | **7 / 7 通過**（31.80 s） |

### 全量回歸

`cd backend && mvn -o verify`（2026-09-01）：

| 項目 | 結果 |
|------|------|
| 單元測試（surefire） | **1074 tests, 0 failures, 0 errors, 0 skipped** |
| 整合測試（failsafe） | **404 tests, 0 failures, 0 errors, 0 skipped** |
| 合計 | **1478 tests, 0 fail** |
| checkstyle-main / checkstyle-test | 皆 0 違規 |
| BUILD | **SUCCESS**（32:54 min） |

**兩個數字互相交叉驗證了本輪的測試計數**：單元測試 **1074，與 Sprint 102 完全相同**——本輪 6 個單元測試是改寫而非新增，淨變化應為 ±0，實測相符；整合測試 397 → **404**，恰為新增的 7 個。這是繼 S101→S102 出現「文件記載 1068、實際基準應為 1069」的差 1 之後，第一次兩邊計數都對得上。

### 測試計數（以 `@Test` 實數核對）

| 檔案 | HEAD | 本輪 | 淨變化 |
|------|------|------|--------|
| `ProductInventoryServiceTest` | 6 | 6 | **0**（6 個全部改寫，非新增） |
| `M12InventoryConcurrencyIntegrationTest` | — | 7 | +7（新建） |
| **合計** | | | **淨 +7**（單元 ±0、整合 +7） |

---

## 6. 範圍外（延後）

- 🟡 **DEF-051（新記錄）**：M16 ERP 的 `StockMovementService.createManualMovement` 與 `PurchaseOrderService.createInboundMovement` 對**同一張 `product_inventory` 表**仍是「載入 → `addStock()`/`deductStock()` → `save()`」的讀後寫，只靠 `@Version` 保護，併發下同樣會拋 `ObjectOptimisticLockingFailureException`。本輪的原子 UPDATE 已確保訂單流程不會與 ERP 互相覆蓋（version 有推進），故 ERP 側**沒有資料正確性風險**，只有「後台併發操作偶發失敗」的可用性問題——ERP 後台的併發程度遠低於買家結帳，優先級判為 🟡。依 Rule 3 不順手擴大範圍
- **`PaymentStateService.deductStockSafely` 靜默吞例外**：本輪修好後該路徑不再會有樂觀鎖失敗可吞，但「付款成功後庫存扣帳失敗該如何補償」仍是未定義的業務問題（與 DEF-044 退款回補同類）。已在 javadoc 標註，未改行為
- **DEF-044（退款不回補庫存）**、**DEF-043**、**DEF-047**、**DEF-048**：維持延後，本輪未動
- **DEF-049（整合測試執行時間）**：本輪未動，但**又加了 1 個整合測試類別（+7 個測試）**，即再付一次 context 冷啟動（本機實測該類別 31.8 秒，其中約 29 秒是啟動）
- **前端無需改動（已確認，非略過）**：`E-3004`「庫存不足」是既有錯誤碼，`checkout/product/page.tsx` 的 `extractErrorMessage` 直接顯示 `response.data.message`。本輪只是讓它從「幾乎不會觸發」變成「正確觸發」，契約未變

---

## 7. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S100 | 8 |
| S101 | 5 |
| S102 | 3 |
| **S103** | **3**（1 個競態修復涵蓋預扣／釋放／扣帳三條路徑 + 7 個併發整合測試 + 6 個單元測試改寫；估點與 DEF-050 記錄的 3 SP 相符） |

---

## 8. 下一步 / Action Items

### 8.1 上輪（Sprint 102）Action Items 追蹤

| Sprint 102 列的項目 | 本輪結果 |
|---------------------|---------|
| 1. 🔴 DEF-050（庫存競態） | ✅ **本輪完成**，並在過程中**更正了它的失效模式與嚴重性定性** |
| 2. DEF-049（整合測試執行時間） | ⏳ 未動，續列。本輪又加 1 個類別（+7 測試），且 S102 已確認**第一支槓桿是 `reuseForks` 而非收斂 context** |
| 3. DEF-047（ROOM 訂單套券） | ⏳ 未動，續列 |
| 4. DEF-048（混合購物車折扣基數） | ⏳ 未動，續列（需產品確認） |
| 5. 橫向掃描的剩餘範圍 | ⏳ 部分推進：本輪確認 ERP 側同型（記為 DEF-051）。尚未掃 `SettlementAdjustment`、`MediaAsset.incrementUsageCount`、`ReviewService.markHelpful` |
| 6. 第九輪 PRD 全文掃描 | ⏳ 未動，續列 |

### 8.2 本輪產出的 Action Items

| # | 項目 | 說明 |
|---|------|------|
| 1 | **橫向掃描剩餘的計數／額度型欄位** | 本輪證明樣式比對**會找對位置但可能推錯後果**。剩餘目標：`SettlementAdjustment`、`MediaAsset.incrementUsageCount`、`ReviewService.markHelpful`。掃描時應一併確認該 entity 有無 `@Version`（全庫只有 `Inventory` / `ProductInventory` 有），以先行判斷失效模式 |
| 2 | DEF-049（整合測試執行時間） | 連續三輪列為候選。第一步已明確：確認 `reuseForks=false` 當初的「SecurityContext 汙染」顧慮是否仍成立 |
| 3 | 🟡 DEF-051（ERP 側庫存讀後寫） | 修法與本輪完全相同，但需 ERP 情境自己的紅燈驗證 |
| 4 | 「付款後庫存扣帳失敗」的補償策略 | 與 DEF-044（退款回補）合併思考，需業務決策 |
| 5 | DEF-047 / DEF-048 / 第九輪 PRD 掃描 | 續列 |

---

**文件版本**: v1.0｜**建立者**: Claude Code（AISDLC v0.09 Sprint Planning）｜**基於**: DEF-050（Sprint 102 橫向掃描命中，列為 Sprint 103 首要項目）
