# Sprint 10 任務分解與進度追蹤

> **Sprint**: Sprint 10
> **期間**: 2026-05-18 ~ 2026-05-29 (2 週)
> **版本**: v1.0
> **建立日期**: 2026-05-07
> **更新日期**: 2026-05-07
> **基於**: Sprint 9 M15 CMS 完成 + M17 Feature Toggle 已確認

---

## 1. Sprint 10 任務清單

### 1.1 Backend Tasks

| 任務 ID | 任務名稱 | SP | 負責人 | 優先級 | 狀態 | 備註 |
|---------|----------|-----|--------|--------|------|------|
| Task-M16-101 | Database Migration: ERP Tables | 3 | Dev | P0 | ✅ COMPLETED | V17~V19 migrations 實作完成 |
| Task-M16-102 | Backend: Supplier CRUD APIs | 3 | Dev | P0 | ✅ COMPLETED | SupplierService + ErpController |
| Task-M16-103 | Backend: PurchaseOrder CRUD + State Machine | 5 | Dev | P0 | ✅ COMPLETED | PurchaseOrderService + POStatus |
| Task-M16-104 | Backend: StockMovement Service + Inventory Ledger | 5 | Dev | P0 | ✅ COMPLETED | StockMovementService + InventoryService |
| Task-M16-105 | Backend: 低庫存預警 API | 2 | Dev | P1 | ✅ COMPLETED | InventoryService.getLowStockAlerts() |

### 1.2 Frontend Tasks

| 任務 ID | 任務名稱 | SP | 負責人 | 優先級 | 狀態 | 備註 |
|---------|----------|-----|--------|--------|------|------|
| Task-M16-106 | FE: 供應商管理頁面 | 3 | FE Dev | P0 | ✅ COMPLETED | /dashboard/erp/suppliers |
| Task-M16-107 | FE: 採購單管理頁面 | 5 | FE Dev | P0 | ✅ COMPLETED | /dashboard/erp/purchase-orders |
| Task-M16-108 | FE: 庫存台帳頁面 | 3 | FE Dev | P0 | ✅ COMPLETED | /dashboard/erp/inventory |

### 1.3 QA Tasks

| 任務 ID | 任務名稱 | SP | 負責人 | 優先級 | 狀態 | 備註 |
|---------|----------|-----|--------|--------|------|------|
| Task-M16-109 | IT: M16 Backend Integration Tests | 3 | QA | P0 | ✅ COMPLETED | M16ErpIntegrationTest (19 test cases) |
| Task-M16-110 | E2E: M16 ERP 流程測試 | 2 | QA | P1 | ✅ COMPLETED | M16ErpE2ETest (6 test cases) |

---

## 2. Story Points Summary

| 角色 | SP | 任務數 |
|------|-----|--------|
| Backend (Dev) | 18 | 5 |
| Frontend (FE Dev) | 11 | 3 |
| QA | 5 | 2 |
| **合計** | **34 SP** | **10** |

---

## 3. Sprint 10 成功標準

| 標準 | 目標 | 狀態 |
|------|------|------|
| Task-M16-101 完成 | V17~V19 migrations 建立並執行成功 | ✅ 完成 (2026-05-09) |
| Task-M16-102 完成 | Supplier CRUD APIs 可正常運作 | ✅ 完成 (2026-05-08) |
| Task-M16-103 完成 | PurchaseOrder 狀態機正確運作 | ✅ 完成 (2026-05-09) |
| Task-M16-104 完成 | StockMovement 產生並更新庫存 | ✅ 完成 (2026-05-09) |
| Task-M16-105 完成 | 低庫存預警查詢正常 | ✅ 完成 (2026-05-08) |
| Task-M16-106 ~ 108 完成 | FE ERP Dashboard 頁面完整 | ✅ 完成 (2026-05-08) |
| Task-M16-109 通過 | IT 測試 15+/15+ 通過 | ✅ 完成 (M16ErpIntegrationTest) |
| Task-M16-110 通過 | E2E 測試 5+/5+ 通過 | ✅ 完成 (M16ErpE2ETest: 6/6) |
| 無 High 缺陷 | High = 0 | ✅ (截至 2026-05-09) |

---

## 4. 依賴關係

```
Sprint 9 完成 ✅
    ↓
Sprint 10 M16 ERP (當前)
    ↓
Phase 2-A 完成 (M15 + M16)
```

### Task-M16-101 前置條件
- M17 Feature Toggle: ✅ 已確認 (ERP_ENABLED 已存在)
- PostgreSQL 連線: ✅ 運行中

### Task-M16-102 ~ 105 前置條件
- Task-M16-101 完成
- FeatureToggleService: ✅ 已存在
- TenantContext: ✅ 已存在

### Task-M16-106 ~ 108 前置條件
- Task-M16-102 ~ 105 完成 (Backend APIs ready)
- Next.js 環境: ✅ 運行中

### Task-M16-109 ~ 110 前置條件
- Task-M16-102 ~ 108 完成
- 測試環境: ✅ 運行中

---

## 5. M16 ERP 功能技術細節

### 5.1 Database Migration (Task-M16-101)

**新增資料表**:

| 資料表 | 說明 |
|--------|------|
| `suppliers` | 供應商主表 |
| `purchase_orders` | 採購單主表 |
| `purchase_order_items` | 採購單明細 |
| `stock_movements` | 庫存異動記錄 |

### 5.2 API Endpoints (Task-M16-102 ~ 105)

| 方法 | 端點 | 說明 |
|------|------|------|
| GET | `/api/v2/dashboard/inventory` | 庫存台帳列表 |
| GET | `/api/v2/dashboard/inventory/:skuId` | SKU 庫存詳情+異動記錄 |
| GET | `/api/v2/dashboard/inventory/alerts` | 低庫存預警列表 |
| POST | `/api/v2/dashboard/purchase-orders` | 建立採購單 (DRAFT) |
| GET | `/api/v2/dashboard/purchase-orders` | 採購單列表 |
| GET | `/api/v2/dashboard/purchase-orders/:id` | 採購單詳情 |
| PUT | `/api/v2/dashboard/purchase-orders/:id` | 更新採購單 (僅 DRAFT) |
| PUT | `/api/v2/dashboard/purchase-orders/:id/submit` | 提交採購單 |
| PUT | `/api/v2/dashboard/purchase-orders/:id/receive` | 確認收貨 |
| PUT | `/api/v2/dashboard/purchase-orders/:id/cancel` | 取消採購單 |
| POST | `/api/v2/dashboard/stock-movements` | 手動庫存異動 |
| GET | `/api/v2/dashboard/stock-movements` | 庫存異動記錄 |
| GET | `/api/v2/dashboard/suppliers` | 供應商列表 |
| POST | `/api/v2/dashboard/suppliers` | 新增供應商 |
| PUT | `/api/v2/dashboard/suppliers/:id` | 更新供應商 |

### 5.3 庫存異動類型

| 類型 | 方向 | 觸發時機 |
|------|------|----------|
| INBOUND | +total_qty | 採購入庫確認 |
| OUTBOUND | -total_qty, -reserved_qty | 訂單出貨 |
| RESERVE | +reserved_qty | 訂單建立預留 |
| RELEASE | -reserved_qty | 訂單取消釋放 |
| ADJUST_PLUS | +total_qty | 盤盈調整 |
| ADJUST_MINUS | -total_qty | 盤虧調整 |

### 5.4 採購單狀態機

```
DRAFT → SUBMITTED → PARTIAL_RECEIVED → RECEIVED
   ↓         ↓
   CANCELLED (僅 DRAFT/SUBMITTED 可取消)
```

---

## 6. 風險追蹤

| 風險 ID | 等級 | 說明 | 緩解措施 | 狀態 |
|---------|------|------|----------|------|
| R-001 | ~~高~~ → ✅ 已解決 | M17 Feature Toggle 未實作 | ✅ 已確認 ERP_ENABLED 已存在 | ✅ 已解決 |
| R-002 | 中 | 庫存連動事務複雜度 | 使用 @Transactional 確保原子性 | ⏳ |
| R-003 | 中 | 多 SKU 訂單出庫邏輯 | Phase 1 限定單一 SKU | ⏳ |
| R-004 | 低 | 採購單取消後庫存處理 | 明確定義 PARTIAL_RECEIVED 不可取消 | ⏳ |

---

## 7. 每日進度追蹤

### Day 1 (2026-05-18)

### Day 2 (2026-05-19)

### Day 3 (2026-05-20)

### Day 4 (2026-05-21)

### Day 5 (2026-05-22)

### Day 6-10 (2026-05-25 ~ 2026-05-29)

---

**最後更新**: 2026-05-07
**下次更新**: Sprint 10 Day 1 (2026-05-18)
**備註**: M17 Feature Toggle 已確認實作 (ERP_ENABLED)，Sprint 10 可隨時開始