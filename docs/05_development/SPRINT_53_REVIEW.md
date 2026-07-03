# Sprint 53 Review / Sprint 53 評審會議

> **Sprint 編號**: Sprint 53
> **期間**: 2027-10-24 ~ 2027-11-06
> **評審日期**: 2026-07-04
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 真實金流 Phase D-1——Stripe Connect Express 帳戶 onboarding

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | Tenant Stripe Connect 帳戶 onboarding（AI-2413 Phase D-1）| 5 | ✅ 完成 |
| US-002 | account.updated webhook 狀態同步 + 冪等（AI-2413 Phase D-1）| 3 | ✅ 完成 |
| US-003 | 真金流上線 checklist 文件（AI-2414，不計點）| — | ✅ 完成 |
| US-004 | 買家閉環 live 走查腳本更新（AI-1903，不計點）| — | 🟡 部分（文件已更新，真人走查續留）|

**正式承諾 8 SP（US-001~002）全數完成**。承 Phase A/B/C（S50~52）真實付款/權威狀態/退款，本 Sprint 讓**賣家可開通 Stripe Connect Express 帳戶**（此前完全無 Connect 相關程式碼）。**只做帳戶 onboarding，不做代收後 transfer 分潤**（另立 AI-2416，Phase D-2）；不含前端；只做 Express（非 Standard/Custom）。

---

## 2. 交付內容

- **US-001（後端，AI-2413，V62）**：
  - **V62 migration**：`tenants` 加 `stripe_connect_account_id`、`connect_onboarding_status`（NOT_STARTED/PENDING/COMPLETE）、`connect_charges_enabled`、`connect_payouts_enabled`；`Tenant` entity 對應欄位 + `ConnectOnboardingStatus` enum。
  - **gateway 擴充**：`StripePaymentGateway` 新增 `createConnectAccount`（`Account.create` type=express）、`createAccountLink`（`AccountLink.create` type=account_onboarding）、`getConnectAccountStatus`（`Account.retrieve`）；`PaymentGateway` 介面加對應 default 方法（比照 checkout session 模式，非 Stripe gateway 拋 `UnsupportedOperationException`）；`PaymentGatewayFactory` 加委派方法。
  - **TenantStripeConnectService**（新）：`initiateOnboarding`（首次建帳戶 + 存 accountId，重複呼叫複用既有 accountId 只重新產生 account link）+ `getAccountStatus`（即時查詢並回填，三條件皆真才轉 COMPLETE）；受 `STRIPE_CONNECT_ENABLED` toggle 保護（`featureToggleService.checkFeatureEnabled`）。
  - **API**：`SellerDashboardController` 新增 `POST /v2/seller/dashboard/stripe-connect/onboarding`、`GET /v2/seller/dashboard/stripe-connect/status`（`@PreAuthorize("hasRole('SELLER')")`）。
- **US-002（後端，AI-2413）**：
  - `PaymentWebhookService` dispatch 擴充 `account.updated`——解析 `charges_enabled`/`payouts_enabled`/`details_submitted`，依 `stripe_connect_account_id` 反查 tenant 回填狀態；找不到對應 tenant 則 log + no-op（不報錯）；沿用既有 V60 `processed_stripe_events` 去重。
  - `TenantStripeConnectService.syncAccountStatusFromWebhook` 新增，與 `getAccountStatus` 共用 COMPLETE 轉換邏輯。
- **US-003（文件，AI-2414）**：`docs/08_deployment/STRIPE_PRODUCTION_CHECKLIST.md`（新）——金鑰/環境變數、webhook 端點註冊事件、Connect Platform Profile、測試模式端到端人工驗證項目、正式金鑰切換順序、已知限制揭露。
- **US-004（文件，AI-1903）**：更新既有 `docs/03_testing/BUYER_JOURNEY_LIVE_WALKTHROUGH_CHECKLIST.md`（Sprint 41 建立）——新增真 Stripe toggle 走查項（區分 mock/真 Stripe 兩路徑）+ 連結 STRIPE_PRODUCTION_CHECKLIST.md。**未重複建立新檔案**（執行中發現既有文件已涵蓋 AI-1903 範圍，Sprint 52 Retro 續留者為「真人執行」而非「撰寫」）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 後端單元（`StripePaymentGatewayTest` +4 含 TC-S007~010、`TenantStripeConnectServiceTest` 6 含 UT-CONNECT-001~006、`PaymentWebhookServiceTest` +2 含 UT-WH-007~008）| ✅ 新增 12 tests，0 fail |
| 後端整合（真實 DB，`M13SellerStripeConnectIntegrationTest`）| ✅ **4 tests 0 fail**（IT-CONNECT-001~004：SELLER 200/BUYER 403/狀態查詢/無 JWT 401）；`M16ErpIntegrationTest` 37 tests 0 fail（不退步）|
| **全量後端回歸測試**（`mvn test`）| ✅ **536 tests，0 failures，0 errors** |
| schema 漂移守門（`make validate-schema`）| ✅ 無漂移（V62 與 entity 對齊，前後各驗證一次）|
| 前端變動 | 無（本 Sprint 明確排除前端）|
| catch(Exception) / @Deprecated 計數 | ✅ 維持 0 |

---

## 4. 誠實揭露（Rule 12）

1. **執行中發現並修正的既有測試 seeding 陷阱**：`TestDatabaseInitializer.java`（system tenant 種子）與 `M16ErpIntegrationTest.java`（fixed-id tenant 種子）皆以 raw SQL INSERT 建立 tenant 列，未包含新增的 3 個 NOT NULL 欄位，導致 integration-test profile（`ddl-auto=update`）下違反 NOT NULL 約束。此為既有「erp-tenant-test-seeding-gotcha」模式的再現（非本 Sprint 引入的新設計問題，而是既有 raw SQL 種子對 schema 變更的脆弱性），已同步修正兩處 INSERT 陳述式並驗證不影響其他測試（M16 ERP 37 tests + 全量 536 tests 皆綠）。
2. **只做 Connect 帳戶 onboarding，不做分潤**：代收後 transfer 給賣家（`Transfer.create`/`transfer_data`）為獨立 Phase D-2，另立 AI-2416，需等本 Phase D-1 帳戶大量上線後再評估時程。
3. **不含前端**：賣家無法從 UI 觸發 onboarding，本 Sprint 僅後端 API（可用 curl/Postman 驗證）；前端串接（賣家後台按鈕）視 Sprint 54+ 容量另評估。
4. **測試以 WireMock（不打真 Stripe）**：`Account.create`/`AccountLink.create`/`Account.retrieve` 皆以 WireMock 攔截驗證；真 onboarding 端到端於 Stripe 測試模式人工驗證（`STRIPE_PRODUCTION_CHECKLIST.md` D 節）。
5. **只做 Express（非 Standard/Custom）**：PO 決策依業界電商 marketplace 慣例（平台主導 UX、Stripe 代管 KYC）。
6. **AI-1903 續留**：真人於 live 環境的跨角色資料流走查（含本 Sprint 新增的真 Stripe toggle 路徑）仍需使用者親自執行，Claude Code 僅能協助文件與腳本，無法代為操作。
7. **正式上線需人工確認 Connect Platform Profile**：Stripe Dashboard 平台資料送審屬帳號層級行政程序，非本次可代查或代辦。
8. **範圍決策記錄**：原規劃 US-001~004 共 10 SP 高於歷史區間（5-8 SP），經 PO 授權 Claude Code 逕行決策後下修為正式承諾 8 SP，US-003/US-004 降為不計點附屬產出；AI-2407 定價規則語意評估延後至 Sprint 54（前置調查發現規模達 5-8 SP，需另做 tie-break 業務語意決策）。

---

## 5. Demo 重點

- **賣家發起 onboarding**：`POST /v2/seller/dashboard/stripe-connect/onboarding` → 首次呼叫建立 Connect Express 帳戶並回傳 onboarding URL（WireMock TC-S007/008 + UT-CONNECT-001 佐證）。
- **複用既有帳戶**：重複呼叫 onboarding 端點不重建帳戶，只重新產生 account link（UT-CONNECT-002）。
- **狀態查詢與同步**：`GET .../stripe-connect/status` 即時查詢 Stripe 回填本地狀態（UT-CONNECT-003/004）；`account.updated` webhook 亦可權威同步（UT-WH-007/008）。
- **toggle 關閉時拒絕**：`STRIPE_CONNECT_ENABLED` 關閉時所有 Connect 操作皆拒絕（UT-CONNECT-005），避免誤觸真實 Stripe 帳戶。
- **金流閉環擴展**：付款（Phase A）+ 權威狀態（Phase B）+ 退款（Phase C）+ 賣家帳戶開通（Phase D-1）皆真實；代收後分潤（Phase D-2）待評估。

---

**文件版本**: v1.0
**建立日期**: 2026-07-04
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
