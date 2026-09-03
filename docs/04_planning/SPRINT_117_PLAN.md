# Sprint 117 Plan — DEF-064：ERP 異動列表三欄空白，參考單號被靜默丟棄

**Sprint**: Sprint 117
**日期**: 2026-09-03
**AI 編號**: AI-2451
**主題**: 使用者拍板選項 C。1 US，3 SP。

---

## 1. 缺陷

`/dashboard/erp/stock-movements` 的表格有「SKU／品名／參考單號」三欄，永遠顯示 `-`；
新增異動表單的「參考單號」輸入框，打了字送出後**什麼也沒發生，而且沒有任何錯誤**。

三個各自獨立的成因：

| # | 成因 |
|---|------|
| ① | `StockMovementDto` 有 `skuCode`／`productName`，但 `toDto()` **從來沒填過值**——那兩個值不在 `StockMovement` 實體上（SKU 編號在 `product_skus`、品名在 `listings`） |
| ② | `StockMovementRequest`（後端）**根本沒有 `referenceNumber` 欄位**，前端送的那格被 Jackson 靜默忽略 |
| ③ | 「參考單號」這個概念在後端不存在——只有 `reference_id`（採購單或訂單的內部 UUID） |

## 2. 拍板：選項 C——兩件事分成兩欄

使用者在三個選項（只修顯示／只修輸入／兩者都做）中選了 **C**。這也是唯一講得通的：

- **來源單據**（系統推導、唯讀）：這筆異動是哪張單據造成的
- **參考單號**（店家自填、可存）：操作者自己記的單號，如「盤點單 2026-09」

共用一欄會讓「這個單號是誰產生的」永遠說不清楚——採購收貨的 `PO-2026-0042` 與店家手寫的
「盤點單」擠在同一格，看的人無從分辨。

---

## 3. 紅燈先行

新增 `M16ErpStockMovementDisplayIntegrationTest`（真實 PostgreSQL）：**6 案例全部 failed**
（`skuCode` 為 null、參考單號未被存下、來源單據不存在）。修復後 6 passed。

其中兩個案例守的是「兩者不得混為一談」：
- 手動異動的 `sourceDocument` 必須是 **null**，店家自填的單號不得被當成來源單據
- 採購收貨的 `sourceDocument` 必須是採購單號，訂單異動則是訂單 id 前八碼

---

## 4. 修法

1. **`V73`**：`stock_movements` 新增 `reference_number VARCHAR(100)`。
2. **投影查詢** `StockMovementRow`：一次 JOIN 齊 SKU 編號（`product_skus`）、品名（`listings`）、
   來源單據（`purchase_orders.po_number`／訂單 id 前八碼），避免列表每列都往回查。
   三個查詢方法（列表分頁／依 SKU／依時間範圍）全部改走投影。
3. **`InventoryService` 委派**給 `StockMovementService.getMovementsBySku`：
   修復前它自己有一份重複的 `toMovementDto`，而那份同樣沒填 SKU 編號與品名
   ——庫存明細頁的異動記錄也缺欄位。移除重複的映射。
4. **前端**：列表新增「來源單據」欄（前綴由 `referenceType` 決定，後端只回單據識別、不回中文標籤，
   顯示用語留在前端）；表單的參考單號現在真的送得出去，提示文字也改成不會誤導的例子。

### 4.1 訂單為什麼是「前八碼」

訂單沒有人看得懂的單號（只有 UUID）。前端既有慣例是 `訂單 #{order.id.slice(0, 8)}`
（見 `orders/[id]/page.tsx`、`bookings/page.tsx`），故沿用同一個縮寫長度，讓兩處看到的是同一組字。

### 4.2 SQL 裡不用 `::text`

來源單據的縮寫用 `CAST(m.reference_id AS text)` 而非 PostgreSQL 的 `::text`：
原生查詢裡的 `::` 會讓 Hibernate 把後面的字當成具名參數（承既有的 `::jsonb` 教訓）。

### 4.3 SKU 與 listing 一律 LEFT JOIN

異動記錄不該因為商品被下架或資料異常而**整列從台帳消失**——台帳的職責是把發生過的事留著。

---

## 5. 中途改掉的一個設計

第一版讓 `createManualMovement` 在存檔後**回讀投影**來組回傳值，理由是「建立後回傳的那筆要與列表
查到的同一筆欄位一致」。結果每個單元測試都得 stub 那次回讀，還撞上 Mockito 的
`UnfinishedStubbingException`（在 `thenAnswer` 的 lambda 裡又去 stub 另一個 mock）。

停下來看了一下，發現這是**設計問題不是測試問題**：SKU 與 listing 在 `createManualMovement`
開頭就已經載入了（庫存查詢一次、租戶檢查一次），直接拿來填就好——
**零額外查詢，測試也不必改**。回讀方案連同那支查詢一起移除。

> 教訓：當一個改動讓「每個測試都要多 stub 一件事」時，先懷疑那個改動，而不是先去改測試。

---

## 6. 文件守門攔下了新欄位

`make validate-schema-doc` 對 `reference_number` 報漂移，並指出兩件事要辦：
SRD 的 DDL 用 `make sync-schema-doc` 重新產生（不可手改），PRD §8.2 需**人工**補欄位與中文說明
（自動產生會失去語意）。兩者都照辦，PRD 的說明特別寫清楚它與 `reference_id` 的分工。

---

## 7. 驗證

| # | 內容 | 結果 |
|---|------|------|
| ① | 新測試 × 未修復程式碼 | **6 failed** |
| ② | 新測試 × 已修復程式碼 | **6 passed** |
| ③ | `StockMovementServiceTest` | **24 passed** |
| ④ | `InventoryServiceTest`／`PurchaseOrderServiceTest` | **6／25 passed** |
| ⑤ | `make validate-schema-doc` | 73 個遷移，PRD §8.2 與 SRD DDL 皆一致 |
| ⑥ | 前端 `tsc --noEmit`／`eslint` | 0 error |
| ⑦ | 後端全量回歸 `mvn -o verify` | **單元 1034 / 整合 442，0 失敗** |

---

## 8. 範圍外

- **`sourceDocument` 對 `STOCKTAKE` 型別**：PRD §8.2 的 `reference_type` 列了 `STOCKTAKE`，
  但 `ReferenceType` 枚舉裡沒有、也沒有任何程式碼產生它（庫存盤點尚未實作，見 DEF-067）。
  本輪不為一個不存在的型別預先寫推導邏輯。
- **備註（`notes`）**：本來就正常運作，未動。
