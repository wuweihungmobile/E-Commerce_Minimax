# Sprint 116 Plan — DEF-066：ERP 庫存台帳讀的是一張永遠空白的表

**Sprint**: Sprint 116
**日期**: 2026-09-03
**AI 編號**: AI-2450
**主題**: Sprint 115 查證 DEF-064 時發現的 P0 缺口。1 US，5 SP。

---

## 1. 缺陷

系統有**兩張庫存表**：

| 表 | 誰寫入 | 誰讀取 |
|---|---|---|
| `product_inventory` | 訂單預扣／扣帳／釋放、ERP 手動異動、採購收貨——**真正的庫存數字全在這** | 訂單流程 |
| `inventory` | **沒有任何生產程式碼寫入**（只有測試固件自己 INSERT） | ERP 庫存列表／明細／低庫存預警、賣場商品卡 |

也就是說，店家在 ERP 後台看到的庫存台帳，讀的是一張沒有人會寫入的表。生產環境上它永遠是空的
——庫存列表空白、庫存明細查不到、低庫存預警永遠不會響。而 PRD §6.7.2 把「庫存台帳：按 SKU 查看
即時庫存、庫存異動記錄」列為 **P0**。

`inventory` 表的來歷寫在 `V50` 的檔頭註解裡，而且它自己講得很清楚：

> 問題: Inventory / InventoryCheck entity 存在，但沒有對應的建表 migration，
> 導致 ddl-auto=validate 時 Hibernate schema 驗證失敗（missing table）。

**它是為了讓 schema 驗證過關而補建的空殼，不是為了存資料。**

### 1.1 為什麼一直沒被發現

既有的 M16 整合測試**同時種兩張表**：`product_inventory`（給庫存異動端點用）與 `inventory`
（給台帳端點用）。要種兩張表才測得起來，本身就是「讀的和寫的不是同一張」的徵兆，
但測試因此全綠——S97「所有相關測試都用固件繞過同一段邏輯」的又一個實例。

更糟的是那兩個台帳案例的斷言：

```java
// IT-M16-201
.andExpect(jsonPath("$.data.content").isArray());   // 空陣列也是陣列
// IT-M16-204
.andExpect(jsonPath("$.success").value(true));       // 空預警清單也是 success
```

**即使把固件那筆 `inventory` 拿掉，這兩個案例仍然會綠。** 承 S114 的 `anyOf(400, 500)`：
斷言鬆到不可能紅，也就不帶任何資訊。

### 1.2 買家端（潛在）

`ListingCardService.buildProductCard` 也讀這張空表，後端恆回 `inStock=false`／`availableQty=null`。
前端型別宣告了這兩個欄位但目前沒有渲染，故**尚未**對買家可見——屬潛在影響。

---

## 2. 不只是讀錯表：欄位名也對不上

即使表裡有資料，ERP 庫存列表**每一欄仍然會是空的**：

| 前端讀 | 後端 `InventoryLedgerDto` | 結果 |
|---|---|---|
| `quantity`／`reservedQuantity`／`availableQuantity` | `totalQty`／`reservedQty`／`availableQty` | undefined |
| `skuCode`／`productName`／`location` | 有欄位，但 `toLedgerDto()` 從未填值 | 空白 |

低庫存預警同理（`currentQty` vs `currentQuantity`；前端還讀 `reorderPoint`／`safetyStock` 兩個
後端根本不回的欄位）。

**方向判斷**（承 S114 的方法：數證據，不投票）：後端**自己的** `InventoryService.InventoryDetailDto`
用的是 `quantity`／`reservedQuantity`／`availableQuantity`，與前端一致。三者之中只有
`InventoryLedgerDto` 與 `LowStockAlertDto` 是例外，故往多數對齊。

---

## 3. 紅燈先行

新增 `M16ErpInventoryLedgerIntegrationTest`（真實 PostgreSQL），**刻意只寫 `product_inventory`**
——這是它與既有 M16 測試唯一但決定性的差別。

因為修復必然要改 DTO 欄位名，新測試無法對 HEAD 編譯，故改以**暫時把資料來源換回舊表**
（保留新欄位名）的混合版取得紅燈：**6 案例 5 failed + 1 error，台帳回傳 `[]`**。修復後 6 passed。

---

## 4. 修法

1. **四個讀取點全部改讀 `product_inventory`**：`getInventoryLedger`／`getInventoryBySku`／
   `getLowStockAlerts`／`ListingCardService.buildProductCard`。
2. **投影查詢**：`product_inventory` 只有數量，SKU 編號在 `product_skus`、品名在 `listings`，
   租戶歸屬也只能經由 listing 取得（該表沒有 `tenant_id`）。用 `InventoryLedgerRow` 投影一次撈齊。
3. **DTO 欄位對齊**前端與 `InventoryDetailDto`，並移除沒有資料來源的欄位
   （`location`／`reorderPoint`／`safetyStock`）——留著只會是永遠顯示「-」的欄位，
   那正是這個缺陷得以長期存活的土壤。
4. **最近進出庫時間由 `stock_movements` 推導**。Sprint 115 補齊訂單流水帳之後，這件事才有意義。
5. **移除孤兒**：`Inventory` entity、`InventoryRepository`、以及 `inventory` 表（`V72`）。
   留下一張名字看起來就是「庫存」的空表，下一個人同樣會理所當然地讀它。

### 4.1 異動方向抽為單一事實來源

台帳要以 SQL 算「最近一次入庫」，需要一份入庫型別清單。若在查詢裡另寫一份，就會多出一組
會各自演化的重複定義——**DEF-063 正是這樣來的**。故把 `INBOUND_TYPES`／`OUTBOUND_TYPES`
抽成 `StockMovement` 的常數，`isInbound()`／`isOutbound()` 與台帳查詢都由它推導，
型別名稱以查詢參數傳入。

### 4.2 🔴 可售量刻意自行相減，不讀 `available_qty`

`product_inventory.available_qty` 在 Flyway schema 是
`GENERATED ALWAYS AS (total_qty - reserved_qty) STORED`，但**整合測試的資料庫由 ddl-auto 建立**，
同名欄位只是普通可空欄位、且因實體標了 `insertable=false` 而**永遠為 NULL**。

第一版實作讀了它，結果測試全部回空。靠它會得到「生產正確、測試全空」
——正是本 Sprint 在修的同一類陷阱，差點自己再引入一次。改為在 SQL 直接 `total_qty - reserved_qty`。

---

## 5. 連帶修掉的三個同族問題

| 問題 | 說明 |
|---|---|
| `IT-M16-201`／`204` 的空斷言 | 只驗 `isArray()`／`success`，空台帳照樣通過。改為斷言內容（數量、SKU 編號、`severity`），並讓固件自給自足 |
| 庫存頁「狀態」欄是寫死的 | `<Badge variant="success">正常</Badge>`——`getStockStatus` 定義了卻**從未被呼叫**，庫存見底時畫面照樣顯示正常 |
| 低庫存預警卡顯示兩種門檻 | `product_inventory` 只有單一門檻，硬填兩欄會讓畫面看起來有兩種標準。改為顯示門檻＋嚴重度 |

### 5.1 `IT-M16-204` 的固件陷阱

修復時一度改用 `UPDATE product_inventory ... WHERE sku_id = testSkuId`，結果預警清單仍是空的。
原因：`testSkuId` 是 **static** 欄位，會被順序較早的 `@Transactional` 測試重新指派，
而那些測試回滾後該 SKU 的列已不存在。改為每個案例自己種 SKU。

---

## 6. 驗證

| # | 內容 | 結果 |
|---|------|------|
| ① | 新測試 × 舊資料來源（暫時還原） | **5 failed / 1 error**，台帳回傳 `[]` |
| ② | 新測試 × 已修復程式碼 | **6 passed** |
| ③ | `M16ErpIntegrationTest` | **37 passed**（含收緊後的 201／202／204） |
| ④ | `M16ErpE2ETest`／`M16ErpInventoryConcurrencyIntegrationTest` | **7／5 passed** |
| ⑤ | `M12OrderStockLedgerIntegrationTest`（S115） | **9 passed** |
| ⑥ | `InventoryServiceTest`（改寫） | **6 passed** |
| ⑦ | `make validate-schema-doc` | 72 個遷移，文件無漂移 |
| ⑧ | 前端 `tsc --noEmit`／`eslint` | 0 error |
| ⑨ | 後端全量回歸 `mvn -o verify` | 見下方守門段落 |

---

## 7. 🔴 工具鏈風險：Maven 編譯失敗後，下一次編譯會回報假成功

本輪實測到一個會影響整個開發循環的問題：

```
$ mvn -o compile          # 真的編不過
[ERROR] cannot find symbol: totalQty(...)
[INFO] BUILD FAILURE

$ mvn -o compile          # 一字未改，再跑一次
[INFO] Nothing to compile - all classes are up to date
[INFO] BUILD SUCCESS      # ← 假的
```

原因是編譯失敗仍會更新 `target/maven-status/maven-compiler-plugin/.../inputFiles.lst`，
下一次的增量判定據此認為「全部已編譯」。**CLAUDE.md 的核心規則是「編譯成功才繼續」，
而這個假成功正好讓壞程式碼溜過那道關卡。**

**處置**：任何一次編譯失敗之後，下一次編譯前先 `rm -rf backend/target/maven-status`（或 `mvn clean`）。
本輪之後的每一次編譯都照此執行。

另附一個相關觀察：編譯錯誤會讓 Lombok 的 `@Slf4j` 來不及產生 `log` 欄位，於是**未被修改的檔案**
也跟著噴 `cannot find symbol: variable log`。那些是連鎖假錯，修掉真正的第一個錯誤即消失
——不要跟著去改那些檔案。

---

## 8. 範圍外

- **`inventory_checks` 表與 `InventoryCheck` entity**：同樣是孤兒（庫存盤點為 PRD §6.7.2 **P1**，
  尚未實作，無任何生產寫入）。但 entity 仍存在，刪表會讓 ddl-auto=validate 失敗，
  且它是尚未實作功能的骨架而非錯誤來源。已另記為追蹤項目。
- **儲位／補貨點／安全庫存**：`product_inventory` 沒有這些概念。要支援需要新增欄位與維護 UI，
  屬新功能而非本輪的缺陷修復（Rule 3）。
