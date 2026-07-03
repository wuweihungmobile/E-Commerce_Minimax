# Release Notes - v2027.10.23-01 (Sprint 52)

**發布日期**: 2027-10-23（規劃）／實作完成 2026-07-03
**發布類型**: Minor（真實金流 Phase C：退款真串接；含 schema 變更 V61；後端聚焦）
**Sprint**: Sprint 52
**狀態**: ⏳ 待 push（本 Sprint 2 commit；push 債累積 S41~S52，於檢查點徵詢後完整 `make validate-release` 後 push）

> Sprint 52 主題：**真實金流 Phase C——退款真串接（Stripe Refund）**。承 Phase A/B（S50/S51）真實付款 + webhook 權威狀態，本 Sprint 讓退款真實串接 Stripe（此前 processRefund 為 stub、mockRefund 為 mock）。**後端聚焦（退款無前端 UI，admin/API 觸發）；只做全額退款；分帳留 Phase D。** V61 續 V59/V60 付款 schema 演進。

---

## 新功能 / 改進 🚀

- **Stripe 退款真串接（AI-2412）**：`StripePaymentGateway.processRefund` 由 stub 改真 `Refund.create`（以 payment_intent 全額退款）；`PaymentStateService.refundOrderPayment` toggle-aware——STRIPE_PAYMENT_ENABLED 開啟且 Payment 為 STRIPE → 真退款 + 存 `stripe_refund_id`，否則 mock。標記 Payment REFUNDED + Order REFUNDED。
- **charge.refunded webhook（AI-2412）**：`PaymentWebhookService` 加 `charge.refunded` → 權威 REFUNDED（冪等 + V60 event 去重）。
- **pi metadata 補強**：`createCheckoutSession` 加 `payment_intent_data.metadata.order_id`，使 payment_intent 相關事件（失敗/退款）可由 order_id 可靠對應（補 S51 best-effort 缺口）。

## 測試 / 驗證 ✅

- **後端單元**：`StripePaymentGatewayTest` 6（含 TC-S006 Refund WireMock）+ `PaymentStateServiceStripeTest` 7（含退款 005~007）+ `PaymentWebhookServiceTest` 6（含 UT-WH-006 charge.refunded）= **19 tests 0 fail**。
- **後端整合（真實 DB，mock/Phase A/B 回歸）**：`OrderPaymentControllerE2ETest` 4（/refund mock 路徑）+ `OrderControllerE2ETest` 12 + `BuyerOrderJourneyE2ETest` 5 = **21 tests 0 fail**。
- **schema 漂移守門（`make validate-schema`）**：無漂移（V61 stripe_refund_id 與 entity 對齊）。
- **本地 E2E 守門（`make validate-e2e`）**：**54 passed / 6 skipped / 0 failed**（後端聚焦無新前端 E2E；相較 S51 持平；V61 全棧啟動驗證通過）。
- **catch(Exception) / @Deprecated 計數**：維持 0。

## 技術決策 / 已知限制 ⚠️

- **只做全額退款（誠實揭露 Rule 12）**：partially_refunded 另評估（AI-2415）。
- **退款需 payment_intent**：以 Phase B 回填的 stripePaymentIntentId 執行；缺 pi 報錯 E_6001（不假退款）。
- **測試以 WireMock（不打真 Stripe）**：真退款端到端於 Stripe 測試模式人工驗證（上線 checklist AI-2414）。
- **餘 stub**：confirmPayment/getPaymentStatus（Checkout 流程未用）+ LinePay 仍為 stub；記錄備查。
- **V61 schema**：續 V59/V60；ADD COLUMN nullable，不影響既有資料。

## 資料庫遷移 🗄️

- **V61__Add_Stripe_Refund_Id_To_Payments.sql**：`payments` 加 `stripe_refund_id`（nullable）。Flyway V60 → **V61**。

## 內含 Commit（Sprint 52）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 52 Plan | 0bc92ea | 真實金流 Phase C 退款真串接（2 US / 5 SP）|
| US-001+US-002 AI-2412 | af1591b | gateway 真 Refund + service toggle-aware + charge.refunded webhook + pi metadata + V61 |
| Sprint 52 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**基於**: AISDLC v0.09 Release Management Workflow
