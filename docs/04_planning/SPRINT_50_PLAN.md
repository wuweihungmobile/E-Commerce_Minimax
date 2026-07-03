# Sprint 50 計劃 / Sprint 50 Plan

> **Sprint 編號**: Sprint 50
> **期間**: 2027-09-12 ~ 2027-09-25 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-03
> **基於**: Sprint 49 決策文件 [PAYMENT_INTEGRATION_ASSESSMENT.md](../07_design/PAYMENT_INTEGRATION_ASSESSMENT.md)（backlog #10 spike）+ PO 拍板
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: 使用者（PO）選定「**AI-2410 真實金流 Phase A：卡片付款 MVP**」；拍板 **Stripe Checkout（hosted）** + **先平台代收（分帳/Connect 後做）**

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 主軸已確認 | ✅ AI-2410 Phase A（卡片付款 MVP）| 承 S49 評估分階段路線 |
| PO 決策：前端選型 | ✅ **Stripe Checkout（hosted）** | PCI SAQ-A 最輕、最快；前端重導、無需 @stripe/Elements |
| PO 決策：分帳架構 | ✅ **先平台代收**（單一 Stripe 帳戶）；分帳/Connect 待 Phase D | Phase A PaymentIntent/Session **不帶** destination/on_behalf_of，不綁死架構 |
| PO 決策（採建議）| ✅ Phase A 先行、`PAYMENT_PROVIDER` toggle 按租戶灰度、接回孤兒 gateway 抽象層並補齊（非重寫）| 見評估文件 §5/§9 |
| S49 狀態 | ✅ 已完成（3 commit，未 push）；活躍 DEF=0 | push 債累積 S41~S49（9 Sprint）|
| **可複用（已真接線）** | ✅ Stripe SDK 24.3.0、webhook 簽章驗證、金鑰 env 設定、WireMock 整合測試框架 | Checkout Session 需新增（既有 createPaymentIntent 為 Elements 用，Phase A 走 hosted Checkout）|
| **schema 影響（本 Sprint 打破 schema-free）** | 🔴 需 migration V59（payments 加 Stripe 欄位 + STRIPE method + Checkout session 欄位）| S42~S46 零-migration 已於 S47 V58 結束；本 Sprint V59 |
| **測試策略（關鍵）** | ⚠️ hosted Checkout 重導無法在 Playwright 走到真 Stripe | 後端用 **WireMock**（已 scaffolded）整合測試 Checkout Session 建立 + 狀態；前端 E2E **mock 後端 session-create 回應** 斷言重導意圖，不追到真 Stripe |
| 向後相容 | ✅ `PAYMENT_PROVIDER=mock`（預設）維持既有 Mock 路徑 + 既有 E2E（E2E-M11-009 等）不退步；stripe 路徑behind toggle | 既有 mock 測試全綠 |
| Phase B/C/D | ⚠️ 本 Sprint **只做 Phase A**（卡片付款閉環）；robust webhook 事件驅動（B）、退款（C）、分帳（D）另立 AI-2411~2413 | 見執行順序 §4 |
| push 前置 | ⚠️ 有實質後端（含 schema）+ 前端變動 → push 需完整 `make validate-release` + `make validate-schema` | 承 S41~S49 債 |

---

## 1. Sprint 50 目標

> **主題**: 真實金流 Phase A——卡片付款 MVP（Stripe Checkout hosted，平台代收）

承 S49 評估，啟動真實金流第一階段。以 **Stripe Checkout（hosted）** 實現真實卡片收款閉環：買家於訂單詳情頁點「付款」→ 後端建 Checkout Session（平台代收）→ 重導 Stripe 託管付款頁 → 完成後回跳 success/cancel → 更新訂單/付款狀態。以 `PAYMENT_PROVIDER` feature toggle 讓 mock（預設）與 stripe 並存、按租戶灰度。**Phase A 狀態更新以「回跳後 retrieve session」為主，robust webhook 事件驅動留 Phase B（AI-2411）**。只做卡片付款；退款/分帳另立。

---

## 2. User Stories

### US-001：後端——Checkout Session 建立 + gateway 接線 + toggle + migration（P3）（AI-2410 後端）

> **SP**: 5 | **優先級**: P3 | **狀態**: 📋 Ready

**AC-001-1**（migration V59）：`payments` 表加 Stripe 欄位——`stripe_session_id`、`stripe_payment_intent_id`、`stripe_charge_id`（皆 nullable，向後相容）；`PaymentMethod` enum 加 `STRIPE`；`PaymentStatus` 視需要加中間態（至少 `PROCESSING`；`requires_action` 於 Phase B 3DS 再評估）。`make validate-schema` 對齊無漂移。

**AC-001-2**（gateway 接線 + toggle）：引入 `PAYMENT_PROVIDER` feature toggle（TenantFeatureToggle，比照 `DYNAMIC_PRICING_ENABLED`；預設 `mock`）。`PaymentStateService`（前端付款實際路徑）於 toggle=`stripe` 時經 `PaymentGatewayFactory` 走 `StripePaymentGateway`，否則維持既有 mock（向後相容）。

**AC-001-3**（Checkout Session）：`StripePaymentGateway` 新增 `createCheckoutSession`（`Checkout.Session.create`，mode=payment、line_items 由訂單金額組、success_url/cancel_url、metadata order_id、idempotency key、**平台代收不帶 destination/on_behalf_of**）；例外對應既有 E_6006/E_6007。

**AC-001-4**（狀態回填）：新增付款回跳處理端點（如 `GET /v2/payments/checkout/return`）——以 `session_id` 呼叫 `getPaymentStatus`（改為真 `Session.retrieve` / `PaymentIntent.retrieve`）→ 付款成功則更新 Payment=SUCCESS + Order=PAID（冪等，重入不重複）。**保留 stub 的 confirmPayment/webhook 事件處理留 Phase B**，Phase A 以回跳 retrieve 為狀態來源。

**AC-001-5**（測試，WireMock）：`StripePaymentGatewayTest`（WireMock）補 createCheckoutSession + retrieve 狀態；新增整合測試（toggle=stripe 走 Stripe path 建 session、toggle=mock 走既有 mock 不退步）；付款回填冪等測試。

**AC-001-6**：現有付款/訂單測試全數不退步（`make test-db-up` 整合綠，mock 路徑）；catch(Exception)=0、@Deprecated=0。

### US-002：前端——Checkout 重導付款 + success/cancel + E2E（P3）（AI-2410 前端）

> **SP**: 3 | **優先級**: P3 | **狀態**: 📋 Ready

**AC-002-1**: `orders/[id]/page.tsx` 付款卡片——toggle=stripe 時（或依後端回應）「確認付款」改為呼叫後端建 Checkout Session → `window.location` 重導 `session.url`；移除/條件化「不會實際扣款」文案（mock 時保留）。**hosted Checkout 無需 @stripe 前端依賴**。

**AC-002-2**: 新增付款結果頁（success/cancel，如 `orders/[id]/payment/success`）——回跳後呼叫後端 return 端點確認狀態，顯示付款成功（訂單編號 + 金額）或取消/失敗（可重試）。

**AC-002-3**: `payment.ts` 補 `createCheckoutSession`（回 session url）+ return 狀態查詢型別。

**AC-002-4**: E2E——mock 後端「建 session」回假 url，斷言前端呼叫並嘗試重導（`page.waitForURL` 或攔截 navigation）；mock return 端點回 SUCCESS，斷言 success 頁顯示。既有 mock 付款 E2E（E2E-M11-009 等，`PAYMENT_PROVIDER=mock`）不退步。

**AC-002-5**: 前端 `tsc` / `build` / `lint` 0 error；`make validate-e2e` 綠（mock 路徑 + 新增 stripe 重導 mock E2E）。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 後端 Checkout Session + gateway 接線 + toggle + V59 migration（AI-2410 後端）| 5 | P3 |
| US-002 | 前端 Checkout 重導付款 + success/cancel + E2E（AI-2410 前端）| 3 | P3 |
| **承諾合計** | | **8 SP** | |

> **Velocity 參考**：S45=5, S46=8, S47=7, S48=8, S49=5。**本 Sprint 8 SP**，實作型（含 schema + 外部整合）。**真實金流首個可運行階段**（卡片付款閉環）。Phase B（webhook 驅動，AI-2411）/ C（退款）/ D（分帳）另立。

---

## 4. 執行順序（依相依性 + 開發-編譯-測試循環）

```
US-001 後端（V59 migration → make validate-schema → PAYMENT_PROVIDER toggle → gateway 接線
   → StripePaymentGateway.createCheckoutSession（WireMock 單元）→ return 端點 + 狀態回填冪等
   → 每步 mvn 編譯 + 測試 → test-db-up 整合（stripe path WireMock + mock path 不退步）)
   ↓ 後端綠
US-002 前端（payment.ts createCheckoutSession → orders/[id] 重導 → success/cancel 頁
   → E2E（mock session + return）→ tsc/build/lint）
   ↓ 前端綠
make validate-schema + make validate-e2e（mock 路徑不退步 + stripe 重導 mock 新案例）
   ↓
收尾（Review / Retro / Release Notes + trackers）
```

**強制**：先落 V59 + `make validate-schema`（記憶 [[local-ci-cannot-catch-schema-validation]]）；toggle 預設 mock 確保既有 E2E 不退步；Stripe 呼叫一律經 WireMock 測試（不打真 Stripe）。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| hosted Checkout 重導無法端到端自動測（外部 Stripe）| 後端 WireMock 測 Session 建立/retrieve；前端 E2E mock 後端 session-create + return，斷言重導意圖，不追真 Stripe |
| 真實扣款風險 | `PAYMENT_PROVIDER` 預設 mock；stripe 僅測試金鑰 + 按租戶灰度；正式上線前人工驗證 |
| Phase A 無 robust webhook（僅回跳 retrieve）| 明示 Phase A 以回跳 retrieve 為狀態來源；**買家關閉分頁未回跳** → 狀態可能停留 PENDING，Phase B（AI-2411 webhook）補權威更新；Review 誠實揭露此限制 |
| 狀態回填冪等 | return 端點以 session/pi status 為準、只前進不回退；重入不重複更新（測試覆蓋）|
| V59 打破 schema（payments 首次改）| ADD COLUMN nullable、既有列 NULL；先 validate-schema |
| 既有 mock 路徑退步 | toggle 預設 mock；既有付款 E2E 全綠為 DoD |
| 外部依賴（Stripe 測試帳號/金鑰）| 測試用 WireMock 免真帳號；真實金鑰於部署環境注入（非本地） |
| 有 schema + 金流 + 前端變動 → push 需完整 validate-release | 承 S41~S49 債累積後徵詢 |

---

## 6. Definition of Done

- [ ] US-001：V59 migration（payments 加 Stripe 欄位 + STRIPE method + PROCESSING 態）；PAYMENT_PROVIDER toggle（預設 mock）；gateway 接線；createCheckoutSession（平台代收）；return 端點狀態回填冪等；WireMock 單元 + 真 DB 整合（stripe path + mock 不退步）；`make validate-schema` 無漂移
- [ ] US-002：orders/[id] Checkout 重導；success/cancel 頁；payment.ts；E2E（stripe 重導 mock + mock 路徑不退步）；tsc/build/lint 0 error
- [ ] `make validate-schema` + `make validate-e2e` 綠、既有 mock 付款不退步；catch(Exception)=0、@Deprecated=0
- [ ] Sprint 50 Review / Retro / Release Notes + trackers（含 Phase A 僅回跳 retrieve、webhook 留 Phase B 之揭露）
- [ ]（檢查點）承 S41~S49 push 債，累積後於徵詢時完整 `make validate-release` 後 push（嚴禁 --no-verify）

---

## 7. 產出物

| 產出物 | 路徑 |
|--------|------|
| Migration | `backend/src/main/resources/db/migration/V59__Add_Stripe_Fields_To_Payments.sql` |
| 後端付款 | `backend/.../core/payment/PaymentStateService.java`（gateway 接線）、`infrastructure/payment/StripePaymentGateway.java`（createCheckoutSession/retrieve）、`infrastructure/payment/PaymentGatewayFactory.java`、`domain/model/payment/Payment.java`（欄位/enum）、`api/controller/payment/*`（return 端點）|
| 後端測試 | `StripePaymentGatewayTest`（WireMock）、付款整合測試 |
| 前端 | `frontend/src/app/(auth)/orders/[id]/page.tsx`、`orders/[id]/payment/success` 頁、`services/payment.ts` |
| 前端 E2E | `frontend/e2e/at-m11-cart-checkout.spec.ts`（或新 payment spec）|
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

---

## 8. 🔴 待使用者（PO）確認點

1. **範圍**：AI-2410 Phase A = US-001 後端 Checkout Session + gateway + toggle + V59（5 SP）+ US-002 前端重導 + success/cancel + E2E（3 SP）= **8 SP**。是否核准?
2. **Phase A 狀態來源**：本 Sprint 以「回跳後 retrieve session」更新狀態；**robust webhook 事件驅動留 Phase B（AI-2411）**。買家未回跳時狀態可能暫留 PENDING（Phase B 補）。是否接受此 MVP 界線?
3. **V59 schema 變更**：payments 首次加 Stripe 欄位（ADD COLUMN nullable）。是否確認?
4. **toggle 預設 mock**：`PAYMENT_PROVIDER` 預設 mock（既有測試/開發不真扣款），stripe 按租戶灰度。是否同意?
5. **測試以 WireMock/前端 mock**：不打真 Stripe（無真帳號依賴），真實金鑰於部署環境驗證。是否同意此測試策略?

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
