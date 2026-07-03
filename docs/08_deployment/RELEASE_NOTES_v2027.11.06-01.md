# Release Notes - v2027.11.06-01 (Sprint 53)

**發布日期**: 2027-11-06（規劃）／實作完成 2026-07-04
**發布類型**: Minor（真實金流 Phase D-1：Stripe Connect Express 帳戶 onboarding；含 schema 變更 V62；後端聚焦）
**Sprint**: Sprint 53
**狀態**: ⏳ 待 push（本 Sprint 2 commit；於檢查點徵詢後完整 `make validate-release` 後 push）

> Sprint 53 主題：**真實金流 Phase D-1——Stripe Connect Express 帳戶 onboarding**。承 Phase A/B/C（S50~52）真實付款/權威狀態/退款，本 Sprint 讓**賣家可開通 Stripe Connect Express 帳戶**（此前完全無 Connect 相關程式碼）。**只做帳戶 onboarding，不做代收後 transfer 分潤**（Phase D-2 另立 AI-2416）；不含前端；只做 Express（非 Standard/Custom）。同時交付 AI-2414 真金流上線 checklist + AI-1903 買家走查文件更新（不計點附屬產出）。

---

## 新功能 / 改進 🚀

- **Stripe Connect Express 帳戶建立（AI-2413）**：`StripePaymentGateway` 新增 `createConnectAccount`（`Account.create` type=express）；`TenantStripeConnectService.initiateOnboarding` 首次呼叫建帳戶並存回 tenant，重複呼叫複用既有 accountId。
- **Onboarding link（AI-2413）**：`createAccountLink`（`AccountLink.create` type=account_onboarding）產生一次性導轉 URL，導向 Stripe 代管 KYC 表單。
- **帳戶狀態查詢與同步（AI-2413）**：`getConnectAccountStatus`（即時查詢）+ `account.updated` webhook（權威同步）雙路徑回填 `charges_enabled`/`payouts_enabled`/`onboarding_status`；三條件皆真才轉 COMPLETE。
- **API（AI-2413）**：`POST /v2/seller/dashboard/stripe-connect/onboarding`、`GET /v2/seller/dashboard/stripe-connect/status`（`hasRole('SELLER')`）。
- **真金流上線 checklist（AI-2414）**：新增 `STRIPE_PRODUCTION_CHECKLIST.md`，涵蓋金鑰/webhook/Connect Platform Profile/測試模式端到端驗證/正式金鑰切換順序。
- **買家走查文件更新（AI-1903）**：既有 `BUYER_JOURNEY_LIVE_WALKTHROUGH_CHECKLIST.md`（Sprint 41）新增真 Stripe toggle 走查項。

## 測試 / 驗證 ✅

- **後端單元**：`StripePaymentGatewayTest` +4（TC-S007~010）+ `TenantStripeConnectServiceTest` 6（UT-CONNECT-001~006）+ `PaymentWebhookServiceTest` +2（UT-WH-007~008）= **新增 12 tests，0 fail**。
- **後端整合（真實 DB）**：`M13SellerStripeConnectIntegrationTest` **4 tests 0 fail**（IT-CONNECT-001~004）；`M16ErpIntegrationTest` 37 tests 0 fail（不退步）。
- **全量後端回歸**：`mvn test` **536 tests，0 failures，0 errors**。
- **schema 漂移守門（`make validate-schema`）**：無漂移（V62 與 entity 對齊，前後各驗證一次）。
- **前端變動**：無（本 Sprint 明確排除前端）。
- **catch(Exception) / @Deprecated 計數**：維持 0。

## 技術決策 / 已知限制 ⚠️

- **只做帳戶 onboarding，不做分潤（誠實揭露 Rule 12）**：代收後 transfer 給賣家為獨立 Phase D-2，另立 AI-2416。
- **不含前端**：賣家無法從 UI 觸發 onboarding，本 Sprint 僅後端 API。
- **測試以 WireMock（不打真 Stripe）**：真 onboarding 端到端於 Stripe 測試模式人工驗證（`STRIPE_PRODUCTION_CHECKLIST.md`）。
- **只做 Express（非 Standard/Custom）**：PO 決策依業界 marketplace 慣例。
- **正式上線需人工確認 Connect Platform Profile**：Stripe Dashboard 平台資料送審屬帳號層級行政程序。
- **既有測試 seeding 修正**：`TestDatabaseInitializer`/`M16ErpIntegrationTest` 兩處 raw SQL tenant 種子已同步補齊新增 NOT NULL 欄位。
- **V62 schema**：續 V59/V60/V61；ADD COLUMN 皆有 DEFAULT，不影響既有資料。

## 資料庫遷移 🗄️

- **V62__Add_Stripe_Connect_Fields_To_Tenants.sql**：`tenants` 加 `stripe_connect_account_id`（nullable）、`connect_onboarding_status`（NOT NULL DEFAULT 'NOT_STARTED'）、`connect_charges_enabled`/`connect_payouts_enabled`（NOT NULL DEFAULT FALSE）。Flyway V61 → **V62**。

## 內含 Commit（Sprint 53）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 53 Plan | 66e7b64 | 真實金流 Phase D-1 Connect onboarding（2 US / 8 SP）|
| US-001+US-002 AI-2413 | 731024f | gateway Connect 方法 + TenantStripeConnectService + API + webhook account.updated + V62 + US-003/US-004 文件 |
| Sprint 53 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-04
**基於**: AISDLC v0.09 Release Management Workflow
