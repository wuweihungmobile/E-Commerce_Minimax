# Sprint 52 計劃 / Sprint 52 Plan

> **Sprint 編號**: Sprint 52
> **期間**: 2027-10-10 ~ 2027-10-23 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-03
> **基於**: Sprint 49 決策文件 PAYMENT_INTEGRATION_ASSESSMENT.md（Phase C）+ Sprint 50/51（Phase A/B 落地）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: 使用者（PO）選定「**AI-2412 真實金流 Phase C：退款真串接**」

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 主軸已確認 | ✅ AI-2412 Phase C（退款真串接）| 延續金流序列（Phase A 卡片付款 → B webhook → C 退款）|
| S51 狀態 | ✅ 已完成（3 commit，未 push）；Phase B webhook 權威狀態落地 | push 債累積 S41~S51（11 Sprint）|
| **退款無前端 UI** | ✅ 探勘確認前端無 refund 呼叫（refund 為 admin/API 觸發，`POST /v2/orders/{id}/refund`）| **Phase C 後端聚焦**（如 S51）|
| **現況缺口（本 Sprint 解）** | 🔴 `StripePaymentGateway.processRefund` 為 STUB（假 re_ id、只讀本地 DB、**從未呼叫 Stripe Refund.create**）；`PaymentStateService.mockRefund` 為 mock 退款（直接設 REFUNDED）| 真實退款需真 Stripe Refund |
| **可複用** | ✅ Payment.stripePaymentIntentId（Phase B webhook/回跳已回填）；PaymentGatewayFactory；toggle STRIPE_PAYMENT_ENABLED | Refund.create 以 payment_intent 退款 |
| **schema 影響** | 🔴 需 migration V61（payments 加 `stripe_refund_id`，記錄 Stripe 退款 id）| 續 V59/V60 付款 schema 演進 |
| **Phase B 失敗路徑 best-effort 補強** | ⚠️ S51 retro：Checkout 惰性建 pi 使失敗/退款事件 pi 查找不可靠 → 本 Sprint 於 createCheckoutSession 補 `payment_intent_data.metadata.order_id`，使事件可由 order_id 可靠對應 | 順帶改善 |
| 冪等 | ✅ 退款狀態轉移冪等（已 REFUNDED → no-op）；charge.refunded webhook 沿用 V60 event 去重 | |
| 向後相容 | ✅ mock 退款路徑保留（toggle 關）；退款 toggle-aware | mock 測試不退步 |
| partial refund | ⚠️ 本 Sprint 只做**全額退款**（REFUNDED）；部分退款（partially_refunded）記錄不做、另評估 | 誠實界線 |
| push 前置 | ⚠️ schema（V61）+ 金流退款變動 → push 需完整 validate-release + validate-schema | 承 S41~S51 債 |

---

## 1. Sprint 52 目標

> **主題**: 真實金流 Phase C——退款真串接（Stripe Refund）

Phase A/B（S50/S51）完成真實卡片付款 + webhook 權威狀態，但**退款仍為 mock/stub**（`StripePaymentGateway.processRefund` 從未呼叫 Stripe）。本 Sprint 讓退款真實串接 Stripe——`Refund.create`（以 payment_intent 退款）+ `charge.refunded` webhook 權威更新 + 記錄 refund id（V61）；退款 toggle-aware（stripe 走真退款、mock 保留）；順帶補 Checkout Session 的 pi metadata 使 Phase B 失敗/退款事件對應可靠。**後端聚焦；只做全額退款；分帳留 Phase D。**

---

## 2. User Stories

### US-001：後端——Stripe Refund 真串接 + 退款 toggle-aware + V61（P3）（AI-2412 後端）

> **SP**: 3 | **優先級**: P3 | **狀態**: 📋 Ready

**AC-001-1**（migration V61）：`payments` 加 `stripe_refund_id VARCHAR`（nullable）；`Payment` entity 加 `stripeRefundId`。`make validate-schema` 對齊無漂移。

**AC-001-2**（gateway 真退款）：`StripePaymentGateway.processRefund` 由 stub 改真——`com.stripe.model.Refund.create`（`RefundCreateParams` 以 `payment_intent` + `amount`；idempotency key），回真 refund id + status；例外對應 E_6006/E_6007。清理假 re_ id 邏輯。

**AC-001-3**（service toggle-aware）：`PaymentStateService.mockRefund` 重構為 `refundOrderPayment(orderId, reason)`——canRefund 檢查 + 找 SUCCESS Payment；toggle 開啟且 Payment 為 STRIPE → 經 gateway 真退款（以 stripePaymentIntentId）+ 存 stripe_refund_id；否則 mock（既有）。標記 Payment REFUNDED + Order REFUNDED（冪等：已 REFUNDED no-op）。OrderPaymentController `/refund` 沿用（改呼叫 refundOrderPayment）。

**AC-001-4**（測試）：`StripePaymentGatewayTest` +真退款（WireMock `/v1/refunds` → refund id）；`PaymentStateServiceStripeTest` +退款（toggle 開 stripe 退款 → gateway 呼叫 + REFUNDED + refund id；toggle 關 mock）；冪等（已 REFUNDED no-op）。

### US-002：後端——charge.refunded webhook + pi metadata 補強 + 測試（P3）（AI-2412 後端）

> **SP**: 2 | **優先級**: P3 | **狀態**: 📋 Ready

**AC-002-1**（webhook 退款事件）：`PaymentWebhookService` 加 dispatch `charge.refunded` → 依 payment_intent（或 order_id metadata）找 Payment → 權威標記 REFUNDED + Order REFUNDED（冪等；沿用 V60 event id 去重）。

**AC-002-2**（pi metadata 補強）：`StripePaymentGateway.createCheckoutSession` 加 `payment_intent_data.metadata.order_id`，使 payment_intent 相關事件（payment_failed / charge.refunded）可由 order_id 可靠對應（補 S51 retro 的 best-effort 缺口）；webhook 失敗/退款路徑改用 order_id metadata 查找（fallback pi id）。

**AC-002-3**（測試）：`PaymentWebhookServiceTest` +charge.refunded（→ markStripeRefunded）+ 重送 skip；現有付款/訂單測試不退步（mock/Phase A/B）；`make validate-schema` V61 無漂移；catch(Exception)=0、@Deprecated=0。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | Stripe Refund 真串接 + 退款 toggle-aware + V61（AI-2412）| 3 | P3 |
| US-002 | charge.refunded webhook + pi metadata 補強 + 測試（AI-2412）| 2 | P3 |
| **承諾合計** | | **5 SP** | |

> **Velocity 參考**：S47=7, S48=8, S49=5, S50=8, S51=5。**本 Sprint 5 SP**，後端聚焦（退款無前端 UI）。**真實金流退款閉環**；分帳（Phase D）+ 上線 checklist（AI-2414）另立。

---

## 4. 執行順序

```
US-001（V61 migration → validate-schema → gateway Refund.create 真（WireMock 單元）
   → PaymentStateService refundOrderPayment toggle-aware + 存 refund_id → 每步編譯+測試 → 整合不退步）
   ↓ 真退款綠
US-002（charge.refunded webhook dispatch + createCheckoutSession pi metadata 補強
   → webhook 退款測試 + 重送 skip → test-db-up 整合）
   ↓ 後端綠
make validate-schema + make validate-e2e（mock/Phase A/B 不退步）
   ↓
收尾（Review / Retro / Release Notes + trackers）
```

**強制**：先 V61 validate-schema；gateway 真退款以 WireMock 驗（不打真 Stripe）；退款冪等 + charge.refunded 沿用 V60 event 去重；mock 退款路徑保留不退步。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| 退款需 payment_intent id，但 Phase A 惰性建 pi | Phase B webhook/回跳已回填 stripePaymentIntentId；退款前檢查 pi 存在，否則報錯（不假退款）|
| 重複退款 | 狀態轉移冪等（已 REFUNDED no-op）+ Refund idempotency key + charge.refunded event 去重（V60）|
| charge.refunded 事件對應不到 Payment（pi 惰性）| AC-002-2 補 pi metadata order_id，webhook 以 order_id 查找（fallback pi）|
| 部分退款未支援 | 本 Sprint 只做全額退款（誠實揭露）；partial refund 另評估 |
| mock 退款路徑退步 | toggle 關保留 mock；既有退款測試綠為 DoD |
| 真退款誤扣 | toggle 預設 mock；stripe 僅測試金鑰 + WireMock 測試；真退款端到端於測試模式人工驗證（AI-2414）|
| V61 schema + 金流退款 → push 需完整 validate-release | 承 S41~S51 債累積後徵詢 |

---

## 6. Definition of Done

- [ ] US-001：V61 migration（stripe_refund_id）+ entity；gateway processRefund 真 Refund.create；PaymentStateService refundOrderPayment toggle-aware（stripe 真退款 + 存 refund_id / mock 保留）；WireMock 單元 + 整合不退步；validate-schema 無漂移
- [ ] US-002：charge.refunded webhook dispatch（權威 REFUNDED，冪等 + event 去重）；createCheckoutSession pi metadata order_id 補強 + webhook order_id 查找；webhook 退款測試 + 重送 skip
- [ ] `make validate-schema` + `make validate-e2e` 綠、mock/Phase A/B 不退步；catch(Exception)=0、@Deprecated=0
- [ ] Sprint 52 Review / Retro / Release Notes + trackers（含只做全額退款、測試不打真 Stripe 之揭露）
- [ ]（檢查點）承 S41~S51 push 債，累積後於徵詢時完整 `make validate-release` 後 push（嚴禁 --no-verify）

---

## 7. 產出物

| 產出物 | 路徑 |
|--------|------|
| Migration | `backend/src/main/resources/db/migration/V61__Add_Stripe_Refund_Id_To_Payments.sql` |
| 後端退款 | `backend/.../infrastructure/payment/StripePaymentGateway.java`（processRefund 真 + createCheckoutSession pi metadata）、`core/payment/PaymentStateService.java`（refundOrderPayment）、`core/payment/PaymentWebhookService.java`（charge.refunded）、`domain/model/payment/Payment.java`（stripeRefundId）|
| 後端測試 | `StripePaymentGatewayTest`、`PaymentStateServiceStripeTest`、`PaymentWebhookServiceTest` |
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 前端無變動**（退款無前端 UI；admin/API 觸發）。

---

## 8. 🔴 待使用者（PO）確認點

1. **範圍**：AI-2412 Phase C = US-001 gateway 真退款 + service toggle-aware + V61（3 SP）+ US-002 charge.refunded webhook + pi metadata 補強（2 SP）= **5 SP**，後端聚焦。是否核准?
2. **只做全額退款**：本 Sprint 全額退款（REFUNDED）；**部分退款（partially_refunded）不做**、另評估。是否同意?
3. **V61 schema**：payments 加 stripe_refund_id。是否確認?
4. **測試不打真 Stripe**：WireMock 驗 Refund.create；真退款端到端於測試模式人工驗證（上線 checklist AI-2414）。是否同意?

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
