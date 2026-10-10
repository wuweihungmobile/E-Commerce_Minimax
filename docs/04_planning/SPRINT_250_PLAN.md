# Sprint 250 Plan — 商業事件通知：訂單生命週期全集（DEF-318 剩餘部分）

**Sprint**: Sprint 250
**日期**: 2026-10-10

## 1. 起點與範圍

### 1.1 起點

Sprint 249 完成 DEF-318 的 PRD 必要兩項（`BOOKING_CONFIRMED`／`PAYMENT_FAILED`）後，使用者說「請繼續完成任務」。`DEFERRED_ITEMS_TRACKER.md` 的 DEF-318 仍登記其餘類型「仍是產品決策，等使用者決定要不要接上」。依 Sprint 229/230/231 建立的慣例（「繼續」不等於替使用者決定產品範圍，需以 `AskUserQuestion` 呈現選項），本輪動工前共兩輪確認。

### 1.2 動工前的兩輪 `AskUserQuestion`

**第一輪（範圍）**：PRD 檢索確認剩餘 8 種類型（`ORDER_CONFIRMED`／`ORDER_PAID`／`ORDER_SHIPPED`／`ORDER_DELIVERED`／`ORDER_COMPLETED`／`ORDER_CANCELLED`／`PAYMENT_SUCCESS`／`REVIEW_REQUEST`）均無 PRD 驗收標準明文要求（不同於 Sprint 249 的兩項）；`BOOKING_REMINDER` 因 PRD US-011 明文列為 Phase 2，未列入選項。使用者選擇**「全部 8 種都做」**。

**第二輪（讀碼後發現的 2 個設計缺口，需在動工前定案）**：

1. `PAYMENT_SUCCESS` 與 `ORDER_PAID` 讀碼後發現沒有各自對應到不同事件——若同一次付款成功時兩者都發，買家會收到兩則意思重複的站內通知。使用者選擇**「兩則都發」**（接受重複，不視為缺陷）。
2. `REVIEW_REQUEST` 沒有任何現成觸發點；「COMPLETED 當下立即發」與「COMPLETED 後延遲幾天發」實作量差異巨大（後者需新建排程機制）。使用者選擇**「COMPLETED 當下立即發，訂單與訂房都適用」**。

### 1.3 讀碼確認的真實狀態機與呼叫點

`Order.OrderStatus`：`CREATED → PAID → CONFIRMED → SHIPPING → DELIVERED → COMPLETED`（終態），`CREATED/PAID/CONFIRMED → CANCELLED → REFUNDING → REFUNDED`（終態）。比照 Sprint 249「成功付款不是只有一條路徑」的教訓，逐一讀碼確認每個轉換的**所有**真實呼叫點（不只看起來明顯的那一條）：

| 通知類型 | 觸發狀態轉換 | 真實呼叫點（全部） |
|----------|--------------|---------------------|
| `ORDER_PAID` + `PAYMENT_SUCCESS` | `CREATED→PAID` | ① `PaymentStateService.mockPaymentSuccess`（Mock）② `PaymentStateService.markOrderPaidByStripe`（Stripe webhook）③ `PaymentService.processOrderPayment`（舊版 `/v2/payments`）——與訂房付款三路徑完全對稱，三者皆**先前零通知** |
| `ORDER_CONFIRMED` | `PAID→CONFIRMED` | `OrderService.updateOrderStatus`（唯一路徑；`CONFIRMED` 目前在全專案沒有任何其他寫入點） |
| `ORDER_SHIPPED` | `CONFIRMED→SHIPPING` | ① `OrderService.updateOrderStatus`（賣家直接以 PATCH 指定 `SHIPPING`，既有 `deductOnShipment` 分支）② `LogisticsService.createLogistics`（賣家建立物流單，CAS 搶占轉換）——兩條路徑都要掛 |
| `ORDER_DELIVERED` | `SHIPPING→DELIVERED` | ① `OrderService.updateOrderStatus`（賣家直接以 PATCH 指定 `DELIVERED`）② `LogisticsService.updateLogisticsStatus`（標記物流 `DELIVERED` 時同步訂單狀態）——兩條路徑都要掛 |
| `ORDER_COMPLETED` + `REVIEW_REQUEST`（訂單側） | `DELIVERED→COMPLETED` | `OrderService.updateOrderStatus`（唯一路徑） |
| `ORDER_CANCELLED` | `*→CANCELLED`（買家／賣家主動，非逾時） | ① `OrderService.cancelOrder`（買家／管理員專用端點）② `OrderService.updateOrderStatus`（賣家以 PATCH 指定 `CANCELLED`，既有 `compensateCancellation` 分支）——逾時取消（`cancelExpiredUnpaidOrder`）**已有**通知（Sprint 229 `notifyOrderPaymentTimeout`），不重複掛載 |
| `REVIEW_REQUEST`（訂房側） | `CHECKED_OUT→COMPLETED` | `BookingService.checkOut`（唯一路徑，同交易內自動完成） |

`NotificationDto.NotificationType`／`Notification.NotificationType`／`NotificationTemplate.NotificationType`／前端 `notificationInbox.ts` 四處確認皆已存在這 8 個列舉值（連同 `ORDER_CANCELLED`），**不需新 migration、不需前端程式碼變更**（沿用 Sprint 249 確認的通用連結渲染邏輯）。

### 1.4 新增依賴注入

`OrderService`、`LogisticsService` 目前未注入 `BuyerNotificationService`（`PaymentService`／`PaymentStateService`／`BookingService` 已有）。兩者皆為 `@RequiredArgsConstructor`，新增 `private final BuyerNotificationService buyerNotificationService;` 欄位即可，並各自新增比照既有模式的私有 `notifyAfterCommit` 方法（未抽共用工具類別，沿用 Sprint 249「每個類別各自一份」的既有慣例）。

## 2. 實作順序（逐一編譯＋測試，不累積開發）

1. `BuyerNotificationService` 新增 8 個 `notifyXxx` 方法＋對應純函式文案 → 編譯＋單元測試
2. `PaymentStateService`／`PaymentService` 掛載 `ORDER_PAID`／`PAYMENT_SUCCESS`（3 呼叫點） → 編譯＋單元測試
3. `OrderService` 新增依賴＋掛載 `ORDER_CONFIRMED`／`ORDER_SHIPPED`（PATCH 路徑）／`ORDER_DELIVERED`（PATCH 路徑）／`ORDER_COMPLETED`／`REVIEW_REQUEST`（訂單側）／`ORDER_CANCELLED`（兩路徑） → 編譯＋單元測試
4. `LogisticsService` 新增依賴＋掛載 `ORDER_SHIPPED`／`ORDER_DELIVERED`（物流路徑） → 編譯＋單元測試
5. `BookingService` 掛載 `REVIEW_REQUEST`（訂房側，`checkOut`） → 編譯＋單元測試
6. 既有整合測試因新通知而需修正的斷言（比照 Sprint 249 §5 的「先付款再做別的事」類型斷言）；全量 `mvn clean verify`
7. 更新 FRD／`DEFERRED_ITEMS_TRACKER.md`／`RELEASE_TRACKER.md`；`make validate-release`；push

## 4. 守門與測試

| 層 | 內容 | 結果 |
|----|------|------|
| 單元 | `BuyerNotificationServiceTest`（+22）、`OrderServiceTest`（+10）、`PaymentStateServiceTest`／`PaymentStateServiceStripeTest`／`PaymentServiceConcurrencyTest`（+6 斷言／案例）、`LogisticsServiceTest`（+4）、`BookingServiceCheckInOutTest`（+1） | ✅ 全量 `mvn -o test` 2190 個測試 0 失敗 |
| 編譯 | 每個步驟後 `mvn -o clean compile`／`clean test-compile`，確認 `Compiling N source files` | ✅ 全程確認為真實編譯 |
| 整合（真實 PostgreSQL） | 針對新/改動路徑的 11 個候選檔（104 個測試）先單獨跑一次 | ✅ 0 失敗 |
| 全量回歸（本機純 JVM，不經 act） | `mvn -o clean verify`（改動生產邏輯，依規範跑全量） | ✅ 單元 2190／整合 813／0 失敗；checkstyle（main+test）0 違規；PMD 通過 |
| 前端 | 無任何檔案變更（現有收件匣連結渲染已是依 `data.orderId`／`bookingId` 的通用邏輯，讀碼確認），略過前端建置／測試 | N/A |

## 5. 既有測試因新通知而需修正的斷言（誠實揭露，非新缺陷）

新增 `ORDER_PAID`／`PAYMENT_SUCCESS` 後，任何「先付款再做別的事」的既有測試都會多收到兩則通知：

1. `OrderTimeoutIntegrationTest.paidOrder_isUntouched`：原斷言「已付款的訂單沒有任何通知」（`isEmpty()`），改為斷言「恰好 `ORDER_PAID`／`PAYMENT_SUCCESS` 兩則、沒有逾時取消通知」。
2. `OrderAutoRefundIntegrationTest` 的通知查詢本就以 `notification_type = 'REFUND_COMPLETED'` 過濾，不受影響，未修改（比照 Sprint 249 對 `BookingCancellationRefundIntegrationTest` 的判斷）。

另外，新增 `BuyerNotificationService` 依賴到 `OrderService`／`LogisticsService` 的建構子後，以下純 Mockito 單元測試檔案原本透過 `@InjectMocks` 的「盡力注入」機制，從未宣告對應 `@Mock`（因此欄位先前為 `null`，只是從未真正被呼叫到）：`OrderServiceTest`、`OrderStoreCheckoutTest`、`OrderPromoCodeTest`、`LogisticsServiceTest`、`LogisticsServiceTenantAccessTest`、`LogisticsServiceCancelTest`、`LogisticsServiceOwnershipTest`、`BookingServiceCheckInOutTest`（後者是既有的 `BuyerNotificationService` 依賴，非本輪新增）。全數補上 `@Mock private BuyerNotificationService buyerNotificationService;`。

## 6. 排查記錄：一次日期邊界瞬斷（非回歸）

第一次 `mvn -o clean verify`（約 00:28 起跑，整合測試階段耗時甚長）出現單一失敗：`BookingApiRealStackIntegrationTest.calendar`，`$.data[0].date` 預期 `2026-11-10` 實際 `2026-11-11`。依 CLAUDE.md 規則先確認檔案與本輪變動**完全無關**（該測試檔案屬房源日曆／訂房查詢，本輪只動通知；`grep` 確認零重疊），且該測試的 `day(n)` 輔助方法以 `LocalDate.now().plusDays(n)` 算相對日期，是典型「跨日期邊界」症狀。單獨重跑 `BookingApiRealStackIntegrationTest`（16 個測試，含 `calendar`）：0 失敗。判定為單次執行耗時過長跨過午夜邊界的瞬斷，與本輪通知程式碼無關；重新完整跑一次 `mvn -o clean verify`：單元 2190／整合 813／0 失敗，checkstyle／PMD 皆通過，確認全綠後才繼續。

## 7. `make validate-release` 與 push

`make validate-release`：act CI＋schema 對齊＋E2E（141 通過／4 略過／0 失敗）全數通過，寫入 FULL 記錄；commit `172f203` 於記錄 0 分鐘內 push，pre-push 輕量守門沿用該記錄直接放行。

雲端 CI run **38073415043**：三個 job 皆 `success`（Backend Unit Tests、Backend Integration Tests & Package、Frontend Lint & Build）。**排查記錄**：第一次 `gh run watch --exit-status` 在「Backend Integration Tests & Package」仍 `in_progress`（卡在 `Run Integration Tests` 步驟）時因一次網路瞬斷（`connection reset by peer`）提早以 exit code 0 退出，若照字面判讀會誤判為「已通過」；改用 `gh run view --json status,conclusion,jobs` 直接查詢才確認真正完成時間點與逐 job 結論，重新 `gh run watch` 一次後以同樣的 JSON 查詢交叉確認皆為 `success` 才採信。

## 8. 待辦

- DEF-317（EMAIL／SMS／PUSH 通道不會真的送出）與 `BOOKING_REMINDER`（PRD US-011，Phase 2）皆非本輪範圍，仍登記未做。
