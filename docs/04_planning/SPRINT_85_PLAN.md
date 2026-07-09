# Sprint 85 Plan — M16 ERP 採購審批金額上限機制

**Sprint**: Sprint 85
**日期**: 2026-07-09
**主題**: 補齊 PRD §6.7.2（第 903 行）記錄、但 Sprint 10 M16 ERP 正式交付時遺漏、且截至目前 `DEFERRED_ITEMS_TRACKER.md`/`PRODUCT_BACKLOG.md` 皆未追蹤的 P0 需求缺口——「StoreOwner 可設定每筆採購單的金額上限；超過上限需 SuperAdmin 核准」。

---

## 1. 探查結果（現況）

- PRD 對此功能僅有一句話描述（§6.7.2 第 903 行），§8.2.8 Schema 與 §9.15 API 規格皆未同步展開（無 `approval_threshold`/`approved_by` 欄位、無 `PENDING_APPROVAL`/`approve` 端點），需求描述與規格本身存在落差，本次一併補齊。
- `PurchaseOrder.POStatus`（`domain/model/inventory/PurchaseOrder.java` 第 101-110 行）已定義 `APPROVED` 狀態值，但完全未被任何狀態機方法（`canSubmit`/`canReceive`/`canCancel`）或 `PurchaseOrderService` 邏輯使用——是懸空多年、未接線的既有欄位，本次予以啟用。
- `Tenant` entity 已有 `commissionRate`（Sprint 53 引入）示範「StoreOwner 相關但由平台/StoreOwner 設定的租戶層級數值欄位」慣例，可直接類比。
- `PUT /v2/tenants/{id}`（`TenantController.updateTenant`，僅 `STORE_OWNER` 本人可呼叫，`TenantService.updateTenant` 已有 `tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(...STORE_OWNER)` 擁有權檢查）是既有的 StoreOwner 自助設定端點，用於承載本次「設定金額上限」的欄位，不需新增端點。
- `AdminController`（`/v2/admin/**`，全數 `@PreAuthorize("hasRole('SUPER_ADMIN')")`）已有結構完全對應的先例：`POST /tenants/{tenantId}/approve`/`reject`（`AdminService.approveTenant`/`rejectTenant`，findById 無租戶篩選 + 狀態驗證 + `recordAudit(...)` 稽核）。本次審批機制直接比照此既有模式，而非另立新的審批慣例。

## 2. 架構決策

1. **門檻儲存位置**：`Tenant.purchaseOrderApprovalThreshold`（`BigDecimal`，nullable，無預設值）。`null` = 不啟用門檻（既有租戶維持原行為，向下相容，不強制既有客戶承擔新流程）。由 StoreOwner 透過既有 `PUT /v2/tenants/{id}` 端點設定。
2. **審批者**：僅 `SUPER_ADMIN`，不比照 Sprint 80/81 `SettlementReviewer` 的「非 SUPER_ADMIN 限自己租戶」雙層模式——PRD 原文明確寫「需 **SuperAdmin** 核准」，此機制的意義正是平台方對租戶自身金流之上的獨立把關，不可由租戶自己的 ADMIN 繞過。故審批動作完全放在 `AdminController`/`AdminService`（既有「僅限 SUPER_ADMIN」慣例），不放在 `ErpController`/`PurchaseOrderService` 內。
3. **狀態機**：啟用既有懸空的 `APPROVED` 值，新增 `PENDING_APPROVAL`、`REJECTED` 兩個值。
   - `submitPurchaseOrder`：金額 **嚴格大於**（不含等於）租戶門檻時 → `PENDING_APPROVAL`；門檻為 `null` 或金額 ≤ 門檻 → 維持既有行為，直接 `SUBMITTED`。
   - `AdminService.approvePurchaseOrder`：`PENDING_APPROVAL` → `APPROVED`（僅接受此起始狀態，否則 `E_7002`）。
   - `AdminService.rejectPurchaseOrder`：`PENDING_APPROVAL` → `REJECTED`，需填 `reason`（寫入新欄位 `rejectionReason`）。
   - `canReceive()`：原為 `SUBMITTED || PARTIALLY_RECEIVED`，新增 `APPROVED`（審批通過的單，等同於已提交，可收貨）。
   - `canCancel()`：原為 `DRAFT || SUBMITTED`，新增 `PENDING_APPROVAL || APPROVED`（等待審批或已審批但尚未開始收貨時，StoreOwner 仍可取消，與既有「`SUBMITTED` 可取消」的寬鬆語意一致，不因多了審批步驟而變嚴格）。
4. **稽核欄位**：`PurchaseOrder` 新增 `reviewedBy`（UUID）、`reviewedAt`（Instant）、`rejectionReason`（TEXT），比照 `SettlementStatement` 的 `reviewedAt/reviewedBy/rejectionReason` 命名慣例（Sprint 80/81）。`approvePurchaseOrder`/`rejectPurchaseOrder` 皆寫入 `reviewedBy`/`reviewedAt`，僅 reject 額外寫入 `rejectionReason`。
5. **稽核記錄**：比照 `AdminService.recordAudit(...)`（DEF-016 既有機制），核准/駁回皆呼叫 `recordAudit("PURCHASE_ORDER_APPROVED"/"PURCHASE_ORDER_REJECTED", "PURCHASE_ORDER", poId, po.getTenantId(), oldStatus, newStatus, reason)`，失敗不中斷主流程（既有 try-catch 語意）。
6. **是否需要租戶擁有權檢查（IDOR 自我檢查，比照 CLAUDE.md 反覆教訓主動檢查）**：`approvePurchaseOrder`/`rejectPurchaseOrder`/`getPendingApprovalPurchaseOrders` 三個方法**刻意不做**任何 `TenantContext`/`tenantId` 篩選——因為呼叫路徑完全被 Controller 層 `@PreAuthorize("hasRole('SUPER_ADMIN')")` 鎖死，只有 SUPER_ADMIN 才能觸達，而 SUPER_ADMIN 跨租戶查看/核准正是本功能存在的目的，不是漏洞。此點在 Service 方法上以 Javadoc 明確註記，避免未來被誤判為「忘記加租戶檢查」而誤修。

## 3. 資料庫變更（Migration V65）

```sql
ALTER TABLE tenants ADD COLUMN purchase_order_approval_threshold NUMERIC(12,2) NULL;
ALTER TABLE purchase_orders ADD COLUMN reviewed_by UUID NULL;
ALTER TABLE purchase_orders ADD COLUMN reviewed_at TIMESTAMP NULL;
ALTER TABLE purchase_orders ADD COLUMN rejection_reason TEXT NULL;
```

## 4. 實作清單（依 CLAUDE.md 開發-編譯-測試循環，一次一個檔案）

1. Migration `V65__Add_Purchase_Order_Approval_Fields.sql`
2. `Tenant.java` 新增欄位
3. `PurchaseOrder.java`：新增 enum 值 + 新欄位 + 更新 `canReceive()`/`canCancel()` + 新增 `canApprove()`/`canReject()`
4. `PurchaseOrderDto.java` 新增 `reviewedBy`/`reviewedAt`/`rejectionReason` 欄位（供 StoreOwner 查詢時可見審批結果）
5. `PurchaseOrderService.submitPurchaseOrder`：注入 `TenantRepository`，加入門檻判斷分支
6. `TenantUpdateRequest.java`/`TenantUpdateResponse.java`/`TenantService.updateTenant`：開放 `purchaseOrderApprovalThreshold` 欄位設定
7. `PurchaseOrderRejectRequest.java`（新 DTO，僅一個 `reason` 欄位，比照 `AdminDto.TenantRejectRequest`）
8. `AdminService.java`：注入 `PurchaseOrderRepository`，新增 `getPendingApprovalPurchaseOrders(page, size)`/`approvePurchaseOrder(poId)`/`rejectPurchaseOrder(poId, reason)`
9. `AdminController.java`：新增 `GET /v2/admin/purchase-orders/pending`、`POST /v2/admin/purchase-orders/{poId}/approve`、`POST /v2/admin/purchase-orders/{poId}/reject`，皆 `@PreAuthorize("hasRole('SUPER_ADMIN')")`

## 5. 測試計畫

- `PurchaseOrderServiceTest`：`submitPurchaseOrder` 新增 3 案例（門檻為 null 維持 SUBMITTED、金額等於門檻維持 SUBMITTED、金額超過門檻轉 PENDING_APPROVAL）。
- 新增/擴充 `PurchaseOrder` entity 層測試：`canReceive`/`canCancel` 對 `APPROVED`/`PENDING_APPROVAL`/`REJECTED` 各狀態的布林結果。
- `AdminServiceTest`：新增 `approvePurchaseOrder`/`rejectPurchaseOrder` 成功案例、非 `PENDING_APPROVAL` 狀態應拋 `E_7002`、`rejectPurchaseOrder` 未填 reason 應驗證失敗（或依既有 DTO validation 慣例）、稽核記錄確有寫入（`verify(auditLogRepository).save(...)`）。
- `AdminControllerE2ETest`：若既有檔案已有 SUPER_ADMIN 認證慣例可延用，新增新端點的 200 成功案例 + 非 SUPER_ADMIN 呼叫應 403 的案例（驗證 `@PreAuthorize` 確實生效，這是本功能唯一的存取控制邊界，需明確驗證）。

## 6. 範圍外

- 前端 UI（StoreOwner 設定門檻的介面、SuperAdmin 審批清單頁面）不在本 Sprint 範圍，後端 API 優先，前端列入下一輪候選。
- 不處理「PO 審批後又收到部分退貨/取消單項」等 clawback 情境（PRD 原文未提及，屬過度延伸）。
- 不追溯處理 Sprint 10 以來已存在、金額超過任意假設門檻的歷史 PO（門檻預設為 `null`，本身即不影響既有資料）。
