# Sprint 5 計劃 / Sprint 5 Plan

> **Sprint 編號**: Sprint 5
> **期間**: 2026-06-10 ~ 2026-06-23 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-04-28
> **基於**: Sprint 4 完成 + Sprint 4 Review 建議

---

## 🔴 人機協作確認點結果

### Sprint 4 回顧摘要
**已完成**:
- M04 購物車 Backend + E2E 測試 (5 SP) ✅
- M06 預訂系統驗收測試 (2 SP) ✅
- FE-M17-006 Admin 審核頁面 (3 SP) ✅
- 166 tests passing, 0 failures

**延後至 Sprint 5**:
- FE-M17-005 功能開關頁面
- US-M17-009 Admin Feature Toggle 更新
- Booking E2E 完整預訂流程測試（需要完整 ROOM Listing 環境）
- M04 Frontend 購物車頁面

### Sprint 5 準備確認
**選擇**: ✅ Sprint 5 專注於功能完善 + Frontend 頁面

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 5 |
| **開始日期** | 2026-06-10 |
| **結束日期** | 2026-06-23 |
| **Sprint 容量** | 18 SP |
| **規劃 SP** | 9 SP |
| **Buffer** | 9 SP (✅ 充足安全範圍) |
| **Buffer 配置** | Booking E2E 環境風險: 3 SP, Frontend 整合風險: 3 SP, 緊急 Bug 應急: 3 SP |
| **團隊** | 2 人 Dev Team |

---

## 2. Sprint 目標

> **目標**: 完成功能開關管理 + M04 Frontend 頁面 + Booking E2E 完整流程測試，形成完整的營運管理體驗。

### 具體目標

#### Sprint 3 延後功能 (3 SP)
1. **FE-M17-005: 功能開關頁面** - StoreOwner 可管理 Feature Toggles
2. **US-M17-009: Admin Feature Toggle 更新** - Admin 可更新任意店鋪 Toggle

#### M04 Frontend (3 SP)
3. **M04 Frontend: 購物車頁面** - 買家購物車 UI

#### M06 Booking E2E (3 SP)
4. **Booking E2E: 完整預訂流程測試** - 建立 ROOM Listing 測試環境

---

## 3. User Stories

### 優先級定義
- **P0 (Critical)**: 此 Sprint 必要完成，系統核心功能，延期將阻礙主要流程
- **P1 (High)**: 此 Sprint 必要完成，重要功能但有 workaround

> **此 Sprint 所有 Stories 皆為必要完成，P0/P1 不影响交付范围，仅作为开发排序依据。**

### 3.1 Sprint 3 延後 Stories

| ID | 標題 | SP | 優先級 | Business Value | 狀態 |
|----|------|-----|--------|----------------|------|
| FE-M17-005 | 功能開關頁面 | 2 | P1 | 提升營運效率，StoreOwner 可自主管理功能上線 | 待實現 |
| US-M17-009 | Admin Feature Toggle 更新 | 1 | P1 | 支援 Admin 緊急停用功能，降低營運風險 | 待實現 |

### 3.2 M04 Frontend Story

| ID | 標題 | SP | 優先級 | Business Value | 狀態 |
|----|------|-----|--------|----------------|------|
| FE-M04-001 | 買家購物車頁面 | 3 | P0 | 完整購物車 UI，提升買家體驗 | 待實現 |

### 3.3 M06 Booking E2E Story

| ID | 標題 | SP | 優先級 | Business Value | 狀態 |
|----|------|-----|--------|----------------|------|
| IT-M06-E2E | Booking E2E 完整預訂流程 | 3 | P0 | 驗證 M06 預訂系統完整流程 | 待實現 |

### 3.4 Sprint 5 Total

| 類別 | SP | 說明 |
|------|-----|------|
| Sprint 3 延後 | 3 | FE-M17-005: 2, US-M17-009: 1 |
| M04 Frontend | 3 | FE-M04-001 買家購物車頁面 |
| M06 Booking E2E | 3 | IT-M06-E2E 完整預訂流程測試 |
| **總計** | **9 SP** | |
| **Buffer** | **9 SP** | 安全範圍 |

---

## 4. 任務分解 / Task Breakdown

> **SP 估算標準**: 1 SP = 4 小時（此為團隊共識估算標準）

### 4.1 FE-M17-005: 功能開關頁面 (2 SP)

**負責人**: Dev
**預估時間**: 3 小時
**頁面路由**: `/dashboard/tenants/[id]/features`

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-FE-005-01 | 建立 `/dashboard/tenants/[id]/features` 頁面路由 | 0.5h | 待實現 |
| T-FE-005-02 | 實作 Feature Toggle 列表元件 | 1h | 待實現 |
| T-FE-005-03 | 串接 GET /v2/dashboard/tenants/features API | 0.5h | 待實現 |
| T-FE-005-04 | 串接 PUT /v2/dashboard/tenants/features/:feature API | 1h | 待實現 |

**驗收標準 (AC)**:
- [ ] AC-FE-005-1: StoreOwner 可查看店鋪所有 Feature Toggles
- [ ] AC-FE-005-2: StoreOwner 可啟用/停用需要審核的 Feature
- [ ] AC-FE-005-3: 非 Owner 角色無權限訪問，回傳 403 Forbidden
- [ ] AC-FE-005-4: StoreOwner 啟用 `requires-admin-review` Feature 後，狀態顯示為 PENDING（需 Admin 審核才生效）

**Feature Toggle 分類**:
- `auto-approved`: StoreOwner 可直接啟用/停用
- `requires-admin-review`: 啟用後需 Admin 審核才生效（狀態為 PENDING）

**依賴**: FE-M17-003 (店鋪詳情頁面) - ✅ 已完成

---

### 4.2 US-M17-009: Admin Feature Toggle 更新 (1 SP)

**負責人**: Dev
**預估時間**: 1.5 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M17-009-01 | 實作 Admin PUT /v2/admin/tenants/:id/features/:feature 端點 | 0.5h | 待實現 |
| T-M17-009-02 | 整合測試：Admin 更新 Feature Toggle | 1h | 待實現 |

**驗收標準 (AC)**:
- [ ] AC-M17-009-1: Admin 可更新任意店鋪的 Feature Toggle，回傳 200 OK
- [ ] AC-M17-009-2: 非 Admin 角色無法呼叫此 API，回傳 403 Forbidden

**依賴**: FE-M17-005 (功能開關頁面需要此 API)

---

### 4.3 FE-M04-001: 買家購物車頁面 (3 SP)

**負責人**: Dev
**預估時間**: 4 小時
**頁面路由**: `/cart`

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M04-FE-01 | 建立 `/cart` 頁面路由 | 0.5h | 待實現 |
| T-M04-FE-02 | 實作購物車列表元件 (CartItemCard) | 1h | 待實現 |
| T-M04-FE-03 | 實作數量更新元件 (QuantitySelector) | 0.5h | 待實現 |
| T-M04-FE-04 | 串接 GET /v2/cart API | 0.5h | 待實現 |
| T-M04-FE-05 | 串接 PUT /v2/cart/items/{cartItemKey} API | 0.5h | 待實現 |
| T-M04-FE-06 | 串接 DELETE /v2/cart/items/{cartItemKey} API | 0.5h | 待實現 |
| T-M04-FE-07 | 整合測試：購物車頁面 E2E | 1h | 待實現 |
| T-M04-FE-08 | 實作結帳按鈕串接 Booking 流程 | 0.5h | 待實現 |

**驗收標準 (AC)**:
- [ ] AC-M04-FE-1: 買家可查看購物車內容（商品列表、數量、單價、小計），回傳 200 OK
- [ ] AC-M04-FE-2: 買家可更新商品數量，回傳 200 OK
- [ ] AC-M04-FE-3: 買家可移除商品，回傳 200 OK
- [ ] AC-M04-FE-4: 買家點擊結帳按鈕可進入預訂流程

**依賴**: M04 Backend APIs - ✅ 已完成 (Sprint 4)

---

### 4.4 IT-M06-E2E: Booking E2E 完整預訂流程測試 (3 SP)

**負責人**: QA/Dev
**預估時間**: 4 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M06-E2E-01 | 建立 ROOM Listing 測試資料 (測試資料準備) | 1h | 待實現 |
| T-M06-E2E-02 | 實作 Booking E2E 測試：建立預訂成功 (API-M06-001) | 0.5h | 待實現 |
| T-M06-E2E-03 | 實作 Booking E2E 測試：日期衝突 (API-M06-002) | 0.5h | 待實現 |
| T-M06-E2E-04 | 實作 Booking E2E 測試：取消預訂 (API-M06-004) | 0.5h | 待實現 |
| T-M06-E2E-05 | 執行並驗證所有 Booking E2E 測試 | 1h | 待實現 |

**驗收標準 (AC)**:
- [ ] AC-M06-E2E-1: 建立預訂成功回傳 201 和 bookingId
- [ ] AC-M06-E2E-2: 日期衝突回傳 E-4001 ROOM_CALENDAR_CONFLICT
- [ ] AC-M06-E2E-3: 取消預訂成功回傳 200

**依賴**: M06 Backend APIs - ✅ 已完成 (Sprint 4)

---

## 5. API 規格

### 5.1 Admin Feature Toggle API

#### 更新店鋪 Feature Toggle

**端點**: `PUT /v2/admin/tenants/:tenantId/features/:feature`

**Request Body**:
```json
{
  "enabled": true
}
```

**Response** (200 OK):
```json
{
  "code": 200,
  "message": "Feature toggle updated",
  "data": {
    "feature": "auto-approved",
    "enabled": true,
    "status": "ACTIVE"
  }
}
```

---

### 5.2 Feature Toggle 列表 API

#### 取得店鋪 Feature Toggles

**端點**: `GET /v2/dashboard/tenants/features`

**Response** (200 OK):
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "features": [
      {
        "feature": "auto-approved",
        "category": "listing",
        "displayName": "自動核准",
        "description": "新商品自動核准上架",
        "enabled": true,
        "status": "ACTIVE",
        "requiresAdminReview": false
      },
      {
        "feature": "requires-admin-review",
        "category": "listing",
        "displayName": "需要管理員審核",
        "description": "新商品需要管理員審核",
        "enabled": false,
        "status": "PENDING",
        "requiresAdminReview": true
      }
    ]
  }
}
```

---

### 5.3 購物車 Frontend API

#### 取得購物車

**端點**: `GET /v2/cart`

**Response** (200 OK):
```json
{
  "code": 200,
  "message": "Cart retrieved",
  "data": {
    "cartId": "uuid",
    "userId": "uuid",
    "items": [
      {
        "cartItemKey": "uuid",
        "listingId": "uuid",
        "listingName": "房源名稱",
        "coverImageUrl": "https://...",
        "quantity": 2,
        "unitPrice": 1500,
        "subtotal": 3000,
        "startDate": "2026-06-01",
        "endDate": "2026-06-03"
      }
    ],
    "totalAmount": 3000,
    "itemCount": 1
  }
}
```

#### 更新購物車商品數量

**端點**: `PUT /v2/cart/items/{cartItemKey}`

**Request Body**:
```json
{
  "quantity": 3
}
```

**Response** (200 OK):
```json
{
  "code": 200,
  "message": "Cart item updated",
  "data": {
    "cartItemKey": "uuid",
    "quantity": 3,
    "subtotal": 4500
  }
}
```

**Error Response** (404 Not Found):
```json
{
  "code": 404,
  "message": "Cart item not found",
  "error": "E-4002",
  "data": null
}
```

**Error Response** (400 Bad Request):
```json
{
  "code": 400,
  "message": "Invalid quantity",
  "error": "E-4003",
  "data": null
}
```

#### 移除購物車商品

**端點**: `DELETE /v2/cart/items/{cartItemKey}`

**Response** (200 OK):
```json
{
  "code": 200,
  "message": "Item removed from cart",
  "data": null
}
```

---

## 6. 測試策略

### 6.1 測試類型分佈

| 測試類型 | 數量 | 負責人 | 說明 |
|----------|------|--------|------|
| Backend UT | 4 | Dev | |
| Backend IT | 4 | Dev/QA | |
| API E2E (Backend) | 8 | QA | 含新增 API-FE-005-003, API-FE-005-004 |
| Frontend UT | 8 | Dev | |
| Frontend IT | 4 | Dev/QA | |
| E2E (Frontend) | 4 | QA | |

### 6.2 測試優先級

| 優先級 | 測試案例 ID | 數量 | 說明 |
|--------|-------------|------|------|
| P0 | API-M17-009-001, API-M17-009-002, API-M17-009-003 | 3 | Admin 更新 toggle |
| P0 | API-M04-FE-001, API-M04-FE-002, API-M04-FE-003, API-M04-FE-004 | 4 | 買家購物車操作 |
| P0 | API-M06-E2E-001, API-M06-E2E-002, API-M06-E2E-003, API-M06-E2E-004, API-M06-E2E-005 | 5 | 建立/取消預訂 |
| P1 | API-FE-005-001, API-FE-005-002, API-FE-005-003, API-FE-005-004 | 4 | StoreOwner 管理功能 |
| P2 | API-M17-ISO-001, API-M17-ISO-002 | 2 | 確保數據隔離 |

---

## 7. Sprint 執行計劃

### 7.1 第一週 (2026-06-10 ~ 2026-06-16)

| 日期 | 重點任務 |
|------|----------|
| Day 1 (06/10) | Sprint Kickoff, FE-M17-005 實作 |
| Day 2 (06/11) | FE-M17-005 IT, US-M17-009 實作 |
| Day 3 (06/12) | US-M17-009 IT, M04 Frontend 規劃 |
| Day 4 (06/13) | M04 Frontend 實作 (路由 + 列表) |
| Day 5 (06/14) | M04 Frontend 實作 (數量更新 + 移除) |
| Day 6-7 | Weekend |

### 7.2 第二週 (2026-06-17 ~ 2026-06-23)

| 日期 | 重點任務 |
|------|----------|
| Day 8 (06/17) | M04 Frontend IT/E2E |
| Day 9 (06/18) | Booking E2E 測試環境準備 |
| Day 10 (06/19) | Booking E2E 執行 (建立/取消) |
| Day 11 (06/20) | Booking E2E 執行 (衝突測試) |
| Day 12 (06/21) | Code Review, Bug Fix |
| Day 13 (06/22) | Sprint Review 準備 |
| Day 14 (06/23) | Sprint Review |

---

## 8. 風險與依賴

### 8.1 風險

| 風險 | 可能性 | 影響 | 緩解措施 |
|------|--------|------|----------|
| Booking E2E 測試環境複雜度 | 中 | 中 | **環境準備**: T-M06-E2E-01 建立 ROOM Listing 測試資料 (1h)；預留 3 SP Buffer 處理環境問題 |
| Feature Toggle 前後端整合 | 低 | 中 | 先完成 Backend API，再串接 Frontend |
| 多租戶資料隔離驗證 | 低 | 高 | 所有 API E2E 測試都需驗證隔離 |
| Redis 連線失敗導致 Cart Service 不可用 | 低 | 高 | **預防措施**: 實作 Redis 連線重試機制 (3 次)；準備 Fallback 回應（Cart 為空）；監控 Redis 健康狀態 |

### 8.2 依賴

| 依賴 | 類型 | 狀態 |
|------|------|------|
| M04 Backend APIs | 基礎設施 | ✅ 已有 (Sprint 4) |
| M06 Backend APIs | 基礎設施 | ✅ 已有 (Sprint 4) |
| FE-M17-003 店鋪詳情頁 | 基礎頁面 | ✅ 已有 (Sprint 3) |
| Redis | 基礎設施 | ✅ 已有 (Sprint 4)；**待驗證可用性** (Redis 可用性驗證) |

---

## 9. Definition of Done (DoD)

| 項目 | 標準 | 狀態 |
|------|------|------|
| Backend 代碼完成 | FE-M17-005 + US-M17-009 + M04 Frontend + Booking E2E | - |
| Code Review | 通過團隊 Code Review | - |
| Backend UT 覆蓋率 | >= 80% (FeatureToggleService, CartService) | - |
| Backend IT 通過 | IT-M17-009-*, IT-M04-FE-*, IT-M06-E2E-* 全部通過 | - |
| API E2E 通過 | API-M17-009-*, API-M04-FE-*, API-M06-E2E-* 全部通過 | - |
| 多租戶隔離驗證 | 買家只能看到自己的 Cart, StoreOwner 只能管理自己的 Feature | - |
| 文檔更新 | API 規格更新 | - |

---

## 10. 相關文件

| 文件 | 路徑 | 說明 |
|------|------|------|
| Sprint 4 計劃 | `docs/04_planning/SPRINT_04_PLAN.md` | Sprint 4 完成狀態 |
| Sprint 4 Review | `docs/05_development/SPRINT_04_REVIEW.md` | Sprint 4 產出與建議 |
| User Stories | `docs/01_requirements/E-Commerce_FRD_v1.0.md` | Phase 1/2 User Stories |
| API 規格 (M17) | `docs/02_architecture/API_M17_Tenant.md` | M17 API 規格 |
| API 規格 (M04) | `docs/02_architecture/API_M04_Cart.md` | M04 API 規格 |
| API 規格 (M06) | `docs/02_architecture/API_M06_Booking.md` | M06 API 規格 |

---

**文件版本**: AISDLC v0.09
**最後更新**: 2026-04-28
**建立人**: Claude Code (AI Assistant)