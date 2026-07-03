# Sprint 50 Review / Sprint 50 評審會議

> **Sprint 編號**: Sprint 50
> **期間**: 2027-09-12 ~ 2027-09-25
> **評審日期**: 2026-07-03
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 真實金流 Phase A——卡片付款 MVP（Stripe Checkout hosted，平台代收）

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | 後端 Checkout Session + gateway 接線 + toggle + V59 migration（AI-2410 後端）| 5 | ✅ 完成 |
| US-002 | 前端 Checkout 重導付款 + success/cancel + E2E（AI-2410 前端）| 3 | ✅ 完成 |

**承諾 8 SP（US-001~002）全數完成**。承 S49 評估分階段路線與 PO 拍板（Stripe Checkout hosted + 先平台代收），交付真實金流第一階段——真實卡片收款閉環。以 `STRIPE_PAYMENT_ENABLED` toggle（預設關=mock）讓真金流與 mock 並存、按租戶灰度。**Phase A 以「回跳 retrieve」為狀態來源；robust webhook 事件驅動留 Phase B（AI-2411）**。V59 打破 schema-free（payments 首次加 Stripe 欄位）。

---

## 2. 交付內容

- **US-001（後端，AI-2410，commit `6d913b5`）**：
  - **V59 migration**：`payments` 加 `stripe_session_id`/`stripe_payment_intent_id`/`stripe_charge_id`（nullable、向後相容）；`PaymentMethod` +`STRIPE`、`PaymentStatus` +`PROCESSING`（Java-only enum，欄位為 VARCHAR 無 CHECK）。
  - **接回孤兒 gateway 抽象層**：`StripePaymentGateway` 新增 `createCheckoutSession`（`Checkout.Session.create`，mode=payment、line_item 由訂單金額組、success/cancel url、metadata order_id、idempotency key、**平台代收不帶 destination/on_behalf_of**）+ `retrieveCheckoutSession`（`Session.retrieve`）；`PaymentGateway` 介面加 default（Mock/LinePay 不支援 → UnsupportedOperation）；`PaymentGatewayFactory` 加 wrapper。
  - **toggle + service**：`PaymentStateService` 引入 `STRIPE_PAYMENT_ENABLED`（TenantFeatureToggle，預設關=mock 路徑不變）；`initiateStripeCheckout`（ownership+canPay+無 SUCCESS → 建 PROCESSING 付款 + 建 Session → 回重導 url）+ `confirmStripeCheckout`（回跳以 sessionId retrieve，paid → Payment SUCCESS + pi id + Order PAID，**冪等**：已 SUCCESS 直接回、重入不重複）；`OrderPaymentStateDto` 加 `paymentProvider`（mock/stripe）。
  - **端點**：`POST /v2/orders/{orderId}/pay/checkout`（建 session）+ `GET /v2/orders/{orderId}/pay/checkout/return?sessionId=`（回填）。
  - **測試**：`StripePaymentGatewayTest` +`TC-S004`（createCheckoutSession WireMock）+`TC-S005`（retrieveCheckoutSession paid）；`PaymentStateServiceStripeTest` 4（toggle 開建單/toggle 關 E_6002/confirm paid→SUCCESS+PAID/已 SUCCESS 冪等）。
- **US-002（前端，AI-2410 前端，commit `8d31f82`）**：
  - `payment.ts` + `api.ts`：`payCheckout`/`payCheckoutReturn` 端點、`createCheckoutSession`/`confirmCheckoutReturn`、`OrderPaymentState.paymentProvider`、`CheckoutSessionResponse` type。
  - `orders/[id]/page.tsx`：付款卡片依 `paymentProvider` 分支——stripe → 「前往付款」建 session + `window.location` 重導（hosted Checkout **無需 @stripe 前端依賴**）；mock → 保留既有模擬按鈕。
  - 新增 `orders/[id]/payment/success`（回跳以 session_id 呼叫 return 端點確認，全 async callback 無同步 setState 符 React 19 嚴格 hooks）+ `cancel` 結果頁。
  - **E2E**：`E2E-M11-013`（mock return 端點回 SUCCESS，驗成功頁顯示付款成功）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 + checkstyle | ✅ 0 error / BUILD SUCCESS |
| 後端單元（`StripePaymentGatewayTest` 5 含 TC-S004/005 + `PaymentStateServiceStripeTest` 4）| ✅ **9 tests 0 fail**（WireMock 驗 Checkout Session SDK；toggle/建單/確認/冪等）|
| 後端整合（真實 DB，mock 路徑回歸）| ✅ **25 tests 0 fail**（`OrderPaymentControllerE2ETest` 4 + `OrderControllerE2ETest` 12 + `BuyerOrderJourneyE2ETest` 5 + `M12EffectivePriceIntegrationTest` 4；mock 付款不退步 + Spring context 載入新依賴）|
| schema 漂移守門（`make validate-schema`）| ✅ 無漂移（V59 三欄與 Payment entity 對齊）|
| 本地 E2E 守門（`make validate-e2e`）| ✅ **54 passed / 6 skipped / 0 failed**（含新增 E2E-M11-013；相較 S49 53 passed +1；既有 mock 付款不退步）|
| 前端 tsc / lint | ✅ tsc 0 error；lint 0 error（修 React 19 嚴格 hooks 的同步 setState；零新增 warning）|
| catch(Exception) / @Deprecated 計數 | ✅ 維持 0 |

---

## 4. 誠實揭露（Rule 12）

1. **Phase A 僅回跳 retrieve，非 webhook 驅動（MVP 界線）**：付款狀態以「Stripe 回跳 success_url 後 retrieve session」更新。**買家若關閉分頁未回跳**，付款雖於 Stripe 完成，本地 Payment 可能停留 PROCESSING、Order 停留 CREATED——**robust webhook 事件驅動（權威狀態）留 Phase B（AI-2411）**補上。已於計劃與此揭露。
2. **V59 打破 schema-free**：payments 表自 V1 後首次改動（加 3 Stripe 欄位）；ADD COLUMN nullable、既有列 NULL、mock 不受影響；先 `make validate-schema` 把關。
3. **toggle 預設 mock，真金流按租戶灰度**：`STRIPE_PAYMENT_ENABLED` 預設關 → mock 路徑（既有 E2E/開發不真扣款）；開啟 → stripe 路徑。既有付款 E2E 全綠為回歸保證。
4. **測試以 WireMock + 前端 mock（不打真 Stripe）**：後端 Checkout Session/retrieve 以 WireMock 攔截驗 SDK 整合；前端 E2E mock return 端點。真實金鑰/端到端於部署環境驗證（無真帳號依賴）。hosted Checkout 重導無法在 Playwright 走到真 Stripe，故未做「重導→真 Stripe→回跳」的全鏈 E2E（誠實揭露）。
5. **平台代收，分帳/提現未做**：Phase A PaymentIntent/Session **不帶** Connect（destination/on_behalf_of）；分帳/提現（Stripe Connect vs 手動）留 Phase D（AI-2413，需 PO 決策）。
6. **confirmPayment/refund/getPaymentStatus 仍為既有 stub**：本 Sprint 只補 Checkout Session 路徑（Phase A）；退款真串接留 Phase C（AI-2412）。
7. **push 債累積 S41~S50（10 Sprint）**：本 Sprint 有 schema + 金流 + 前端變動 → push 需完整 `make validate-release`。承 S41~S49 累積，於檢查點徵詢後一次守門 push（AI-1908；嚴禁 --no-verify）。

---

## 5. Demo 重點

- **真實卡片收款閉環（MVP）**：（toggle 開啟時）訂單詳情頁「前往付款」→ 重導 Stripe 託管付款頁 → 完成回跳 success 頁 → 確認付款成功、訂單 PAID。
- **mock 並存**：toggle 關閉時完全維持既有模擬付款（「不會實際扣款」）；`paymentProvider` 驅動前端 UI 切換。
- **不退步證明**：validate-e2e 54/6/0（既有 mock 付款 + 全買家閉環全綠）、後端單元 9 + 整合 25 全過。
- **可複用基礎驗證**：WireMock 證實 Stripe SDK Checkout Session 整合正確（TC-S004/005）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
