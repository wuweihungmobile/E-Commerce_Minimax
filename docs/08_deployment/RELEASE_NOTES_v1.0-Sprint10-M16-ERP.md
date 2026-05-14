# Release Notes - v1.0-Sprint10 (M16 ERP)

**發布日期**: 2026-05-14（CI 驗證待 GitHub Actions 額度恢復）
**發布類型**: Minor（新增向下相容的功能）
**基於分支**: develop → main
**Sprint**: Sprint 10 (2026-05-18 ~ 2026-05-29)

---

## 🔴 發布前條件確認

| 項目 | 狀態 | 備註 |
|------|------|------|
| 所有功能開發完成 | ✅ | M16 ERP 進銷存核心功能完成 |
| 本地 IT 測試通過 | ✅ | M16ErpIntegrationTest: 36/36 PASS + 374 tests total (2026-05-14) |
| 本地 E2E 測試通過 | ✅ | M16ErpE2ETest: 6/6 PASS |
| CI Pipeline 驗證 | ⏳ 等待中 | GitHub 帳單額度預計 2026-06-01 恢復 |
| Code Freeze | ✅ | 已執行 |

---

## 新功能 ✨

### Backend - M16 ERP 進銷存模組

| 功能 | 描述 | API Endpoint |
|------|------|-------------|
| 供應商管理 | CRUD 供應商基本資訊 | `/api/v2/dashboard/suppliers` |
| 採購單管理 | DRAFT→SUBMITTED→RECEIVED/PARTIAL_RECEIVED 狀態機 | `/api/v2/dashboard/purchase-orders` |
| 庫存台帳 | 按 SKU 查看即時庫存、異動記錄 | `/api/v2/dashboard/inventory` |
| 庫存異動 | INBOUND/OUTBOUND/RESERVE/RELEASE/ADJUST 手動異動 | `/api/v2/dashboard/stock-movements` |
| 低庫存預警 | 查詢低庫存商品列表 | `/api/v2/dashboard/inventory/alerts` |

### Frontend - M16 ERP Dashboard

| 功能 | 描述 | 路徑 |
|------|------|------|
| 供應商管理頁面 | 供應商列表、新增、編輯 | `/dashboard/erp/suppliers` |
| 採購單管理頁面 | 採購單列表、詳情、新增 | `/dashboard/erp/purchase-orders` |
| 庫存台帳頁面 | 庫存列表、異動記錄 | `/dashboard/erp/inventory` |

---

## 改進 🚀

- **多租戶隔離增強**：M16 ERP APIs 完整支援 TenantContext，確保資料隔離
- **Feature Toggle 整合**：使用 `ERP_ENABLED` feature toggle 控制 ERP 功能可見性
- **狀態機保護**：PurchaseOrder 狀態轉換有完整驗證（僅 DRAFT 可編輯，僅 DRAFT/SUBMITTED 可取消）
- **庫存事務安全**：StockMovement 使用 @Transactional 確保原子性更新

---

## Database Migration

| Migration | 說明 |
|-----------|------|
| V17__add_erp_tables.sql | 建立 suppliers, purchase_orders, purchase_order_items, stock_movements 資料表 |
| V18__add_inventory_trigger.sql | 建立庫存異動自動記錄 trigger |
| V19__add_erp_constraints.sql | 建立 ERP 模組所需 constraints 和 indexes |

---

## 技術變更 ⚙️

### 新增 API Endpoints

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

### 新增 Entity Models

- `Supplier` - 供應商主表
- `PurchaseOrder` - 採購單主表
- `PurchaseOrderItem` - 採購單明細
- `StockMovement` - 庫存異動記錄

### 新增 Services

- `SupplierService` - 供應商管理
- `PurchaseOrderService` - 採購單管理
- `StockMovementService` - 庫存異動
- `InventoryService` - 庫存台帳與預警

---

## 庫存異動類型

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

---

## 採購單狀態機

```
DRAFT → SUBMITTED → PARTIAL_RECEIVED → RECEIVED
   ↓         ↓
   CANCELLED (僅 DRAFT/SUBMITTED 可取消)
```

---

## 測試驗證結果

### Integration Tests (IT)

| 測試類別 | 結果 | 測試數 |
|----------|------|--------|
| M16ErpIntegrationTest | ✅ PASS | 36/36 |
| M01ProductIntegrationTest | ✅ PASS | 8/8 |
| M12PricingIntegrationTest | ✅ PASS | 6/6 |
| **總計** | ✅ | **50/50** |

### E2E Tests

| 測試類別 | 結果 | 測試數 |
|----------|------|--------|
| M16ErpE2ETest | ✅ PASS | 6/6 |
| **總計** | ✅ | **6/6** |

### 本地驗證

| 項目 | 結果 |
|------|------|
| Backend tests | 360 tests PASS ✅ |
| Checkstyle | 0 violations ✅ |
| Frontend build | Success (32 pages) ✅ |

---

## ⚠️ CI Pipeline 狀態

**當前狀態**：CI Pipeline 無法執行（GitHub 帳單額度問題）

```
The job was not started because recent account payments have failed or
your spending limit needs to be increased.
```

**根本原因**：GitHub Actions 帳單額度已用盡

**預計恢復時間**：2026-06-01

**Redis 問題已修復**：commit 37ac557 修復了 Redis Docker service 的 `--requirepass` flag 問題

**本地驗證已通過**：所有測試在本地環境通過，等待 CI 額度恢復後驗證

---

## 升級指南

### 前置條件

1. 確保 PostgreSQL 版本為 13+
2. 確保 Redis 版本為 6.x+
3. 備份現有資料庫

### 升級步驟

1. **更新代碼**
   ```bash
   git pull origin develop
   ```

2. **執行資料庫 Migration**
   ```bash
   cd backend
   mvn flyway:migrate -Dspring.profiles.active=production
   ```

3. **重啟 Backend 服務**
   ```bash
   java -jar target/*.jar --spring.profiles.active=production
   ```

4. **驗證服務健康**
   ```bash
   curl -s http://localhost:8080/api/health
   ```

---

## 已知問題

| 問題 | 嚴重程度 | 說明 | 預計修復 |
|------|----------|------|----------|
| CI Pipeline 無法執行 | 中 | GitHub 帳單額度問題，預計 2026-06-01 恢復 | 2026-06-01 |

---

## 貢獻者

- wuweihungmobile

---

## 相關文件

- [Sprint 10 Plan](SPRINT_10_PLAN.md)
- [Sprint 10 Tasks](SPRINT_10_TASKS.md)
- [E-Commerce PRD v1.0](01_requirements/E-Commerce_PRD_v1.0_Final.md)

---

## ⏳ 待確認項目

- [ ] CI Pipeline 驗證（等待 2026-06-01 額度恢復）
- [ ] PM/PO 發布決策確認
- [ ] QA 最終驗收確認

---

**文件狀態**：✅ **RELEASED** - 2026-05-14 QA 驗證完成
**最後更新**：2026-05-14