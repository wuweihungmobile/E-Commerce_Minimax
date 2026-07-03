# Release Notes - v2027.09.25-01 (Sprint 50)

**發布日期**: 2027-09-25（規劃）／實作完成 2026-07-03
**發布類型**: Minor（新功能：真實金流 Phase A 卡片付款 MVP；含 schema 變更 V59；toggle 預設 mock）
**Sprint**: Sprint 50
**狀態**: ⏳ 待 push（本 Sprint 3 commit；push 債累積 S41~S50，於檢查點徵詢後完整 `make validate-release` 後 push）

> Sprint 50 主題：**真實金流 Phase A——卡片付款 MVP（Stripe Checkout hosted，平台代收）**。承 S49 評估分階段路線與 PO 拍板，交付真實金流第一階段：真實卡片收款閉環。以 `STRIPE_PAYMENT_ENABLED` toggle（預設關=mock）讓真金流與 mock 並存、按租戶灰度。**Phase A 以回跳 retrieve 為狀態來源；robust webhook 事件驅動留 Phase B（AI-2411）**。V59 打破 schema-free（payments 首次加 Stripe 欄位）。

---

## 新功能 🚀

- **真實金流 Phase A 卡片付款（AI-2410）**：（`STRIPE_PAYMENT_ENABLED` 開啟時）買家於訂單詳情頁「前往付款」→ 後端建 Stripe Checkout Session（平台代收）→ 重導 Stripe 託管付款頁 → 完成回跳 success 頁 → 確認付款成功、訂單 PAID。含 success/cancel 結果頁。
- **mock↔real 並存 toggle**：`STRIPE_PAYMENT_ENABLED`（TenantFeatureToggle，預設關）讓 mock 與 stripe 付款並存、按租戶灰度；`paymentProvider`（mock/stripe）驅動前端付款 UI。

## 改進 / 架構 🔧

- **接回孤兒 gateway 抽象層（AI-2410）**：`StripePaymentGateway` 補 `createCheckoutSession`（`Checkout.Session.create`）+ `retrieveCheckoutSession`（`Session.retrieve`）；`PaymentStateService` 經 `PaymentGatewayFactory` 於 toggle 開啟時走 Stripe，否則維持 mock。複用既有 Stripe SDK 24.3.0 + webhook 驗簽。
- **付款狀態冪等**：confirmStripeCheckout 對已 SUCCESS 直接回、回跳重入不重複更新。

## 變更 🔧

- **DTO / enum**：`Payment` 加 `stripeSessionId`/`stripePaymentIntentId`/`stripeChargeId`；`PaymentMethod` +STRIPE、`PaymentStatus` +PROCESSING；`OrderPaymentStateDto` +paymentProvider。
- **⚠️ 行為變更**：`STRIPE_PAYMENT_ENABLED` 開啟時，訂單付款走真實 Stripe 收款；toggle 關閉維持既有 mock。

## 測試 / 驗證 ✅

- **後端單元**：`StripePaymentGatewayTest` 5（含 TC-S004/005 Checkout Session WireMock）+ `PaymentStateServiceStripeTest` 4（toggle/建單/確認/冪等）= **9 tests 0 fail**。
- **後端整合（真實 DB，mock 路徑回歸）**：`OrderPaymentControllerE2ETest` 4 + `OrderControllerE2ETest` 12 + `BuyerOrderJourneyE2ETest` 5 + `M12EffectivePriceIntegrationTest` 4 = **25 tests 0 fail**。
- **schema 漂移守門（`make validate-schema`）**：無漂移（V59 三欄與 Payment entity 對齊）。
- **本地 E2E 守門（`make validate-e2e`）**：**54 passed / 6 skipped / 0 failed**（含新增 E2E-M11-013；相較 S49 53 passed +1；既有 mock 付款不退步）。
- **前端**：tsc 0 error；lint 0 error（修 React 19 嚴格 hooks 同步 setState；零新增 warning）。
- **catch(Exception) / @Deprecated 計數**：維持 0。

## 技術決策 / 已知限制 ⚠️

- **Phase A 僅回跳 retrieve（誠實揭露 Rule 12）**：狀態以回跳 success_url 後 retrieve session 更新；買家未回跳時本地狀態可能滯後（PROCESSING/CREATED）。robust webhook 權威狀態留 Phase B（AI-2411），上線前建議緊接。
- **平台代收，分帳/提現未做**：Session 不帶 Connect；分帳/提現留 Phase D（AI-2413，需 PO 決策 Connect vs 手動）。
- **confirm/refund/getStatus 仍為既有 stub**：本 Sprint 只接 Checkout 路徑；退款留 Phase C（AI-2412）。
- **測試以 WireMock + 前端 mock**：不打真 Stripe；hosted Checkout 重導的全鏈端到端須人工於 Stripe 測試模式驗證（上線 checklist）。
- **toggle 預設 mock**：既有付款 E2E/開發不真扣款；真金流灰度上線。

## 資料庫遷移 🗄️

- **V59__Add_Stripe_Fields_To_Payments.sql**：`payments` 加 `stripe_session_id`/`stripe_payment_intent_id`/`stripe_charge_id`（皆 nullable、`ADD COLUMN IF NOT EXISTS` 冪等）。既有列自動 NULL，mock 不受影響。Flyway V58 → **V59**。

## 內含 Commit（Sprint 50）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 50 Plan | d7392d5 | 真實金流 Phase A 卡片付款 MVP（2 US / 8 SP）|
| US-001 AI-2410 | 6d913b5 | 後端 Checkout Session + gateway 接線 + toggle + V59 |
| US-002 AI-2410 | 8d31f82 | 前端 Checkout 重導 + success/cancel + E2E-M11-013 |
| Sprint 50 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**基於**: AISDLC v0.09 Release Management Workflow
