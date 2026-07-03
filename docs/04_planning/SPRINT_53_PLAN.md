# Sprint 53 計劃 / Sprint 53 Plan

> **Sprint 編號**: Sprint 53
> **期間**: 2027-10-24 ~ 2027-11-06 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-04
> **基於**: Sprint 52 Retro Action Items（AI-2413 / AI-2407 / AI-2414 / AI-1903）+ 本 Sprint 前置技術調查
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: 使用者（PO）選定「**AI-2413 真實金流 Phase D-1：Stripe Connect 帳戶 onboarding**」為主軸；AI-2407 定價規則語意評估另立 Sprint 54（需先做 tie-break 語意決策）；Phase D-2（代收後分潤/提現）另立 Sprint 55

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| 分帳方式 PO 決策 | ✅ **Stripe Connect**（非手動撥款）| 2026-07-04 PO 決策 |
| Phase D 拆分 | ✅ 拆兩個 Sprint：D-1 帳戶 onboarding（本 Sprint）/ D-2 代收後 transfer 分潤（Sprint 55）| 與既有 Phase A/B/C 單一聚焦拆分粒度一致 |
| Connect 現況調查 | ✅ 完成（本 Sprint 前置）| 目前**完全無** Connect 相關程式碼；無 `stripeAccountId` 欄位、無 Account/AccountLink/Transfer API 呼叫 |
| 可複用架構 | ✅ `PaymentGateway` 抽象層、Order-Tenant 一對一歸屬、tenant-scoped feature toggle（`FeatureToggleService`）| Phase D-1 可比照既有模式擴充，不需重構付款閉環 |
| AI-2407 定價規則 | ⏸️ **延後至 Sprint 54**（獨立評估：range 查詢 containment→overlap 語意修正 + 同優先級 tie-break 業務語意決策，估 5-8 SP，非本 Sprint 範圍）| Retro 原描述為「待評估」，前置調查發現規模超出小型修正 |
| Stripe Connect 平台啟用（外部依賴）| 🔴 **待確認**：Stripe 帳號是否已於 Dashboard 啟用 Connect Platform Profile | 測試模式通常可用；正式環境需 Stripe 審核平台資料，這是外部依賴，非程式問題 |
| 前端範圍 | ⚠️ 本 Sprint **僅後端 API**（onboarding 發起/狀態查詢）；賣家後台「前往設定收款帳戶」按鈕視容量列為 stretch，不計入承諾 SP | 與過去金流各 Phase「聚焦後端」模式一致 |
| push 狀態 | ✅ 本地 main 與 origin/main 已同步（S52 收尾已 push，push 債已清償）| 不再累積 |

---

## 1. Sprint 53 目標

> **主題**: 真實金流 Phase D-1——Stripe Connect Express 帳戶 onboarding

賣家目前完全沒有 Stripe Connect 帳戶概念（`Tenant` 無 `stripeConnectAccountId` 等欄位）。本 Sprint 讓平台能為賣家（`Tenant`）建立 Stripe Connect Express 帳戶、產生 onboarding link 導向 Stripe 代管的 KYC 流程，並透過 `account.updated` webhook 同步帳戶狀態（`charges_enabled`/`payouts_enabled`/`details_submitted`）回本地資料庫。**本 Sprint 只做「帳戶開通」，不做「代收後分潤 transfer」（Phase D-2，Sprint 55）**，兩者是可獨立驗收的技術模組。額外交付兩份小型文件產出：AI-2414 真金流上線 checklist、AI-1903 買家閉環 live 走查腳本（皆為文件撰寫，實際人工執行需使用者親自操作）。

---

## 2. User Stories

### US-001：後端——Tenant Stripe Connect 帳戶 onboarding（P2）（AI-2413 Phase D-1）

> **SP**: 5 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-001-1**: migration V62 `tenants` 表新增欄位：`stripe_connect_account_id VARCHAR`、`connect_onboarding_status VARCHAR`（`NOT_STARTED`/`PENDING`/`COMPLETE`，預設 `NOT_STARTED`）、`connect_charges_enabled BOOLEAN`（預設 false）、`connect_payouts_enabled BOOLEAN`（預設 false）；`Tenant` entity 對應欄位。

**AC-001-2**: `StripePaymentGateway` 擴充兩個方法（比照既有 `createCheckoutSession` 模式，走官方 SDK + `RequestOptions` API key）：
- `createConnectAccount()` → `Account.create`（`type=express`）回傳 Stripe account id；
- `createAccountLink(accountId, refreshUrl, returnUrl)` → `AccountLink.create`（`type=account_onboarding`）回傳 onboarding URL。

**AC-001-3**: 新增 `TenantStripeConnectService`：
- `initiateOnboarding(tenantId)`：若 tenant 尚無 `stripeConnectAccountId` 則呼叫 `createConnectAccount` 並存回 tenant；接著呼叫 `createAccountLink` 回傳 onboarding URL 給呼叫端；狀態轉 `PENDING`。冪等：已有 accountId 則直接用既有 id 產生新的 account link（Stripe account link 為一次性、可重新產生）。
- `getAccountStatus(tenantId)`：呼叫 `Account.retrieve` 查最新 `charges_enabled`/`payouts_enabled`/`details_submitted`，回填 tenant 欄位（若 `details_submitted=true` 且兩者皆 true → 狀態轉 `COMPLETE`）。
- 受 `STRIPE_CONNECT_ENABLED`（tenant-scoped feature toggle，比照 `STRIPE_PAYMENT_ENABLED` 模式）保護；toggle 關閉時回錯誤，不呼叫 Stripe。

**AC-001-4**: API：`SellerDashboardController` 新增 `POST /v2/seller/dashboard/stripe-connect/onboarding`（發起 onboarding，回傳 URL）、`GET /v2/seller/dashboard/stripe-connect/status`（查詢帳戶狀態）；僅限該賣家（tenant context）操作自己的帳戶。

**AC-001-5**: 測試——WireMock 驗 `Account.create`/`AccountLink.create` 呼叫參數正確（單元）；`TenantStripeConnectServiceTest`：首次 onboarding 建立 accountId、重複呼叫複用既有 accountId、狀態查詢回填正確、toggle 關閉時拒絕；API 整合測試（`test-db-up`）。

### US-002：後端——`account.updated` webhook 狀態同步 + 冪等 + 測試（P2）（AI-2413 Phase D-1）

> **SP**: 3 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-002-1**: `PaymentWebhookService` dispatch 擴充 `account.updated` 事件：解析 `data.object.id`（Stripe account id）+ `charges_enabled`/`payouts_enabled`/`details_submitted`；依 `stripe_connect_account_id` 反查對應 `Tenant`，回填三個狀態欄位；找不到對應 tenant → log + 回 OK（不報錯，可能是非本平台帳戶事件）。

**AC-002-2**: 沿用 V60 `processed_stripe_events` 事件去重表（不需新表）；`account.updated` 事件同樣走既有去重 + 冪等機制，與 Phase B/C 一致。

**AC-002-3**: 測試——`account.updated` 樣本事件（`charges_enabled=true, payouts_enabled=true, details_submitted=true`）→ tenant 狀態轉 `COMPLETE`；事件重送 → 第二次 skip；找不到對應 tenant → 不拋錯、回 OK。

**AC-002-4**: 既有付款/退款 webhook（checkout.session.completed / payment_intent.payment_failed / charge.refunded）與 mock 路徑全數不退步；catch(Exception)=0、@Deprecated=0。

### US-003：AI-2414 真金流上線 checklist 文件（P3）

> **SP**: 1 | **優先級**: P3 | **狀態**: 📋 Ready

**AC-003-1**: 撰寫 `docs/08_deployment/STRIPE_PRODUCTION_CHECKLIST.md`，涵蓋：`STRIPE_WEBHOOK_SECRET`/`STRIPE_SECRET_KEY` 正式金鑰設定步驟、webhook 公開端點確認（HTTPS + Stripe Dashboard 註冊事件）、Stripe **測試模式**端到端人工驗證步驟（付款成功/失敗、退款、Connect onboarding 各一次）、正式金鑰切換前後注意事項、Connect Platform Profile 啟用確認。

**AC-003-2**: 本 Sprint 僅交付「文件」；文件中列出的人工驗證步驟由使用者於測試模式親自執行，不在本 Sprint 自動化範圍內（誠實揭露）。

### US-004：AI-1903 買家閉環 live 走查腳本（P3）

> **SP**: 1 | **優先級**: P3 | **狀態**: 📋 Ready

**AC-004-1**（執行中發現：`docs/03_testing/BUYER_JOURNEY_LIVE_WALKTHROUGH_CHECKLIST.md` 已於 Sprint 41 建立且涵蓋此範圍，Sprint 52 Retro 續留者為「執行」而非「撰寫」）：改為**更新既有文件**，反映 S49-52 真實 Stripe 金流（Phase A/B/C）上線後對走查步驟的影響（C 節金流走查項需區分 mock/真 Stripe toggle 兩種路徑），並連結新增的 [STRIPE_PRODUCTION_CHECKLIST.md](../08_deployment/STRIPE_PRODUCTION_CHECKLIST.md)（US-003）。不重複建立新檔案。

**AC-004-2**: 本 Sprint 僅交付「走查腳本」；實際 live 環境走查由使用者親自執行，不在本 Sprint 自動化範圍內（誠實揭露）。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | Tenant Stripe Connect 帳戶 onboarding（AI-2413 Phase D-1）| 5 | P2 |
| US-002 | account.updated webhook 狀態同步 + 冪等（AI-2413 Phase D-1）| 3 | P2 |
| **承諾合計** | | **8 SP** | |
| US-003 | 真金流上線 checklist 文件（AI-2414）| 不計點 | P3 |
| US-004 | 買家閉環 live 走查腳本（AI-1903）| 不計點 | P3 |

> **Velocity 參考** S47=7, S48=8, S49=5, S50=8, S51=5, S52=5，歷史區間 5-8 SP。**本 Sprint 正式承諾 8 SP（US-001+US-002），貼齊歷史高點但不超支**（2026-07-04 決策：原規劃含 US-003/US-004 共 10 SP 高於歷史區間，改將兩份純文件產出降為「不計點附屬產出」，與主軸容量脫鉤，徹底消除超支風險，而非事後才降級）。US-003/US-004 仍於本 Sprint 交付，但**主軸（US-001/002）優先，容量緊繃時可順延至收尾前完成，不影響 Connect onboarding 主軸驗收**。

---

## 4. 執行順序

```
US-001（V62 migration tenants 加 Connect 欄位 → validate-schema
   → StripePaymentGateway 擴充 createConnectAccount/createAccountLink（WireMock 單元）
   → TenantStripeConnectService（onboarding 發起/複用/狀態查詢 + toggle 保護）
   → SellerDashboardController API 接線 → 每步編譯+測試 → 整合測試）
   ↓ Connect onboarding 綠
US-002（PaymentWebhookService 擴充 account.updated dispatch
   → 反查 tenant 回填狀態 → 沿用 V60 去重 → webhook 測試 + 重送冪等 + 既有 webhook 不退步）
   ↓ 後端綠
make validate-schema + make validate-e2e（mock/Phase A/B/C 不退步）
   ↓
US-003 + US-004（文件撰寫，不影響程式碼；可與 US-001/002 並行或收尾前完成）
   ↓
收尾（Review / Retro / Release Notes + trackers）
```

**強制**：先 V62 validate-schema；Connect API 呼叫以 WireMock 驗（不打真 Stripe）；`STRIPE_CONNECT_ENABLED` toggle 預設關閉，避免誤建正式 Connect 帳戶。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| Stripe 帳號尚未啟用 Connect Platform Profile（外部依賴，非程式問題）| 開發/測試以測試模式進行（測試模式通常免申請即可用 Express Connect）；正式上線前確認 Dashboard 平台資料已送審，列入 US-003 checklist |
| Account link 為一次性、逾期即失效 | `initiateOnboarding` 設計為可重複呼叫複用既有 accountId、重新產生新 link（AC-001-3 冪等設計）|
| account.updated 事件對應不到 tenant（accountId 尚未存回本地、或非本平台帳戶）| 找不到對應 tenant 時 log + 回 OK，不報錯、不阻斷其他 webhook 處理（AC-002-1）|
| 正式承諾 8 SP 貼齊歷史高點 | US-003/US-004 已降為不計點附屬產出，與主軸容量脫鉤，主軸（US-001/002）超支風險已於規劃階段消除 |
| 前端未串接 onboarding 按鈕，賣家無法從 UI 觸發 | 本 Sprint 明確排除前端（僅後端 API）；API 可先以 Postman/curl 驗證，前端串接視 Sprint 54+ 容量另評估 |
| Connect 帳戶為敏感金流操作，誤觸可能建立真實 Stripe 帳戶 | toggle 預設關閉；測試一律用 Stripe 測試模式金鑰；WireMock 驗 SDK 呼叫正確性，不打真 Stripe API |

---

## 6. Definition of Done

- [ ] US-001：V62 migration（tenants 加 4 個 Connect 欄位）+ entity；`StripePaymentGateway` 新增 `createConnectAccount`/`createAccountLink`；`TenantStripeConnectService`（onboarding 發起/複用/狀態查詢 + toggle 保護）；API 接線；WireMock 單元 + 整合測試；`make validate-schema` 無漂移
- [ ] US-002：`account.updated` webhook dispatch + 反查 tenant 回填狀態 + 沿用 V60 去重；webhook 測試 + 重送冪等；既有 webhook（付款/退款）不退步
- [ ] US-003：`docs/08_deployment/STRIPE_PRODUCTION_CHECKLIST.md` 完成（含誠實揭露：人工驗證步驟需使用者親自執行）
- [ ] US-004：`docs/03_testing/BUYER_JOURNEY_LIVE_WALKTHROUGH_CHECKLIST.md` 更新（新增真 Stripe toggle 走查項 + 連結 STRIPE_PRODUCTION_CHECKLIST.md；不重複建檔）
- [ ] `make validate-schema` + `make validate-e2e` 綠、mock/Phase A/B/C 不退步；catch(Exception)=0、@Deprecated=0
- [ ] Sprint 53 Review / Retro / Release Notes + trackers（含 US-003/US-004 不計點附屬產出、Connect Platform Profile 外部依賴之揭露）
- [ ]（檢查點）承諾範圍內完成後，於徵詢時執行 `make validate-release` 後 push（嚴禁 --no-verify）

---

## 7. 產出物

| 產出物 | 路徑 |
|--------|------|
| Migration | `backend/src/main/resources/db/migration/V62__Add_Stripe_Connect_Fields_To_Tenants.sql` |
| 後端 Connect | `backend/.../infrastructure/payment/StripePaymentGateway.java`（新增 createConnectAccount/createAccountLink）、`core/tenant/TenantStripeConnectService.java`（新）、`domain/model/tenant/Tenant.java`（新欄位）|
| 後端 webhook | `backend/.../core/payment/PaymentWebhookService.java`（account.updated dispatch）|
| 後端 API | `api/controller/SellerDashboardController.java`（onboarding/status 端點）|
| 後端測試 | `TenantStripeConnectServiceTest`、`StripePaymentGatewayTest`（擴充）、`PaymentWebhookServiceTest`（擴充）|
| 文件 | `docs/08_deployment/STRIPE_PRODUCTION_CHECKLIST.md`（新）、`docs/03_testing/BUYER_JOURNEY_LIVE_WALKTHROUGH_CHECKLIST.md`（更新）|
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 前端無變動**（Connect onboarding 僅後端 API；賣家後台按鈕串接視後續容量評估）。

---

## 8. 範圍決策紀錄（2026-07-04，PO 授權 Claude Code 逕行決策）

> 以下皆屬工程/範圍取捨判斷、非需 PO 獨有商業知識之決策，經 PO 明確授權後由 Claude Code 直接拍板，不再逐項徵詢：

1. **範圍與容量**：AI-2413 Phase D-1 = US-001 Connect onboarding 後端（5 SP）+ US-002 webhook 狀態同步（3 SP）= **正式承諾 8 SP**（貼齊歷史高點、不超支）；US-003 上線 checklist + US-004 live 走查腳本降為**不計點附屬產出**，容量緊繃時可順延至收尾前完成。原規劃 10 SP 已依此下修。
2. **Connect 帳戶類型**：採 **Express**（非 Standard）——平台主導 UX、Stripe 代管 KYC，符合本平台「代收後 transfer 給賣家」的既有架構（`Order.tenantId` 一對一歸屬），為電商/服務型 marketplace 業界標準做法。
3. **Phase D 拆分**：本 Sprint 只做帳戶 onboarding，**不做代收後分潤（transfer）**；分潤邏輯獨立為 Phase D-2，另立 Sprint 55，與既有 Phase A/B/C 單一聚焦拆分粒度一致。
4. **前端範圍**：本 Sprint **不含前端**，賣家無法從 UI 觸發 onboarding（API 直接呼叫驗證）；前端串接（賣家後台按鈕）視 Sprint 54+ 容量另評估。
5. **AI-2407 順序**：定價規則評估延後至 **Sprint 54**——前置技術調查已發現規模達 5-8 SP、且需 PO/SD 先做「同優先級 tie-break」業務語意決策，非本 Sprint 範圍。

> **仍需 PO 於執行過程中另行確認的真實外部依賴**（非本次可代為決策）：正式上線前需 PO/Dev 確認 Stripe Dashboard 之 Connect Platform Profile 已完成平台資料送審——這屬於 Stripe 帳號層級的行政事項，Claude Code 無法代為查證或送審，已列入 US-003 checklist 待上線前執行。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
