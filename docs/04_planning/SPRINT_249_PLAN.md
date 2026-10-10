# Sprint 249 Plan — 商業事件通知：訂房確認與支付失敗（DEF-318 部分）

**Sprint**: Sprint 249
**日期**: 2026-10-10

## 1. 起點與範圍

### 1.1 起點

Sprint 248（店鋪成員管理 UI）完成後，使用者指示依先前計畫繼續 DEF-318（商業事件通知）。`DEFERRED_ITEMS_TRACKER.md` 原登記此項為「🟡 登記，未做，等使用者決定哪些要接」——除 Sprint 229 已接上的三種（訂房取消、未付款逾時取消、自動退款完成）之外，`ORDER_CONFIRMED`／`ORDER_PAID`／`ORDER_SHIPPED`／`ORDER_DELIVERED`／`ORDER_COMPLETED`、買家或賣家取消訂單、`BOOKING_CONFIRMED`／`BOOKING_REMINDER`、`PAYMENT_SUCCESS`／`PAYMENT_FAILED`、`REVIEW_REQUEST` 這些通知類型都存在、前端也有標籤，但沒有任何流程會送出——這是明確的產品決策點，不宜由 AI 片面選定範圍。

### 1.2 動工前的 `AskUserQuestion`

依讀碼結果（PRD 僅 US-001／US-014 兩項有明文驗收標準；其餘為「要不要做、何時做」的產品決策；`BOOKING_REMINDER` 是 PRD US-011 明訂的 Phase 2），呈現由小到大四個範圍選項，**使用者選擇「PRD 必要兩項（Recommended）」**：僅接上 `BOOKING_CONFIRMED`（US-001）與 `PAYMENT_FAILED`（US-014）。

### 1.3 方法

掛載方式沿用 Sprint 229 建立的 `BuyerNotificationService` 慣例（站內通知、盡力而為永不拋例外、交易提交之後才通知）：

- **`BOOKING_CONFIRMED`**：訂房付款成功時觸發。讀碼確認訂房「成功付款」有 3 條真實可達路徑（並非只有一條），三者都需掛載才不會重現「某條路徑悄悄沒有通知」的同型缺陷：
  1. `PaymentStateService.mockBookingPaymentSuccess`（新版 Mock 付款端點）
  2. `PaymentStateService.markBookingPaidByStripe`（Stripe webhook／回跳確認共用核心）
  3. `PaymentService.processBookingPayment`（舊版 `/v2/payments` 端點；讀碼確認 `booking:create` 權限買家本來就能呼叫，雖然前端目前未使用此路徑，但非技術上不可達，不可視為可以不修的死碼）
- **`PAYMENT_FAILED`**：僅接 `PaymentStateService.markStripePaymentFailed`（Stripe webhook `payment_intent.payment_failed`，PRD 明文的「金流阻斷」）。單一掛載點依 `Payment.orderId`／`bookingId` 分別通知，同時涵蓋訂單與訂房。
- 兩個新的交易提交後通知點（`PaymentStateService`、`PaymentService`）需要「在交易提交之後才呼叫」，比照 `BookingService` 既有的 `notifyAfterCommit` 寫法，各自在這兩個類別內新增同名私有方法（未抽共用工具類別：僅 2 個新用例，且不更動已經在用、沒有問題的 `BookingService` 寫法）。

## 2. 使用者決策與假設

### 2.1 已拍板（本輪 `AskUserQuestion`）

見 §1.2。

### 2.2 本輪假設（PRD／既有決議未明定）

| 題目 | 本輪採用 | 理由 |
|------|----------|------|
| Mock 模擬付款失敗端點（`PaymentStateService.mockPaymentFailure`，僅訂單側存在）是否也要通知 | **不通知** | PRD US-014 的「金流阻斷」對應的是真實金流拒絕交易，在本系統具體落地為 Stripe webhook `payment_intent.payment_failed`；Mock 模擬失敗是測試／QA 用途的工具端點，不是真實買家會撞到的「金流阻斷」情境，也沒有對應的訂房版本 |
| `BOOKING_CONFIRMED`／`PAYMENT_FAILED` 的 `NotificationType`、CHECK 約束、前端型別是否要新增 | **不需要** | 確認這兩個列舉值已存在於全部 4 個地方（`NotificationDto.NotificationType`、`Notification.NotificationType`、V86 的 CHECK 約束、前端 `notificationInbox.ts`），只是先前從未被任何流程送出；不重複 Sprint 229 新增 `REFUND_COMPLETED` 時的 migration 工作 |
| 前端是否需要改動 | **不需要** | `(auth)/notifications/page.tsx` 既有的連結渲染邏輯已是通用的（偵測 `data.orderId`／`data.bookingId`／`data.listingId` 決定顯示「查看訂單」／「查看訂房」連結），兩個新通知只要在 `data` 放對 key 即可自動取得「重試付款」連結（PRD US-014「提供重試連結」），不需新增任何前端程式碼 |

## 3. 實作內容（清單）

| 檔案 | 變更 |
|------|------|
| `backend/.../core/notification/BuyerNotificationService.java` | 新增 `notifyBookingConfirmed`／`notifyOrderPaymentFailed`／`notifyBookingPaymentFailed` 三個方法＋對應純函式文案 |
| `backend/.../core/payment/PaymentStateService.java` | 新增 `buyerNotificationService` 依賴＋`notifyAfterCommit` 私有方法；掛載 `mockBookingPaymentSuccess`、`markBookingPaidByStripe`（成功分支）、`markStripePaymentFailed`（新增 `notifyPaymentFailed` 私有方法依 orderId／bookingId 分流） |
| `backend/.../core/payment/PaymentService.java` | 新增 `buyerNotificationService` 依賴＋`notifyAfterCommit` 私有方法；掛載 `processBookingPayment` 成功路徑 |
| `backend/.../core/notification/BuyerNotificationServiceTest.java` | 新增 3 案例（`Sending`）；擴充 `FailureIsolation` 既有 2 案例涵蓋新方法 |
| `backend/.../core/payment/PaymentStateServiceTest.java` | `markStripePaymentFailed` 既有 5 案例補 `never()`／成功通知斷言；新增訂房分流案例 |
| `backend/.../core/payment/PaymentStateServiceBookingTest.java` | `mockBookingPaymentSuccess`／`markStripePaymentSucceeded` 既有案例補通知斷言；新增 `MarkStripePaymentFailedForBooking`（2 案例） |
| `backend/.../core/payment/PaymentStateServiceStripeTest.java`／`PaymentStateServiceStoreGuardTest.java`／`PaymentRefundIdempotencyKeyTest.java` | 建構子新增參數，補 mock（無行為斷言變更） |
| `backend/.../core/payment/PaymentServiceConcurrencyTest.java` | 補 mock；新增 `processBookingPayment_success_notifiesBuyer`（本專案先前完全沒有這支舊端點成功路徑的單元測試） |
| `backend/.../core/payment/PaymentServiceOwnershipTest.java`／`PaymentServiceStoreGuardTest.java`／`PaymentServiceRefundOwnershipTest.java`／`PaymentServiceRefundConcurrencyTest.java` | 補 `@Mock BuyerNotificationService`（`@InjectMocks` 解析用，無行為斷言變更） |
| `backend/.../integration/BookingPaymentIntegrationTest.java` | 既有 2 案例補通知斷言（Mock 成功、Stripe webhook 成功＋重送不重複通知）；新增 Stripe `payment_intent.payment_failed`（訂房）案例 |
| `backend/.../integration/OrderStripeCheckoutRetryIntegrationTest.java` | 新增 Stripe `payment_intent.payment_failed`（訂單）案例 |
| `backend/.../integration/BookingTimeoutIntegrationTest.java` | 修正 1 處既有斷言（見 §5） |
| `backend/.../integration/BookingNoShowIntegrationTest.java` | 修正 3 處既有斷言（見 §5） |
| [E-Commerce_FRD_v1.0.md](../01_requirements/E-Commerce_FRD_v1.0.md) | 更新 3 處「PRD US-001／US-014：未實作（DEF-318）」為已實作 |
| [DEFERRED_ITEMS_TRACKER.md](DEFERRED_ITEMS_TRACKER.md) | DEF-318 標記部分完成，其餘類型仍登記未做 |
| [RELEASE_TRACKER.md](RELEASE_TRACKER.md) | Sprint 249 列 |
| 本檔 | 計畫書 |

## 4. 守門與測試

| 層 | 內容 | 結果 |
|----|------|------|
| 單元 | `BuyerNotificationServiceTest`（22）、`PaymentStateServiceTest`＋`PaymentStateServiceBookingTest`＋其餘 3 個 `PaymentStateService*Test`（99）、`PaymentService*Test` 5 個檔案（26） | ✅ 全數通過，共 147 個相關測試 0 失敗 |
| 編譯 | 每次新增方法後 `mvn -o clean compile`／`clean test-compile`，確認有 `Compiling N source files` 字樣 | ✅ 確認為真實編譯結果（曾遇到一次 `Nothing to compile` 的 IDE 預編幻影成功，改用 `clean` 重新確認） |
| 整合（真實 PostgreSQL，直接針對本輪新增／修改的 4 個檔案跑一次完整輸出） | `BookingPaymentIntegrationTest`（17）、`OrderStripeCheckoutRetryIntegrationTest`（2）、`BookingTimeoutIntegrationTest`（10）、`BookingNoShowIntegrationTest`（4） | ✅ 33 個測試 0 失敗／0 錯誤；日誌確認 `type=PAYMENT_FAILED` 通知確實依 `orderId`／`bookingId` 正確分流寄出 |
| 全量回歸 | `mvn -o clean verify`（改動生產邏輯，依規範跑全量） | ✅ BUILD SUCCESS：整合 811（0 回歸）；checkstyle（main+test）0 違規；PMD 通過 |
| 前端 | 無任何檔案變更（見 §2.2），略過前端建置／測試 | N/A |

## 5. 既有整合測試因新增通知而需修正的斷言（誠實揭露，非新缺陷）

新增 `BOOKING_CONFIRMED` 通知後，任何「先付款再做別的事」的既有整合測試都會多收到一則通知，原本斷言「無通知」或「恰好 N 則」的測試需要相應調整，否則會被我自己的新功能攻陷：

1. `BookingTimeoutIntegrationTest.paidBooking_isUntouched`：原斷言「已付款的訂房沒有任何通知」（`isEmpty()`），改為斷言「恰好一則 `BOOKING_CONFIRMED`、沒有逾時取消通知」。
2. `BookingNoShowIntegrationTest.paidBookingPastGracePeriod_isCancelledWithoutRefund`：原斷言「取消後通知買家一次」（`hasSize(1)`），改為「付款確認＋取消通知共兩則」，並調整索引指向第二則。
3. `BookingNoShowIntegrationTest.paidBookingWithinGracePeriod_isUntouched`：原斷言「不通知」（`isEmpty()`），改為「只有付款確認、沒有 no-show 通知」。
4. `BookingNoShowIntegrationTest.runningTwice_cancelsAndNotifiesOnce`：原斷言「只通知一次」（`hasSize(1)`），改為「付款確認＋取消通知（重複執行不重複）共兩則」。

`BookingCancellationRefundIntegrationTest.java` 的通知斷言另外用 `notification_type` 過濾查詢，不受影響，未修改。
