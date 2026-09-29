# Sprint 220 Plan — 同一張訂單有多筆付款紀錄時，付款狀態端點與週結算會壞掉（DEF-309）

**Sprint**: Sprint 220
**日期**: 2026-09-30

## 1. 起點

Sprint 219 啟用了排程，其中包含每週一的結算單。規劃下一步（訂房付款）時讀到 `PaymentRepository`：`findByOrderId` 回傳單一 `Optional<Payment>`，而 `payments.order_id` 沒有唯一約束。這與剛啟用的週結算有直接關係——`SettlementGenerator.buildRefundedAmountMap` 對每張訂單都呼叫它。

## 2. 缺陷

`POST /v2/orders/{id}/pay/fail`（Mock 模式訂單詳情頁的「模擬付款失敗」按鈕）會建立一筆 `FAILED` 付款紀錄，訂單維持 `CREATED`；買家接著按「確認付款」再建一筆 `SUCCESS`。同一個 `order_id` 於是有兩列。`findByOrderId` 遇到兩列拋 `IncorrectResultSizeDataAccessException`（Spring Data 對 `Optional` 回傳型別的行為）。

受影響的兩個呼叫點：

1. **`PaymentStateService.getOrderPaymentState`**（`GET /v2/orders/{id}/payment`）：這張訂單的付款狀態端點從此永遠出錯（訂單詳情頁載入付款狀態的那個請求）。
2. **`SettlementGenerator.buildRefundedAmountMap`**：週結算對每張已完成訂單查退款金額。租戶只要有一張「先失敗、後成功」的已完成訂單，`generateStatementForTenant` 整個拋例外；`generateWeeklyStatements` 對單一租戶失敗是 catch 後繼續下一個租戶，所以**這個租戶每週都產不出結算單**，直到那張訂單的付款紀錄被人手動處理。

沒有測試發現：付款測試都 mock `PaymentRepository`，而 `M07PaymentMockIntegrationTest` 對每張訂單只建立一筆付款紀錄；沒有任何測試在真實資料庫走「失敗 → 成功」。這與 Sprint 194（DEF-272）、196（DEF-277）是同一類：全庫 mock Repository 的測試讓查詢在真實資料庫的行為從未被執行過。Sprint 219 啟用排程後才變得要緊：結算單原本根本不會自動產生，這個缺陷也就不會被踩到。

**Stripe 路徑本身**：`initiateStripeCheckout` 用固定的冪等鍵 `ORDER-CHECKOUT-<orderId>` 且 `payments.idempotency_key` 有唯一索引，純 Stripe 的訂單只會有一筆 Stripe 付款紀錄，所以多筆主要來自 Mock 路徑。但**啟用 Stripe 之前曾按過「模擬付款失敗」的訂單**，之後走 Stripe 會是一筆 FAILED（Mock）加一筆 PROCESSING／SUCCESS（Stripe），同樣會踩到——所以啟用 Stripe 前這個修正也必須已部署。舊版 `POST /v2/payments` 訂房付款的 `findByBookingId` 是同型隱患，一併修。

**紅燈實測**（真實 PostgreSQL，`MultiplePaymentRowsIntegrationTest`，修正前）：`IncorrectResultSizeDataAccessException: Query did not return a unique result: 2 results were returned`。

## 3. 修法

- `PaymentRepository` 移除 `findByOrderId`／`findByBookingId`（名稱本身暗示唯一性，留著只會被再次誤用），改為 `findAllByOrderIdOrderByCreatedAtDesc`／`findAllByBookingIdOrderByCreatedAtDesc`（回傳 `List`）與兩個 `default` 方法 `findEffectiveByOrderId`／`findEffectiveByBookingId`。
- 「挑哪一筆」的規則在 `Payment.pickEffective`（純函式，有單元測試）：已有金流結果的（`SUCCESS`／`PARTIALLY_REFUNDED`／`REFUNDED`）→ 進行中的結帳（`PROCESSING`）→ `PENDING` → `FAILED`；同一順位取建立時間較晚者。**不是單純取最新一筆**：成功之後如果又出現一筆較新的失敗紀錄，代表訂單付款狀況的仍是那筆成功的。
- `PaymentStateService`（訂單與訂房兩個付款狀態方法）與 `SettlementGenerator.buildRefundedAmountMap` 改用 `findEffective*`。

## 4. 測試

- **`MultiplePaymentRowsIntegrationTest`**（新，2 案例，真實 DB）：Mock 付款失敗一次再成功 → 付款狀態端點回 `SUCCESS`／訂單 `PAID`；已完成訂單有 `FAILED`＋含部分退款的 `SUCCESS` 兩筆 → 週結算單照常產生，`totalRefunds` 取成功那一筆的 100。兩個案例在修正前皆為紅燈。
- **突變驗證**：把 `findEffectiveByOrderId` 改回「多於一筆就拋 `IncorrectResultSizeDataAccessException`」（等同舊行為）→ 兩個整合案例都紅（含結算那一個，證實週結算確實會壞）；把 `pickEffective` 改成只看時間、不看狀態順位 → `PaymentPickEffectiveTest` 6 案例中 2 個紅。
- **`PaymentPickEffectiveTest`**（新，6 案例）：空集合、先失敗後成功、成功之後又出現較新的失敗（仍取成功）、三種金流結果狀態同順位、進行中優先於 PENDING／FAILED、同順位取最新且缺建立時間的排最後。
- 既有 `PaymentStateServiceTest`、`SettlementScheduledJobIntegrationTest` 的 mock 改為 stub 新方法名。

## 5. 驗證結果

- 全量 `mvn -o clean verify`（`make test-db-up` 已啟動真實 postgres/redis）：單元 **1847**（+6：`PaymentPickEffectiveTest`）／整合 **619**（+2：`MultiplePaymentRowsIntegrationTest`）／0 failures／0 errors／0 skipped；checkstyle（main+test）0 violations；PMD 通過；`BUILD SUCCESS`（10 分 36 秒）。
- 未變更 entity 欄位／migration（`Payment` 只新增靜態方法與列舉方法）；前端沒有改動。

## 6. 已知限制

- 「先失敗、後成功」是 Mock 模式的正常操作，**不是**只有測試才會發生：訂單詳情頁的「模擬付款失敗」按鈕在 Mock 模式對所有買家可見。已上線的環境若有買家按過，DB 裡可能已有這樣的訂單；修正後它們的付款狀態與結算會自動恢復正常，不需要資料修補。
- 這一輪只修 DEF-309。付款狀態端點在買家看得到的畫面上出錯的實際樣子（前端如何處理 500）沒有以瀏覽器確認。
