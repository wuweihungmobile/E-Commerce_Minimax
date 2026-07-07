# Sprint 80 計劃 / Sprint 80 Plan

> **Sprint 編號**: Sprint 80
> **期間**: 2026-07-07
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-07
> **基於**: `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` AI-2416（Phase D-1 於 Sprint 53 拆分保留）；多 Sprint 測試強化計劃（Sprint 66-79）完成、DEF-038 修復完成後，PO 選定為下一輪主軸
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 重大發現：既有 Settlement 結算模組改變本 Sprint 架構設計

規劃初稿（原設計：付款成功即逐筆訂單觸發 transfer）寫完後，前置程式碼調查發現 `core/settlement/`（Sprint 15 建立）已有完整的結算單生成 + 人工審核狀態機（`PENDING → PENDING_REVIEW → APPROVED/REJECTED`），且 `SettlementReviewer` 類別 Javadoc（L33）**明確已寫下** `APPROVED → PAID（撥款成功）` 為既定狀態轉換規則，只是這一步從未被實作（`PAID`/`FAILED` 這兩個 enum 值目前無任何程式碼寫入）。若沿用原設計（逐筆訂單付款成功立即轉帳），會產生兩個嚴重問題：

1. **雙抽成率衝突**：`SettlementCalculator` 用硬編碼 `COMMISSION_RATE = 0.10`，原設計卻打算改用 `Tenant.commissionRate`（預設 0.05）——實際轉帳金額會跟結算報表官方數字對不起來。
2. **繞過既有人工審核關卡**：錢在 Admin 審核結算單（`SettlementReviewer.approveStatement`）之前就已經轉出去，等於讓 Settlement 的審核機制形同虛設。

`docs/07_design/PAYMENT_INTEGRATION_ASSESSMENT.md`（L119）也早已預留正確定位：「既有 `core/settlement/` 可轉為『對帳/報表』層，實際轉帳交 Connect」。**PO 已拍板（2026-07-07）：改採「結算單審核通過後才轉帳」設計**，本文件為修正後版本。

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 55「需正式環境上線才能評估」前提 | ✅ **PO 決策解除** | 全案自始至終皆在 Stripe test mode 開發，此前提已無意義 |
| 分潤架構 | ✅ **PO 決策：Separate charge + Transfer**（非 destination charge）| 付款時仍 100% 進平台帳戶 |
| **Transfer 觸發點** | ✅ **PO 決策（重大修正）：結算單 `APPROVED` 後才觸發**，非逐筆訂單付款成功時 | 保留 Settlement 既有人工審核關卡；`SettlementReviewer.approveStatement` 為既定但未實作的 `APPROVED → PAID` 狀態轉換提供實作 |
| **抽成率口徑統一** | ✅ **PO 決策（隱含於上述選項描述，一併授權）：`SettlementCalculator.calculateCommission` 改用 `Tenant.commissionRate`**，取代硬編碼 10% | 需同步更新 `SettlementCalculatorTest`/`SettlementGeneratorTest` 等既有測試中對 10% 的斷言；此為對既有生產邏輯的行為變更，需全量回歸把關 |
| Phase D-1 現況 | ✅ 已於 Sprint 53 完成：`TenantStripeConnectService` + `Tenant.stripeConnectAccountId`/`connectOnboardingStatus`/`connectChargesEnabled`/`connectPayoutsEnabled`/`commissionRate`（V62） | 可直接複用 |
| Transfer/Payout 既有基礎 | ✅ 已確認：`Transfer`/`Payout` 相關 entity/repository/service/controller 全文搜尋零結果；但 `SettlementStatement` 已有 `paidAt`/`PAID`/`FAILED` 欄位與狀態（皆未被賦值），本 Sprint 是它們第一次被實際使用 | 需新建 tenant-scoping 慣例時比照 `SettlementStatementRepository` 既有 `findByIdAndTenantId` 模式（該模式已驗證無跨租戶漏洞）|
| Feature toggle 命名慣例 | ✅ 比照 `STRIPE_CONNECT_ENABLED`/`STRIPE_PAYMENT_ENABLED` 既有模式，新增 `STRIPE_TRANSFER_ENABLED`（tenant-scoped，預設關閉）| 避免誤觸真實（即便是 test mode）transfer 呼叫 |
| Push 狀態 | ⚠️ 本 Sprint 涉及真實金錢移動邏輯（即便為 test mode）+ 修改既有生產抽成計算邏輯，**收尾 commit 後需徵詢使用者同意才 push**，不比照一般 Sprint 自動 push | 風險等級高於一般 Sprint |

---

## 1. Sprint 80 目標

> **主題**: 真實金流 Phase D-2——結算單審核通過後 transfer 分潤給賣家

Phase D-1 已讓賣家能完成 Stripe Connect 帳戶 onboarding，既有 Settlement 模組已能每週彙總訂單、算出商家應得淨額、走人工審核流程，但「審核通過後真的把錢轉給賣家」這一步從未實作。本 Sprint 補上這個斷點：`SettlementCalculator` 改用 `Tenant.commissionRate`（取代硬編碼 10%）統一抽成口徑；`SettlementReviewer.approveStatement` 於 `APPROVED` 狀態轉換後，觸發 `TransferService` 依結算單已算好的 `netSettlementAmount` 呼叫 Stripe `Transfer.create` 轉入賣家 Connect 帳戶，成功則推進狀態至 `PAID`，失敗則 `FAILED` + 記錄原因並支援 Admin 重試。

---

## 2. User Stories

### US-000：抽成口徑統一——`SettlementCalculator` 改用 `Tenant.commissionRate`（P2）

> **SP**: 2 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-000-1**: `SettlementCalculator.calculateCommission(BigDecimal gmv, BigDecimal commissionRate)` 簽名變更（新增參數），移除/棄用硬編碼 `COMMISSION_RATE` 常數；`commissionRate` 型別轉換（`Tenant.commissionRate` 為 `Double`）需注意精度，比照既有 `BigDecimal` + `RoundingMode.HALF_UP` 慣例轉換。

**AC-000-2**: `SettlementGenerator.generateStatementForTenant` 呼叫處改傳入 `tenant.getCommissionRate()`。

**AC-000-3**: 更新既有 `SettlementCalculatorTest` 中對 10% 的斷言為參數化（傳入 tenant 的 `commissionRate` 值驗證），不可殘留對已移除硬編碼行為的斷言；確認 `SettlementGeneratorTest`/`SettlementScheduledJobIntegrationTest` 等既有測試因此變更需要同步更新的斷言皆已修正，且變更前後對「不同 commissionRate 值算出不同抽成」有明確測試覆蓋（避免變更後其實沒生效卻測試恰好通過）。

**AC-000-4**: 此為既有生產邏輯行為變更，變更後執行既有 Settlement 相關測試全數通過，不可有測試被跳過或註解掉來讓變更「看起來」沒問題。

### US-001：Transfer 領域模型 + Migration（P2）

> **SP**: 2 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-001-1**: 新 migration（V64，已建立，內容需依本次修正調整為以結算單為主鍵關聯）建立 `transfers` 表：`id UUID PK`、`settlement_statement_id UUID NOT NULL UNIQUE`（一張結算單僅一筆 transfer 記錄，冪等保障）、`tenant_id UUID NOT NULL`、`stripe_transfer_id VARCHAR`（nullable）、`transfer_amount NUMERIC(14,2) NOT NULL`（= 觸發當下的 `netSettlementAmount` 快照，即便結算單事後被其他流程異動，轉帳記錄的金額仍以實際轉帳當下為準）、`currency VARCHAR(3) NOT NULL`、`status VARCHAR NOT NULL`（`PENDING`/`COMPLETED`/`FAILED`/`SKIPPED_ONBOARDING_INCOMPLETE`，見 US-003）、`failure_reason VARCHAR`（nullable）、`created_at`/`updated_at`。

**AC-001-2**: `Transfer` entity（`domain/model/settlement/` 或 `domain/model/transfer/`，比照 `SettlementStatement` 分層慣例）+ `TransferRepository`（`findBySettlementStatementId`、`findByTenantIdOrderByCreatedAtDesc`，比照 `SettlementStatementRepository.findByIdAndTenantId` 既有 tenant-scoped 查詢慣例）。

**AC-001-3**: `make validate-schema` 無漂移。

### US-002：`PaymentGateway.createTransfer` + Stripe SDK 整合（P2）

> **SP**: 2 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-002-1**: `PaymentGateway` 介面（`infrastructure/payment/PaymentGateway.java`）比照 `createConnectAccount`/`createAccountLink` 既有模式（L54/L61/L69）新增 default method：`createTransfer(String destinationAccountId, long amountInCents, String currency, String sourceReferenceId)` → 回傳 Stripe transfer id；未覆寫的 gateway 拋 `UnsupportedOperationException`。

**AC-002-2**: `StripePaymentGateway` 實作：呼叫 Stripe SDK `Transfer.create`（`destination`=賣家 Connect 帳戶、`amount`/`currency`、`metadata` 帶 `settlementStatementId` 供對帳追溯，比照既有 `createConnectAccount`（L265-288）程式風格）。

**AC-002-3**: 測試：WireMock 驗 `Transfer.create` 呼叫參數正確（比照 `StripePaymentGatewayTest` 既有 TC-S007~010 風格），不打真 Stripe API。

### US-003：`TransferService`——依結算單觸發轉帳 + 前置條件檢查 + 租戶隔離（本 Sprint 核心）

> **SP**: 5 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-003-1**: `createTransferForStatement(UUID statementId)`：查 `SettlementStatement` 確認 `status == APPROVED`（非此狀態直接拒絕，避免誤觸發）；取 `netSettlementAmount`/`currency`/`tenantId`；查 `Tenant` 確認 Connect 帳戶狀態；建立/更新 `Transfer` 記錄（`settlement_statement_id` 唯一索引保障冪等，重複呼叫查既有記錄，不重複轉帳）。

**AC-003-2**: 前置條件檢查——僅當 `tenant.connectOnboardingStatus == COMPLETE` 且 `connectChargesEnabled`/`connectPayoutsEnabled` 皆為 true 才呼叫 `createTransfer`；否則建立 `status=SKIPPED_ONBOARDING_INCOMPLETE` 的 `Transfer` 記錄並記 log（**不可靜默跳過不留痕跡**），結算單狀態維持 `APPROVED`（不進 `PAID`，代表「已核准但尚未撥款」，可事後補建）。

**AC-003-3**: 呼叫 `PaymentGateway.createTransfer` 成功：`Transfer.status=COMPLETED` + 回填 `stripeTransferId`；同步將 `SettlementStatement.status` 推進為 `PAID`、`paidAt=now()`（實作既有 `SettlementReviewer` Javadoc 早已記載但從未實作的 `APPROVED → PAID` 轉換）。失敗（Stripe API 例外）：`Transfer.status=FAILED` + `failure_reason`，`SettlementStatement.status=FAILED`；**不可拋例外中斷呼叫端**（見 AC-003-4）。

**AC-003-4**: 觸發點：`SettlementReviewer.approveStatement()` 完成 `PENDING_REVIEW → APPROVED` 狀態轉換並 save 之後，呼叫 `transferService.createTransferForStatement(statementId)`；此呼叫包在 try-catch 內，任何例外僅記 log，絕不讓 transfer 失敗回滾或中斷「審核通過」這個動作本身（Admin 的審核決策應被尊重並持久化，即便後續撥款失敗）。受 `STRIPE_TRANSFER_ENABLED` feature toggle 保護（關閉時記錄等價的「toggle 關閉」原因並 return，不呼叫 Stripe，結算單維持 `APPROVED`）。

**AC-003-5**: 🔴 **租戶擁有權檢查**（比照 DEF-038 教訓，第一版就做對）——查詢 Transfer 記錄時：一般角色/`ADMIN` 僅能查詢自己 `tenantId` 的記錄；`SUPER_ADMIN` 可查詢全部。任何嘗試查詢/操作非自己租戶 transfer 記錄的請求需被拒絕（`E_1007` 或既有慣例錯誤碼）。

**AC-003-6**: `retryFailedTransfer(UUID statementId)`（管理端）：僅能重試 `SettlementStatement.status == FAILED` 且對應 `Transfer.status` 為 `FAILED`/`SKIPPED_ONBOARDING_INCOMPLETE` 的記錄；重試前重新檢查 AC-003-2 前置條件；僅 `ADMIN`（限自己租戶）/`SUPER_ADMIN` 可呼叫。

**AC-003-7**: 測試涵蓋：`APPROVED` 觸發轉帳成功推進至 `PAID`、`settlement_statement_id` 唯一索引冪等（重複呼叫不重複轉帳）、前置條件未滿足時正確記錄 `SKIPPED_ONBOARDING_INCOMPLETE`（結算單維持 `APPROVED`）、Stripe API 失敗時結算單正確轉 `FAILED` 且不拋例外、**跨租戶查詢/重試應被拒絕**（紅燈驗證後綠燈）、toggle 關閉時不呼叫 Stripe、非 `APPROVED` 狀態呼叫 `createTransferForStatement` 應被拒絕。

### US-004：`TransferController`——對帳查詢 + 管理端重試 API（P1）

> **SP**: 2 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-004-1**: `GET /v2/transfers`（比照既有 `SettlementController` 權限慣例）：回傳呼叫者所在租戶的 transfer 記錄（對帳用，含關聯結算單資訊），支援分頁；`SUPER_ADMIN` 可加 `tenantId` query 參數查任意租戶。

**AC-004-2**: `POST /v2/admin/transfers/{statementId}/retry`（比照既有 `/v2/admin/settlements/**` 權限慣例）：呼叫 `retryFailedTransfer`，套用 AC-003-5/AC-003-6 的租戶隔離規則。

**AC-004-3**: 測試：API 層權限測試（含跨租戶存取應被拒絕的整合測試）。

### US-005：`transfer.*` webhook 事件同步（P3，視容量決定是否本 Sprint 納入或列候選）

> **SP**: 1 | **優先級**: P3 | **狀態**: 📋 Ready（stretch，容量緊繃可順延至 Sprint 81）

**AC-005-1**: `PaymentWebhookService` 新增 `transfer.paid`/`transfer.failed` 事件分派，依 `stripe_transfer_id` 反查 `Transfer` 記錄回填最新狀態（`Transfer.create` 呼叫成功只代表 Stripe 已受理，非資金已實際到帳，webhook 才是最終真相來源）；沿用既有 `ProcessedStripeEvent`（V60）去重機制。

**AC-005-2**: 測試：webhook 事件測試 + 重送冪等 + 既有 webhook（付款/退款/account.updated）不退步。

---

## 3. 執行順序

```
US-000（SettlementCalculator 改用 tenant.commissionRate → 更新既有測試 → 編譯+測試，確認 Settlement 既有流程不退步）
   ↓
US-001（migration transfers 表（settlement_statement_id 為主鍵關聯）→ validate-schema → Transfer entity + Repository → 編譯+測試）
   ↓
US-002（PaymentGateway.createTransfer 介面 → StripePaymentGateway 實作 → WireMock 單元 → 編譯+測試）
   ↓
US-003（TransferService：結算單狀態檢查 → 前置條件檢查 → 租戶隔離 → SettlementReviewer.approveStatement 接線
   → 每個子功能寫完立即編譯+測試，含跨租戶紅燈測試）
   ↓ 後端核心邏輯綠
US-004（TransferController API 接線 → 權限測試）
   ↓
US-005（webhook 同步，容量允許則做，否則列 Sprint 81 候選）
   ↓
make test-db-up → mvn verify -Pintegration-test 全量回歸 → make test-db-down
   ↓
更新 DEFERRED_ITEMS_TRACKER（AI-2416 → 已完成）+ SPRINT_80_RETRO + Release Notes
   ↓
git commit → 徵詢使用者同意後 push（本 Sprint 涉及金錢移動邏輯 + 修改既有生產抽成計算，不自動 push）
```

**強制**：`STRIPE_TRANSFER_ENABLED` toggle 預設關閉；所有 Stripe 呼叫測試皆用 WireMock/test mode，不打真 Stripe API；每個檔案/單元完成立即編譯+測試（CLAUDE.md 開發-編譯-測試循環規則），不累積；US-000 屬既有生產邏輯變更，務必優先完成並確認 Settlement 既有測試全綠才進入 US-001+。

---

## 4. 風險與緩解

| 風險 | 緩解 |
|------|------|
| `SettlementCalculator` 抽成口徑變更（10%→tenant.commissionRate）影響既有已產生的結算單/報表數字 | 本 Sprint僅影響**未來新產生**的結算單（`generateWeeklyStatements` 排程），既有歷史 `SettlementStatement` 記錄的 `commissionAmount` 不回溯重算；若使用者認為需要回溯修正歷史記錄，需另外評估，本 Sprint 範圍不含資料回填 |
| `approveStatement` 與 transfer 建立耦合過緊，transfer 失敗拖累審核動作本身 | AC-003-3/AC-003-4 明確要求 try-catch 隔離，transfer 失敗僅記錄不拋例外，審核通過的狀態轉換必須先 commit 成功 |
| commission 計算四捨五入誤差累積 | 使用 `BigDecimal` + 明確的 `RoundingMode`，測試涵蓋邊界金額 |
| 賣家 Connect 帳戶未完成 onboarding 但結算單已被核准 | AC-003-2 明確處理：記錄 `SKIPPED_ONBOARDING_INCOMPLETE`，結算單維持 `APPROVED`（未撥款），支援事後補建（`retryFailedTransfer`）|
| 新增 Transfer 查詢/操作重蹈 DEF-038 類跨租戶漏洞 | AC-003-5/AC-003-6 從設計階段就寫入租戶隔離規則 + 強制紅燈測試驗證 |
| 本 Sprint 涉及真實金錢移動邏輯，即便 test mode 也需謹慎 | 收尾 commit 後停下徵詢使用者同意才 push；所有測試僅用 Stripe test mode/WireMock |
| 結算單審核通過後才轉帳，若後續發現該期間內某訂單被退款，是否需 clawback 已分潤金額 | 本 Sprint **不處理**部分退款/爭議情境的 clawback 邏輯（超出範圍，且 `SettlementGenerator` 現行邏輯本已將 `REFUNDED` 訂單排除在結算範圍外，故本 Sprint 觸及的退款交互風險低於逐筆訂單觸發設計）；若實作過程發現其他交互影響，停下來問使用者，列為 Sprint 81+ 候選 |

---

## 5. Definition of Done

- [ ] US-000：`SettlementCalculator` 改用 `tenant.commissionRate`；既有 Settlement 測試更新且全數通過
- [ ] US-001：`transfers` migration（`settlement_statement_id` 關聯）+ entity + repository；`make validate-schema` 無漂移
- [ ] US-002：`PaymentGateway.createTransfer` + `StripePaymentGateway` 實作；WireMock 測試
- [ ] US-003：`TransferService` 結算單觸發轉帳 + 前置條件檢查 + 租戶隔離 + `approveStatement` 接線；跨租戶存取紅燈→綠燈測試
- [ ] US-004：`TransferController` 對帳查詢 + 管理端重試 API + 權限測試
- [ ] US-005（容量允許）：webhook 同步
- [ ] `mvn verify -Pintegration-test` 全量回歸 0 fail（含新增測試，過去 12 個 DEF 修復相關測試 + 既有 Settlement 測試不退步）
- [ ] `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` 更新：AI-2416 → 已完成延後項目
- [ ] `docs/05_development/SPRINT_80_RETRO.md` 撰寫
- [ ]（檢查點）commit 完成後徵詢使用者同意才 push（嚴禁 --no-verify）

---

## 6. 產出物

| 產出物 | 路徑 |
|--------|------|
| Migration | `backend/src/main/resources/db/migration/V64__Create_Transfers_Table.sql`（已建立，內容依本次修正調整）|
| 既有邏輯變更 | `core/settlement/SettlementCalculator.java`（`calculateCommission` 簽名變更）、`SettlementGenerator.java`（呼叫處改傳 `tenant.getCommissionRate()`）|
| Domain | `domain/model/settlement/Transfer.java`（新）、`domain/repository/settlement/TransferRepository.java`（新）|
| Gateway | `infrastructure/payment/PaymentGateway.java`（新增 `createTransfer`）、`StripePaymentGateway.java`（實作）|
| Service | `core/settlement/TransferService.java`（新）、`SettlementReviewer.java`（`approveStatement` 接線 transfer 觸發）|
| Controller | `api/controller/settlement/TransferController.java`（新）|
| Webhook | `core/payment/PaymentWebhookService.java`（新增 `transfer.*` 分派，US-005）|
| 測試 | `TransferServiceTest`、`TransferServiceTenantIsolationTest`、既有 `SettlementCalculatorTest`/`SettlementGeneratorTest`（更新）、`StripePaymentGatewayTest`（擴充）、`TransferControllerE2ETest` |
| 文件 | `DEFERRED_ITEMS_TRACKER.md`（更新）、`SPRINT_80_RETRO.md`（新）、`RELEASE_NOTES_v????.??.??-??.md`（新）|

---

**文件版本**: v1.0（規劃過程中因發現既有 Settlement 模組而修正架構設計，見文首「重大發現」章節）
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
