# Sprint 6 任務追蹤 / Sprint 6 Tasks

> **Sprint 編號**: Sprint 6
> **期間**: 2026-05-11 ~ 2026-05-24 (2 週)
> **文件版本**: v1.0
> **建立日期**: 2026-04-30
> **更新日期**: 2026-04-30

---

## 1. 任務狀態總覽

| 狀態 | 數量 |
|------|------|
| ✅ 已完成 | 17 |
| 🔄 進行中 | 0 |
| ⏳ 待處理 | 0 |
| ❌ 已阻礙 | 0 |

---

## 2. 詳細任務清單

### 2.1 DEF-001 環境建設

| 任務 ID | 任務名稱 | 負責 | 狀態 | 備註 |
|---------|----------|------|------|------|
| T-DEF-001-01 | 統一 Listing 建立端點 | Backend | ✅ | POST /v2/dashboard/listings |
| T-DEF-001-02 | Feature Toggle 檢查實作 | Backend | ✅ | BOOKING_ENABLED/RETAIL_ENABLED |
| T-DEF-001-03 | 測試資料建立 | Backend | ✅ | 測試 Tenant/User/Listing |
| T-DEF-001-04 | Feature Toggle 邏輯測試 | QA | ✅ | IT-M02-002, IT-M12-002 |
| T-DEF-001-05 | Booking E2E 測試執行 | QA | ✅ | AT-M06-* 13 個 AT |

### 2.2 M01/M02 Frontend

| 任務 ID | 任務名稱 | 負責 | 狀態 | 備註 |
|---------|----------|------|------|------|
| T-M01-01 | 商品列表頁面 | Frontend | ✅ | /dashboard/products |
| T-M01-02 | 商品建立/編輯表單 | Frontend | ✅ | /dashboard/products/new |
| T-M02-01 | 房源列表頁面 | Frontend | ✅ | /dashboard/rooms |
| T-M02-02 | 房源建立/編輯表單 | Frontend | ✅ | /dashboard/rooms/new |

### 2.3 M01/M02 Backend API 測試

| 任務 ID | 任務名稱 | 負責 | 狀態 | 備註 |
|---------|----------|------|------|------|
| T-M01-03 | M01 Backend API IT 測試 | QA | ✅ | 5 個 IT |
| T-M01-04 | M01 Backend API E2E 測試 | QA | ✅ | 7 個 API E2E |
| T-M02-03 | M02 Backend API IT 測試 | QA | ✅ | 7 個 IT |
| T-M02-04 | M02 Backend API E2E 測試 | QA | ✅ | 6 個 API E2E |

### 2.4 M12 動態定價

| 任務 ID | 任務名稱 | 負責 | 狀態 | 備註 |
|---------|----------|------|------|------|
| T-M12-01 | 動態價格計算端點 | Backend | ✅ | GET /api/v2/listings/:id/price |
| T-M12-02 | API 路徑重構 | Backend | ✅ | /v2/pricing/* → /api/v2/dashboard/pricing/* |
| T-M12-03 | 手動覆蓋價格端點 | Backend | ✅ | POST .../rules/:id/override |
| T-M12-04 | 定價規則管理頁面 | Frontend | ✅ | /dashboard/pricing/rules |
| T-M12-05 | PricingService 單元測試 | QA | ✅ | 9 個 UT |
| T-M12-06 | Pricing 整合測試 | QA | ✅ | 8 個 IT |

---

## 3. 產出文件

| 文件 | 位置 | 狀態 |
|------|------|------|
| Sprint 6 Plan | docs/04_planning/SPRINT_06_PLAN.md | ✅ |
| Sprint 6 Tasks | docs/05_development/SPRINT_06_TASKS.md | ✅ |
| Sprint 6 Review | docs/05_development/SPRINT_06_REVIEW.md | 🔜 |
| Sprint 6 Test Report | docs/03_testing/SPRINT_06_TEST_REPORT.md | 🔜 |
| API_Listing_Unified_Create.md | docs/02_architecture/api/ | ✅ |

---

## 4. 測試覆蓋統計

| 模組 | UT | IT | API E2E | AT | 合計 |
|------|----|----|---------|----|----|
| M01 | - | 5 | 7 | - | 12 |
| M02 | - | 7 | 6 | - | 13 |
| M06 | - | - | - | 13 | 13 |
| M12 | 9 | 8 | - | - | 17 |
| **合計** | **9** | **20** | **13** | **13** | **55** |

---

## 5. Sprint 6 總結

**完成率**: 100% (17/17 任務)
**測試覆蓋**: 55 個 TC/AT

### 關鍵交付物

1. ✅ 統一 Listing 建立端點 (POST /v2/dashboard/listings)
2. ✅ Feature Toggle 檢查邏輯 (BOOKING_ENABLED/RETAIL_ENABLED)
3. ✅ 動態價格計算 (GET /api/v2/listings/:id/price)
4. ✅ 手動覆蓋價格端點 (POST /api/v2/dashboard/pricing/rules/:id/override)
5. ✅ 商品管理頁面 (/dashboard/products)
6. ✅ 房源管理頁面 (/dashboard/rooms)
7. ✅ 定價規則管理頁面 (/dashboard/pricing/rules)
8. ✅ PricingService 單元測試 (9 UT)
9. ✅ M01/M02/M12 整合測試 (20 IT)

---

**文件狀態**: ✅ 已完成
**下一步**: Sprint 6 Review
