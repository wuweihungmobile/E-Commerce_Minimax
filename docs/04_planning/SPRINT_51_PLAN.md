# Sprint 51 計劃 / Sprint 51 Plan

> **Sprint 編號**: Sprint 51
> **期間**: 2027-09-26 ~ 2027-10-09 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-03
> **基於**: Sprint 49 決策文件 PAYMENT_INTEGRATION_ASSESSMENT.md（Phase B）+ Sprint 50 Phase A 落地（AI-2410）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: 使用者（PO）選定「**AI-2411 真實金流 Phase B：webhook 事件驅動狀態（權威）**」

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 主軸已確認 | ✅ AI-2411 Phase B（webhook 驅動狀態）| 補 Phase A「回跳未達」的權威狀態缺口，上線前必要 |
| S50 狀態 | ✅ 已完成（4 commit，未 push）；Phase A 卡片付款 MVP 落地 | push 債累積 S41~S50（10 Sprint）|
| **Phase A 缺口（本 Sprint 解）** | 🔴 付款狀態僅由「回跳 success_url 後 retrieve」更新；買家未回跳（關分頁）→ Stripe 已收款但本地 Payment 停 PROCESSING、Order 停 CREATED | webhook 為 Stripe 官方推薦的**權威狀態來源** |
| **可複用（已真接線）** | ✅ `StripeWebhookController` 簽章驗證真實（`StripeSignatureVerifierService` HMAC-SHA256）；`confirmStripeCheckout` 的狀態更新邏輯（可抽共用） | webhook 目前驗簽後只回 OK、不解析事件（stub）|
| **schema 影響** | 🔴 需 migration V60（`processed_stripe_events` 事件去重表）| S50 V59 後續；付款 webhook 冪等的標準做法 |
| 冪等策略 | ✅ 雙層：(1) 狀態轉移冪等（已 PAID→no-op，S50 已有）(2) 事件 id 去重（V60，防 Stripe 重送重複處理副作用）| Stripe 明確要求 event 去重 |
| 測試策略 | ⚠️ 以樣本 payload（checkout.session.completed / payment_intent.payment_failed）餵入 webhook 端點 | 驗簽於測試模式（secret 空）跳過；整合測試以 MockMvc POST 樣本事件 |
| 向後相容 | ✅ Phase A 回跳 retrieve 保留（webhook 與回跳雙路徑，皆冪等）；mock 路徑不受影響 | toggle 不變 |
| Phase C/D | ⚠️ 本 Sprint 只做 Phase B（付款成功/失敗事件）；退款事件（charge.refunded）留 Phase C（AI-2412）| |
| push 前置 | ⚠️ 有 schema（V60）+ 金流 webhook 變動 → push 需完整 validate-release + validate-schema | 承 S41~S50 債 |

---

## 1. Sprint 51 目標

> **主題**: 真實金流 Phase B——webhook 事件驅動的權威付款狀態

Phase A（S50）以「回跳 retrieve」更新狀態，但買家未回跳時本地狀態會滯後（Stripe 已收款、本地未 PAID）。本 Sprint 讓 `StripeWebhookController` 真正**解析 Stripe 事件**（`checkout.session.completed` → 付款成功、`payment_intent.payment_failed` → 付款失敗），以 webhook 作為**權威狀態來源**更新 Payment/Order，補上 Phase A 缺口。含事件 id 去重（V60，防重送）+ 狀態轉移冪等。**上線前必要；本 Sprint 只做付款成功/失敗事件，退款事件留 Phase C。**

---

## 2. User Stories

### US-001：後端——webhook 事件解析 + 權威狀態更新 + 冪等（P2）（AI-2411 後端）

> **SP**: 3 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-001-1**: 新增 `PaymentWebhookService`——解析 Stripe 事件 payload（驗簽後以 Jackson 取 `id`/`type`/`data.object`）；dispatch：
- `checkout.session.completed`（`payment_status=paid`）→ 依 session id（= Payment.transactionId/stripeSessionId）找 Payment → SUCCESS + 回填 payment_intent + Order PAID（**權威**，冪等：已 SUCCESS/PAID → no-op）；
- `payment_intent.payment_failed` → 依 pi id 找 Payment → FAILED（不改 Order，維持 CREATED 可重試）；
- 未知事件型別 → log + 回 OK（不報錯，Stripe 要求 2xx）。

**AC-001-2**: `StripeWebhookController` 接線——驗簽（既有）通過後委派 `PaymentWebhookService.handleEvent(payload)`；回 200 OK（Stripe 要求 2xx，處理失敗記錄但仍回 2xx 避免無限重送，除非簽章無效回 4xx）。

**AC-001-3**: 抽共用狀態更新——`confirmStripeCheckout`（S50 回跳路徑）與 webhook 路徑共用「標記付款成功 + Order PAID（冪等）」核心邏輯，確保雙路徑一致（避免分歧）。

**AC-001-4**: 測試——`PaymentWebhookServiceTest`（單元）：checkout.session.completed paid → SUCCESS+PAID、payment_intent.payment_failed → FAILED、已 PAID 冪等 no-op、未知事件 no-op；`StripeWebhookControllerE2ETest` 或整合：POST 樣本事件 payload（測試模式 secret 空跳驗簽）→ 驗 Payment/Order 狀態更新。

### US-002：後端——事件去重表（V60）+ webhook 冪等強化 + 測試（P2）（AI-2411 後端）

> **SP**: 2 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-002-1**: migration V60 `processed_stripe_events`（`event_id VARCHAR PK`、`event_type`、`processed_at`）；`ProcessedStripeEvent` entity + repository。

**AC-002-2**: `PaymentWebhookService` 事件 id 去重——處理前檢查 event id 是否已處理，已處理則 skip（回 OK）；處理成功後記錄 event id。防 Stripe 重送造成重複副作用。

**AC-002-3**: 測試——事件重送（同 event id 兩次）→ 第二次 skip（不重複更新）；`make validate-schema` V60 對齊無漂移。

**AC-002-4**: 現有付款/訂單測試全數不退步（mock 路徑 + Phase A 回跳路徑）；catch(Exception)=0、@Deprecated=0。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 後端 webhook 事件解析 + 權威狀態更新 + 冪等（AI-2411）| 3 | P2 |
| US-002 | 事件去重表 V60 + 冪等強化 + 測試（AI-2411）| 2 | P2 |
| **承諾合計** | | **5 SP** | |

> **Velocity 參考**：S46=8, S47=7, S48=8, S49=5, S50=8。**本 Sprint 5 SP**，後端聚焦（webhook 為 server-side，前端無變動）。**補上真實金流上線前必要的權威狀態**。Phase C（退款）/ D（分帳）另立。

---

## 4. 執行順序

```
US-001（PaymentWebhookService 事件解析 + dispatch → 抽共用狀態核心（與 confirmStripeCheckout 共用）
   → 每步 mvn 編譯 + 單元測試 → 整合測試 POST 樣本事件）
   ↓ webhook 權威狀態更新綠
US-002（V60 processed_stripe_events + entity/repo → make validate-schema → 事件去重接入 service
   → 重送冪等測試 → test-db-up 整合不退步）
   ↓ 後端綠
make validate-schema + make validate-e2e（mock/Phase A 不退步）
   ↓
收尾（Review / Retro / Release Notes + trackers）
```

**強制**：webhook 一律回 2xx（除簽章無效）避免 Stripe 無限重送；狀態轉移冪等 + 事件 id 去重雙保險；先 V60 validate-schema。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| webhook 重送造成重複處理 | 雙層冪等：狀態轉移（已 PAID no-op）+ event id 去重（V60）|
| webhook 與回跳雙路徑狀態分歧 | 抽共用「標記成功 + Order PAID」核心，兩路徑共用（AC-001-3）|
| 處理失敗回非 2xx → Stripe 無限重送 | 除簽章無效（4xx），處理失敗記錄但回 2xx；失敗事件不阻斷 |
| 測試模式驗簽跳過（secret 空）| 整合測試以測試模式 POST 樣本事件；生產須配置 STRIPE_WEBHOOK_SECRET（部署 checklist）|
| 3DS/requires_action 中間態 | 本 Sprint 聚焦 completed/failed；requires_action 中間態記錄不阻斷，後續評估 |
| V60 schema + 金流 webhook → push 需完整 validate-release | 承 S41~S50 債累積後徵詢 |

---

## 6. Definition of Done

- [ ] US-001：PaymentWebhookService 解析 + dispatch（checkout.session.completed/payment_intent.payment_failed/未知）；StripeWebhookController 接線回 2xx；與 confirmStripeCheckout 共用狀態核心；單元 + 整合（樣本事件）
- [ ] US-002：V60 processed_stripe_events + entity/repo；event id 去重接入；重送冪等測試；`make validate-schema` 無漂移
- [ ] `make validate-schema` + `make validate-e2e` 綠、mock/Phase A 不退步；catch(Exception)=0、@Deprecated=0
- [ ] Sprint 51 Review / Retro / Release Notes + trackers（含測試模式驗簽跳過、生產須配 secret 之揭露）
- [ ]（檢查點）承 S41~S50 push 債，累積後於徵詢時完整 `make validate-release` 後 push（嚴禁 --no-verify）

---

## 7. 產出物

| 產出物 | 路徑 |
|--------|------|
| Migration | `backend/src/main/resources/db/migration/V60__Create_Processed_Stripe_Events.sql` |
| 後端 webhook | `backend/.../core/payment/PaymentWebhookService.java`（新）、`api/controller/payment/StripeWebhookController.java`（接線）、`core/payment/PaymentStateService.java`（抽共用狀態核心）|
| 後端 entity | `backend/.../domain/model/payment/ProcessedStripeEvent.java`（新）+ repository |
| 後端測試 | `PaymentWebhookServiceTest`、webhook 整合測試 |
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 前端無變動**（webhook 為 server-side；Phase A success 頁的回跳確認保留為輔助路徑）。

---

## 8. 🔴 待使用者（PO）確認點

1. **範圍**：AI-2411 Phase B = US-001 webhook 事件解析 + 權威狀態（3 SP）+ US-002 事件去重表 V60 + 冪等（2 SP）= **5 SP**，後端聚焦。是否核准?
2. **事件範圍**：本 Sprint 只做付款成功（checkout.session.completed）+ 失敗（payment_intent.payment_failed）；**退款事件（charge.refunded）留 Phase C（AI-2412）**。是否同意?
3. **V60 schema**：新增 processed_stripe_events 事件去重表。是否確認?
4. **測試模式驗簽**：測試以 secret 空跳過驗簽 POST 樣本事件；生產須配置 STRIPE_WEBHOOK_SECRET（部署 checklist）。是否同意此測試策略?

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
