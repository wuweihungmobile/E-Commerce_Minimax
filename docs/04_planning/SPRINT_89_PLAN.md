# Sprint 89 Plan — M16 採購審批門檻前端補齊

**Sprint**: Sprint 89
**日期**: 2026-07-09
**主題**: Sprint 88 retro 排定「三者最佳化順序」第二項的前半——M16 採購審批金額上限機制前端串接（後端已於 Sprint 85 完成）。M07 結算逆轉表單改列 Sprint 90 獨立處理，避免單 Sprint 範圍過大。

---

## 1. 探查結果（現況）

- 後端 API 三個新端點皆已於 Sprint 85 完成且無變更需求：`GET /v2/admin/purchase-orders/pending`、`POST /v2/admin/purchase-orders/{id}/approve`、`POST /v2/admin/purchase-orders/{id}/reject`（`AdminController.java`，`hasRole('SUPER_ADMIN')`）。無對應的「單筆詳情」端點，僅有列表（`PurchaseOrderSummaryResponse`，不含品項明細）。
- `PUT /v2/tenants/{id}`（`TenantController`）已支援 `purchaseOrderApprovalThreshold` 欄位寫入，但探查發現 **GET `/v2/tenants/{id}`（`TenantDetailsResponse`）未回傳此欄位**——StoreOwner 進編輯表單時無法看到目前已設定的門檻值，只能盲寫覆蓋。此為本 Sprint 前端所需，必須補上（`TenantService.getTenantDetails` 少組一個既有欄位，非新設計決策，風險低）。
- 前端 `POStatus` 型別（`services/erp/purchaseOrder.ts`）與後端 `PurchaseOrder.POStatus` enum 不同步：既缺 Sprint 85 新增的 `PENDING_APPROVAL`/`APPROVED`/`REJECTED`，且既有值 `PARTIAL_RECEIVED` 拼字與後端實際的 `PARTIALLY_RECEIVED` 不一致（在 3 個前端檔案重複出現：`services/erp/purchaseOrder.ts`、`components/erp/PurchaseOrderForm.tsx`、`app/dashboard/erp/purchase-orders/page.tsx`），導致該狀態的徽章永遠 fallback 顯示原始英文字串。本 Sprint 一併修正（與本次要新增的狀態徽章同一段程式碼，非額外範圍）。
- 前端無 SuperAdmin 共用 nav/layout（每頁各自做 client-side 角色檢查），比照既有 `admin/tenants` 頁面模式即可，不需新增導覽基礎設施。
- 無 Dialog 元件，駁回原因輸入延續既有「切換顯示卡片」模式（`admin/tenants/[id]/review/page.tsx`）。

## 2. 架構決策

1. **SuperAdmin 審批頁不做「列表+詳情」兩頁**：因後端無單筆詳情端點，且 `PurchaseOrderSummaryResponse` 已含審批所需全部資訊（單號、租戶、金額、幣別、提交時間），故直接在列表頁內以卡片內建核准/駁回按鈕（比照 `admin/tenants/[id]/review` 的按鈕邏輯，但不拆頁），避免建立呼叫不存在 API 的假詳情頁。
2. **門檻設定沿用既有 `TenantEditForm.tsx`**，不建新頁面：新增一個數字輸入欄位＋非負驗證，送出時併入既有 `PUT /v2/tenants/{id}` payload。
3. **PARTIAL_RECEIVED → PARTIALLY_RECEIVED 修正**：直接改字面值，因為是同一個 union type 定義、同一段 switch/case 邏輯，屬本次改動範圍內的直接缺陷，非無關重構。

## 3. 後端變更（僅 1 處，補既有欄位，非新設計）

- `TenantDetailsResponse.java`：新增 `purchaseOrderApprovalThreshold` 欄位。
- `TenantService.getTenantDetails`：`isApproved` 分支的 builder 補 `.purchaseOrderApprovalThreshold(tenant.getPurchaseOrderApprovalThreshold())`。
- `TenantServiceTest`：新增一個案例驗證 `getTenantDetails` 回傳值含已設定的門檻。

## 4. 前端實作清單（依 CLAUDE.md 開發-編譯-測試循環，一次一個檔案）

1. `services/erp/purchaseOrder.ts`：`POStatus` 修正拼字並新增三個狀態；`PurchaseOrderDto` 新增 `reviewedBy`/`reviewedAt`/`rejectionReason`。
2. `components/erp/PurchaseOrderForm.tsx`：`getStatusBadge` 補三個狀態徽章＋修正拼字；`view` 模式下若 `status === 'REJECTED'` 顯示駁回原因。
3. `app/dashboard/erp/purchase-orders/page.tsx`：狀態篩選下拉、`getStatusBadge` 同步修正。
4. `lib/api.ts`：`admin` 區塊新增 `purchaseOrders: { pending, approve(id), reject(id) }`。
5. `components/tenant/TenantEditForm.tsx`：`Tenant`/`TenantEditFormData` 新增 `purchaseOrderApprovalThreshold?: number`；新增數字輸入欄位（非負驗證，空值＝不設定門檻）；`fetchTenantDetail`/`handleSubmit` 併入此欄位。
6. 新頁面 `app/admin/purchase-orders/page.tsx`：SuperAdmin 待審採購單清單，卡片內建核准／駁回（駁回原因用切換卡片模式），角色守衛比照 `admin/tenants` 頁面慣例。

## 5. 測試計畫

- 後端：`TenantServiceTest` 新增 1 案例（如上）。純補既有欄位，不修改生產邏輯分支，`mvn test` 即可，依 [[sprint-full-regression-policy]] 免跑全量整合回歸。
- 前端：無既有 Jest/RTL 測試基礎設施覆蓋此類頁面（既有 `admin/tenants` 系列頁面亦無單元測試），比照既有慣例不新增，僅以 `npm run lint` + `npm run build` 驗證型別與建置正確性。

## 6. 範圍外（延後）

- M07 結算逆轉發起/確認表單 → Sprint 90。
- M18 客服工單子系統 → Sprint 91+。
- DEF-043（ROOM 購物車清空）、DEF-044（退款回補庫存）→ 待排入。
