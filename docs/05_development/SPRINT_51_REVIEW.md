# Sprint 51 Review / Sprint 51 評審會議

> **Sprint 編號**: Sprint 51
> **期間**: 2027-09-26 ~ 2027-10-09
> **評審日期**: 2026-07-03
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 真實金流 Phase B——webhook 事件驅動的權威付款狀態

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | 後端 webhook 事件解析 + 權威狀態更新 + 冪等（AI-2411 後端）| 3 | ✅ 完成 |
| US-002 | 事件去重表 V60 + webhook 冪等強化 + 測試（AI-2411 後端）| 2 | ✅ 完成 |

**承諾 5 SP（US-001~002）全數完成**。承 S50 Phase A（卡片付款 MVP）落地，本 Sprint 補上「買家未回跳時付款狀態滯後」的缺口——讓 Stripe webhook 成為**權威狀態來源**。**後端聚焦（webhook 為 server-side，無前端變動）；只做付款成功/失敗事件，退款事件留 Phase C。** V60 續 S50 V59 的付款 schema 演進。

---

## 2. 交付內容

- **US-001 + US-002（後端，AI-2411，commit `8321f2f`）**：
  - **webhook 事件解析 + 權威狀態**：新增 `PaymentWebhookService`——驗簽後以 Jackson 解析事件（`id`/`type`/`data.object`），dispatch：`checkout.session.completed`（`payment_status=paid`）→ 依 session id 找 Payment → SUCCESS + 回填 payment_intent + Order PAID（**權威、冪等**）；`payment_intent.payment_failed` → 依 pi id 找 Payment → FAILED（Order 維持 CREATED 可重試）；未知事件 → log + 記錄不 dispatch。
  - **controller 接線**：`StripeWebhookController` 驗簽（既有 HMAC-SHA256）通過後委派 `handleEvent`；**一律回 2xx**（處理失敗記錄但不回 5xx，避免 Stripe 無限重送；簽章無效仍回 4xx）。順手清理 LinePay handler 的無用 throw。
  - **雙路徑一致（AC-001-3）**：抽 `PaymentStateService.markStripePaymentSucceeded`/`markStripePaymentFailed` 共用核心，`confirmStripeCheckout`（S50 回跳路徑）與 webhook 路徑共用同一「標記成功 + Order PAID（冪等）」邏輯，杜絕分歧。
  - **事件去重（US-002）**：migration V60 `processed_stripe_events`（event_id PK + type + processed_at）+ `ProcessedStripeEvent` entity/repo；處理前 `existsById` 去重、成功後記錄——防 Stripe 重送造成重複副作用。`PaymentRepository` 加 `findByStripeSessionId`/`findByStripePaymentIntentId`。
  - **雙層冪等**：(1) event id 去重（V60）(2) 狀態轉移冪等（已 PAID/終態 → no-op）。
  - **測試**：`PaymentWebhookServiceTest` 5（UT-WH-001~005：成功/失敗/重送 skip/未知/未付款）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 + checkstyle | ✅ 0 error / BUILD SUCCESS |
| 後端單元（`PaymentWebhookServiceTest` 5 + `PaymentStateServiceStripeTest` 4）| ✅ **9 tests 0 fail** |
| 後端整合（真實 DB，mock/Phase A 回歸）| ✅ **21 tests 0 fail**（`OrderPaymentControllerE2ETest` 4 + `OrderControllerE2ETest` 12 + `BuyerOrderJourneyE2ETest` 5；mock/Phase A 付款路徑不退步 + Spring context 載入新 bean）|
| schema 漂移守門（`make validate-schema`）| ✅ 無漂移（V60 processed_stripe_events 與 entity 對齊）|
| 本地 E2E 守門（`make validate-e2e`）| ✅ **54 passed / 6 skipped / 0 failed**（後端聚焦無新前端 E2E；相較 S50 持平；V60 全棧啟動驗證通過）|
| 前端變動 | 無（webhook 為 server-side；Phase A success 頁回跳確認保留為輔助路徑）|
| catch(Exception) / @Deprecated 計數 | ✅ 維持 0 |

---

## 4. 誠實揭露（Rule 12）

1. **補 Phase A「回跳未達」缺口**：Phase A（S50）僅由 success_url 回跳 retrieve 更新狀態；本 Sprint 的 webhook 為**權威來源**——即使買家關閉分頁未回跳，Stripe 的 `checkout.session.completed` 事件仍會將訂單更新為 PAID。回跳與 webhook 雙路徑共用核心、皆冪等。
2. **只做付款成功/失敗事件**：`checkout.session.completed` + `payment_intent.payment_failed`；**退款事件（`charge.refunded`）留 Phase C（AI-2412）**；分帳留 Phase D（AI-2413）。
3. **失敗路徑為 best-effort**：`payment_intent.payment_failed` 依 pi id 找 Payment，但 Phase A 建 Checkout Session 時 pi 可能尚未產生（Checkout 惰性建 pi），故 stripePaymentIntentId 可能為 null → 失敗事件可能找不到 Payment（log + skip，訂單維持 CREATED 可重試，安全預設）。成功路徑（session id）可靠。
4. **測試模式驗簽跳過**：測試以 `STRIPE_WEBHOOK_SECRET` 空跳過驗簽、單元測試直接餵樣本 payload；**生產須配置 STRIPE_WEBHOOK_SECRET**（部署 checklist）+ 公開可達 webhook 端點（Stripe CLI/域名）。
5. **webhook 一律回 2xx**：除簽章無效（4xx），處理失敗記錄但回 2xx 避免 Stripe 無限重送；重送由雙層冪等保障不重複副作用。
6. **V60 schema**：續 S50 V59 的付款 schema 演進（新增去重表）；ADD table、不影響既有資料。
7. **push 債累積 S41~S51（11 Sprint）**：本 Sprint 有 schema + 金流 webhook 變動 → push 需完整 `make validate-release`。承 S41~S50 累積，於檢查點徵詢後一次守門 push（AI-1908；嚴禁 --no-verify）。

---

## 5. Demo 重點

- **權威狀態（未回跳也 PAID）**：（測試模式）模擬買家於 Stripe 付款後未回跳，直接發送 `checkout.session.completed` webhook → 訂單自動 PAID（單元 UT-WH-001 佐證）。
- **重送冪等**：同一 event id 送兩次 → 第二次 skip（UT-WH-003），不重複更新。
- **雙路徑一致**：回跳（Phase A）與 webhook（Phase B）共用 `markStripePaymentSucceeded`，狀態一致。
- **不退步**：validate-e2e 54/6/0、後端單元 9 + 整合 21 全過；mock/Phase A 付款不受影響。

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
