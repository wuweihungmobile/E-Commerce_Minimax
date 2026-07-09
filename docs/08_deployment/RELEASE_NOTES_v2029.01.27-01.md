# Release Notes - v2029.01.27-01 (Sprint 85)

**發布日期**: 2029-01-27（規劃）／實作完成 2026-07-09
**發布類型**: ✨ 新功能（M16 ERP 採購審批金額上限機制，PRD §6.7.2）；無破壞性變更
**Sprint**: Sprint 85（承接 Sprint 84 retro 建議項目之一，使用者指示「兩者依序進行」）

> Sprint 85 主題：補齊 PRD §6.7.2 記錄、但 Sprint 10 M16 ERP 正式交付時遺漏、且截至 Sprint 84 從未被任何追蹤文件記錄的 P0 需求缺口（AI-2419）——StoreOwner 可設定每筆採購單的金額上限，超過上限需 SUPER_ADMIN 核准。本次完成後端 API 全量，前端 UI 列入下一輪候選。

---

## ✨ 新功能：M16 採購審批金額上限機制

- **門檻設定**：`Tenant.purchaseOrderApprovalThreshold`（nullable，`null` = 不啟用門檻，既有租戶行為不受影響），透過既有 `PUT /v2/tenants/{id}` StoreOwner 自助端點設定（比照既有 `commissionRate` 欄位慣例）。
- **狀態機**：啟用 `PurchaseOrder.POStatus` 中懸空多年未使用的 `APPROVED` 值，新增 `PENDING_APPROVAL`/`REJECTED`。`submitPurchaseOrder` 金額嚴格大於門檻時轉 `PENDING_APPROVAL`，否則維持既有 `SUBMITTED` 行為（等於門檻不算超過）。`canReceive()`/`canCancel()` 分別擴充支援 `APPROVED`（可收貨/可取消）與 `PENDING_APPROVAL`（可取消）。
- **審批 API**（僅 `SUPER_ADMIN`，`AdminController`/`AdminService`，比照既有 `approveTenant`/`rejectTenant` 精確模式）：
  - `GET /v2/admin/purchase-orders/pending`：跨租戶待審批清單
  - `POST /v2/admin/purchase-orders/{poId}/approve`：核准（`PENDING_APPROVAL` → `APPROVED`）
  - `POST /v2/admin/purchase-orders/{poId}/reject`：駁回（`PENDING_APPROVAL` → `REJECTED`，需填 `reason`）
  - 皆寫入 `reviewedBy`/`reviewedAt`（真實 `TenantContext.getCurrentUser()`）+ `recordAudit` 稽核紀錄
- **架構決策**：審批**刻意不比照** Sprint 80/81 `SettlementReviewer` 的「非 SUPER_ADMIN 限自己租戶」雙層模式——PRD 原文明確寫「需 **SuperAdmin** 核准」，此機制的意義正是平台方對租戶自身金流之上的獨立把關，不可由租戶自己的 ADMIN 繞過。
- **IDOR 自我檢查**：`AdminService` 三個新方法刻意不做租戶篩選，已於 Javadoc + Sprint Plan 明確記錄理由（呼叫路徑已被 `@PreAuthorize("hasRole('SUPER_ADMIN')")` 鎖死，跨租戶查看/核准正是本功能目的，非漏洞遺漏）。

## 🗄️ 資料庫變更（Migration V65）

```sql
ALTER TABLE tenants ADD COLUMN purchase_order_approval_threshold NUMERIC(12,2) NULL;
ALTER TABLE purchase_orders ADD COLUMN reviewed_by UUID NULL;
ALTER TABLE purchase_orders ADD COLUMN reviewed_at TIMESTAMP NULL;
ALTER TABLE purchase_orders ADD COLUMN rejection_reason TEXT NULL;
```

## 🧹 過程中修復的既有技術債

- `TenantService.updateTenant` 因新增門檻欄位判斷分支，NPath 複雜度超出 checkstyle 上限（256 > 200），抽出 `applyTenantUpdates` private helper 解決，行為不變。

## 測試 / 驗證 ✅

- **`PurchaseOrderServiceTest`**：+8 tests（門檻判斷 3 案例：null 維持 SUBMITTED / 等於門檻維持 SUBMITTED / 超過門檻轉 PENDING_APPROVAL；APPROVED 可收貨；PENDING_APPROVAL/APPROVED 可取消；REJECTED 無法取消），共 24 tests。
- **`AdminServiceTest`**：+6 tests（approve/reject 成功案例、非 PENDING_APPROVAL 狀態拋 E_7002、不存在拋 E_7001、跨租戶查詢待審清單），共 29 tests。
- **`AdminControllerE2ETest`**：+4 tests（SUPER_ADMIN 核准/駁回/查詢待審成功，StoreOwner 核准應 403），共 22 tests。
- **`TenantServiceTest`**：+1 test（StoreOwner 設定門檻），共 8 tests。
- **全量回歸**（`mvn verify -Pintegration-test`）：**單元 894 + 整合 353 = 1247 tests，0 failures，0 errors，BUILD SUCCESS**。
- **schema 漂移守門**：`make validate-schema` 無漂移（V65 已對齊）。

## 內含 Commit（Sprint 85）

| 項目 | 說明 |
|------|------|
| Sprint 85 Plan | M16 採購審批金額上限機制規劃 |
| Migration V65 | `tenants.purchase_order_approval_threshold` + `purchase_orders.reviewed_by`/`reviewed_at`/`rejection_reason` |
| 核心實作 | `PurchaseOrder`/`Tenant` entity、`PurchaseOrderService.submitPurchaseOrder` 門檻判斷、`AdminService`/`AdminController` 審批端點、`TenantService` 自助設定門檻 |
| 技術債修復 | `TenantService.updateTenant` NPath 複雜度超標（checkstyle） |
| Sprint 85 收尾 | Retro / Release Notes + `DEFERRED_ITEMS_TRACKER.md`（AI-2419 移至已完成）+ `PRODUCT_BACKLOG.md` 更新 |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-09
**基於**: AISDLC v0.09 Release Management Workflow
