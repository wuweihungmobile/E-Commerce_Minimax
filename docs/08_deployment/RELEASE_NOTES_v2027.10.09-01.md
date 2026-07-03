# Release Notes - v2027.10.09-01 (Sprint 51)

**發布日期**: 2027-10-09（規劃）／實作完成 2026-07-03
**發布類型**: Minor（真實金流 Phase B：webhook 權威狀態；含 schema 變更 V60；後端聚焦）
**Sprint**: Sprint 51
**狀態**: ⏳ 待 push（本 Sprint 2 commit；push 債累積 S41~S51，於檢查點徵詢後完整 `make validate-release` 後 push）

> Sprint 51 主題：**真實金流 Phase B——webhook 事件驅動的權威付款狀態**。承 S50 Phase A（卡片付款 MVP），補上「買家未回跳時付款狀態滯後」的缺口，讓 Stripe webhook 成為**權威狀態來源**。**後端聚焦（webhook server-side，無前端變動）；只做付款成功/失敗事件，退款留 Phase C。** V60 續 V59 的付款 schema 演進（事件去重表）。

---

## 新功能 / 改進 🚀

- **webhook 事件驅動權威狀態（AI-2411）**：`PaymentWebhookService` 解析 Stripe 事件——`checkout.session.completed`（paid）→ 付款成功 + 訂單 PAID（權威，即使買家未回跳）；`payment_intent.payment_failed` → 付款失敗。`StripeWebhookController` 驗簽後委派、一律回 2xx（避免 Stripe 無限重送）。
- **雙路徑一致**：回跳（Phase A）與 webhook（Phase B）共用 `markStripePaymentSucceeded`/`markStripePaymentFailed` 核心，狀態一致。
- **事件去重（AI-2411）**：V60 `processed_stripe_events` 表 + event id 去重，防 Stripe 重送重複副作用。雙層冪等（去重 + 狀態轉移冪等）。

## 測試 / 驗證 ✅

- **後端單元**：`PaymentWebhookServiceTest` 5（UT-WH-001~005：成功/失敗/重送 skip/未知/未付款）+ `PaymentStateServiceStripeTest` 4 = **9 tests 0 fail**。
- **後端整合（真實 DB，mock/Phase A 回歸）**：`OrderPaymentControllerE2ETest` 4 + `OrderControllerE2ETest` 12 + `BuyerOrderJourneyE2ETest` 5 = **21 tests 0 fail**。
- **schema 漂移守門（`make validate-schema`）**：無漂移（V60 processed_stripe_events 與 entity 對齊）。
- **本地 E2E 守門（`make validate-e2e`）**：**54 passed / 6 skipped / 0 failed**（後端聚焦無新前端 E2E；相較 S50 持平；V60 全棧啟動驗證通過）。
- **catch(Exception) / @Deprecated 計數**：維持 0。

## 技術決策 / 已知限制 ⚠️

- **補 Phase A 缺口（誠實揭露 Rule 12）**：webhook 為權威狀態來源，補上「買家未回跳」時本地狀態滯後的缺口，真金流上線前必要項。
- **只做付款成功/失敗事件**：退款事件（charge.refunded）留 Phase C（AI-2412）；分帳留 Phase D（AI-2413）。
- **失敗路徑 best-effort**：payment_intent.payment_failed 依 pi id 找 Payment；Checkout 惰性建 pi 使 stripePaymentIntentId 可能為 null → 失敗事件可能找不到（log+skip，訂單維持 CREATED 可重試）。成功路徑（session id）可靠。Phase C 補強（pi metadata 或 session.expired）。
- **測試模式驗簽跳過**：測試以 secret 空 + 樣本 payload；**生產須配 STRIPE_WEBHOOK_SECRET + 公開 webhook 端點**（上線 checklist AI-2414）。
- **V60 schema**：新增去重表；不影響既有資料。

## 資料庫遷移 🗄️

- **V60__Create_Processed_Stripe_Events.sql**：新增 `processed_stripe_events`（event_id PK / event_type / processed_at），webhook 事件去重用。Flyway V59 → **V60**。

## 內含 Commit（Sprint 51）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 51 Plan | 4ac1453 | 真實金流 Phase B webhook 驅動狀態（2 US / 5 SP）|
| US-001+US-002 AI-2411 | 8321f2f | webhook 事件解析 + 權威狀態 + V60 去重 + 雙層冪等 |
| Sprint 51 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**基於**: AISDLC v0.09 Release Management Workflow
