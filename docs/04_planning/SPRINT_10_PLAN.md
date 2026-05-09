# Sprint 10 計劃 / Sprint 10 Plan

> **Sprint 編號**: Sprint 10
> **期間**: 2026-05-18 ~ 2026-05-29 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-05-06
> **更新日期**: 2026-05-06
> **基於**: Sprint 9 M15 CMS 完成 + PM/PO 確認

---

## 🔴 前置條件確認

**Sprint 9 發布**: ✅ **APPROVED** - 2026-05-06

| 項目 | 確認結果 |
|------|----------|
| Sprint 9 發布決策 | ✅ APPROVED - M15 CMS FE-BE 整合完成 |
| Sprint 10 開始日期 | ✅ **2026-05-18** (週一) |
| M17 Tenant Dependency | ⚠️ 需要確認 - M17 必須在 M16 之前完成 |

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 10 |
| **開始日期** | 2026-05-18 (週一) |
| **結束日期** | 2026-05-29 (週四) |
| **Sprint 容量** | 30 SP |
| **規劃 SP** | 待規劃 |
| **Buffer** | ~13% |
| **團隊** | 2 人 Dev Team |

---

## 2. Sprint 目標

> **目標**: 完成 M16 ERP 進銷存核心功能（採購入庫、庫存台帳、出庫管理），為 B 端商家提供完整的進銷存管理能力，並與 C 端庫存連動。

### 具體目標

#### M16 ERP Backend 功能

| 功能 | 優先級 | 說明 |
|------|--------|------|
| 供應商管理 | P0 | CRUD 供應商基本資訊 |
| 採購單管理 | P0 | DRAFT → SUBMITTED → RECEIVED/PARTIAL_RECEIVED 狀態機 |
| 庫存台帳 | P0 | 按 SKU 查看即時庫存、異動記錄 |
| 庫存異動 | P0 | INBOUND/OUTBOUND/RESERVE/RELEASE/ADJUST 手動異動 |
| 低庫存預警 | P1 | 查詢低庫存商品列表 |
| 庫存盤點 | P1 | ADJUST_PLUS/ADJUST_MINUS 盤盈/盤虧記錄 |

#### M16 ERP Frontend 功能

| 功能 | 優先級 | 說明 |
|------|--------|------|
| 供應商列表頁 | P0 | `/dashboard/suppliers` |
| 新增/編輯供應商 | P0 | `/dashboard/suppliers/new`, `/dashboard/suppliers/:id/edit` |
| 採購單列表頁 | P0 | `/dashboard/purchase-orders` |
| 新增採購單 | P0 | `/dashboard/purchase-orders/new` |
| 採購單詳情 | P0 | `/dashboard/purchase-orders/:id` |
| 庫存台帳頁 | P0 | `/dashboard/inventory` |
| 庫存異動記錄 | P1 | `/dashboard/inventory/:skuId` |
| 低庫存預警 | P1 | `/dashboard/inventory/alerts` |

---

## 3. M16 ERP 功能清單

### 3.1 資料模型

#### 新增資料表

| 資料表 | 說明 | 優先級 |
|--------|------|--------|
| `suppliers` | 供應商主表 | P0 |
| `purchase_orders` | 採購單主表 | P0 |
| `purchase_order_items` | 採購單明細 | P0 |
| `stock_movements` | 庫存異動記錄 | P0 |

#### 修改現有資料表

| 資料表 | 欄位 | 說明 | 優先級 |
|--------|------|------|--------|
| `product_inventory` | 已存在 | ERP 連動欄位 | P0 |

### 3.2 API Endpoints

| 方法 | 端點 | 說明 | 角色 | 優先級 |
|------|------|------|------|--------|
| `GET` | `/api/v2/dashboard/inventory` | 庫存台帳列表 | Seller/StoreOwner | P0 |
| `GET` | `/api/v2/dashboard/inventory/:skuId` | SKU 庫存詳情+異動記錄 | Seller/StoreOwner | P0 |
| `GET` | `/api/v2/dashboard/inventory/alerts` | 低庫存預警列表 | Seller/StoreOwner | P1 |
| `POST` | `/api/v2/dashboard/purchase-orders` | 建立採購單 (DRAFT) | Seller/StoreOwner | P0 |
| `GET` | `/api/v2/dashboard/purchase-orders` | 採購單列表 | Seller/StoreOwner | P0 |
| `GET` | `/api/v2/dashboard/purchase-orders/:id` | 採購單詳情 | Seller/StoreOwner | P0 |
| `PUT` | `/api/v2/dashboard/purchase-orders/:id` | 更新採購單 (僅 DRAFT) | Seller/StoreOwner | P0 |
| `PUT` | `/api/v2/dashboard/purchase-orders/:id/submit` | 提交採購單 (DRAFT→SUBMITTED) | Seller/StoreOwner | P0 |
| `PUT` | `/api/v2/dashboard/purchase-orders/:id/receive` | 確認收貨 (SUBMITTED→RECEIVED) | Seller/StoreOwner | P0 |
| `PUT` | `/api/v2/dashboard/purchase-orders/:id/cancel` | 取消採購單 | Seller/StoreOwner | P0 |
| `POST` | `/api/v2/dashboard/stock-movements` | 手動庫存異動 | StoreOwner | P0 |
| `GET` | `/api/v2/dashboard/stock-movements` | 庫存異動記錄 | Seller/StoreOwner | P0 |
| `GET` | `/api/v2/dashboard/suppliers` | 供應商列表 | Seller/StoreOwner | P0 |
| `POST` | `/api/v2/dashboard/suppliers` | 新增供應商 | Seller/StoreOwner | P0 |
| `PUT` | `/api/v2/dashboard/suppliers/:id` | 更新供應商 | Seller/StoreOwner | P0 |

### 3.3 庫存異動類型

| 類型 | 方向 | 觸發時機 |
|------|------|----------|
| INBOUND | +total_qty | 採購入庫確認 |
| OUTBOUND | -total_qty, -reserved_qty | 訂單出貨 |
| RESERVE | +reserved_qty | 訂單建立預留 |
| RELEASE | -reserved_qty | 訂單取消釋放 |
| ADJUST_PLUS | +total_qty | 盤盈調整 |
| ADJUST_MINUS | -total_qty | 盤虧調整 |
| TRANSFER_OUT | -total_qty | 調撥出庫 |
| TRANSFER_IN | +total_qty | 調撥入庫 |
| SCRAP | -total_qty | 報廢出庫 |

### 3.4 採購單狀態機

```
DRAFT → SUBMITTED → PARTIAL_RECEIVED → RECEIVED
   ↓         ↓
   CANCELLED (僅 DRAFT/SUBMITTED 可取消)
```

### 3.5 ERP → C 端連動

```
B 端入庫流程：
1. StoreOwner 建立採購單 (PurchaseOrder)
2. 收貨確認 → 產生 StockMovement (type=INBOUND)
3. product_inventory.total_qty += inbound_qty
4. available_qty 自動重算 (total_qty - reserved_qty)
5. C 端商品頁即時反映新庫存
```

---

## 4. User Stories 摘要

| US ID | 標題 | SP | 優先級 |
|-------|------|-----|--------|
| US-M16-001 | BE: StoreOwner 管理供應商 | 2 | P0 |
| US-M16-002 | BE: StoreOwner 建立採購單 | 3 | P0 |
| US-M16-003 | BE: StoreOwner 確認收貨入庫 | 3 | P0 |
| US-M16-004 | BE: StoreOwner 檢視庫存台帳 | 2 | P0 |
| US-M16-005 | BE: StoreOwner 手動調整庫存 | 2 | P0 |
| US-M16-006 | FE: StoreOwner 檢視庫存台帳 | 3 | P0 |
| US-M16-007 | FE: StoreOwner 管理採購單 | 5 | P0 |
| US-M16-008 | FE: StoreOwner 管理供應商 | 3 | P0 |
| **待補充** | 視評估調整 | | |

---

## 5. 任務分解（初步）

| 任務 ID | 任務名稱 | SP | 負責人 | 優先級 |
|---------|----------|-----|--------|--------|
| Task-M16-101 | Database Migration: suppliers, purchase_orders, purchase_order_items, stock_movements | 3 | Dev | P0 |
| Task-M16-102 | Backend: Supplier CRUD APIs | 3 | Dev | P0 |
| Task-M16-103 | Backend: PurchaseOrder CRUD + State Machine | 5 | Dev | P0 |
| Task-M16-104 | Backend: StockMovement Service + Inventory Ledger | 5 | Dev | P0 |
| Task-M16-105 | Backend: 低庫存預警 API | 2 | Dev | P1 |
| Task-M16-106 | FE: 供應商管理頁面 | 3 | FE Dev | P0 |
| Task-M16-107 | FE: 採購單管理頁面 | 5 | FE Dev | P0 |
| Task-M16-108 | FE: 庫存台帳頁面 | 3 | FE Dev | P0 |
| Task-M16-109 | IT: M16 Backend Integration Tests | 3 | QA | P0 |
| Task-M16-110 | E2E: M16 ERP 流程測試 | 2 | QA | P1 |
| **合計** | | **34 SP** | | |

> ⚠️ **注意**: 任務分解為初步估計，實際 SP 可能需要根據詳細設計調整

---

## 6. Sprint 10 前置準備檢查清單

### 6.1 Backend ✅ 已就緒

| 項目 | 狀態 | 備註 |
|------|------|------|
| product_inventory 資料表 | ✅ 已存在 | M01 Phase 1 已建立 |
| Tenant Context Filter | ✅ 已存在 | 多租戶隔離機制 |
| JWT 認證 | ✅ 已存在 | Bearer Token 認證 |

### 6.2 Backend 需要新增

| 項目 | 狀態 | 備註 |
|------|------|------|
| ERP Domain Models | ⏳ 待建立 | Supplier, PurchaseOrder, PurchaseOrderItem, StockMovement |
| ERP Repositories | ⏳ 待建立 | SupplierRepository, PurchaseOrderRepository, StockMovementRepository |
| ERP Services | ⏳ 待建立 | SupplierService, PurchaseOrderService, StockMovementService |
| ERP Controllers | ⏳ 待建立 | SupplierController, PurchaseOrderController, InventoryController |
| Database Migrations | ⏳ 待建立 | V5__add_erp_tables.sql |

### 6.3 Frontend ⏳ 待開始

| 項目 | 狀態 | 備註 |
|------|------|------|
| React 頁面 | ⏳ 待開始 | 見 3.1 |
| API Client | ⏳ 待開始 | 使用現有 Axios 配置 |
| Dashboard Layout | ⏳ 待開始 | 使用現有佈局 |

### 6.4 測試環境 ✅ 已就緒

| 項目 | 狀態 | 備註 |
|------|------|------|
| Backend | ✅ 運行中 | http://localhost:8080 |
| Frontend | ✅ 運行中 | http://localhost:3000 |
| PostgreSQL | ✅ 運行中 | Port 5432 |
| Redis | ✅ 運行中 | Port 6379 |

---

## 7. Phase 2-A 依賴關係

```
Phase 1 完成 ✅
    ↓
Phase 2-A: M17(部分) → M15 ✅ → M12 ✅ → M16 (Sprint 10) ← 當前
                        ↑
                        └─ Sprint 9 完成
```

### 7.1 M17 Feature Toggle 已確認

**狀態**: ✅ **已實作** - 確認於 2026-05-07

| 項目 | 狀態 | 備註 |
|------|------|------|
| `tenant_feature_toggles` 資料表 | ✅ 已存在 | V6__Tenant_Feature_Toggles_Fix.sql |
| `TenantFeatureToggle` Entity | ✅ 已存在 | backend/src/main/java/.../model/tenant/TenantFeatureToggle.java |
| `TenantFeatureToggleRepository` | ✅ 已存在 | backend/src/main/java/.../repository/TenantFeatureToggleRepository.java |
| `FeatureToggleService` | ✅ 已存在 | backend/src/main/java/.../core/feature/FeatureToggleService.java |
| `ERP_ENABLED` Feature Key | ✅ 已定義 | TenantService.FEATURE_DEFINITIONS |
| 預設值 | ✅ 已啟用 | AdminService 預設 ERP_ENABLED=true |

**結論**: M17 Feature Toggle 機制已完整實作，M16 ERP 可直接使用 `checkFeatureEnabled("ERP_ENABLED")` 做 RBAC 控制。

---

## 8. 風險追蹤

| 風險 ID | 等級 | 說明 | 緩解措施 | 狀態 |
|---------|------|------|----------|------|
| R-001 | ~~高~~ → ✅ 已解決 | M17 Feature Toggle 未實作 | ✅ 已確認 ERP_ENABLED 已存在，可直接使用 | ✅ 已解決 (2026-05-07) |
| R-002 | 中 | 庫存連動事務複雜度 | 使用 @Transactional 確保原子性 | ⏳ |
| R-003 | 中 | 多 SKU 訂單出庫邏輯 | Phase 1 限定單一 SKU | ⏳ |
| R-004 | 低 | 採購單取消後庫存處理 | 明確定義 PARTIAL_RECEIVED 不可取消 | ⏳ |

---

## 9. 成功標準

| 標準 | 目標 | 狀態 |
|------|------|------|
| Backend IT 測試通過 | 15+/15+ | ⏳ |
| E2E 測試通過 | 5/5 | ⏳ |
| 採購單狀態機正確 | DRAFT→SUBMITTED→RECEIVED | ⏳ |
| 庫存連動正確 | total_qty 更新無誤 | ⏳ |
| 無 High 缺陷 | High = 0 | ⏳ |

---

## 10. 與前一 Sprint 的差異

| 項目 | Sprint 9 | Sprint 10 |
|------|---------|---------|
| **焦點** | M15 CMS FE-BE 整合 | M16 ERP 進銷存 |
| **測試** | IT 21/21 + E2E 12/12 | IT 15+ + E2E 5 |
| **前端** | CMS + Blog 頁面 | ERP Dashboard 頁面 |
| **後端** | Post + Media APIs | Supplier + PO + Stock APIs |

---

**文件狀態**: ✅ **待 PM/PO 確認** - M17 已確認實作，Sprint 10 可隨時開始
**下一步**: PM/PO 確認 Sprint 10 範圍和開始日期
**相關文件**:
- [SPRINT_09_PLAN.md](SPRINT_09_PLAN.md)
- [SPRINT_09_TASKS.md](../05_development/SPRINT_09_TASKS.md)
- [E-Commerce_PRD_v1.0_Final.md](../../01_requirements/E-Commerce_PRD_v1.0_Final.md) (§6.7, §8.1.5, §9.15)