# Release Notes - v2029.02.10-01 (Sprint 86)

**發布日期**: 2029-02-10（規劃）／實作完成 2026-07-09
**發布類型**: ✨ 新功能（M07 結算跨週期退款調整單機制，PRD §6.2.1）＋ 🔒 安全修復（既有結算審核流程權限缺口）
**Sprint**: Sprint 86（承接 Sprint 83/84/85 retro 連續三次列為候選的建議項目）

> Sprint 86 主題：補齊 PRD §6.2.1「跨結算週期退款處理機制」——已 `APPROVED`/`PAID` 的結算單涉及退款時自動生成 `adjustment_statement`；`PAID` 結算單逆轉需 SuperAdmin + 財務長雙重授權並產生 `CREDIT_NOTE`。過程中額外發現並經使用者同意併入修復兩個既有缺陷：退款金額計算死碼、既有結算審核端點權限缺口。

---

## ✨ 新功能：M07 結算跨週期退款調整單機制

- **門檻/狀態機**：啟用 `SettlementStatement` 新狀態 `REVERSAL_PENDING`（發起後、確認前的中間態）、`REVERSED`；新增 `adjustment_amount`/`reversal_initiated_by`/`reversal_initiated_by_role`/`reversal_requested_at`/`reversal_reason` 欄位（Migration V66）。
- **`adjustment_statement`**（新表）：`PaymentStateService.refundOrderPayment` 退款成功後由 `SettlementAdjustmentService.handleOrderRefund` 判斷訂單所屬結算單狀態：
  - `PENDING`/`PENDING_REVIEW`（未核准）：直接對該結算單做 delta 更新（`totalRefunds`/`netSettlementAmount`），不產生獨立單據。
  - `APPROVED`/`PAID`（已核准/已撥款，不可逆）：產生 `adjustment_statements`（`status=PENDING`），於下一結算週期由 `SettlementGenerator` 折入並標記 `APPLIED`。
  - `REJECTED`/`FAILED`（終態）或找不到對應結算單：不做事。
- **PAID 結算單逆轉雙重授權**：`User.UserRole` 新增 `CFO`；`SettlementReversalService.initiateReversal`（`PAID`→`REVERSAL_PENDING`，需 `SUPER_ADMIN`/`CFO`）+ `confirmReversal`（`REVERSAL_PENDING`→`REVERSED`，確認角色須與發起角色**不同**，同角色拒絕），確認後建立 `CreditNote`（沿用既有孤兒 entity/repository，V36 migration 建表後首次啟用）沖銷原結算金額。不涉及實際銀行資金收回（沿用 Sprint 80/81 既定「不處理 clawback」範圍界線）。
- **新端點**（`SettlementController`，`@PreAuthorize("hasAuthority('settlement:reverse')")`）：
  - `POST /v2/admin/settlements/{id}/reverse/initiate`
  - `POST /v2/admin/settlements/{id}/reverse/confirm`

## 🧹 探查中發現並經使用者同意併入修復的既有缺陷

- **退款金額扣除死碼（PRD §6.2.1 最基本機制從未真正運作）**：`SettlementCalculator.calculateTotalRefunds` 對已過濾為 `COMPLETED`/`DELIVERED` 的訂單再篩選 `status==REFUNDED`，但該篩選已排除 `REFUNDED`，故永遠回傳 0。全額退款訂單本已被 GMV 排除（無影響），但**部分退款**訂單（`Order.status` 不變）從未被扣除。修正：改依 `Payment.refundedAmount` 建立退款對照表，正確扣除部分退款金額。
- **`admin:read`/`admin:write` 權限缺口（DEF-042）**：`SettlementController`/`TransferController` 共 4 個既有端點（結算單待審清單/批准/駁回、Transfer 駁回重試）的 `@PreAuthorize` 檢查權限字串從未被 `RolePermissionMapping`/`Permission` enum 定義或授予任何角色（含 SUPER_ADMIN），真實環境任何人呼叫皆回 403，Sprint 80/81 建立的結算審核流程完全不可達。修復：`Permission` 新增 `ADMIN_READ`/`ADMIN_WRITE`，`RolePermissionMapping` 授予 `ADMIN`/`SUPER_ADMIN`（不修改 Controller，字串本身正確，只是缺少授予端）。

## 🗄️ 資料庫變更（Migration V66）

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
```

## 測試 / 驗證 ✅

- **`SettlementCalculatorTest`**：+4 tests（無退款/部分退款/多筆混合/null map）。
- **`SettlementAdjustmentServiceTest`**（新檔）：7 tests（找不到結算單、金額為零、PENDING/PENDING_REVIEW 直接扣除、APPROVED/PAID 產生調整單、REJECTED/FAILED 不做事）。
- **`SettlementReversalServiceTest`**（新檔）：7 tests（發起成功、非 PAID 拒絕、非法角色拒絕、確認成功產生 CreditNote、同角色確認拒絕、非 REVERSAL_PENDING 確認拒絕、結算單不存在）。
- **`SettlementScheduledJobIntegrationTest`**：+2 tests（adjustment 折入淨額計算、依訂單建立退款對照表）。
- **`PaymentStateServiceStripeTest`**：+2 tests（退款成功呼叫結算 hook、hook 拋例外不影響退款主流程）。
- **`M07SettlementIntegrationTest`**：+4 tests（SUPER_ADMIN 發起+CFO 確認成功、同角色確認拒絕、非授權角色 403）。
- **全量回歸**（`mvn verify -Pintegration-test`）：**單元 914 + 整合 359 = 1273 tests，0 failures，0 errors，BUILD SUCCESS**。
- **schema 漂移守門**：`make validate-schema` 無漂移（V66 已對齊）。

## 內含 Commit（Sprint 86）

| 項目 | 說明 |
|------|------|
| Sprint 86 Plan | M07 跨週期退款調整單機制規劃（含範圍決策記錄） |
| Migration V66 | `settlement_statements` 逆轉欄位 + `adjustment_statements` 新表 |
| 核心實作 | `SettlementAdjustmentService`/`SettlementReversalService`（新檔）、`SettlementCalculator`/`SettlementGenerator` 修正、`CFO` 角色、`SettlementController` 新端點 |
| 既有缺陷修復 | 退款扣除死碼（`SettlementCalculator.calculateTotalRefunds`）+ `admin:read`/`admin:write` 權限缺口（DEF-042） |
| Sprint 86 收尾 | Retro / Release Notes + `DEFERRED_ITEMS_TRACKER.md`（AI-2420 + DEF-042 移至已完成） |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-09
**基於**: AISDLC v0.09 Release Management Workflow
