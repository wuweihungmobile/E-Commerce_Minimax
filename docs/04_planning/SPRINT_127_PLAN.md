# Sprint 127 Plan — DEF-070 採購單收貨超收無驗證 + DEF-071 ERP 收貨確認頁完全無法使用

**Sprint**: Sprint 127
**日期**: 2026-09-04

---

## 1. 缺口盤點結果

待辦清單（`DEFERRED_ITEMS_TRACKER.md` 高優先級區）目前無項目，延續第八輪以來的方法論
（孤兒錯誤碼 → 零呼叫死碼 → 欄位零讀取）重新掃描一輪，這次鎖定 `ErrorCode` 全庫比對：
131 個定義的 `E_XXXX` 中有 27 個從未以 `ErrorCode.E_XXXX` 字面被拋出，逐一排查後：

- 多數為既有已審視過的偽陽性（如 `E_8009` 於 Sprint 100 已確認擁有權檢查完整只是回應方式
  不同；`E_4091` 店鋪名稱重複——PRD §8.2.1 明訂 `tenants.name`「**無唯一約束**」，設計上本就
  不檢查）。
- **`E_7009`（無效的收貨數量）是真缺口**：`PurchaseOrderService.receivePurchaseOrder` 只把
  `item.getReceivedQuantity() + receiveItem.getReceivedQuantity()` 直接寫回，從未檢查新的累計
  收貨量是否超過 `item.getQuantity()`（訂購量）。單次超收或分批累計超收皆無阻擋，會虛增
  `product_inventory.total_qty`（透過 `createInboundMovement`）且讓 PO 狀態機
  （`RECEIVED`/`PARTIALLY_RECEIVED` 判定依據 `receivedQuantity >= quantity`）看起來正常，實際
  庫存已失真。既有測試（含 `M16ErpIntegrationTest`／`M16ErpInventoryConcurrencyIntegrationTest`）
  從未涵蓋超收情境。定為 **DEF-070**。

## 2. DEF-070 修復

`PurchaseOrderService.receivePurchaseOrder`：在寫回 `item.setReceivedQuantity()` 前新增檢查，
`newReceivedQty > item.getQuantity()` 時拋出 `BusinessException(ErrorCode.E_7009, ...)`，
涵蓋單次超收與分批累計超收兩種情境。

**紅燈測試先行**（`PurchaseOrderServiceTest`，修復前執行皆失敗，確認缺口為真）：
- `receivePurchaseOrder_exceedsOrderedQuantity_throwsE7009`：訂購量 10，單次輸入 15 →
  修復前落到 `createInboundMovement` 未 mock 的路徑而回傳 `E_3003`（非預期），修復後正確拋
  `E_7009` 且不觸碰 `productInventoryRepository`/`stockMovementRepository`/`purchaseOrderRepository.save`
- `receivePurchaseOrder_cumulativeExceedsOrderedQuantity_throwsE7009`：訂購量 10、已收 8，
  本次再收 5（累計 13）→ 同樣拋 `E_7009`

## 3. 使用者決策（AskUserQuestion 拍板紀錄）

在修復 DEF-070 過程中，順藤摸瓜檢視 `receivePurchaseOrder` 前端呼叫端時，發現範圍外但更嚴重
的既有缺陷（詳見 §4），已用 AskUserQuestion 呈現給使用者拍板：

| # | 問題 | 拍板結果 |
|---|------|---------|
| 1 | 新發現的 ERP 收貨確認頁完全無法使用（見 §4），如何處理？ | 併入 Sprint 127 一起修 |

## 4. DEF-071：ERP 收貨確認頁完全無法使用（新發現，併入本輪）

**根因為三個問題疊加**，皆位於 `PurchaseOrderForm.tsx`（`mode="receive"`）與其型別定義
`frontend/src/services/erp/purchaseOrder.ts`，後端 `receivePurchaseOrder` API 本身無問題：

1. **欄位名稱契約不符**：前端 `PurchaseOrderReceiveRequest.items[]` 型別定義送出 `skuId`，
   後端 `PurchaseOrderReceiveRequest.ReceiveItemRequest` 要求的卻是 `itemId`（`@NotNull`）。
   請求送達時 `itemId` 恆為 `null`，`@Valid` 於 Controller 層直接以 400 拒絕——**無論如何操作，
   點擊「確認收貨」必定回傳 400**。
2. **沒有輸入框可填寫本次收貨量**：畫面上「已收」欄位是唯讀 `<span>{item.receivedQuantity}</span>`，
   整個 `mode="receive"` 的表格裡不存在任何可編輯的收貨數量欄位。
3. **`handleReceive` 把讀到的舊值原樣送回**：送出的 `receivedQuantity` 直接取自
   `item.receivedQuantity`（GET 回來的既有累計已收量），不是「這次要收多少」。即使修好 (1)，
   對一張全新採購單（`receivedQuantity` 初始為 0）送出仍等於「這次收 0 件」，功能等同無作用。

三者疊加代表**這條功能路徑事實上從未真正運作過**——沒有任何 Playwright E2E 涵蓋 ERP 模組
（`grep` 全專案 `*.spec.ts` 對 `purchase-order`/`erp` 零命中），符合本專案已多次記錄的警訊模式
「測試都用固件（或這裡是完全沒有測試）繞過同一段邏輯」。

**修復內容**：
- `purchaseOrder.ts`：`PurchaseOrderReceiveRequest.items[].skuId` → `itemId`，對齊後端契約。
- `PurchaseOrderForm.tsx`：
  - `OrderItem` 新增 `id`（品項 ID，收貨模式據此送出 `itemId`）與 `receiveQty?`（本次收貨量，
    僅收貨模式使用，其餘模式維持 optional 不影響既有 view/edit 的直接賦值路徑）。
  - `fetchOrder()` 收貨模式載入時，`receiveQty` 預設帶入尚未收到的剩餘量
    （`quantity - receivedQuantity`），使用者可再調整。
  - 新增 `handleReceiveQtyChange`：輸入值限制在 `[0, 剩餘量]` 區間，防止畫面端就先允許超收
    （與 DEF-070 的後端防線互補，非取代）。
  - 表格新增「本次收貨」欄位（可編輯 `<Input type="number">`），與既有唯讀「已收」欄位並列。
  - `handleReceive`：改為送出 `{ itemId: item.id, receivedQuantity: item.receiveQty }`，且過濾
    掉 `receiveQty <= 0` 的品項；若過濾後為空清單，前端先擋下並提示，不送出必然被拒的空請求。

**前端驗證**：`npx tsc --noEmit`（0 error）、`npx eslint`（異動檔案 0 error、0 新增 warning，
既有 6 個 warning 皆為與本次改動無關的既有項目——未使用的 import/handler、`useEffect` 缺依賴、
`purchaseOrder.ts` 匿名預設匯出）、`npm run build`（成功，
`/dashboard/erp/purchase-orders/[id]/receive` 正確產生為動態路由）。

## 5. 範圍外

- **ERP 模組 Playwright E2E**：本輪查證確認整個 ERP 後台（採購單/庫存/供應商）目前零 E2E
  涵蓋，DEF-071 之所以存活正因為此。修復本身純屬前端契約對齊（無新業務邏輯），依 Sprint 126
  同一判準（`tsc`＋`eslint`＋`build` 對低風險純 UI 改動已足夠把關）本輪不新建 E2E 規格。
  **誠實揭露殘留風險**：未來若此契約再度漂移（例如後端 DTO 欄位改名），現有把關方式不會抓到，
  因為 `tsc` 只驗證前端型別內部一致，無法對照後端契約。建議列為未來 Sprint 候選：仿照
  `at-m17-002.spec.ts` 的「apply → admin 核准 → 重新登入取得新角色 JWT」模式，建立 ERP 模組的
  StoreOwner E2E 固件鏈，一次性補上整個模組的 E2E 缺口（非僅收貨這一頁）。
- `PurchaseOrder.poNumber` 由 `generatePoNumber()`（日期＋6 位隨機數）產生，`E_7006`
  （採購單號重複）同樣是孤兒錯誤碼——理論上極低機率碰撞會讓 `DataIntegrityViolationException`
  以原始 500 洩漏而非友善錯誤。碰撞機率極低（每日 90 萬組隨機值）且 PO 號碼為系統自動產生非
  使用者輸入，性質類似 DEF-037（低風險、記錄不修），本輪不處理。

## 6. 驗證

| # | 內容 | 結果 |
|---|------|------|
| ① | `mvn -o clean compile`（逐支修改後即編譯，非累積到最後） | BUILD SUCCESS |
| ② | `mvn -o checkstyle:check`（main） | 0 violations |
| ③ | `mvn -o checkstyle:check@checkstyle-test` | 0 violations |
| ④ | `mvn -o -Dtest=PurchaseOrderServiceTest#...` 紅燈確認（修復前執行，2 個新測試皆失敗） | 確認缺口為真 |
| ⑤ | 同上 2 個測試（修復後） | 綠燈 |
| ⑥ | `mvn -o test`（全量單元測試，1074 個） | 0 Failures／3 Errors（`SellerDashboardServiceCacheTest`，`make test-db-up` 後重跑確認為既有環境缺口——該測試需真實 DB，與本輪改動無關，見 [[backend-integration-test-profile-needs-real-db]]） |
| ⑦ | `mvn -o verify`（全量整合回歸，含 failsafe） | BUILD SUCCESS，整合測試 459 個 0 Failures／0 Errors；checkstyle（main+test）0 violations；PMD 0 issues；7m34s |
| ⑧ | 前端 `npx tsc --noEmit` | 0 error |
| ⑨ | 前端 `npx eslint`（新增/修改檔案） | 0 error，0 新增 warning |
| ⑩ | 前端 `npm run build` | 成功 |

## 7. Velocity 紀錄

| Sprint | 主題 | 估點 (SP) | 實際規模 |
|--------|------|-----------|----------|
| 127 | DEF-070（後端驗證缺口）+ DEF-071（前端契約缺陷，過程中發現後併入） | 3（DEF-070 原估）+ 2（DEF-071 使用者拍板併入） | 後端 2 檔異動（1 主程式修改＋1 測試修改，+6/-0 主程式、+45 測試）＋前端 2 檔異動（+59/-13） |

## 8. 下一步 / Action Items

| 項目 | 狀態 |
|------|------|
| `mvn -o verify` 全量整合回歸結果回填 | 已完成（見 §6 ⑦） |
| `make validate-release` ＋ `git commit` ＋ push | 待執行 |
| RELEASE_TRACKER.md 新增 Sprint 127 row、依「狀態欄維護規則」於下一 Sprint 開工時回填本輪 push 狀態 | 待 push 後執行 |
| DEF-070／DEF-071 移入 DEFERRED_ITEMS_TRACKER.md「已完成延後項目」 | 待本輪收尾一併處理 |
| ERP 模組 Playwright E2E 固件鏈（見 §5） | 記錄為未來 Sprint 候選，非本輪範圍 |
| `E_7006` 採購單號碰撞的原始 500（見 §5） | 記錄不修（低風險） |
