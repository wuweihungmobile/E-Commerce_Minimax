# Sprint 114 Plan — DEF-063：ERP 手動異動的前後端枚舉對齊（外加一個假綠燈）

**Sprint**: Sprint 114
**日期**: 2026-09-03
**AI 編號**: AI-2448
**主題**: 承 Sprint 113 順帶記錄的 DEF-063。1 US，3 SP。

---

## 1. 為什麼挑這一項

追蹤表 🔴 高優先級為空，🟡 中優先級三項：

| 項目 | 判斷 |
|------|------|
| **DEF-063 ERP 異動類型枚舉不一致** | **本輪**。使用者可見、必然發生（下拉預設值送出就爆），且方向已由使用者拍板 |
| DEF-044 退款不回補庫存 | 條目自身寫明「需業務決策」，尚未拍板 |
| DEF-021 CJK 字體 | 純外觀技術債，S41 已決策維持系統字體堆疊 |

DEF-063 是 S113 修 DEF-051 時核對 PRD §6.7.4 順手發現的，當時因「往哪邊對齊是產品決策」而未動手（Rule 3）。

---

## 2. 拍板：改後端對齊 PRD

三個選項都攤開給使用者：(a) 改後端枚舉對齊 PRD（需 migration）、(b) 改前端送後端現有名稱、(c) 加轉換層。
**使用者選 (a)**，另裁示 `RETURN`「保留但明確拒絕手動建立」。

方向本身其實有很強的證據支持——**除了枚舉常數本身，這個功能的每一份文件與程式碼都已經是 PRD 命名**：

| 位置 | 用的是哪一組 |
|------|-------------|
| PRD §6.7.4 異動類型表、PRD 資料表定義（`movement_type ENUM`） | PRD 命名 |
| `StockMovementDto.movementType` 的欄位註解 | `// INBOUND, OUTBOUND, ADJUST_PLUS, ADJUST_MINUS...` |
| `StockMovementRequest.movementType` 的欄位註解 | `// ADJUST_PLUS / ADJUST_MINUS` |
| `StockMovement` 類別自己的 javadoc（列了 9 型與方向） | PRD 命名 |
| 前端 `StockMovementType` | PRD 命名 |
| **`StockMovement.MovementType` 的枚舉常數** | **另一組**（`PURCHASE_RECEIPT`／`SALE`／`RESERVATION`／`ADJUSTMENT`／`DAMAGE`／`THEFT`…） |

換句話說，漂掉的是枚舉常數這一處，連它自己的行內註解都在自我矛盾（`// 報廢 (對應資料庫 THEFT)`、`// 盤虧調整 (對應資料庫 DAMAGE)`——註解寫的是 PRD 語意，常數名是另一回事）。改後端只需動 **2 處生產程式碼**（`StockMovementService` 的 switch 與禁用清單、`PurchaseOrderService` 的 `PURCHASE_RECEIPT`），且 `stock_movements.movement_type` 是純 `VARCHAR(20)`、**沒有 CHECK 約束**，migration 只是一組 UPDATE。

---

## 3. 追蹤表沒記到的一點：光改名字修不好這個缺陷

DEF-063 的條目寫「7 個選項只有 `TRANSFER_IN`／`TRANSFER_OUT` 後端認得」，這是對的；但把枚舉改名之後，**`INBOUND` 與 `OUTBOUND` 這兩個選項仍然會失敗**——因為後端從第一天起就禁止手動建立採購入庫與訂單出貨：

```java
// 修復前就存在的檢查（只是名字叫 PURCHASE_RECEIPT / SALE）
if (movementType == PURCHASE_RECEIPT || movementType == SALE
        || movementType == RESERVATION || movementType == RELEASE) {
    throw new BusinessException(E_7005, "...");
}
```

這道檢查是對的（PRD §6.7.3 明訂那四型由採購單與訂單流程產生），錯的是**下拉選單一開始就不該提供它們**。所以本輪的修復是兩件事，缺一不可：

1. 枚舉命名對齊 PRD（讓 `ADJUST_PLUS`／`ADJUST_MINUS`／`SCRAP` 從「後端不認識」變成可用）
2. 下拉選單只保留後端實際接受的 5 型，預設值由 `INBOUND` 改為 `ADJUST_PLUS`

只做第 1 件，使用者選「入庫」照樣拿到 E_7005——**缺陷會從 5/7 失敗變成 2/7 失敗，而不是修好**。

---

## 4. 修法

### 4.1 枚舉與對照表

`StockMovement.MovementType` 改為 PRD §6.7.4 的 9 型 ＋ `RETURN`。`isInbound()`／`isOutbound()` 同步改名，方向判定內容不變。

`V71__Align_Stock_Movement_Types_To_PRD.sql`：

| 舊值 | 新值 | 依據 |
|------|------|------|
| `PURCHASE_RECEIPT` | `INBOUND` | 舊註解即「採購入庫」 |
| `SALE` | `OUTBOUND` | 舊註解即「訂單出貨」 |
| `RESERVATION` | `RESERVE` | 舊註解即「訂單建立預留」 |
| `ADJUSTMENT` | `ADJUST_PLUS` | 舊註解即「盤盈調整」，且 switch 對它是 `+total_qty` |
| `DAMAGE` | `ADJUST_MINUS` | 舊註解即「盤虧調整」 |
| `THEFT` | `SCRAP` | 舊註解即「報廢」 |
| `RELEASE`／`TRANSFER_IN`／`TRANSFER_OUT`／`RETURN` | 不變 | 名稱本來就相同 |

對照關係全部取自**舊枚舉自己的中文註解**，不是自行推測——這也是為什麼 `THEFT → SCRAP`（而不是照英文字面理解成「失竊」）。

migration 末尾補上 `stock_movements_movement_type_check`（比照 V19 對 `reference_type` 的作法）。加這道約束的理由很直接：**DEF-063 之所以能存活到被使用者撞見，就是因為沒有任何機制擋得住不合法值**——`movement_type` 是裸 `VARCHAR(20)`，兩邊怎麼漂都不會有東西喊。有了 CHECK，下次漂移會在 DB 層失敗，而不是在畫面上。

### 4.2 `RETURN`：從靜默 no-op 改為明確拒絕

修復前 `RETURN` 可以被 `valueOf()` 解析、會寫下一筆 `isInbound()` 為真的流水帳，但 `switch` 落到 `default` → 庫存文風不動。帳面上退了貨、庫存沒回補，**而且沒有任何錯誤**。

本輪不給它庫存語意（那正是 DEF-044 要拍板的事），改為在寫入前明確拒絕，錯誤訊息指向 DEF-044。單元測試同時斷言拒絕發生在寫入之前：

```java
verify(stockMovementRepository, never()).save(any(StockMovement.class));
verify(productInventoryRepository, never()).increaseTotalQty(any(UUID.class), anyInt());
```

沒有這兩條，「拒絕」有可能只是把靜默 no-op 換個地方留著。

### 4.3 前端

- `MANUAL_MOVEMENT_TYPES`（5 型）獨立出來，`StockMovementRequest.movementType` 改用這個窄型別——**送不出後端不接受的值變成型別錯誤**，而不是執行期 400。
- 下拉只列這 5 型，預設 `ADJUST_PLUS`，並加一行說明「採購入庫與訂單出貨由採購單、訂單流程自動產生」。
- `StockMovementType` 擴為全部 10 型（列表要顯示採購收貨等系統流水帳），badge 對照表補齊——**否則系統型別會 fallback 成裸英文代碼**。
- 數量正負號改依 `INBOUND_MOVEMENT_TYPES` 判定。原本寫的是 `movementType.endsWith('OUT') || === 'ADJUST_MINUS' || === 'SCRAP'`，而 `'OUTBOUND'.endsWith('OUT')` 是 **false**（結尾是 `ND`）——訂單出貨會被顯示成綠色 `+N`。

---

## 5. 順帶抓到的假綠燈：E2E-M16-005

`M16ErpE2ETest` 的 `E2E-M16-005「OUTBOUND 庫存不足時回傳錯誤」`：

```java
.movementType("OUTBOUND")
.quantity(999999)          // 超過庫存
...
.statusCode(anyOf(is(400), is(500)));   // 預期錯誤
```

它拿到的一直是 **E-7005「無效的庫存調整」**（`OUTBOUND` 當時後端根本不認識，就算認識也在禁用清單裡），不是庫存不足。寬鬆的 `anyOf(400, 500)` 讓它一路是綠的，但**「庫存不足」這條路徑一次都沒被驗到**。

改為送手動確實可用的 `ADJUST_MINUS`，並斷言到錯誤碼 `E-7004`。這是 S97「測試以固件繞過同一段邏輯」的同型問題，差別在於這次繞過的不是固件，而是**寬鬆的斷言**：`anyOf(400, 500)` 幾乎不可能紅，也就幾乎不帶資訊。

---

## 6. 驗證

| # | 內容 | 結果 |
|---|------|------|
| ① | `mvn -o compile` / `test-compile` | 通過 |
| ② | ERP 單元測試 | `StockMovementServiceTest` **24 passed**（18 → 24，新增 DEF-063 守衛 5 個參數化案例 ＋ RETURN 拒絕 1 個）、`PurchaseOrderServiceTest` **25 passed** |
| ③ | `make validate-schema-doc` | **71 個遷移套用成功**，SRD DDL／PRD §8.2 與實際 schema 一致（V71 含 CHECK 約束無漂移） |
| ④ | `make validate-schema` | 通過（ddl-auto=validate ＋ Flyway，entity 與 schema 對齊） |
| ⑤ | M16 整合／E2E | `M16ErpE2ETest` **7 passed**（6 → 7）、`M16ErpIntegrationTest` **37 passed**、`M16ErpInventoryConcurrencyIntegrationTest` **5 passed** |
| ⑥ | 前端 `tsc --noEmit` / `eslint` | 通過；lint 0 error（5 個 warning 皆為既有，與本輪無關） |
| ⑦ | 後端全量回歸 `mvn -o verify` | 見下方守門段落 |

### 6.1 DEF-063 的回歸守衛守的是什麼

新增的兩處測試守的是**耦合本身**，不只是當下的值：

- `StockMovementServiceTest#createManualMovement_everyTypeOfferedByUi_isAccepted`（參數化 5 型）
- `M16ErpE2ETest#E2E-M16-005b`（同樣 5 型，走完整 HTTP 路徑並斷言 201）

兩處都在註解裡指名對應的前端檔案。DEF-063 的成因是「前後端各自演化、中間沒有轉換層、也沒有任何測試跨過那條邊界」——補上型別對齊卻不補這條測試，下一次改名仍然是使用者先撞到。

---

## 7. 範圍外：本輪查證過但刻意不動的

### 7.1 🆕 DEF-064（新記錄）：異動列表三個欄位永遠空白、表單「參考單號」靜默丟棄

`StockMovementDto` 有 `skuCode`／`productName`／`referenceNumber` 三個欄位，但 `StockMovementService.toDto()` **從未填值**，前端列表的「SKU／品名／參考單號」三欄因此永遠顯示 `-`。另 `StockMovementRequest`（後端）**沒有 `referenceNumber` 欄位**，前端表單那格輸入送出後被 Jackson 靜默忽略。

與 DEF-063 同一個畫面，但根因不同（DTO 未填值 ＋ 請求欄位缺漏，不是枚舉漂移），且要不要保留「參考單號」這個欄位涉及與採購單／訂單編號的關係，是設計問題。依 Rule 3 不順手擴大，記為 DEF-064。

### 7.2 🆕 DEF-065（新記錄）：訂單流程完全不寫庫存流水帳

PRD §6.7.3 明訂 C 端扣減流程要產生 `StockMovement type=RESERVE`／`OUTBOUND`，取消回滾要產生 `type=RELEASE`。實際上**全專案只有兩處寫入 `stock_movements`**：`PurchaseOrderService`（收貨）與 `StockMovementService`（手動異動）。S103 改寫的 `ProductInventoryService` 三段式庫存操作對 `StockMovement` **零引用**——`product_inventory` 的數字會動，流水帳一筆都不會留。

後果是庫存台帳（PRD §6.7.3「按 SKU 查看即時庫存、庫存異動記錄」P0）對 C 端的所有進出完全沒有記錄，`RESERVE`／`OUTBOUND`／`RELEASE` 三型在資料庫裡從來沒有出現過。這也解釋了為什麼枚舉漂移能存活這麼久：**那三型從來沒有被寫入過，自然沒人發現名字是錯的**。

不在本輪修：這是補功能（要決定寫入時點、`reference_type`／`reference_id` 怎麼填、`before/after_reserved_qty` 是否一併補），不是修 DEF-063 的枚舉漂移。

### 7.3 沒有動的

- **`RETURN` 既有資料不轉換**：PRD 9 型沒有一個能忠實表達「有記錄但庫存沒動」，改寫成任一型都會竄改歷史語意。保持原值，由 CHECK 約束的白名單容納。
- **`before_reserved_qty`／`after_reserved_qty`**：承 S113，仍是從未填值的兩個欄位。填它是新功能。
- **`InventoryService`**：全為唯讀查詢，未涉及枚舉寫入。

---

## 8. 下一輪建議

| 優先 | 項目 | 說明 |
|------|------|------|
| 1 | 🟡 **DEF-065**（本輪新記錄） | 訂單流程不寫流水帳，PRD §6.7.3 P0 功能實際缺失，且範圍明確（無需業務決策） |
| 2 | 🟡 **DEF-064**（本輪新記錄） | 列表三欄永遠空白 ＋ 參考單號靜默丟棄，使用者可見；需決定「參考單號」的定位 |
| 3 | 🟡 DEF-044 | 退款回補庫存，仍待業務決策 |
| 4 | 🟡 DEF-021 | CJK 字體，S41 已決策維持現狀 |
