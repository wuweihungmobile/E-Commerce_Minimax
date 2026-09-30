# Sprint 221 Plan — 訂房付款後端（DEF-303 (1)）＋ 訂單「按返回再付款」會 500（DEF-310）

**Sprint**: Sprint 221
**日期**: 2026-09-30

## 1. 起點

使用者對 DEF-303 的回覆：「前端訂房流程完全沒有付款步驟。==> 以下請處理，符合邏輯」。Sprint 217~220 依序處理了其他項目，本輪開始做訂房付款，分段進行：**Sprint 221 = 後端**（本輪）、Sprint 222 = 前端付款步驟。

## 2. 動手前的現況核對

- **訂房其實付不了款**：唯一的付款入口是舊版 `POST /v2/payments`，但 `PaymentDto.PaymentRequest.orderId` 是 `@NotNull`（驗證訊息卻寫「Order ID or Booking ID is required」）。只帶 `bookingId` 的請求在 controller 驗證階段就被 400 擋下，`PaymentService.processBookingPayment` 從 HTTP 根本打不到。
- **訂房狀態只有三種寫入者**：建立（CREATED）、取消（CANCELLED）、舊版付款／退款（打不到／管理員）。沒有任何程式路徑會走到 CONFIRMED 之後的狀態。
- **訂房付款狀態端點存在但沒人用**：`GET /v2/orders/bookings/{id}/payment`（放在 `OrderPaymentController` 底下，回傳 `OrderPaymentStateDto`，以 `orderId` 欄位裝訂房 id），前端零呼叫點，回應沒有 `paymentProvider`。
- **Stripe 路徑全部只認訂單**：`CheckoutSessionRequest.orderId` 被直接 `.toString()`、Stripe metadata 只有 `order_id`、`markStripePaymentSucceeded` 只更新訂單。

## 3. 設計（沿用訂單付款的模式）

| 項目 | 作法 |
|---|---|
| 端點 | 新增 `BookingPaymentController`：`POST /v2/bookings/{id}/pay`（Mock）、`POST /v2/bookings/{id}/pay/checkout`（Stripe 發起）、`GET /v2/bookings/{id}/pay/checkout/return?sessionId=`（回跳確認）。付款狀態沿用既有 `GET /v2/orders/bookings/{id}/payment`（不新增重複端點；補上 `paymentProvider`、`refundedAmount`） |
| 權限 | 付款動作放行 `booking:create` **或** `booking:update`。買家有 `booking:create`／`booking:cancel` 但**沒有** `booking:update`——與 DEF-298 是同一個坑，只要求 update 的話一般買家付款一律 403。能不能付「這一筆」仍由服務層本人／ADMIN 檢查決定 |
| Mock 付款 | 啟用 Stripe 時拒絕（E-6004，DEF-299 的同一道防線）。先以條件式 UPDATE 搶占 `CREATED → PAID`，搶到才建立付款紀錄（`BookingRepository.updateStatusIfCurrent`），併發付款只有一個成功 |
| Stripe | `CheckoutSessionRequest` 新增 `bookingId`（與 `orderId` 二擇一），Stripe metadata 帶 `booking_id`；付款紀錄以 `bookingId` 建立，冪等鍵 `BOOKING-CHECKOUT-<bookingId>`（53 字元，符合 `VARCHAR(64)`）；回跳確認只認屬於這筆訂房的 session（訂單版沒有這個檢查） |
| webhook | `markStripePaymentSucceeded` 新增訂房分支：條件式 UPDATE `CREATED → PAID` |
| 冪等 | 依 PRD DEF-285 的決策，金流端點以伺服器端狀態（條件式 UPDATE／唯一索引）為準，不要求用戶端冪等標頭 |
| 不動的東西 | `BookingService.cancelBooking`（取消與退款是下一段的事，見 §7）、舊版 `POST /v2/payments` 的訂房分支（打不到，留著不動） |

## 4. 實作中的發現

### 4.1 DEF-310：訂單「按返回再付款」在真實資料庫回 500（新登記並結案）

寫訂房的 Stripe 發起時照抄訂單版的「寫入付款紀錄、捕捉 `DataIntegrityViolationException`、記 log 後照常回傳」，寫測試時心想「同一筆訂房連續發起兩次」會怎樣，先用一個暫時的真實資料庫實驗在**既有的訂單版**上跑：

```
second initiateStripeCheckout => UnexpectedRollbackException:
  Transaction silently rolled back because it has been marked as rollback-only
```

**原因**：違反唯一索引（V79 `payments.idempotency_key`）的例外發生在 repository 的交易代理內，Spring 在例外穿出參與中的交易方法時就把外層交易標成 rollback-only。外層方法即使捕捉了例外，提交時仍拋 `UnexpectedRollbackException`，請求以 500 收場；何況在 PostgreSQL，交易一旦有語句失敗，之後的語句本來就都會失敗。原本的單元測試以 mock 讓 `saveAndFlush` 丟例外、斷言「仍回傳 session」，mock 沒有交易，測不到這件事。

**影響**：買家在 Stripe 付款頁按返回、回到訂單再按一次「前往付款」——這是正常操作，不是罕見競態——會得到 500。Stripe 尚未啟用，所以還沒有人撞到；啟用前必須修。

**修法**（訂單、訂房兩條路徑相同）：先以冪等鍵查有沒有這一列，有就不寫；查與寫之間的極小競態由唯一索引兜底，輸家收到可重試的 `E-6005`（422）而非 500。**紅燈實測**：`OrderStripeCheckoutRetryIntegrationTest` 修正前 `UnexpectedRollbackException`，修正後綠燈。原本那個 mock 測試改為兩個：既有列時不再寫入、極小競態回 `E-6005`。

### 4.2 open-in-view 讓「提交後再讀」也讀到舊實體

回跳確認在一個交易內用條件式 UPDATE 改付款與訂房（不經過 persistence context）。先前已載入的付款實體因此是舊的（`PROCESSING`）。最初想讓控制器在交易提交後另外呼叫一次狀態查詢，結果 HTTP 層測試仍讀到 `PROCESSING`——`spring.jpa.open-in-view` 預設開啟，同一個 request 的多個交易共用同一個 persistence context，「提交後再讀」並不是新的 persistence context。實際回應會是「訂房 `PAID`、付款 `PROCESSING`」自相矛盾。

**修法**：更新後對已載入的付款實體 `EntityManager.refresh`，再組回應；service 層與 HTTP 層測試在拿掉這一行後都紅燈（`was "PROCESSING"`）。**注意**：不能改成「更新後對實體 setStatus」——`Payment` 沒有 `@DynamicUpdate`，實體一旦變髒，flush 會用記憶體裡舊的欄位整列覆寫（例如 `stripe_payment_intent_id`、併發退款寫入的 `refunded_amount`）。

### 4.3 測試資料庫沒有 Flyway 的唯一索引

integration-test profile 以 `ddl-auto=update` 建表，**沒有** V79 的 `payments.idempotency_key` 唯一索引。第一版併發測試因此寫出 5 列重複紀錄，`findByIdempotencyKey` 拋 `IncorrectResultSizeDataAccessException`。與生產一致的行為必須在測試裡補上那條索引（只涵蓋本類別產生的鍵，不影響其他測試）。

## 5. 測試

- **`PaymentStateServiceBookingTest`**（新，21 案例，Mockito）：Mock 付款（搶占成功／搶占失敗不建立付款紀錄／Stripe 啟用時拒絕且不嘗試轉換／非待付款／已有成功付款／別人的訂房／找不到）、狀態的 `paymentProvider` 與已退款金額、Stripe 發起（請求內容、PROCESSING 付款紀錄、未啟用、各種守門、再次發起不重複寫入、極小競態回 `E-6005`）、回跳確認（已付款／未付款／別筆訂房的 session／找不到／早已成功）、webhook 訂房分支（轉 PAID／訂房已不是待付款時留稽核／重送不動作）。
- **`BookingPaymentIntegrationTest`**（新，16 案例，真實 PostgreSQL＋真實 Redis，Stripe 閘道以 `@MockBean`）：Mock 付款端到端、啟用 Stripe 拒絕 Mock、別人付款、重複付款、已取消訂房不能付、**8 執行緒同時付款恰好一個成功**、Stripe 發起（DB 內容與請求內容）、連續發起兩次、**8 執行緒同時發起**、webhook（含重送冪等）、回跳確認再收 webhook、**回跳確認與 webhook 同時抵達只轉換一次**、拿別筆訂房的 session 被拒、**付款前先取消訂房後才收到付款成功——付款 SUCCESS、訂房維持 CANCELLED、稽核紀錄可查**。
- **`BookingPaymentApiIntegrationTest`**（新，5 案例，真實 JWT＋**生產**權限表）：一般買家（無 `booking:update`）付得了自己的訂房、別人 403、未登入 401、啟用 Stripe 拒絕 Mock、Stripe 發起＋回跳確認。
- **`OrderStripeCheckoutRetryIntegrationTest`**（新，1 案例）：DEF-310 的回歸測試。
- **`StripePaymentGatewayTest`**（+3：TC-S013～015）：不 mock 組請求那層，用 WireMock 看**實際送出**的請求——訂房以 `metadata[booking_id]`（不帶 `order_id`）、冪等鍵沿用呼叫端給的值、訂單請求維持原樣、兩者都沒有時在呼叫 Stripe 前就拒絕。
- **突變驗證**（每一個都讓對應測試轉紅，已還原）：①拿掉 webhook 的訂房分支（3 個測試紅）；②Mock 付款忽略搶占結果（併發測試紅：7 個輸家只有 3 個被拒）；③發起結帳不先查、直接寫（連續發起兩次紅）；④拿掉 `refresh`（service 層與 HTTP 層各 1 紅，`was "PROCESSING"`）；⑤把訂房的 metadata 鍵改成 `order_id`（TC-S013 紅）。

## 6. 驗證結果

全量 `mvn -o clean verify`（`make test-db-up` 已啟動真實 postgres/redis）：單元 **1872**（+25）/整合 **641**（+22）/0 failures/0 errors/0 skipped；checkstyle 0 violations；PMD 通過；`BUILD SUCCESS`。前端沒有改動。

## 7. 已知限制與未做

- **已付款訂房取消後錢沒有退（新登記 `DEF-312`，等使用者決定）**：`BookingService.cancelBooking` 沒有動付款——PAID 的訂房取消後，日曆釋放、優惠券退還，但付款維持 SUCCESS，也沒有任何訂房退款端點（`refundOrderPayment` 只認訂單）。PRD Q14 已有明確規則（取消前 ≥ 24 小時全額退款、< 24 小時不退，商家主動取消一律全額退），但這是自動移動真實金錢，與 DEF-303 (5)（訂單取消後的自動退款）同一類，未在你回覆的範圍內，**未實作**。Mock 模式沒有實際金額，沒有立即影響；**啟用 Stripe 之前必須先決定**。舊版 `POST /v2/payments/refund` 對訂房付款：把付款標成 REFUNDED、訂房標成 CANCELLED，但**不釋放日曆、不退優惠券**，Stripe 付款也不會呼叫 Stripe（DEF-303 (3)）。
- **訂房沒有未付款逾時取消（新登記 `DEF-311`）**：與 DEF-302 同一類，你回覆的「24 小時」針對訂單。現況訂房從來不會過期（過去也不會——沒有付款入口時每一筆都停在 CREATED），直接套用 24 小時規則會在部署後把**所有既有的 CREATED 訂房**（從來沒有付款入口可用）全部取消、釋放日曆，所以需要區分新舊訂房。見 §8。
- **付款成功但訂房已不能付款**（買家在 Stripe 頁停留時把訂房取消）：付款 SUCCESS、訂房維持 CANCELLED（日曆可能已被別人訂走，不拉回已付款）。這次補上 `STRIPE_PAYMENT_BOOKING_NOT_PAYABLE` 稽核與 `log.error`，**不再靜默**，但自動處理（退款？改期？）仍是 DEF-308 的產品決定，訂房與訂單同一類。
- **訂房建立 Stripe 結帳後被 API 改期**：前端沒有訂房編輯功能，只有直接打 API 才會發生；付款金額（建立當下的訂房總額）與改期後重算的總額可能不一致。未處理（推論、未重現）。
- **Stripe session 過期（>24 小時）後重新付款**：冪等鍵超過 24 小時失效會開新 session，但本地付款紀錄依冪等鍵只有一列，新 session 的付款成功 webhook 找不到對應付款。訂房若有未付款逾時（DEF-311）則不會發生；訂單版有 24 小時逾時所以同理。未處理。
- 前端沒有改動；沒有以真實 Stripe 驗證。

## 8. 下一步

- Sprint 222：前端付款步驟（結帳完成後顯示付款區塊、`bookings/[id]` 付款卡片、Stripe 成功／取消頁、E2E）。
- DEF-311（訂房逾時）預計以 `bookings.payment_due_at` 區分新舊訂房（舊訂房為 NULL，永不逾時）——需要 Flyway 遷移，另開 Sprint。
- DEF-312（訂房取消退款）等使用者決定。
