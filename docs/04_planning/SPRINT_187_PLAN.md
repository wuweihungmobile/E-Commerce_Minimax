# Sprint 187 Plan — 金流/訂單生命週期稽核日誌覆蓋率補齊（DEF-257）

**Sprint**: Sprint 187
**日期**: 2026-09-23

## 1. 起點

Sprint 185 掃描「稽核日誌完整性」時發現 `AuditService` 本身（`core/audit/AuditService.java`）完整性無缺陷——真正 append-only、查詢權限正確、內容不含敏感欄位，但實際呼叫端全部屬於「Admin 審核類」操作（Stripe Connect 上架審核、租戶功能開關、租戶成員管理、結算單審核、使用者停權、退貨審核），核心金流/訂單生命週期（下單、付款、退款、取消、定價規則異動）完全沒有寫入稽核紀錄，僅依賴各自的 `log.info`。此缺口登記為 `DEF-257`，因「涉及至少 5 個 Service 的多處改動，屬獨立專案性質工作」，Sprint 185/186 皆評估後決定不隨手夾帶，留待專門 Sprint 處理。Sprint 186 結尾建議候選方向之一即為此項，本輪據此開工。

## 2. 調查方法與結果

派出 1 個背景唯讀調查 agent，逐行複查：
- `AuditService` 的完整方法簽章、`AuditLog` entity 欄位結構、是否同步/是否納入呼叫端交易、失敗是否吞噬。
- 全庫既有 11 處呼叫點（`TenantStripeConnectService`/`TenantService`/`SettlementReviewer`/`UserPrivacyService`/`ReturnRequestService`）的 action/entityType 命名慣例——確認**沒有** `AuditAction` enum，一律裸字串，命名慣例為 `NOUN_PAST_TENSE_VERB`。
- 附帶發現：`AdminService` 走的是自己另一份重複邏輯（private `recordAudit`），並未依賴 `AuditService`——`audit_log` 表實際有兩條並存寫入路徑，本輪不處理，僅記錄為已知技術債。
- 目標 5 個 Service（`PricingService`/`OrderService`/`BookingService`/`PaymentService`/`PaymentStateService`）逐行確認，共約 25-26 個 public/private 寫入方法完全沒有任何 `AuditService` 呼叫。
- 既有的 `recordStateLog`/`recordOrderStateLog`（寫入 `OrderStateLog`）為平行機制，無 `tenant_id` 欄位、無法取代跨租戶維度查詢，需與新補的 `AuditService.record()` 並存而非二選一。
- 風險評估：最大隱藏工作量非主邏輯改動本身，而是受影響的 `@InjectMocks` 單元測試檔案需同步補上 `@Mock AuditService auditService`，否則會在補上呼叫的當下讓既有綠燈測試變 NPE 紅燈。

## 3. 修復範圍與實作

比照既有 11 處呼叫點的命名慣例（自由字串，`NOUN_PAST_TENSE_VERB`），刻意**不**新增 `AuditAction` enum（Rule 11：配合既有慣例，非必要不重構）。於以下 5 個 Service 共 20 處核心狀態轉換補上 `auditService.record(...)`：

- **`PricingService`**（5 處）：`createRule`／`updateRule`（前後狀態快照）／`deleteRule`／`setCalendarPrice`／`overridePrice`。
- **`OrderService`**（3 處）：`buildProductOrder`（單一結帳 `createOrderFromCart` 與合併結帳 `CombinedCheckoutService` 共用此核心方法，補在此處一次涵蓋兩條路徑，不在各自入口重複補）／`updateOrderStatus`（4 參數核心版本，3 參數入口為純委派，同一次呼叫已涵蓋）／`cancelOrder`。
- **`BookingService`**（3 處）：`buildBookingCore`（同 `OrderService.buildProductOrder`，涵蓋單一訂房與合併結帳共用路徑）／`updateBooking`（前後日期/人數快照）／`cancelBooking`。
- **`PaymentService`**（3 處）：`processOrderPayment`／`processBookingPayment`／`processRefund`。`processRefund` 既有的 `resolveAndAuthorizePaymentTarget`／`PaymentTarget` 容器新增 `tenantId()` 方法（見 §4 NPath 修正）。
- **`PaymentStateService`**（6 處）：`mockPaymentSuccess`／`mockPaymentFailure`／`refundOrderPayment`／`markStripeRefunded`／`markStripePaymentSucceeded`／`markStripePaymentFailed`。webhook 類方法（無登入使用者）沿用既有 7 參數 `record(...)` 重載，`actorUserId` 交由 `AuditService` 內部 fallback 至 `TenantContext.getCurrentUser()`（webhook 情境下為 `null`，與既有 `recordOrderStateLog(..., null, ...)` 慣例一致）。

**刻意不補的方法**（依 Rule 2 簡潔優先，避免非必要改動）：
- `OrderService.createRoomOrder`（既有記憶 `room-booking-dual-path-gotcha`：前端零呼叫點死碼）。
- `PaymentStateService.initiateStripeCheckout`（僅建立 `PROCESSING` 佔位付款記錄，非終態轉換）、`confirmStripeCheckout`（純委派至 `markStripePaymentSucceeded`，該方法已補稽核，重複呼叫無意義）。

`PaymentStateService` 為手動建構子（非 `@RequiredArgsConstructor`），新增 `AuditService auditService` 依賴為建構子新增參數，屬編譯期強制修改，同步更新 `PaymentStateServiceTest`／`PaymentStateServiceStripeTest` 兩個既有測試檔的 `new PaymentStateService(...)` 呼叫。其餘 4 個 Service 皆為 `@RequiredArgsConstructor`，新增欄位即自動納入建構子。

## 4. NPath 複雜度修正

第一次 `mvn -o clean verify` 的 checkstyle 檢查抓到 `PaymentService.processRefund` NPath 複雜度由新增的巢狀三元運算式（`target.order != null ? ... : target.booking != null ? ... : null`）推高至 384（上限 200）。修法：比照既有 `resolveAndAuthorizePaymentTarget` 抽出獨立方法的精神，把 tenantId 判斷邏輯移入 `PaymentTarget` 容器類別本身，新增 `private UUID tenantId()` 方法，`processRefund` 呼叫端改為 `target.tenantId()` 單一方法呼叫，複雜度歸零。第二次 `mvn -o clean verify` 確認 checkstyle 0 違規。

## 5. 測試

**紅燈驗證**：本輪 20 處新增呼叫皆為既有方法內部新增副作用（不改變方法簽章、不改變回傳值），比照近期慣例，紅燈驗證改採「新增後跑既有完整測試套件確認無破壞」為主；另外針對 5 個 Service 各補 1 個**明確驗證稽核呼叫本身**的測試（見下），這類新測試本身即是自身的紅燈依據（新增前必定不存在對應 `verify(auditService)...` 斷言可通過）。

**機械性測試基礎設施更新**（15 個受影響的 `@InjectMocks` 單元測試檔案，僅對「測試路徑會實際走到寫入方法」的檔案補 `@Mock AuditService auditService`，避免非必要變更）：
- `PricingServiceTest`
- `OrderServiceTest`、`OrderPromoCodeTest`
- `BookingServiceOwnerCalendarTest`、`BookingPromoCodeTest`、`BookingServiceOwnershipTest`、`BookingServiceOpenWindowTest`、`BookingServiceCreateBookingTest`、`BookingServiceUpdateDateChangeTest`
  （`BookingServiceRoomTitleTest`／`BookingServiceDynamicPricingTest` 逐一核對後確認測試路徑只呼叫唯讀方法，未受影響，未改動）
- `PaymentServiceOwnershipTest`、`PaymentServiceRefundOwnershipTest`、`PaymentServiceConcurrencyTest`、`PaymentServiceRefundConcurrencyTest`
- `PaymentStateServiceTest`、`PaymentStateServiceStripeTest`（另需同步更新手動建構子呼叫）

**新增 5 個稽核驗證測試**（各 Service 挑選一個既有 happy-path 情境延伸，而非重建全新測試固件）：
- `PricingServiceTest`：新增 `createRule_success_recordsAuditLog`。
- `OrderServiceTest`：延伸既有 `updateOrderStatus_systemTriggeredPaidTransition_succeedsAndLogsState`，追加 `verify(auditService)...` 斷言。
- `BookingServiceOwnershipTest`：延伸既有 `cancelBooking_sameUser_passesOwnership`，追加 `verify(auditService)...` 斷言。
- `PaymentServiceRefundOwnershipTest`：延伸既有 `refund_orderPayment_sameUser_succeeds`，追加 `verify(auditService)...` 斷言。
- `PaymentStateServiceTest`：延伸既有 `success_createsSuccessPaymentAndPaidOrder`（`MockPaymentSuccess` 巢狀類別），追加 `verify(auditService)...` 斷言。

## 6. 驗證結果

`mvn -o clean verify`（`make test-db-up` 真實 postgres/redis）：**BUILD SUCCESS**，**1635 個單元測試（+1）+ 486 個整合測試（持平），0 failed**；checkstyle（main+test）**0 違規**（含中途修正的 NPath 複雜度問題）。

## 7. 更新 `DEFERRED_ITEMS_TRACKER.md`

- `DEF-257` 狀態由「⚠️ 已記錄，不排入排程」改為「✅ 已修復（Sprint 187）」。

## 8. 誠實揭露總結

- 本輪刻意不新增 `AuditAction` enum，維持既有 11 處呼叫點的自由字串慣例一致——這是遵循 Rule 11（配合程式碼庫慣例，即使不同意）的判斷，而非遺漏；若未來認為裸字串慣例本身有害，應另案公開討論再決定是否全庫重構，不宜本輪隨手夾帶。
- 調查過程中發現 `AdminService` 有自己獨立一份重複的稽核寫入邏輯（`private recordAudit`），並未依賴共用的 `AuditService`——這代表 `audit_log` 表目前有兩條並存但邏輯重複的寫入路徑。本輪範圍明確限定在「金流/訂單生命週期缺乏稽核」這個具體缺口，不處理既有重複邏輯的整併，僅在此誠實記錄供未來評估是否值得專案化處理。
- `PaymentStateService.initiateStripeCheckout`／`OrderService.createRoomOrder` 等方法刻意不補稽核，理由已於 §3 具體說明（非終態轉換／已知死碼），非遺漏。
- 5 個新增的稽核驗證測試均延伸自既有 happy-path 測試而非新建固件，覆蓋率上僅能代表「audit 呼叫確實發生」這個事實本身，不是對 20 處呼叫點逐一的窮舉驗證；其餘 15 處呼叫點的正確性依賴程式碼審查與既有完整回歸測試套件（全部通過、無 NPE）間接佐證，而非各自獨立的斷言驗證。
