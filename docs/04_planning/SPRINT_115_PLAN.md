# Sprint 115 Plan — DEF-065：訂單流程補上庫存流水帳（PRD §6.7.3 的 P0 缺口）

**Sprint**: Sprint 115
**日期**: 2026-09-03
**AI 編號**: AI-2449
**主題**: 承 Sprint 114 查證中發現的 DEF-065。1 US，5 SP。

---

## 1. 為什麼挑這一項

追蹤表 🔴 高優先級為空，🟡 中優先級四項：

| 項目 | 判斷 |
|------|------|
| **DEF-065 訂單流程不寫流水帳** | **本輪**。PRD §6.7.3 列為 P0 的功能實際缺失，且**四項中唯一不需要任何產品決策**——PRD 已明訂三個寫入點與各自的方向 |
| DEF-064 列表三欄空白 | 需設計決策（「參考單號」與採購單號／訂單編號的關係） |
| DEF-044 退款回補庫存 | 需業務決策 |
| DEF-021 CJK 字體 | S41 已決策維持現狀 |

---

## 2. 缺口本身

PRD §6.7.3「ERP → C 端連動機制」寫得很明確：

```
C 端扣減流程：
1. 買家下單 → 產生出庫預留 (StockMovement type=RESERVE)
3. 訂單出貨 → StockMovement type=OUTBOUND
取消回滾：
1. 訂單取消 → StockMovement type=RELEASE
```

實際上**全專案只有兩處寫入 `stock_movements`**：`PurchaseOrderService`（採購收貨）與
`StockMovementService`（ERP 手動異動）。S103 改寫的 `ProductInventoryService`——也就是訂單流程
三段式庫存操作的所在——對 `StockMovement` **零引用**：`product_inventory` 的數字會動，
流水帳一筆都不會留。

後果不只是「少了記錄」：

- 庫存台帳（PRD §6.7.3 列為 P0 的「按 SKU 查看即時庫存、庫存異動記錄」）對 C 端的**所有**進出完全沒有資料。ERP 後台看得到採購入庫與手動調整，看不到任何一筆賣出。
- `RESERVE`／`OUTBOUND`／`RELEASE` 三型在資料庫裡**從來沒有出現過**。

第二點正是 Sprint 114 那個缺陷（DEF-063）能存活這麼久的原因：**沒有資料的枚舉值不會有人發現它拼錯了**。
後端把這三型寫成 `RESERVATION`／`SALE`／`RELEASE` 也沒有任何人撞到，因為沒有任何程式碼會寫入它們。

---

## 3. 紅燈先行

新增 `M12OrderStockLedgerIntegrationTest`（真實 PostgreSQL）。對**未修復**的程式碼跑出 **5 failed / 2 passed**：

| 案例 | 未修復時 |
|------|---------|
| 001 建立預扣寫 RESERVE | ❌ `Expected size: 1 but was: 0` |
| 002 付款扣帳寫 OUTBOUND | ❌ 同上 |
| 003 取消釋放寫 RELEASE | ❌ 同上 |
| 004 無庫存列的 SKU 不留流水帳 | ✅ **對照組**——它斷言的是「零筆」，今天當然成立 |
| 005 預扣失敗不留流水帳 | ✅ **對照組**，同上 |
| 006 多品項逐項各一筆 | ❌ |
| 007 未持久化訂單須大聲失敗 | ❌ 沒有守衛 |

兩個通過的是刻意留的對照組（承 S106／S113）：它們證明測試基礎設施本身是好的，
失敗的五個是機制缺失而不是環境問題。

---

## 4. 修法

### 4.1 三段操作各補一筆流水帳

`ProductInventoryService` 注入 `StockMovementRepository`，三段各在**庫存列確實被改動時**寫入：

| 呼叫 | 型別 | totalDelta | reservedDelta |
|------|------|-----------|---------------|
| `reserveForOrder` | `RESERVE` | 0 | `+quantity` |
| `deductForOrder` | `OUTBOUND` | `-quantity` | `-quantity` |
| `releaseForOrder` | `RELEASE` | 0 | `-quantity` |

方向完全依 PRD §6.7.4 的異動類型表。前後數量的取法比照 S113：**after 一律回讀 DB**
（原子 UPDATE 後實體快照已過期），before 由「after − 帶號變化量」反推。
新增 `findReservedQtyBySkuId` 回讀預留量——`before_reserved_qty`／`after_reserved_qty`
兩個欄位自 S113 起就被記為「存在但從未填值」，對 RESERVE／RELEASE 而言，
不填它們等於寫一筆沒有資訊的列（`total_qty` 前後相同）。

**只在受影響筆數 > 0 時寫入**：SKU 無庫存列（未啟用追蹤）時什麼都沒發生，
寫一筆 before/after 皆為 0 的列會讓台帳出現不存在的異動。

### 4.2 🔴 兩個踩到的陷阱

**其一：`Order.tenantId`／`userId` 是 `insertable=false` 的影子欄位。**
`OrderService` 建單時設的是 `.tenant(...)`／`.user(...)`，影子欄位要等實體重新從 DB 載入才有值。
剛建立的訂單 `getTenantId()` 是 **null**，而 `stock_movements.tenant_id` 是 NOT NULL——
用影子欄位會在建單當下直接炸掉。故實作一律走 `order.getTenant().getId()`
（LAZY 代理取 id 不需初始化）。測試固件也刻意**只設關聯物件、不設影子欄位**，
比照生產的物件形狀；若實作改回讀影子欄位，測試會立刻紅。

**其二：預扣必須排在 `orderRepository.save()` 之後。**
修復前 `OrderService` 是「先 `reserveForOrder` 再 `save`」。流水帳的 `reference_id`／`order_item_id`
取自 `Order`／`OrderItem` 的 id，而兩者皆為 `@GeneratedValue`——`save()` 之前都是 null，
在那個時點寫入只會得到一批**查不到來源的孤兒列**。

移到 `save()` 之後不影響超賣防護：兩者同屬一個 `@Transactional` 方法，
`BusinessException` 是 `RuntimeException`，庫存不足時整筆回滾，訂單一樣不會留下——
而且**原本的註解本來就寫「庫存不足拋例外交易回滾」**，保證從來就是回滾提供的，
排序只是那個保證的其中一種實現方式。

為了防止有人改回去，`ProductInventoryService` 加了 `requirePersisted(order)`：
訂單或品項缺 id 就丟 `IllegalStateException`。**大聲失敗**，不靜默寫出壞資料。

---

## 5. 固件差點繞過我自己改的那一段

前七個案例用 raw SQL 種訂單——失敗訊息乾淨，代價是**完全繞過 `OrderService`**，
而本輪修改動到的正是它的呼叫順序。只靠那些案例，這條改動在測試裡一次都沒被真的跑過
（S97「固件繞過同一段邏輯」的同型陷阱，這次差點自己踩進去）。

補上兩個走真實 `createOrderFromCart` 的案例（購物車走 Redis、走完整建單流程）：

- **008**：斷言流水帳的 `reference_id` 等於回傳的訂單 id、`order_item_id` **非 null**——
  這兩欄在 `save()` 之前都是 null，非 null 才證明順序真的對了。
- **009**：庫存不足 → 拋 `E_3004`，且 `orders` 為空、`reserved_qty` 歸零、流水帳無殘留（回滾）。

---

## 6. 既有測試的處置

| 檔案 | 處置 |
|------|------|
| `ProductInventoryServiceTest` | 6 → **9 案例**。建構子多一個依賴；訂單固件補 id 與租戶／買家；三段各加「有沒有寫對型別的流水帳」斷言；新增未持久化訂單的守衛案例。既有的 `never()).save(ProductInventory)` 讀後寫守衛**保留**（那是 `productInventoryRepository`，與流水帳的 `stockMovementRepository` 是不同 mock，不衝突） |
| `M12InventoryConcurrencyIntegrationTest` | 固件改為帶 id 與租戶／買家關聯的訂單。**7 案例全數維持綠燈**——確認在同一交易內多寫一筆流水帳，不影響 S103 建立的原子 UPDATE 併發保證 |
| `OrderServiceTest` | 移除 `verify(orderRepository, never()).save(any())`，並補上 `save` 的 stub（見下） |

### 6.1 為什麼移除那條 `never()).save()`

它驗的是「`reserveForOrder` 排在 `save()` 之前，所以根本沒 save」——是**機制**，不是不變量。
真正的不變量「庫存不足時訂單不留下」現在由交易回滾保證，而 mock 測試看不到回滾。
硬留著只會變成「測試綁死了一個與需求無關的實作順序」。

該不變量改由 `IT-M12-LEDGER-009` 以真實 DB 驗證（`orders` 為空 + `reserved_qty` 歸零 + 流水帳無殘留），
覆蓋比原本更強。單元測試保留仍然成立、且使用者可見的那一條：**購物車不得被清掉**。

另外該案例需要補 `orderRepository.save` 的 stub：預扣移到 save 之後，未 stub 的 mock 回傳 null，
`reserveForOrder(null)` 不匹配 `any(Order.class)`（Mockito 2+ 的 `any(Class)` **不匹配 null**），
測試會死在後續 NPE 而不是預期的 `E_3004`。

---

## 7. 驗證

| # | 內容 | 結果 |
|---|------|------|
| ① | 新測試 × **未修復**程式碼 | **5 failed / 2 passed**（見 §3；2 個通過的是刻意留的對照組） |
| ② | 新測試 × 已修復程式碼 | **9 passed**（含兩個走真實建單路徑的案例） |
| ③ | `ProductInventoryServiceTest` | **9 passed**（6 → 9） |
| ④ | `M12InventoryConcurrencyIntegrationTest` | **7 passed**（固件更新後全綠，DEF-050 保證不受影響） |
| ⑤ | `OrderServiceTest` | **50 passed** |
| ⑥ | 後端全量回歸 `mvn -o verify` | 見下方守門段落 |

---

## 8. 範圍外：本輪查證過但刻意不動的

### 8.1 PRD 說 OUTBOUND 在「出貨」，實作在「付款」

PRD §6.7.3 寫「訂單出貨 → StockMovement type=OUTBOUND」，但本專案自 S88（AI-2422）起採
**deduct-at-payment**：`PaymentStateService` 付款成功時扣帳，沒有獨立的出貨扣帳步驟。

本輪把 `OUTBOUND` 寫在**實際扣帳的時點**，而不是 PRD 字面的「出貨」時點——
流水帳的職責是記錄庫存真正移動的那一刻，把它記在一個系統裡根本不存在的時點上只會讓台帳說謊。
若日後補上獨立的出貨流程，`OUTBOUND` 的寫入點應隨扣帳一起移動。**不改既有的扣帳時點**（Rule 3）。

### 8.2 沒有動的

- **DEF-044（退款回補庫存）**：仍待業務決策。本輪沒有為退款新增任何流水帳型別。
- **`stock_movements` 對 `orders`／`order_items` 沒有 FK**：既有 schema 如此（V1 起），本輪不加。加 FK 要考慮訂單刪除時流水帳該不該連帶消失——那是保存政策問題，不是本輪的缺陷。
- **ERP 側的流水帳查詢／台帳 UI**：`StockMovementService.getMovements` 已能分頁列出本輪新增的列，前端列表在 S114 也已補齊全部型別的 badge。台帳的篩選／彙總是新功能。
