# Sprint 225 Plan — 訂房未付款逾時取消（DEF-311）

**Sprint**: Sprint 225
**日期**: 2026-10-01

## 1. 起點

使用者貼上 Sprint 223／224 的總結，在「需要你決定」的四項旁逐一回覆，並說「請繼續執行任務！」：

| 項目 | 使用者回覆（原文） | 排入 |
|------|------------------|------|
| `DEF-312`（已付款訂房取消不退款） | 「請對齊PRD」 | Sprint 227 |
| `DEF-311`（訂房未付款逾時） | 「依照建議」 | **本輪** |
| `DEF-308`（付款成功但訂單／訂房已不可付款）、`DEF-303` (5)（取消已付款訂單後實際退款） | 「請依照最佳化進行！」 | Sprint 226（訂單側）、Sprint 227（訂房側） |
| 依賴審計要不要進 CI 關卡 | 「請依照最佳化進行！」 | Sprint 228 |

排序理由：自動退款執行器（226）是訂房取消退款（227）的前提；`DEF-311` 最小且獨立，先做。

「依照建議」指的是上輪 `DEF-311` 的建議做法：`bookings.payment_due_at`——新訂房建立時設建立時間＋24 小時，舊訂房為 NULL 永不逾時。

## 2. 設計

**為什麼不能直接套用訂單的規則**：訂單逾時用 `created_at < now - 24h`。訂房在 Sprint 221 之前沒有付款入口，所有既有訂房都停在 `CREATED`；直接套用會在部署後第一輪排程把它們全部取消並釋放日曆。所以訂房自己記錄付款期限：

- 新訂房：`payment_due_at = 建立時間 + 24 小時`（`BOOKING_PAYMENT_TIMEOUT_HOURS`，預設 24）。單一類型結帳與合併結帳都走 `buildBookingCore`，一處設定兩條路徑都涵蓋。
- 歷史訂房：不回填（NULL）＝永不因逾時被取消。它們從來沒有機會付款，不能追溯適用。（SQL 裡 `NULL < x` 為未知，不會被 `payment_due_at < now` 撈到，語意上不需要額外判斷。）

**排程與取消**沿用 Sprint 219（`OrderTimeoutService`）的做法，差別只在判斷條件：

- `BookingTimeoutService`（每 5 分鐘）逐筆取消「`CREATED`、付款期限已過、沒有成功付款、24 小時內沒開始過 Stripe 結帳」的訂房；逐筆各自一個交易，一筆失敗不影響其他筆；整批沒進展就停。
- 取消把判斷與狀態轉換寫在同一條條件式 UPDATE（`BookingRepository.cancelIfPaymentExpired`），與買家付款（`CREATED → PAID` 的 CAS）搶同一個狀態，恰好一邊成功。
- 補償與買家自己取消相同：釋放日曆、退還優惠券額度；操作者為系統，稽核使用者為 null。日曆釋放（`FOR UPDATE NOWAIT`）失敗時整個交易回滾，下一輪再試。
- 「24 小時內開始過 Stripe 結帳就不取消」：Stripe Checkout 工作階段預設建立後 24 小時才到期，太早取消會讓買家仍能付款成功、訂房卻已取消。此排除由 `BOOKING_STRIPE_SESSION_HOURS`（預設 24，Stripe 的預設值）決定。

前端：訂房詳情與結帳完成畫面的付款卡片顯示「請於 … 前完成付款，逾時預訂將自動取消並釋出日期」；歷史訂房（沒有期限）不顯示這句話——後端不會取消它，畫面不能說謊。

## 3. 實作

- `V84__Bookings_Payment_Due_At.sql`：`bookings.payment_due_at TIMESTAMPTZ`（可 NULL）＋待付款部分索引 `idx_bookings_payment_due`。
- `Booking.paymentDueAt`、`BookingDto.BookingResponse.paymentDueAt`。
- `BookingRepository.findExpiredUnpaidBookingIds`／`cancelIfPaymentExpired`（JPQL，含 `NOT EXISTS` 子查詢）。
- `BookingService.buildBookingCore` 寫入期限；`cancelExpiredUnpaidBooking`（CAS → 釋放日曆 → 退還優惠券 → 稽核）。
- `BookingTimeoutService`（`@Scheduled`）；`application.yml` 的 `app.booking-timeout.*`；`SETTLEMENT_JOB_RUNBOOK.md` 補設定清單。
- 前端：`Booking.paymentDueAt`、`BookingPaymentCard` 新增 `paymentDueAt` 屬性。
- 文件：`API_M06_Booking.md` 補 `paymentDueAt`；SRD 資料庫文件守門通過（`bookings` 屬 §2.6 已知範圍外資料表，不需改 DDL）。

## 4. 驗證

**測試**

- 單元：`BookingTimeoutServiceTest`（5 案例：結帳截止時間、批次、失敗隔離、無進展停止、每輪上限）；`BookingServiceCreateBookingTest` 新增「新訂房寫入期限＝建立時間＋24 小時且回應帶出同一個時間」；`SchedulingConfigTest` 已知排程清單加入新排程。
- 整合（真實 PostgreSQL＋真實 Redis，`BookingTimeoutIntegrationTest` 10 案例）：新訂房帶期限（欄位對應）；期限已過 → 取消並釋放日曆、退還優惠券、稽核記為系統操作；期限未到不動；**歷史訂房（NULL）建立 30 天也不取消**；已付款不動；24 小時內開始過 Stripe 結帳不取消、工作階段也過期才取消；已有成功付款的 `CREATED` 訂房不取消；取消用的條件式 UPDATE 自己把關（不依賴候選查詢）；被取消後買家再付款 → `E-5011` 且無付款紀錄；重複執行只補償一次。取消的 JPQL 含 `NOT EXISTS` 子查詢，mock Repository 的測試碰不到，這些必須在真實資料庫跑過。
- 前端 mock E2E：`E2E-BPAY-11`（有期限顯示、歷史訂房不顯示）；真實後端 E2E `E2E-BPAYR-01` 新增「新訂房期限約 24 小時後」與「冪等重放（從 Redis 反序列化）期限時間不變」。

**突變驗證**（整合測試，逐一套用後跑 `BookingTimeoutIntegrationTest`，每個都被抓到）：

| 突變 | 失敗的測試 |
|------|-----------|
| M1 拿掉 `paymentDueAt` 的寫入 | `newBooking_carriesPaymentDeadline` |
| M2 歷史訂房以建立時間當期限（`COALESCE(payment_due_at, created_at)`） | `legacyBookingWithoutDeadline_neverExpires` |
| M3 取消用的 UPDATE 不排除已有付款／進行中的結帳 | `cancelExpiredUnpaidBooking_guardsItself` 等 5 個 |
| M4 取消時不釋放日曆 | `expiredUnpaidBooking_isCancelledAndCompensated` 等 2 個 |
| M5 取消時不退優惠券 | `expiredUnpaidBooking_isCancelledAndCompensated` 等 2 個 |

M2 原本只被專屬案例抓到：`cancelExpiredUnpaidBooking_guardsItself` 的歷史訂房固件是「剛建立」的，突變版規則下期限＝建立時間仍晚於基準時間，所以不會被取消。已把該固件的 `created_at` 改成 30 天前，讓它也能抓到這類突變。

**全量**（`mvn -o clean verify`，13 分 37 秒）：單元 1883（+6）／整合 651（+10）／0 失敗；checkstyle 0 violations、PMD 通過。`make validate-schema`（entity ↔ Flyway，`ddl-auto=validate`）與 `make validate-schema-doc`（SRD 文件）通過。`tsc --noEmit` 乾淨、`eslint` 對變更檔案 0 error（3 個警告為既有）；`at-booking-payment.spec.ts` 11 案例全過。

## 5. 決策與已知限制

- **付款期限一律 24 小時、不考慮入住日**：入住日前一天才訂、或當天訂的房，也有 24 小時付款期（可能晚於入住時間）。與訂單的 24 小時同一原則，沒有另訂規則；若要「期限不得晚於入住時間」是另一個產品決定。
- **逾時後沒有通知買家**（PRD US-014 的通知，同 Sprint 219 訂單的未做項目）。
- **沒有對真實 Stripe 驗證「24 小時內開始過結帳就不取消」的假設**：依 Stripe 文件，Checkout 工作階段預設 24 小時到期；`BOOKING_STRIPE_SESSION_HOURS` 可調。
- `next dev`（本輪用它跑前端 mock E2E）會把 `frontend/AGENTS.md` 重寫成新版 Next 的標記區塊；內容是 Next 16.3.x 自己產生的，一併提交以維持工作樹乾淨。
- 順帶觀察（不在本輪範圍，未處理）：`BookingPaymentCard` 已付款分支在 `<p>` 內放了 `Badge`（`<div>`），dev 模式會印出 DOM 巢狀警告；該分支是用戶端在資料載入後才渲染，不會造成 SSR hydration 差異。

## 6. 後續

- Sprint 226：`DEF-303` (5)＋`DEF-308` 訂單側——自動退款執行器、取消已付款訂單（`REFUNDING`）自動退款、Stripe 遲到付款（訂單已取消）自動轉 `REFUNDING`。
- Sprint 227：`DEF-312`——訂房取消依 PRD Q14 退款（≥24 小時全額、<24 小時不退、商家取消全額）；`DEF-308` 訂房側。
- Sprint 228：依賴審計改為每週排程（不擋 push）。
- 已登記未修：`DEF-315`（同一秒簽發的 Refresh Token 位元組相同）。
