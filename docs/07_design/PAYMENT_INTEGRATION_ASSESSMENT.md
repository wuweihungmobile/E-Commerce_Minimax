# 真實金流（Stripe）整合評估 / Payment Integration Assessment

> **文件類型**: 設計評估 / 決策 spike（ADR 候選）
> **版本**: v1.0
> **建立日期**: 2026-07-03
> **來源**: Sprint 49（backlog #10「M07 真實金流串接」spike）；PRODUCT_BACKLOG #10（13 SP + 外部依賴，建議單獨 Sprint 評估）
> **狀態**: 🔴 評估完成，待 PO 拍板；實作分階段另立
> **範疇**: Stripe 真串接（卡片付款 / webhook / 退款）+ 分帳 / 提現（payout）
> **基礎**: S49 金流現況全面探勘（檔案:行號佐證如下）

---

## 1. 問題陳述

平台付款目前為**純 Mock**（訂單詳情頁明文「目前為模擬付款（Mock），不會實際扣款」），無法真實收款。backlog #10（真實金流）為平台**變現關鍵**。本文件評估「真實 Stripe 上線」的現況、缺口、分階段路線與關鍵架構決策。

**核心認知**：專案已有 S14/S21「Stripe Phase 3」遺留的 Stripe scaffolding，但**「真實上線」≠「填金鑰」**——存在兩套並行付款程式碼，且孤兒抽象層未接入主流程、多數方法為 stub。

---

## 2. 現況盤點（探勘結論）

### 2.1 兩套並行付款程式碼

| 路徑 | 狀態 | 位置 | 說明 |
|------|------|------|------|
| **上線中主路徑** | 🔴 純 Mock | `core/payment/PaymentService.java`（類註解 L24-27「Mock Implementation」）、`core/payment/PaymentStateService.java`（`mockPaymentSuccess`/`mockPaymentFailure`/`mockRefund`）| 前端付款按鈕實際打此路徑；直接設 `SUCCESS`、產 `MOCK-xxxx` 交易號；無任何 Stripe 呼叫 |
| **孤兒 Gateway 抽象層** | ⚠️ 未接線（死碼）| `infrastructure/payment/PaymentGatewayFactory.java`、`StripePaymentGateway.java`、`MockPaymentGateway`/`LinePayPaymentGateway` | S14/S21 遺留；**grep 全 main source 確認無任何 Controller/Service 注入 `PaymentGatewayFactory`**——只在 infrastructure/payment 內部互相引用 |

> **關鍵含義**：真實上線需**接回孤兒抽象層 + 補齊 stub + 補 DB 欄位 + 補 webhook 事件處理 + 補前端 Stripe.js**，非單純設定金鑰。需先決定「接回孤兒層 vs 重寫」。

### 2.2 real / stub / missing 速查表

| 能力 | 狀態 | 位置 |
|------|------|------|
| Stripe SDK 依賴 | ✅ **真實** | `backend/pom.xml` L144-149（`com.stripe:stripe-java:24.3.0`）|
| 建立 PaymentIntent | ✅ **真實**（未接主流程）| `StripePaymentGateway.createPaymentIntent` L39-87（真 `PaymentIntent.create` + idempotency key + metadata order_id + 金額×100 + CardException→E_6006/StripeException→E_6007）|
| Webhook 簽章驗證 | ✅ **真實**（secret 空則跳過）| `StripeSignatureVerifierService` L33-133（真 HMAC-SHA256、300s 容忍窗、常數時間比對）|
| Stripe 金鑰 / webhook secret 設定 | ✅ **真實**（env + placeholder）| `application.yml` L76-81（`STRIPE_SECRET_KEY:sk_test_placeholder` / `STRIPE_WEBHOOK_SECRET:`）|
| WireMock SDK 整合測試 | ✅ **真實** | `StripePaymentGatewayTest`（TC-S001~003）+ pom WireMock 3.5.4（test scope）|
| Stripe 專用錯誤碼 | ✅ **真實** | `ErrorCode` E_5015（webhook 簽章）/ E_6006（卡片拒絕）/ E_6007（provider error）|
| confirmPayment（確認/capture）| 🔴 **STUB** | `StripePaymentGateway.confirmPayment` L89-100（直接 success(true)，不呼叫 Stripe）|
| Stripe refund | 🔴 **STUB**（假 re_ id）| `StripePaymentGateway.processRefund` L102-151（假 `re_`+UUID，只讀本地 DB，從未呼叫 `Refund.create`）|
| getPaymentStatus（查 Stripe）| 🔴 **STUB**（讀本地）| `StripePaymentGateway.getPaymentStatus` L153-184（未呼叫 `PaymentIntent.retrieve`）|
| Webhook 事件處理 / 更新狀態 | 🔴 **STUB** | `StripeWebhookController` L55-72（驗簽後只 `return ok`，不解析事件、不更新狀態）|
| LinePay gateway | 🔴 **STUB** | `LinePayPaymentGateway`（假 LP 交易號）|
| Gateway 抽象層接入主流程 | ❌ **完全沒有** | `PaymentGatewayFactory` 無人注入 |
| Stripe 專屬 DB 欄位（pi_id/client_secret/charge_id/refund_id）| ❌ **完全沒有** | `payments` 表僅 `transaction_id`(VARCHAR 255) + `payment_data`(jsonb)；V1 後 49 個 migration 未動 payments |
| Stripe 中間狀態（requires_action/processing/partially_refunded）| ❌ **完全沒有** | `PaymentStatus` enum 僅 PENDING/SUCCESS/FAILED/REFUNDED |
| `STRIPE` PaymentMethod | ❌ **無字面** | enum 為 LINE_PAY/CREDIT_CARD/MOCK（Factory 靠 CREDIT_CARD→stripe 映射）|
| 付款非同步 / MQ 對帳 | ❌ **完全沒有** | 全同步 DB 寫入；Redis Stream 僅用於 M09 通知，不涉付款 |
| 前端 Stripe.js / Elements 收單 | ❌ **完全沒有** | `frontend/package.json` 無 @stripe；無 loadStripe/CardElement |
| `/v2/payments/mock`、`/callback` 端點 | ❌ **完全沒有**（前端常數空指）| `frontend/src/lib/api.ts` L54-56 定義但後端無對應 |
| 分帳/提現真實轉帳（Connect payout）| ❌ **完全沒有**（僅對帳單）| `core/settlement/`（V35/V36 產 SettlementStatement/CreditNote，無 withdraw/payout）|

### 2.3 資料模型現況

- `Payment` entity（`domain/model/payment/Payment.java` L38-81）：`id/orderId/bookingId/paymentMethod/amount/currency/status/transactionId/idempotencyKey/paymentData(jsonb)/paidAt/...`。Stripe `pi_xxx` 目前只能塞 `transactionId` 或 `paymentData`。
- `payments` 表：`V1__Initial_Schema.sql` L307-326（含 order/booking/idempotency/status 四索引）；V1 後未改。
- Order/Booking 無獨立 `paymentStatus` 欄，用單一 `OrderStatus`/`BookingStatus`（含 PAID/REFUNDING/REFUNDED）。

### 2.4 前端現況

- `frontend/src/services/payment.ts`（`OrderPaymentService`）：僅 `getPaymentState`/`pay`（Mock 成功）/`payFail`（Mock 失敗），無 refund。
- 付款 UI：`orders/[id]/page.tsx` L264-287「確認付款（模擬）」/「模擬付款失敗」；明文「不會實際扣款」。
- 無 @stripe 依賴、無 Elements、無真實信用卡收單 UI。

---

## 3. 真實上線缺口（US-001）

要從 Mock 走到真實 Stripe 上線，需補齊：

1. **接回 gateway 抽象層**：讓 `PaymentService`/`PaymentStateService` 經 `PaymentGatewayFactory` 呼叫 `StripePaymentGateway`（決定：接回孤兒層 vs 重寫）。
2. **補齊 stub**：`confirmPayment`（真 capture/confirm）、`processRefund`（真 `Refund.create`）、`getPaymentStatus`（真 `PaymentIntent.retrieve`）。
3. **webhook 事件處理**：`StripeWebhookController` 補 `Webhook.constructEvent` 解析事件（`payment_intent.succeeded`/`.payment_failed`/`charge.refunded`…）→ 更新 Payment/Order 狀態（冪等）。
4. **DB 擴充**（需 migration）：`payments` 加 `stripe_payment_intent_id`/`client_secret`/`stripe_charge_id`/`refund_id`；`PaymentStatus` 加 Stripe 中間態（requires_action/processing/partially_refunded）；`PaymentMethod` 加 `STRIPE`。
5. **付款狀態機**：PaymentIntent 狀態 → 既有 Order/Booking 狀態的對映（含 requires_action 的 3DS 流程）。
6. **非同步/對帳**：webhook 驅動狀態更新（現況全同步）；重送冪等；同步回應 vs 非同步 confirm 的一致性。

---

## 4. 分階段實作路線（US-001，建議）

> 依風險與相依遞增分期；每期可獨立上線、以 feature toggle 灰度。SP 為粗估（backlog #10 總計 13 SP + 外部依賴）。

| Phase | 範圍 | 可複用（已真接線）| 需補 | 估 SP | 相依/風險 |
|-------|------|------------------|------|-------|-----------|
| **A：卡片付款 MVP** | 接回 gateway → 用 `createPaymentIntent` 建單 → 前端 Stripe.js 收單 → 同步取回 status | createPaymentIntent、金鑰設定 | gateway 接線、DB 加 pi_id/client_secret、前端 Stripe.js、`STRIPE` method、mock↔real toggle | 5 | Stripe 測試帳號；PCI（見 §7）|
| **B：webhook 驅動狀態** | webhook 解析事件 → 冪等更新 Payment/Order（權威狀態來源）| 簽章驗證 | 事件解析、冪等、狀態機對映、requires_action(3DS) | 3 | webhook 端點需公開可達（ngrok/正式域名）|
| **C：退款真串接** | `processRefund` 真 `Refund.create` + `charge.refunded` webhook + partially_refunded 態 | — | 補 stub、退款狀態、與既有 REFUNDING/REFUNDED 對映 | 2 | Phase B webhook |
| **D：分帳/提現** | 賣家收款與分潤（見 §6）| 既有 settlement 對帳單 | Stripe Connect（或手動）、賣家 onboarding/KYC、payout | 5+ | 外部依賴最重；商業/合規決策 |

> **建議**：先交付 **Phase A（卡片付款 MVP）** 驗證真實收款閉環，再 B（權威狀態）、C（退款）、D（分帳）。Phase D 為獨立大主題（見 §6）。

---

## 5. mock ↔ real 切換策略（US-001）

- **現況**：無 payment feature toggle；mock 與 real 無並存機制（因孤兒層未接線）。
- **建議**：引入 `PAYMENT_PROVIDER`（`mock` | `stripe`）feature toggle（比照既有 `DYNAMIC_PRICING_ENABLED` 的 TenantFeatureToggle 機制），讓：
  - 開發/測試維持 mock（免真扣款）；
  - 正式環境切 stripe；
  - 可**按租戶灰度**（部分商家先上真金流）。
- `PaymentGatewayFactory` 天生支援此路由（`default→mock`），toggle 決定注入哪個 gateway。
- **Mock 下線策略**：Phase A~C 穩定後，決定保留 mock 為測試路徑 or 完全下線（建議保留為 `PAYMENT_PROVIDER=mock` 測試用）。

---

## 6. 分帳 / 提現（payout）（US-002）

平台為 B2B2C 多租戶，賣家收款與平台分潤需真實轉帳。兩選項：

### 選項 A：Stripe Connect（marketplace，推薦評估）

- **機制**：賣家為 Connected Account，買家付款直接進賣家帳戶（或平台代收後分帳），平台抽成（application fee），Stripe 自動 payout 給賣家。
- **優點**：金流合規/KYC/payout 由 Stripe 處理；分潤自動化；與 marketplace 語意契合。
- **缺點**：賣家需 Connect onboarding（KYC）；帳戶模型（Standard/Express/Custom）需選型；改動較大。
- **銜接既有**：既有 `core/settlement/`（對帳單/貸項通知）可轉為「對帳/報表」層，實際轉帳交 Connect。

### 選項 B：手動分帳（沿用既有 settlement）

- **機制**：平台單一 Stripe 帳戶代收，`SettlementService` 產對帳單，平台**手動/自建**轉帳給賣家。
- **優點**：改動小、沿用既有 settlement 模組；賣家無需 Connect onboarding。
- **缺點**：轉帳非自動（營運成本 + 資金/合規風險由平台承擔）；不具擴展性。

> **待 PO 決策**：Connect（自動、合規外包、改動大）vs 手動（沿用 settlement、營運/合規負擔）。**建議**：若目標為真正 marketplace 規模，選 Connect（Express 帳戶）；MVP 階段可先手動分帳、Connect 另立。

---

## 7. 前端收單 Stripe.js 選型（US-002）

| 選項 | PCI 等級 | 說明 | 取捨 |
|------|----------|------|------|
| **Stripe Elements**（自建卡片 UI）| SAQ-A EP | 前端 `@stripe/stripe-js` + `@stripe/react-stripe-js`，卡號輸入用 Stripe iframe，UI 可客製 | UI 一致性佳；PCI 範疇略大（SAQ-A EP）|
| **Stripe Checkout**（hosted）| SAQ-A | 導向 Stripe 託管付款頁，回跳 success/cancel | PCI 最輕（SAQ-A）；UI 客製受限；最快上線 |
| **Payment Links** | SAQ-A | 無需前端整合，產付款連結 | 最簡但最不整合，不適合 in-app 流程 |

- **現況**：前端無 @stripe 依賴、無收單 UI；需取代 `orders/[id]` 的「確認付款（模擬）」。
- **建議**：**Phase A 用 Checkout（hosted，PCI 最輕、最快驗證閉環）**，後續視 UX 需求再評估 Elements。

---

## 8. 風險與相容（US-001）

| 風險 | 說明 / 緩解 |
|------|------|
| webhook 冪等 | Stripe 事件會重送；需以 event id 去重 + 狀態轉移冪等（只前進不回退）|
| 對帳一致性 | 同步回應與 webhook 非同步更新可能競態；以 webhook 為權威狀態來源、同步回應僅樂觀更新 |
| PaymentIntent ↔ Order 狀態對映 | requires_action(3DS)、processing 等中間態需對映；避免「Stripe 已收款但本地仍 CREATED」|
| 既有 Mock 向後相容 | toggle 並存；mock 路徑保留為測試用，避免一次性大改 |
| PCI 合規 | 依 §7 選型決定 SAQ 等級；卡號不落地本地 |
| 金鑰管理 | 已 env 驅動（STRIPE_SECRET_KEY/STRIPE_WEBHOOK_SECRET）；正式環境需 secret 管理 |
| webhook 公開可達 | 本地開發需 Stripe CLI / ngrok；正式需公開域名 |
| 孤兒層品質未知 | createPaymentIntent/驗簽已真接線可複用；confirm/refund/status 需重寫 |

---

## 9. 🔴 待 PO 決策事項（US-002）

1. **分帳架構**：Stripe Connect（自動、合規外包、改動大）vs 手動分帳（沿用 settlement、營運負擔）？
2. **分期範圍與優先**：是否採建議分期 A（卡片付款 MVP）→ B（webhook）→ C（退款）→ D（分帳）？先上哪些？
3. **前端 Stripe.js 選型**：Checkout（hosted，最快/PCI 最輕）vs Elements（自建 UI）？
4. **mock 下線時機**：Phase A~C 穩定後保留 mock 為測試路徑 or 完全下線？
5. **上線 gating**：`PAYMENT_PROVIDER` toggle 是否按租戶灰度（部分商家先上）？
6. **接回 vs 重寫**：孤兒 gateway 抽象層接回主流程 vs 重寫乾淨？（建議接回並補齊，複用已真接線的 createPaymentIntent/驗簽）

---

## 10. 後續實作 US 建議（backlog #10 拆解，13 SP + 外部依賴）

決策通過後，另立實作 US（依 §4 分期）：

| 建議 US | 對應 Phase | 估 SP | 外部依賴 |
|---------|-----------|-------|----------|
| AI-2410 卡片付款 MVP（接 gateway + PaymentIntent + Stripe.js + toggle + DB migration）| A | 5 | Stripe 測試帳號 |
| AI-2411 webhook 事件驅動狀態（解析 + 冪等 + 狀態機）| B | 3 | 公開 webhook 端點 |
| AI-2412 退款真串接（Refund.create + charge.refunded）| C | 2 | Phase B |
| AI-2413 分帳/提現（Connect 或手動，含 onboarding）| D | 5+ | Connect onboarding / KYC / 合規 |

> **注意**：Phase D（分帳/提現）為獨立大主題，可能再拆；外部依賴（Stripe 帳號、Connect onboarding、合規/KYC）為排程關鍵路徑。

---

**文件版本**: v1.0｜**建立者**: SD Marcus + SA Amanda + PM Victoria + Dev David + Claude Code｜**基於**: AISDLC v0.09
