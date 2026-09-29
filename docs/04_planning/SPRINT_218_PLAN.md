# Sprint 218 Plan — 訂單取消依 PRD 補償、出貨才扣庫存（DEF-301、DEF-303 (2)(4)(5)(6)）

**Sprint**: Sprint 218
**日期**: 2026-09-29

## 1. 起點

使用者對 Sprint 216 登記的項目回覆：

- DEF-301（賣家以改狀態端點取消訂單，不釋放庫存、不退優惠券）：「請遵守 PRD」
- DEF-303 (6)（DEF-044 只涵蓋退貨，出貨前取消已付款訂單的庫存仍不回補）：「以下請處理，符合邏輯」

兩項都是「訂單取消時要做哪些補償」，本輪一起處理。DEF-303 的 (2)(4)(5) 與它們綁在一起（見 §3），一併處理；(1)（前端訂房沒有付款步驟）與 (3)（舊版退款端點對 Stripe 只做 Mock）留到訂房付款那一輪。DEF-302（24 小時逾時取消）需要先解決 DEF-305（全專案從未啟用排程），放在下一輪。

## 2. PRD 怎麼規定

- **§15.2.5 取消訂單**：`canceledBy` 為 `MERCHANT | CUSTOMER | SYSTEM`。RETAIL 取消補償：「庫存釋放：實時歸還至 M02」、「退款：若已支付，觸發 M04 退款流程」、「優惠券：若已使用促銷碼，則退還」。RETAIL 路徑 `CREATED ──[cancel by any]──→ CANCELLED`（Phase 1 的 CREATED 等同已付款）。TC-M05-015：商家取消未付款訂單 → CANCELLED、庫存釋放、無退款。
- **§15.3.1.2**：狀態變更與資源釋放必須在同一交易內原子提交。
- **§6.7.3／§6.7.4**：下單 `RESERVE`（+reserved）、**出貨** `OUTBOUND`（−total、−reserved）、取消 `RELEASE`（−reserved）。

## 3. 現況與缺口

| # | 現況 | 與 PRD 的落差 |
|---|------|---------------|
| DEF-301 | 買家／管理員的 `POST /cancel` 有補償；賣家的 `PATCH /status → CANCELLED` 只改狀態，預扣庫存與優惠券額度永遠不還 | §15.2.5 取消補償不分取消方 |
| DEF-301 延伸 | `PATCH /status` 可把 PAID 直接改成 REFUNDING——賣家「取消已付款訂單」同樣跳過全部補償 | 同上 |
| DEF-303 (6) | Sprint 88 起扣帳發生在**付款成功**（`PaymentStateService` 兩條成功路徑呼叫 `deductForOrder`）。付款後貨還在倉庫、`total_qty` 已扣掉；取消時只能「釋放預留」，而預留早已轉成扣帳，所以出貨前取消已付款訂單，庫存永遠不回來 | §6.7.3 規定出貨才扣 |
| DEF-303 (2) | 舊版 `POST /v2/payments` 的訂單付款不扣庫存，與另外兩條付款路徑不一致 | 改成出貨才扣之後，三條付款路徑都不動庫存，自然一致 |
| DEF-303 (4) | 取消 CONFIRMED（只能由 PAID 轉入，必然已付款）只到 CANCELLED、不進 REFUNDING——錢沒有下一步可退 | 「若已支付，觸發退款流程」 |
| DEF-303 (5) | 取消已付款訂單時狀態紀錄寫「Automatic refund triggered」，實際上沒有觸發任何退款 | 描述與行為不符 |
| PRD §15.3.1.2 | 取消時以 try/catch 吞掉庫存釋放與優惠券退還的例外 | 兩者都經過交易代理，例外一拋出交易就已被標成 rollback-only，吞掉只會讓提交時改丟 `UnexpectedRollbackException`，並沒有真的讓取消在資源沒釋放下成立；但寫法與 PRD 的原子要求相反 |

**紅燈實測**（新整合測試跑在未修改的程式上，9 案例中 7 個失敗，失敗點皆在預期的斷言）：賣家取消未付款訂單後 `reserved_qty` 仍為 3（預期 0）；買家付款後 `total_qty` 已變 97（預期仍為 100）；取消 CONFIRMED 得到 CANCELLED（預期 REFUNDING）；賣家 PATCH PAID→CANCELLED 被狀態機拒絕；PATCH 直接指定 REFUNDING 沒有被拒絕；兩個改版前在途訂單案例在前置步驟即不符。兩個「出貨扣帳」案例在舊程式也通過（付款即扣帳的最終數字相同），它們是新程式的守門。

## 4. 修法

### 4.1 庫存改為出貨才扣（PRD §6.7.3）

- `PaymentStateService`：Mock 付款與 Stripe 付款成功都**不再扣庫存**，移除 `deductStockSafely` 與對庫存服務的依賴。
- 出貨的兩個入口都扣帳，且與狀態轉換在同一交易：`LogisticsService.createLogistics`（建立物流單，CONFIRMED→SHIPPING）、`OrderService.updateOrderStatus`（PATCH →SHIPPING）。
- `ProductInventoryService` 以兩個新方法取代 `deductForOrder`／`releaseForOrder`：
  - `deductOnShipment`：依本訂單的流水帳算每個品項「仍在預留中」的數量（RESERVE − RELEASE − OUTBOUND），扣那麼多、寫 OUTBOUND。
  - `releaseOnCancellation(order, 取消前狀態)`：釋放仍在預留中的數量、寫 RELEASE；另把「改版前付款時已扣帳、尚未加回」的數量（OUTBOUND − ADJUST_PLUS）加回 `total_qty`、寫 ADJUST_PLUS（備註寫明原因）。

**為什麼要看流水帳，而不是依訂單狀態或品項數量**：改版當下會有「付款在改版前（已扣帳）、出貨或取消在改版後」的在途訂單。出貨時若照數量再扣一次就是重複扣帳；取消時若照數量釋放預留，會把別張訂單的預留釋放掉（`reserved_qty` 有下限保護，不會變負，只會默默少掉別人的）。流水帳記得每張訂單實際做過什麼，照它算就兩種情況都正確，重複呼叫也不會多做。

**沒有流水帳的訂單**（Sprint 115 之前建立）：取消前為 CREATED 時必然仍在預留中（Sprint 88 起建單即預扣），沿用改版前「依數量釋放」；已付款的無從判斷付款時是否扣過，不動並記 WARN；出貨時一律不動並記 INFO。選擇的原則是寧可可售量偏低，也不冒超賣或扣走別人預留的風險。

### 4.2 取消補償只有一份（DEF-301）

`OrderService.compensateCancellation` 是唯一的取消補償，買家／管理員的 `POST /cancel` 與賣家的 `PATCH → CANCELLED` 都呼叫它：

1. 釋放預留（`releaseOnCancellation`）
2. 退還優惠券額度（既有 `refundPromoUsage`）
3. 取消前為 PAID 或 CONFIRMED（已付款）→ 轉 REFUNDING，狀態紀錄寫「Order was paid: refund pending」

不再以 try/catch 吞掉 1、2 的例外（PRD §15.3.1.2）。

### 4.3 狀態機（DEF-301）

- PAID 的下一步由 {CONFIRMED, REFUNDING} 改為 {CONFIRMED, CANCELLED}：賣家要取消已付款訂單就指定 CANCELLED，補償後自動轉 REFUNDING（PRD：CREATED(=PAID) 可由任何一方取消）。
- `PATCH /status` 不可直接指定 REFUNDING（回 `E-5001`，不論角色）：REFUNDING 只能由取消已付款訂單進入。

## 5. 測試

- **`OrderCancellationCompensationIntegrationTest`**（新，9 案例，真實 PostgreSQL＋真實 Redis 購物車，走真實的建單／付款／確認／出貨／取消服務方法，身分權限取自生產 `RolePermissionMapping`）：賣家取消未付款訂單（預留與優惠券額度都還回）、付款不動總量且付款後取消釋放預留、取消 CONFIRMED 進 REFUNDING、賣家取消已付款訂單、PATCH 指定 REFUNDING 被拒、建立物流單與 PATCH 出貨各扣一次、改版前已扣帳的在途訂單取消（ADJUST_PLUS 加回）與出貨（不重複扣）。
- **突變驗證**（每次只拿掉一處，跑完以備份還原並以 grep 確認）：拿掉物流建立時的扣帳 → 出貨案例紅；拿掉改版前扣帳的加回 → 在途取消案例紅；出貨扣帳改回依品項數量 → 在途出貨案例紅；拿掉 PATCH 取消的補償 → 兩個賣家取消案例紅。
- `ProductInventoryServiceTest`（9→13）：以 8 個流水帳情境案例取代原本 4 個 `deductForOrder`／`releaseForOrder` 案例。
- `OrderServiceTest`（68→73）：新增 PATCH 取消未付款（含優惠券退還）、PATCH 取消已付款、PATCH 指定 REFUNDING 被拒、PATCH 出貨扣帳、PATCH 確認不動庫存。
- `LogisticsServiceTest`（5→7）：建立物流單時扣帳；沒搶到 CONFIRMED→SHIPPING 時不扣。
- **被改正的既有測試（原本把缺陷行為當正確行為鎖住）**：
  - `OrderServiceTest.cancelOrder_owner_confirmed_noAutoRefund`：斷言取消 CONFIRMED 只到 CANCELLED＝DEF-303 (4) 本身，改為斷言 REFUNDING。
  - `OrderServiceTest.cancelOrder_owner_paid_triggersAutoRefund`：斷言取消已付款訂單「不釋放庫存（已扣帳）」＝DEF-303 (6) 本身，改為斷言釋放預留，並斷言狀態紀錄不再寫「Automatic refund triggered」。
  - `PaymentStateServiceTest`（55→54）：刪除「扣帳失敗不影響付款成功」案例與付款時扣帳的斷言（付款已不動庫存）。
- `M12OrderStockLedgerIntegrationTest`、`M12InventoryConcurrencyIntegrationTest`：改呼叫新方法；出貨併發扣帳案例改為每張訂單先各自預扣、再同時出貨（新方法依流水帳扣剩餘預留），仍壓同一條原子 UPDATE。

## 6. 驗證結果

- 相關單元測試 11 個類別 204 案例、相關整合測試 8 個類別 61 案例先行通過。
- 全量 `mvn -o clean verify`（`make test-db-up` 已啟動真實 postgres/redis）：單元 **1823**（+10）／整合 **606**（+9）／0 failures／0 errors／0 skipped；但 checkstyle（test）擋下 1 個錯誤——新整合測試多了一個沒用到的 `Mockito.when` import（`BUILD FAILURE`，10 分 43 秒）。移除該 import（不影響任何測試行為）後重跑 checkstyle（main＋test）0 violations、PMD 通過、`BUILD SUCCESS`。
- 未變更 entity／migration；前端沒有改動。

## 7. 決策與已知限制

- **扣帳時點改到出貨（我做的判斷）**：「出貨前取消已付款訂單回補庫存」有兩種修法——維持付款即扣帳、取消時再加回；或照 PRD §6.7.3 改成出貨才扣。選後者：PRD 明定出貨 OUTBOUND、取消 RELEASE；`total_qty` 從此等於倉庫實際在庫（付款後、出貨前的貨仍算在內），盤點不會出現差異；取消永遠只是釋放預留，不需要 PRD 沒有定義的「撤銷出庫」型別；舊版付款路徑不扣庫存的不一致（DEF-303 (2)）也隨之消失。代價是賣家在 ERP 看到 `total_qty` 下降的時點從付款變成出貨（可售量不變）。
- **改版前在途訂單以 ADJUST_PLUS 加回**：PRD §6.7.4 沒有「撤銷出庫」型別，選盤盈調整並在備註寫明原因。只會發生在改版前已付款、改版後才取消的訂單。ADJUST_PLUS 屬入庫方向，會更新該 SKU 在庫存台帳上的「最近入庫時間」。
- **REFUNDING 之後誰退款**：本輪讓已付款訂單取消後正確進入 REFUNDING，但**錢仍需有人執行退款**——前端沒有任何退款介面，只有管理員直接呼叫 `POST /v2/orders/{id}/refund`。PRD M07 寫的是「自動化退款流」；自動退款需要排程（DEF-305），放在下一輪與 DEF-302 一起處理。在那之前，賣家取消已付款訂單的前端按鈕也先不做。
- **賣家取消的前端入口**：後端 `PATCH → CANCELLED` 現在完整依 PRD 補償，但店家後台訂單頁仍沒有取消按鈕（與 Sprint 216 相同）。
- **沒有流水帳的舊訂單**：見 §4.1，選擇安全方向（可售量偏低），不保證帳面完全精確。
- **未以真實瀏覽器走過**。
