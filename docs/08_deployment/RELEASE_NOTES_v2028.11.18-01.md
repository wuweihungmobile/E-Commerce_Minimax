# Release Notes - v2028.11.18-01 (Sprint 80)

**發布日期**: 2028-11-18（規劃）／實作完成 2026-07-07
**發布類型**: 💰 新功能（真實金流 Phase D-2）+ 🔒 租戶隔離強化；後端聚焦，無前端變動
**Sprint**: Sprint 80（AI-2416，多 Sprint 測試強化計劃 + DEF-038 修復後的下一輪主軸）
**狀態**: ⏳ 待推送（commit 已完成，等待使用者確認後 push——本 Sprint 涉及真實金錢移動邏輯 + 修改既有生產抽成計算，不比照一般 Sprint 自動 push）

> Sprint 80 主題：AI-2416 真實金流 Phase D-2——結算單審核通過後 transfer 分潤給賣家。規劃過程中發現既有 `core/settlement/` 結算模組（Sprint 15 建立）早已存在但「審核通過後撥款」從未實作，因此將原「逐筆訂單付款成功即轉帳」設計改為「結算單 `APPROVED` 後才觸發 `Transfer.create`」，保留既有人工審核關卡並統一抽成口徑（`SettlementCalculator` 改用 `Tenant.commissionRate` 取代硬編碼 10%）。

---

## 💰 新功能：結算單審核通過後 transfer 分潤（US-001~US-004）

- **US-000（抽成口徑統一）**：`SettlementCalculator.calculateCommission` 簽名變更為吃 `commissionRate` 參數，取代硬編碼 `COMMISSION_RATE=0.10`；`SettlementGenerator` 改傳入 `tenant.getCommissionRate()`（預設 0.05）。既有 `SettlementCalculatorTest`/`SettlementScheduledJobIntegrationTest` 同步更新。
- **US-001（Transfer 領域模型）**：新 migration `V64__Create_Transfers_Table.sql`（`settlement_statement_id` 唯一索引冪等）+ `Transfer` entity/repository。
- **US-002（Stripe SDK 整合）**：`PaymentGateway.createTransfer` 新增 default method（比照既有 `createConnectAccount` 模式）；`StripePaymentGateway` 實作呼叫 `Transfer.create`（separate charges and transfers）。
- **US-003（核心邏輯）**：新增 `TransferService.createTransferForStatement`——僅 `APPROVED` 結算單可觸發、`STRIPE_TRANSFER_ENABLED` feature toggle 保護（預設關閉）、賣家 Connect 帳戶未就緒記錄 `SKIPPED_ONBOARDING_INCOMPLETE`（結算單維持 `APPROVED`，可事後補建）、Stripe 失敗記錄 `FAILED`（結算單同步轉 `FAILED`）且不拋例外中斷「審核通過」這個已持久化的 Admin 決策；`SettlementReviewer.approveStatement` 接線觸發（try-catch 隔離）。
- **US-004（API）**：`GET /v2/transfers`（對帳查詢）+ `POST /v2/admin/transfers/{statementId}/retry`（管理端重試）。
- **US-005（webhook 同步）順延**：`transfer.paid`/`transfer.failed` 事件同步列為 Sprint 81 建議優先候選（`Transfer.create` 成功僅代表 Stripe 已受理，非資金已到帳）。

## 🔒 租戶隔離強化（比照 DEF-038 教訓）

- `TransferService` 從第一版就落實租戶擁有權檢查：一般角色/`ADMIN` 僅能查詢/重試自己租戶的 transfer 記錄，`SUPER_ADMIN` 可跨租戶。
- 新增 `FeatureToggleService.isFeatureEnabledForTenant(tenantId, featureKey)`，修正「Admin 審核他人結算單時 `TenantContext` 是 Admin 自己租戶而非結算單所屬租戶」的潛在誤判風險。

## 🔍 規劃過程中的架構修正（誠實記錄）

- 初稿設計「逐筆訂單付款成功即轉帳」，前置調查發現既有 `core/settlement/` 模組已有完整結算單生成+人工審核狀態機，且 `SettlementReviewer` Javadoc 早已記載 `APPROVED→PAID` 為既定但未實作的轉換。改為「結算單審核通過後才轉帳」，理由：避免雙抽成率口徑衝突（原計劃用 `tenant.commissionRate`，Settlement 舊有硬編碼 10%）+ 保留既有人工審核關卡。詳見 `docs/04_planning/SPRINT_80_PLAN.md` 文首「重大發現」章節。

## 🆕 本 Sprint 發現的新待決策項目

- **DEF-040**（🔴 高優先級）：`SettlementController` 的 `/v2/admin/settlements/**` 三個端點完全無租戶過濾，任一租戶 ADMIN 理論上可審核/批准/駁回他租戶結算單。本 Sprint 新增的真實 transfer 功能使此缺口風險從「查看他租戶資料」升級為「觸發他租戶資金轉移」，記錄於 `DEFERRED_ITEMS_TRACKER.md`，待業務/架構決策，不在本 Sprint 範圍內修改。

## 測試 / 驗證 ✅

- **`TransferServiceTest`**：10 tests，涵蓋狀態守門/冪等/toggle 跳過/前置條件跳過/Stripe 成功轉 PAID/Stripe 失敗轉 FAILED/跨租戶重試拒絕/SUPER_ADMIN 跨租戶重試/範圍化查詢，0 fail。
- **`TransferControllerE2ETest`**：5 tests，含跨租戶 API 存取拒絕（403）、SUPER_ADMIN 跨租戶查詢/重試，0 fail。
- **`StripePaymentGatewayTest`**：新增 TC-S011/S012（WireMock `Transfer.create` 成功/失敗），0 fail。
- **既有 `SettlementCalculatorTest`/`SettlementScheduledJobIntegrationTest`**：因 US-000 簽名變更同步更新，新增「不同租戶 commissionRate 算出不同抽成」測試確保變更真的生效，0 fail。
- **後端全量整合回歸**（`mvn verify -Pintegration-test`，`make test-db-up` 後）：**1218 tests 0 fail**（因本 Sprint 修改生產程式碼，依政策執行完整 verify）。
- **schema 漂移守門**：`make validate-schema` 無漂移（V64 對齊）。
- **checkstyle-test**：全量回歸初次執行攔到 `TransferServiceTest.java` 一個未使用 import，立即修正重跑確認乾淨（沿用 Sprint 79 教訓）。

## 技術決策 / 已知限制 ⚠️

- **分潤架構**：Separate charge + Transfer（非 destination charge）——付款時仍 100% 進平台帳戶，事後分步轉給賣家；PO 決策，理由是與現有付款閉環風險隔離。
- **Stripe test mode**：全案自始至終皆在 Stripe test mode 開發驗證，未接真實正式金鑰；Sprint 55 記錄的「需正式環境上線才能評估」前提已由 PO 解除。
- **不處理 clawback**：本 Sprint 不處理已分潤後訂單被退款的資金收回邏輯（超出範圍，且 `SettlementGenerator` 現行邏輯本已將 `REFUNDED` 訂單排除在結算範圍外）。
- **無前端變動**：本 Sprint 純後端功能，對帳/重試 UI 視後續容量另行評估。

## 資料庫遷移 🗄️

- `V64__Create_Transfers_Table.sql`：新增 `transfers` 表（`settlement_statement_id` 唯一索引、`tenant_id` 索引）。

## 內含 Commit（Sprint 80）

| US / 項目 | 說明 |
|----------|------|
| Sprint 80 Plan | AI-2416 Phase D-2 規劃（含規劃過程中的架構修正記錄）|
| US-000 | `SettlementCalculator`/`SettlementGenerator` 抽成口徑改用 `tenant.commissionRate` |
| US-001 | `Transfer` entity/repository + V64 migration |
| US-002 | `PaymentGateway.createTransfer` + `StripePaymentGateway` 實作 + WireMock 測試 |
| US-003 | `TransferService` 核心邏輯 + 租戶隔離 + `SettlementReviewer` 接線 |
| US-004 | `TransferController` API + 權限測試 |
| Sprint 80 收尾 | Review / Retro / Release Notes + trackers（含新發現 `DEF-040`）|

> 實際 commit hash 詳見 git log（依 Sprint 慣例於收尾 commit 訊息中記錄）。

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-07
**基於**: AISDLC v0.09 Release Management Workflow
