# Sprint 86 Plan — M07 結算系統跨週期退款調整單機制（PRD §6.2.1）

**Sprint**: Sprint 86
**日期**: 2026-07-09
**主題**: 補齊 PRD §6.2.1「跨結算週期退款處理機制」——已 `PAID`/`APPROVED` 的結算單涉及退款時自動生成 `adjustment_statement`；`PAID` 結算單逆轉需 SuperAdmin + 財務長雙重授權並產生 `CREDIT_NOTE`。此需求已連續三個 Sprint（83/84/85）retro 列為候選但未排入，本次正式實作。

---

## 1. 探查結果與範圍決策（使用者已拍板，不需再問）

1. **範圍**：使用者選擇「完整三層」——`adjustment_statement` + `CREDIT_NOTE` + SuperAdmin/財務長雙重授權，全部納入本 Sprint。
2. **雙重授權落地方式**：使用者選擇「新增 CFO 角色，需兩種不同角色各批一次」——`User.UserRole` 新增 `CFO`；結算單逆轉需一位 `SUPER_ADMIN` 發起 + 一位 `CFO` 確認（或反過來），同一角色不可自己批自己。
3. **意外發現並經使用者同意併入本 Sprint 的既有 bug**：`SettlementController`/`TransferController` 現有 4 個端點（待審清單、批准、駁回、Transfer 駁回重試）的 `@PreAuthorize("hasAuthority('admin:read'/'admin:write')")` 檢查的權限字串**從未被 `RolePermissionMapping`/`Permission` enum 定義或授予任何角色**（含 SUPER_ADMIN），JWT 簽發流程不可能產生這兩個字串，導致這些端點在真實環境中對任何人都回 403，完全不可達。既有測試全數通過是因為測試手動組裝 Authentication 時直接塞入字面值字串繞過真實授權邏輯。**本 Sprint 一併修復**。
4. **意外發現的第二個既有 bug（PRD §6.2.1 最基本機制未真正運作）**：`SettlementCalculator.calculateTotalRefunds` 對「已過濾為 COMPLETED/DELIVERED」的訂單列表再篩選 `status==REFUNDED`，但 `filterSettleableOrders` 已排除 REFUNDED 訂單，故此方法永遠回傳 0（程式碼註解自白）。這只影響「全額退款」情境（該訂單已被 GMV 排除，不需額外扣除，行為正確）；但**遺漏了「部分退款」情境**——`PaymentStateService.refundOrderPayment` 部分退款不改變 `Order.status`（仍是 COMPLETED/DELIVERED），該筆訂單全額 `total_amount` 仍被計入 GMV，退款金額完全沒被扣除。這是「跨週期退款調整單」機制的前置正確性缺口，**本 Sprint 一併修復**（僅修正部分退款遺漏，不變更既有全額退款排除的正確設計）。

## 2. 架構決策

### 2.1 權限修復（前置）
- `Permission` enum 新增 `ADMIN_READ("admin:read", ...)`、`ADMIN_WRITE("admin:write", ...)`。
- `RolePermissionMapping`：`ADMIN`、`SUPER_ADMIN`（already `EnumSet.allOf`）皆取得這兩個權限——與 `SettlementReviewer` 既有「ADMIN 限自己租戶、SUPER_ADMIN 跨租戶」的 tenant-scoping 邏輯語意一致（該邏輯早已假設 ADMIN 可呼叫這些端點，只是權限字串本身從未真正授予）。
- 不修改 Controller（既有 `@PreAuthorize` 字串已經正確，只是缺少授予端）。

### 2.2 退款金額真正扣除（前置修復）
- `SettlementCalculator.calculateTotalRefunds` 改簽名為 `(List<Order> settleableOrders, Map<UUID orderId, BigDecimal> refundedAmountByOrderId)`，對每筆可結算訂單累加其已退款金額（`Payment.refundedAmount`），取代原本對 `Order.status==REFUNDED` 的篩選（該篩選在已過濾清單中恆為空)。
- `SettlementGenerator.generateStatementForTenant` 新增 `PaymentRepository` 依賴，依 `completedOrders` 的 `orderId` 逐一 `findByOrderId` 建立 refund map（訂單量級為單租戶單週，數量可控，不做批次查詢優化）。

### 2.3 adjustment_statement（新機制）
- 新表 `adjustment_statements`：`id/tenant_id/order_id/original_statement_id(FK settlement_statements)/adjustment_type(固定 REFUND_DEDUCTION)/amount(負值)/status(PENDING/APPLIED)/applied_statement_id(FK, nullable)/created_at/applied_at`。
- 新增 `SettlementAdjustmentService.handleOrderRefund(tenantId, orderId, refundAmount)`，由 `PaymentStateService.refundOrderPayment` 退款成功後呼叫（比照既有 helper 方法呼叫慣例，退款主流程失敗不應被此邏輯拖垮——比照既有 `recordAudit` 的 try-catch 容錯模式）：
  - 查詢該訂單所屬期間是否已有 `SettlementStatement`（`tenantId` + 訂單 `createdAt` 落在 `periodStart`~`periodEnd` 之間）。
  - 無符合的結算單（訂單太新，尚未被任何一輪結算生成觸及）→ 不做事，未來 `generateStatementForTenant` 執行時，2.2 的修復會自動正確扣除。
  - 結算單狀態為 `PENDING`/`PENDING_REVIEW`（尚未進入不可逆的核准/撥款流程）→ 直接對該結算單做 delta 更新：`totalRefunds += refundAmount`、`netSettlementAmount -= refundAmount`（PRD 原文「直接從當週 total_refunds 扣除，不產生獨立單據」）。
  - 結算單狀態為 `APPROVED`/`PAID`（已核准或已撥款，不可逆）→ 產生一筆 `adjustment_statements`（`status=PENDING`），不動原結算單數字（PRD 原文「於下一結算週期一併結算」）。
  - 結算單狀態為 `REJECTED`/`FAILED`（終態、金流未發生）→ 不做事。
- `SettlementGenerator.generateStatementForTenant` 生成新結算單時，先查詢該租戶所有 `status=PENDING` 的 `adjustment_statements`，加總其 `amount`（負值）計入新單 `netSettlementAmount`（新增欄位 `adjustmentAmount` 記錄此金額供對帳追溯），並將這些調整單標記 `APPLIED` + `appliedStatementId`。

### 2.4 CFO 角色 + PAID 結算單逆轉雙重授權 + CREDIT_NOTE
- `User.UserRole` 新增 `CFO`；`RolePermissionMapping` 新增 CFO 權限集合：`TENANT_READ, ORDER_READ, ADMIN_READ, SETTLEMENT_REVERSE`（新 Permission，僅供逆轉流程，不給 CFO 一般結算審核的 `ADMIN_WRITE`，避免權限過度擴張）。
- `SettlementStatement` 新增狀態 `REVERSAL_PENDING`（發起後、確認前的中間態，PRD 原文未定義此中間態，為避免「發起=已生效」的歧義而新增）、`REVERSED`；新增欄位 `reversalInitiatedBy`/`reversalInitiatedByRole`/`reversalRequestedAt`/`reversalReason`。
- 新增 `CreditNote` entity 對應既有 `credit_notes` 表（V36 migration 已建表但從未有程式碼引用，本次啟用）。
- 新增 `SettlementReversalService`：
  - `initiateReversal(statementId, initiatorId, initiatorRole, reason)`：僅 `PAID` 可發起；`initiatorRole` 須為 `SUPER_ADMIN`/`CFO`；轉 `REVERSAL_PENDING`，記錄發起人/角色/原因。
  - `confirmReversal(statementId, confirmerId, confirmerRole)`：僅 `REVERSAL_PENDING` 可確認；`confirmerRole` 須為 `SUPER_ADMIN`/`CFO` **且不同於** `reversalInitiatedByRole`（雙重授權核心檢查）；建立 `CreditNote`（`amount = -netSettlementAmount`, `status=PENDING`）、結算單轉 `REVERSED`。
  - 不做實際銀行資金收回（沿用 Sprint 80/81 既定「不處理 clawback」範圍界線，`CREDIT_NOTE` 僅為會計沖銷記錄）。
- `SettlementController` 新增 `POST /v2/admin/settlements/{statementId}/reverse/initiate`、`POST /v2/admin/settlements/{statementId}/reverse/confirm`，`@PreAuthorize("hasAuthority('settlement:reverse')")`。

## 3. 資料庫變更（Migration V66）

```sql
ALTER TABLE settlement_statements ADD COLUMN reversal_initiated_by UUID NULL REFERENCES users(id);
ALTER TABLE settlement_statements ADD COLUMN reversal_initiated_by_role VARCHAR(20) NULL;
ALTER TABLE settlement_statements ADD COLUMN reversal_requested_at TIMESTAMP WITH TIME ZONE NULL;
ALTER TABLE settlement_statements ADD COLUMN reversal_reason TEXT NULL;
ALTER TABLE settlement_statements ADD COLUMN adjustment_amount DECIMAL(14,2) DEFAULT 0;

CREATE TABLE adjustment_statements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    order_id UUID NOT NULL REFERENCES orders(id),
    original_statement_id UUID NOT NULL REFERENCES settlement_statements(id),
    adjustment_type VARCHAR(30) NOT NULL DEFAULT 'REFUND_DEDUCTION',
    amount DECIMAL(14,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    applied_statement_id UUID NULL REFERENCES settlement_statements(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    applied_at TIMESTAMP WITH TIME ZONE NULL
);
CREATE INDEX idx_adjustment_statements_tenant_status ON adjustment_statements(tenant_id, status);
CREATE INDEX idx_adjustment_statements_original ON adjustment_statements(original_statement_id);
```

## 4. 實作清單（依 CLAUDE.md 開發-編譯-測試循環，一次一個檔案）

1. Migration V66
2. `Permission.java`：新增 `ADMIN_READ`/`ADMIN_WRITE`/`SETTLEMENT_REVERSE`
3. `RolePermissionMapping.java`：授予 ADMIN/CFO 對應權限
4. `User.java`：`UserRole` 新增 `CFO`
5. `SettlementStatement.java`：新狀態 + 新欄位
6. `CreditNote.java` entity + `CreditNoteRepository.java`
7. `SettlementAdjustment.java` entity + `SettlementAdjustmentRepository.java`
8. `SettlementCalculator.calculateTotalRefunds` 簽名修正
9. `SettlementGenerator`：refund map 查詢 + adjustment 折入邏輯
10. `SettlementReversalService.java`（新檔案）
11. `SettlementAdjustmentService.java`（新檔案）
12. `PaymentStateService.refundOrderPayment`：呼叫 `SettlementAdjustmentService.handleOrderRefund`
13. `SettlementController`：新增 reverse/initiate、reverse/confirm 端點
14. `SettlementMapper`/`SettlementService` DTO：曝露 `adjustmentAmount`、逆轉相關欄位

## 5. 測試計畫

- `SettlementCalculatorTest`：`calculateTotalRefunds` 新簽名案例（無退款/部分退款/多筆混合）
- `SettlementGeneratorTest`（或整合測試）：部分退款訂單正確扣除；PENDING adjustment 正確折入下期
- `SettlementAdjustmentServiceTest`：四種結算單狀態分支（PENDING/PENDING_REVIEW 直接扣除、APPROVED/PAID 產生調整單、REJECTED/FAILED 不做事、找不到結算單不做事）
- `SettlementReversalServiceTest`：發起成功、確認成功（產生 CreditNote）、同角色確認應拒絕、非 PAID 發起應拒絕、非 REVERSAL_PENDING 確認應拒絕
- `RolePermissionMappingTest`（若存在則擴充，否則於相關 Service 測試中驗證）：ADMIN/SUPER_ADMIN 取得 `admin:read`/`admin:write`；CFO 權限集合正確
- `SettlementControllerE2E`/`M07SettlementIntegrationTest`：新增 reverse/initiate + reverse/confirm 案例（SUPER_ADMIN 發起 + CFO 確認成功；CFO 發起 + CFO 確認應 403 或業務錯誤）

## 6. 範圍外

- 不做真實銀行/Stripe 資金收回（clawback），`CREDIT_NOTE` 僅會計沖銷記錄，沿用 Sprint 80/81 既定範圍界線。
- 不做前端 UI（CFO 登入介面、逆轉發起/確認表單）。
- 不處理「CFO 使用者帳號如何指派」的管理介面（本 Sprint 僅需要能透過既有使用者管理機制將某帳號 role 設為 CFO 即可，不新增專屬指派 UI/API）。
