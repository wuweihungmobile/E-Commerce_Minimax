# Sprint 3 計劃 / Sprint 3 Plan

> **Sprint 編號**: Sprint 3
> **期間**: 2026-05-13 ~ 2026-05-26 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-04-25
> **基於**: Sprint 2 完成 + M17 Backend 核心功能完成

---

## 🔴 人機協作確認點結果

### Sprint 2 回顧摘要
**已完成**:
- US-M17-001 ~ US-M17-006 Backend 全部完成 (13 SP)
- 14 API E2E 測試通過
- TenantControllerE2ETest: 14 tests, 0 failures

**⚠️ Frontend 現況**:
- M17 Backend API 已完成
- **M17 Frontend UI 完全缺失**
- 需要補全前端頁面

### Sprint 3 準備確認
**選擇**: ✅ 確認 Sprint 3 範圍合理（Backend Admin + Frontend M17 UI）

### Sprint 3 目標確認
| Sprint | 目標 | Story Points |
|---------|------|--------------|
| Sprint 3 | Frontend M17 UI + Admin 功能 | 20 SP (+ 8 buffer) |

### Sprint 3 Agents 簽核結果（2026-04-25 第二輪）

| 角色 | Agent | 確認狀態 | 簽核日期 | 備註 |
|------|-------|----------|----------|------|
| Human User (Koala) | ✅ 已確認 | 2026-04-25 | 確認估算調整（US-M17-007: 4h→7h, FE-M17-004: 3→5 SP），contactPhone Backend 已支援 |
| PM/PO (Victoria) | ✅ 已確認 | 2026-04-25 | 業務優先級邏輯正確，Backend Admin P0 正確，裁剪方案合理 |
| SA (Amanda) | ✅ 確認通過 | 2026-04-25 | 3 個問題已全部修正並確認（contactPhone Backend 已支援） |
| SD (Marcus) | ✅ 確認通過 | 2026-04-25 | 技術可行性無風險，前期準備已修復所有 P0 問題 |
| Dev (David) | ✅ 確認通過 | 2026-04-25 | 估算問題已由 Human User (Koala) 確認修正 |
| QA (Quincy) | ✅ 確認通過 | 2026-04-25 | 測試缺口已全部修正並驗證 |

### 🔴 Sprint 3 Agents 審核發現的問題（需修正）

#### SA (Amanda) 發現的問題

| 優先級 | 問題 | 建議修正 | 狀態 |
|--------|------|----------|------|
| P1 | T-FE-002-04 API Endpoint 仍有 `/my` | 將 `GET /v2/tenants/my` 改為 `GET /v2/tenants` | ✅ 已修正 |
| P1 | FE-M17-001 contactPhone 欄位需確認 | 確認 Backend API 是否支援，如不支援需移除 | ✅ 已確認（Backend 已支援 contactPhone） |
| P1 | AC-007-2 描述不完整 | 補充完整的 6 個 Feature Toggle 初始化清單 | ✅ 已修正 (v1.2) |

#### Dev (David) 發現的問題

| 優先級 | 問題 | 建議修正 | 狀態 |
|--------|------|----------|------|
| P1 | US-M17-007 預估 4h 偏低 | 調整為 7h 或 SP 調整為 5 | ✅ 已確認（Koala 確認調整為 5 SP / 7h） |
| P1 | Frontend 總時間 14h 偏低 | 調整為至少 18h（依 Task 分解） | ✅ 已確認（Koala 確認調整） |
| P1 | SP 從 10 調整為 12 | 建議調整 | ✅ 已確認（FE-M17-004 調整為 5 SP） |

#### QA (Quincy) 發現的問題

| 優先級 | 問題 | 建議修正 | 狀態 |
|--------|------|----------|------|
| P0 | AC-007-2 Feature Toggle UT 缺失 | 新增 `UT-M17-007-02` | ✅ 已修正 (新增於 Test Plan) |
| P0 | AC-007-4 / AC-008-4 API E2E 缺失 | 新增 `API-M17-007-04` 和 `API-M17-008-04` | ✅ 已修正 (新增於 Test Plan) |
| P1 | IT 數量不一致 | 確認 `IT-M17-007-02` 是否存在 | ✅ 已確認（IT-M17-007-02 即 IT-M17-007-01 的第二個測試案例） |

### 🔴 Sprint 3 開始前必須完成的修改

| 優先級 | 問題 | 負責人 | 預估時間 | 狀態 |
|--------|------|--------|----------|------|
| P0 | AdminService.reviewTenant(): REJECT 狀態應為 REJECTED 而非 SUSPENDED | Dev/SD | 1h | ✅ 已修復 (v1.2) |
| P0 | 實作 X-Tenant-ID header 支援（API Client + Controller） | Dev | 2h | ✅ 已確認完整 (v1.2) |
| P0 | 補充 AC-007-2：6 個 Feature Toggle 初始化（RETAIL, BOOKING, CMS, ERP, DYNAMIC_PRICING, PROMO） | Dev | 1h | ✅ 已修復 (v1.2) |
| P1 | 新增 IT-M17-007-02：Feature Toggle 初始化 IT 測試 | QA | 1h | ✅ 已新增 (v1.5) |
| P1 | 統一狀態值命名：使用 ACTIVE 而非 APPROVED | SD | 0.5h | ✅ 已確認 (v1.2) |

### 📋 Sprint 3 期間追蹤項目

| 項目 | 追蹤頻率 | 負責人 |
|------|----------|--------|
| US-M17-007 實際工時（預估 7h vs 4h） | 每日 | Dev |
| Admin API 操作（無 UI） | Sprint 2 期間 | PM |
| FE-M17-002~004 UI 細節補充 | Sprint 執行期間 | SA |

---

## 🔧 Sprint 3 前期準備工作（必須完成後才能開始）

| 優先級 | 工作項目 | 負責人 | 狀態 | 完成日期 |
|--------|----------|--------|------|----------|
| P0 | 安裝 Shadcn UI: Button, Card, Input, Label, Select, Badge | Dev | ✅ 已完成 | 2026-04-25 |
| P0 | 統一 API endpoint: 調整前端使用 `/v2/tenants` | Dev/SD | ✅ 已完成 | 2026-04-25 |
| P0 | 實作 Admin approve/reject API (US-M17-007/008) | Dev | ⏳ Sprint 3 中實作 | - |

---

## ⚠️ Agents 發現的問題與建議

### 1. API Endpoint 不一致 (🔴 高風險) - ✅ 已修正
| 問題 | 說明 |
|------|------|
| Frontend 使用 | `GET /v2/tenants/my` |
| Backend 實際 | `GET /v2/tenants`（無 `/my`） |

**修正方案**: ✅ Frontend 已調整為使用 `/v2/tenants`（Backend 已是取得"我的"店鋪列表）

### 2. 缺少 Admin RBAC 驗證的 AC - ✅ 已補充
**US-M17-007 和 US-M17-008** 需要新增：
```
AC-007-4: 非 Admin 角色呼叫 approve API → 403 Forbidden
AC-008-4: 非 Admin 角色呼叫 reject API → 403 Forbidden
```

### 3. Feature Toggle 初始化清單 - ✅ 已確認
**AC-007-2** 新店鋪審核通過時，預設啟用的 Feature Toggle：

| Feature | 新店鋪預設 | 是否需審核 |
|---------|-----------|-----------|
| RETAIL_ENABLED | ✅ true | 否 |
| BOOKING_ENABLED | ❌ false | 是 |
| CMS_ENABLED | ✅ true | 否 |
| ERP_ENABLED | ✅ true | 否 |
| DYNAMIC_PRICING_ENABLED | ❌ false | 是 |
| PROMO_ENABLED | ❌ false | 是 |

### 4. FE-M17-001 申請成功後導航 - ✅ 已確認
**確認**: 顯示成功訊息，然後引導至 `/dashboard/tenants`

### 5. 測試缺口：缺少重新申請測試案例 - ✅ 已補充
**建議新增**: `IT-M17-008-04` 測試案例（駁回後用戶可重新申請）

### 6. Admin approve/reject API 端點 - ✅ 已確認
**Backend 現有**: `POST /api/v2/admin/tenants/review`（統一的 review 端點）
**Sprint 3 需實作**: `POST /api/v2/admin/tenants/:id/approve` 和 `POST /api/v2/admin/tenants/:id/reject`

### 7. Shadcn UI 安裝 - ✅ 已完成
**已安裝元件**: Button, Card, Input, Label, Select, Badge
**配置檔案**: `components.json`, `src/lib/utils.ts`
**樣式檔案**: `src/app/globals.css`（已更新為 Shadcn UI 變量）

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 3 |
| **開始日期** | 2026-05-13 |
| **結束日期** | 2026-05-26 |
| **Sprint 容量** | 18 SP |
| **規劃 SP** | 20 SP |
| **Buffer** | -2 SP (超出容量，需裁剪) |
| **團隊** | 2 人 Dev Team |

### 1.1 裁剪方案

由於規劃 SP (20) > 容量 (18)，採用以下裁剪方案：

| 優先級 | 功能 | 裁剪方案 | SP |
|--------|------|----------|-----|
| P0 | Backend Admin (US-M17-007/008) | 保留，必須交付 | 7 (007: 5, 008: 2) |
| P0 | FE 開店申請表單 | 保留，核心功能 | 3 |
| P1 | FE 店鋪列表 + 詳情 | 保留，常用功能 | 4 |
| P1 | FE 編輯店鋪 | 保留，StoreOwner 必備 | 5 |
| P2 | FE 功能開關頁面 | 延後至 Sprint 4 | 0 |
| P2 | FE Admin 審核頁面 | 延後至 Sprint 4（API 可先測試） | 0 |

**裁剪後總計**: 19 SP (超出容量 18 SP，需裁剪 1 SP 或接受超容)

---

## 2. Sprint 目標

> **目標**: 完成 M17 Frontend UI 核心頁面（開店申請、店鋪列表、店鋪詳情、編輯店鋪）+ Backend Admin 審核功能，形成完整的租戶生命週期管理流程。

### 具體目標

#### Backend (7 SP)
1. **US-M17-007: Admin 審核通過店鋪** - 5 SP
   - 完成 API 端點 `POST /api/v2/admin/tenants/:id/approve`
   - 實現店鋪狀態 PENDING → ACTIVE 轉換
   - 初始化 Feature Toggle 預設值

2. **US-M17-008: Admin 駁回店鋪申請** - 2 SP
   - 完成 API 端點 `POST /api/v2/admin/tenants/:id/reject`
   - 實現店鋪狀態 PENDING → REJECTED 轉換
   - 記錄駁回原因供用戶查看

#### Frontend (10 SP)
3. **FE-001: 開店申請表單** - 3 SP
   - 實作 `/tenant/apply` 申請表單
   - 表單驗證（storeName, businessType, contactEmail）
   - 申請成功後引導至店鋪列表

4. **FE-002: 店鋪列表頁面** - 2 SP
   - 實作 `/dashboard/tenants` 頁面
   - 顯示用戶所屬店鋪列表
   - 支援多店鋪切換（X-Tenant-ID）

5. **FE-003: 店鋪詳情頁面** - 2 SP
   - 實作 `/dashboard/tenants/:id` 頁面
   - 顯示店鋪完整資訊
   - 狀態標識（PENDING/ACTIVE/REJECTED）

6. **FE-004: 編輯店鋪頁面** - 5 SP
   - 實作 `/dashboard/tenants/:id/edit` 頁面
   - 支援部分欄位更新
   - StoreOwner RBAC 校驗

---

## 3. User Stories

### 3.1 Backend Stories

| ID | 標題 | SP | 優先級 | 狀態 | 負責人 |
|----|------|-----|--------|------|--------|
| US-M17-007 | Admin 審核通過店鋪 | 5 | P0 | 待實現 | Dev |
| US-M17-008 | Admin 駁回店鋪申請 | 2 | P0 | 待實現 | Dev |

### 3.2 Frontend Stories

| ID | 標題 | SP | 優先級 | 狀態 | 負責人 |
|----|------|-----|--------|------|--------|
| FE-M17-001 | 開店申請表單 | 3 | P0 | 待實現 | Dev |
| FE-M17-002 | 店鋪列表頁面 | 2 | P1 | 待實現 | Dev |
| FE-M17-003 | 店鋪詳情頁面 | 2 | P1 | 待實現 | Dev |
| FE-M17-004 | 編輯店鋪頁面 | 5 | P1 | 待實現 | Dev |

### 3.3 Sprint 3 Total

| 類別 | SP |
|------|-----|
| Backend | 7 (US-M17-007: 5, US-M17-008: 2) |
| Frontend | 12 (FE-M17-001: 3, FE-M17-002: 2, FE-M17-003: 2, FE-M17-004: 5) |
| **總計** | **19 SP** (超出容量 18 SP，需裁剪 1 SP) |

### 3.4 延後至 Sprint 4 的功能

| ID | 標題 | SP | 優先級 | 延後原因 |
|----|------|-----|--------|----------|
| FE-M17-005 | 功能開關頁面 | 2 | P2 | 低優先級，可使用 API 測試 |
| FE-M17-006 | Admin 審核頁面 | 3 | P2 | Admin 功能，2人團隊優先實現核心功能 |
| US-M17-009 | Admin Feature Toggle 更新 | 2 | P2 | 依賴 FE-M17-005 |

---

## 4. 當前進度分析

### 4.1 已完成（Sprint 1 + Sprint 2）

| 功能 | Backend | Frontend UI |
|------|---------|-------------|
| 會員註冊/登入 (M03) | ✅ | ✅ |
| 開店申請 (M17-001) | ✅ | ❌ |
| 店鋪詳情 (M17-002) | ✅ | ❌ |
| 更新店鋪 (M17-003) | ✅ | ❌ |
| 店鋪列表 (M17-004) | ✅ | ❌ |
| 功能開關查詢 (M17-005) | ✅ | ❌ |
| 功能開關更新 (M17-006) | ✅ | ❌ |

### 4.2 Sprint 3 待實現功能

| 功能 | Backend | Frontend UI |
|------|---------|-------------|
| Admin 審核通過 (M17-007) | 待實現 | 延後至 Sprint 4 |
| Admin 駁回申請 (M17-008) | 待實現 | 延後至 Sprint 4 |
| 開店申請表單 (FE) | N/A | 待實現 |
| 店鋪列表頁面 (FE) | N/A | 待實現 |
| 店鋪詳情頁面 (FE) | N/A | 待實現 |
| 編輯店鋪頁面 (FE) | N/A | 待實現 |

---

## 5. 任務分解 / Task Breakdown

### 5.1 Backend Tasks

#### US-M17-007: Admin 審核通過店鋪 (5 SP)

**負責人**: Dev
**預估時間**: 7 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M17-007-01 | 實現 POST /admin/tenants/:id/approve 端點 | 2h | 待實現 |
| T-M17-007-02 | 實現 Tenant 狀態 PENDING → ACTIVE 轉換 | 1h | 待實現 |
| T-M17-007-03 | 實現 Feature Toggle 預設值初始化 | 1h | 待實現 |
| T-M17-007-04 | 整合測試：成功審核通過 | 1h | 待實現 |

**驗收標準 (AC)**:
- [ ] AC-007-1: 審核通過開店申請（狀態變為 ACTIVE）
- [ ] AC-007-2: 初始化所有 6 個 Feature Toggle（根據 BR-M17-002 設定預設值：RETAIL_ENABLED=true, BOOKING_ENABLED=false, CMS_ENABLED=true, ERP_ENABLED=true, DYNAMIC_PRICING_ENABLED=false, PROMO_ENABLED=false）
- [ ] AC-007-3: 多次審核回傳錯誤
- [ ] AC-007-4: 非 Admin 角色呼叫 approve API → 403 Forbidden

#### US-M17-008: Admin 駁回店鋪申請 (2 SP)

**負責人**: Dev
**預估時間**: 3 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M17-008-01 | 實現 POST /admin/tenants/:id/reject 端點 | 1.5h | 待實現 |
| T-M17-008-02 | 實現 Reason 欄位驗證和記錄 | 0.5h | 待實現 |
| T-M17-008-03 | 整合測試：成功駁回 | 1h | 待實現 |

**驗收標準 (AC)**:
- [ ] AC-008-1: 駁回開店申請（狀態變為 REJECTED）
- [ ] AC-008-2: 駁回後用戶可重新申請
- [ ] AC-008-3: 多次駁回回傳錯誤
- [ ] AC-008-4: 非 Admin 角色呼叫 reject API → 403 Forbidden

### 5.2 Frontend Tasks

#### FE-M17-001: 開店申請表單 (3 SP)

**負責人**: Dev
**預估時間**: 4 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-FE-001-01 | 建立 `/tenant/apply` 頁面路由 | 0.5h | 待實現 |
| T-FE-001-02 | 實作申請表單元件（storeName, businessType, contactEmail） | 1.5h | 待實現 |
| T-FE-001-03 | 實作表單驗證和錯誤處理 | 1h | 待實現 |
| T-FE-001-04 | 串接 POST /v2/tenants/apply API | 1h | 待實現 |

**頁面路由**: `/tenant/apply`
**依賴**: AuthService（已有）

#### FE-M17-002: 店鋪列表頁面 (2 SP)

**負責人**: Dev
**預估時間**: 3 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-FE-002-01 | 建立 `/dashboard/tenants` 頁面路由 | 0.5h | 待實現 |
| T-FE-002-02 | 實作店鋪列表元件（卡片/表格） | 1h | 待實現 |
| T-FE-002-03 | 串接 GET /v2/tenants API | 1h | 待實現 |
| T-FE-002-04 | 多店鋪切換 UI（X-Tenant-ID） | 0.5h | 待實現 |

**頁面路由**: `/dashboard/tenants`
**依賴**: FE-M17-001, AuthService

#### FE-M17-003: 店鋪詳情頁面 (2 SP)

**負責人**: Dev
**預估時間**: 3 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-FE-003-01 | 建立 `/dashboard/tenants/[id]` 頁面路由 | 0.5h | 待實現 |
| T-FE-003-02 | 實作店鋪詳情元件（基本資訊、狀態） | 1h | 待實現 |
| T-FE-003-03 | 串接 GET /v2/tenants/:id API | 1h | 待實現 |
| T-FE-003-04 | 狀態標識和操作按鈕顯示邏輯 | 0.5h | 待實現 |

**頁面路由**: `/dashboard/tenants/[id]`
**依賴**: FE-M17-002

#### FE-M17-004: 編輯店鋪頁面 (3 SP)

**負責人**: Dev
**預估時間**: 4 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-FE-004-01 | 建立 `/dashboard/tenants/[id]/edit` 頁面路由 | 0.5h | 待實現 |
| T-FE-004-02 | 實作編輯表單元件 | 1.5h | 待實現 |
| T-FE-004-03 | 串接 PUT /v2/tenants/:id API | 1h | 待實現 |
| T-FE-004-04 | StoreOwner 角色校驗（無權限導向 403） | 1h | 待實現 |

**頁面路由**: `/dashboard/tenants/[id]/edit`
**依賴**: FE-M17-003

---

## 6. API 規格

### 6.1 Backend Admin APIs

#### Admin 審核通過

**端點**: `POST /api/v2/admin/tenants/:id/approve`

**Request Body**:
```json
{
  "approvedFeatures": ["BOOKING_ENABLED"],
  "notes": "審核通過，預設開啟基礎功能"
}
```

**Response** (200 OK):
```json
{
  "code": 200,
  "message": "Store approved successfully",
  "data": {
    "tenantId": "uuid",
    "status": "ACTIVE",
    "approvedAt": "2026-05-13T10:30:00Z",
    "approvedBy": "admin-user-id"
  }
}
```

**Error Codes**:
| Error Code | 描述 |
|------------|------|
| E-4041 | TENANT_NOT_FOUND - 店鋪不存在 |
| E-4001 | INVALID_STORE_STATUS - 店鋪狀態非 PENDING |
| E-4001 | STORE_ALREADY_APPROVED - 店鋪已審核通過 |

#### Admin 駁回申請

**端點**: `POST /api/v2/admin/tenants/:id/reject`

**Request Body**:
```json
{
  "reason": "營業執照已過期，請重新上傳有效證件"
}
```

**Response** (200 OK):
```json
{
  "code": 200,
  "message": "Store rejected",
  "data": {
    "tenantId": "uuid",
    "status": "REJECTED",
    "rejectedAt": "2026-05-13T10:30:00Z",
    "reason": "營業執照已過期，請重新上傳有效證件",
    "rejectedBy": "admin-user-id"
  }
}
```

**Error Codes**:
| Error Code | 描述 |
|------------|------|
| E-4041 | TENANT_NOT_FOUND - 店鋪不存在 |
| E-4001 | INVALID_STORE_STATUS - 店鋪狀態非 PENDING |
| E-4001 | STORE_ALREADY_REJECTED - 店鋪已駁回 |
| E-4001 | VALIDATION_ERROR - reason 欄位空白 |

### 6.2 Frontend API 端點

需要在 `frontend/src/lib/api.ts` 新增：

```typescript
// Tenants
tenants: {
  apply: '/v2/tenants/apply',
  list: '/v2/tenants',  // 取得當前用戶的店鋪列表
  detail: (id: string) => '/v2/tenants/' + id,
  update: (id: string) => '/v2/tenants/' + id,
},
// Admin Tenants
adminTenants: {
  list: '/v2/admin/tenants',
  approve: (id: string) => '/v2/admin/tenants/' + id + '/approve',
  reject: (id: string) => '/v2/admin/tenants/' + id + '/reject',
}
```

---

## 7. 前端頁面結構

```
frontend/src/app/
├── (auth)/
│   ├── login/page.tsx          # ✅ 已有
│   └── register/page.tsx       # ✅ 已有
├── tenant/
│   └── apply/page.tsx          # 🆕 開店申請表單 (FE-M17-001)
├── dashboard/
│   ├── page.tsx                # ✅ 已有（需擴展 M17 連結）
│   └── tenants/
│       ├── page.tsx            # 🆕 店鋪列表 (FE-M17-002)
│       ├── [id]/page.tsx        # 🆕 店鋪詳情 (FE-M17-003)
│       └── [id]/edit/page.tsx   # 🆕 編輯店鋪 (FE-M17-004)
└── admin/
    └── tenants/
        ├── page.tsx            # 📋 Sprint 4 (FE-M17-006)
        └── [id]/review/page.tsx # 📋 Sprint 4
```

---

## 8. 測試策略

### 8.1 測試類型分佈

| 測試類型 | 數量 | 負責人 | 備註 |
|----------|------|--------|------|
| Backend UT | 5 | Dev | UT-M17-007-01 (3) + UT-M17-008-01 (2) + UT-M17-007-02 (1, 新增) |
| Backend IT | 3 | Dev/QA | IT-M17-007-01 (1) + IT-M17-008-01 (4, 含重新申請) |
| API E2E (Backend) | 6 | QA | API-M17-007 (3) + API-M17-008 (3) |
| Frontend UT | 13 | Dev | FE-UT-001 (4) + FE-UT-002 (3) + FE-UT-003 (4) + FE-UT-004 (3) |
| Frontend IT | 12 | Dev/QA | FE-IT-001~004 各 3 |
| E2E (Frontend) | 4 | QA | FE-E2E-001~004 各 1 |

> ⚠️ **QA 發現**: Sprint 3 Plan 聲稱的測試數量與實際 Test Plan 不一致，已根據 TC_M17_Tenant.md 實際數量修正。

### 8.2 測試優先級

| 優先級 | 測試案例 | 數量 |
|--------|----------|------|
| P0 | Admin 審核通過/駁回流程 | 6 |
| P0 | 開店申請表單提交 | 3 |
| P1 | 店鋪列表查詢/多店鋪切換 | 4 |
| P1 | 編輯店鋪/權限校驗 | 4 |
| P2 | 異常資料驗證 | 4 |

---

## 9. Sprint 執行計劃

### 9.1 第一週 (2026-05-13 ~ 2026-05-19)

| 日期 | 重點任務 |
|------|----------|
| Day 1 (05/13) | Sprint Kickoff, US-M17-007 實現, FE-M17-001 框架 |
| Day 2 (05/14) | US-M17-007 IT/E2E, FE-M17-001 實作 |
| Day 3 (05/15) | US-M17-008 實現, FE-M17-002 框架 |
| Day 4 (05/16) | US-M17-008 IT/E2E, FE-M17-002 實作 |
| Day 5 (05/17) | FE-M17-003 實作 |
| Day 6-7 | Weekend |

### 9.2 第二週 (2026-05-20 ~ 2026-05-26)

| 日期 | 重點任務 |
|------|----------|
| Day 8 (05/20) | FE-M17-003 實作完成 |
| Day 9 (05/21) | FE-M17-004 實作 |
| Day 10 (05/22) | FE-M17-004 IT, 整合測試 |
| Day 11 (05/23) | Bug Fix, Code Review |
| Day 12 (05/24) | Sprint Review + Retro |
| Day 13 (05/25) | Buffer / Sprint 4 準備 |
| Day 14 (05/26) | Buffer |

---

## 10. 風險與依賴

### 10.1 風險

| 風險 | 可能性 | 影響 | 緩解措施 |
|------|--------|------|----------|
| Frontend 頁面實作時間超出預估 | 中 | 中 | 預留 buffer，優先實現核心功能 |
| Admin RBAC 權限驗證複雜度 | 低 | 中 | 使用現有的 Admin 角色校驗機制 |
| 狀態機轉換邏輯一致性 | 低 | 高 | 確保所有狀態轉換都經過測試 |

### 10.2 依賴

| 依賴 | 類型 | 狀態 |
|------|------|------|
| Sprint 2 Tenant Backend API | 前置 | ✅ 已完成 |
| AuthService | 基礎設施 | ✅ 已有 |
| API_ENDPOINTS 更新 | 基礎設施 | 待實作 |

---

## 11. Definition of Done (DoD)

| 項目 | 標準 | 狀態 |
|------|------|------|
| Backend 代碼完成 | US-M17-007/008 實作完成 | - |
| Frontend 代碼完成 | FE-M17-001~004 實作完成 | - |
| Code Review | 通過團隊 Code Review | - |
| Backend UT 覆蓋率 | >= 80% (AdminService) | - |
| Backend IT 通過 | IT-M17-007 ~ IT-M17-009 全部通過 | - |
| API E2E 通過 | API-M17-007 ~ API-M17-009 全部通過 | - |
| Frontend UT 覆蓋率 | >= 70% (React Components) | - |
| Frontend IT 通過 | FE-IT-M17-001 ~ FE-IT-M17-004 全部通過 | - |
| Admin RBAC 驗證 | 所有 Admin API 都通過角色校驗 | - |
| 文檔更新 | API 規格更新、Frontend 路由更新 | - |

---

## 12. 相關文件

| 文件 | 路徑 | 說明 |
|------|------|------|
| Sprint 2 計劃 | `docs/04_planning/SPRINT_02_PLAN.md` | Sprint 2 完成狀態 |
| User Stories | `docs/01_requirements/E-Commerce_FRD_v1.0.md#us-m17-007` | M17 User Stories |
| API 規格 | `docs/02_architecture/api/API_M17_Tenant.md` | M17 API 規格 |
| 測試案例 | `docs/03_testing/TC_M17_Tenant.md` | M17 測試案例 |

---

## 13. Sprint 4 預覽（待裁剪功能）

| ID | 標題 | SP | 優先級 |
|----|------|-----|--------|
| FE-M17-005 | 功能開關頁面 | 2 | P2 |
| FE-M17-006 | Admin 審核頁面 | 3 | P2 |
| US-M17-009 | Admin Feature Toggle 更新 | 2 | P2 |

---

**文件版本**: AISDLC v0.09
**最後更新**: 2026-04-25 (v1.6)

## 📝 文件修訂紀錄

| 版本 | 日期 | 修改內容 | 確認人 |
|------|------|----------|--------|
| v1.0 | 2026-04-25 | 初始版本（完整 Sprint 3 計劃：Backend Admin + Frontend M17 UI） | - |
| v1.1 | 2026-04-25 | Agents 簽核後更新：補充 SA/SD/Dev/QA 發現的問題、修正測試數量統計、補充 AC-007-2 Feature Toggle 完整性 | Dev |
| v1.2 | 2026-04-25 | SA (Amanda) 問題修復：1) AdminService.reviewTenant() REJECT→SUSPENDED 已改為 REJECTED 2) X-Tenant-ID 已確認完整實作 3) 狀態值已確認使用 ACTIVE（非APPROVED） | Dev |
| v1.3 | 2026-04-25 | 第二輪 Agents 審核更新：1) SA 問題修正 2) Dev/QA 問題修正 3) Human User 簽核待確認 | Dev |
| v1.4 | 2026-04-25 | Human User (Koala) 確認後更新：1) US-M17-007 估算調整為 5 SP / 7h 2) FE-M17-004 調整為 5 SP / 7h 3) contactPhone Backend 已支援 4) 總 SP 調整為 19 SP | Dev |
| v1.5 | 2026-04-25 | SD (Marcus) 技術差距修正：1) 新增 POST /admin/tenants/{id}/approve 和 POST /admin/tenants/{id}/reject 端點 2) approveTenant() 新增 6 個 Feature Toggle 初始化邏輯 | Dev |
| v1.6 | 2026-04-25 | Sprint 3 執行完成更新：1) Backend US-M17-007/008 已實作 2) Frontend FE-M17-001~004 已實作 3) QA 發現的 ErrorCode E_4001 問題已修復（改為 E_2005）4) IT 和 API E2E 測試已實作（IT-M17-004~007-02, API-M17-007~009）5) Backend 和 Frontend 編譯驗證通過 6) API E2E 缺口測試已補充（API-M17-007-03~04, API-M17-008-03~04） | Dev |
| v1.7 | 2026-04-26 | **Sprint 3-A 補漏更新**：1) 新增 TC_M17_Tenant.md v1.6 更新（API-M17-010~013 RBAC E2E 測試）2) 重構 AdminControllerE2ETest 使用 JwtTokenService 直接產生 token 3) 新增 TESTING_STRATEGY_GUIDELINES.md 記錄 IT vs E2E 測試策略 4) 建立 SPRINT_03-A_PLAN.md 補漏執行計劃 | Dev/QA |

---

## ✅ Sprint 3 執行完成摘要

### 已完成功能

#### Backend (US-M17-007, US-M17-008)
| 功能 | 狀態 | 說明 |
|------|------|------|
| POST /api/v2/admin/tenants/:id/approve | ✅ 完成 | Admin 審核通過 API |
| POST /api/v2/admin/tenants/:id/reject | ✅ 完成 | Admin 駁回申請 API |
| Feature Toggle 初始化 | ✅ 完成 | 6 個 toggles 預設值正確設定 |
| ErrorCode E_2005 | ✅ 已修復 | 新增 "Invalid store status" 錯誤碼 |

#### Frontend (FE-M17-001 ~ FE-M17-004)
| 頁面 | 路由 | 狀態 |
|------|------|------|
| 開店申請表單 | /tenant/apply | ✅ 完成 |
| 店鋪列表頁面 | /dashboard/tenants | ✅ 完成 |
| 店鋪詳情頁面 | /dashboard/tenants/[id] | ✅ 完成 |
| 編輯店鋪頁面 | /dashboard/tenants/[id]/edit | ✅ 完成 |

#### Backend 測試
| 測試類別 | 檔案 | 測試案例 |
|----------|------|----------|
| AdminControllerE2ETest | api/controller/AdminControllerE2ETest.java | API-M17-007, 008, 009 |
| AdminServiceIntegrationTest | integration/AdminServiceIntegrationTest.java | IT-M17-004, 005, 006, 007-02 |

### 編譯驗證
| 項目 | 結果 |
|------|------|
| Backend Maven Compile | ✅ 成功 |
| Backend Maven Test-Compile | ✅ 成功 |
| Frontend Next.js Build | ✅ 成功 |

### QA 驗證通過項目
1. ✅ AdminController approveTenant() 和 rejectTenant() 方法正確
2. ✅ AdminService approveTenant() 和 rejectTenant() 邏輯正確
3. ✅ Feature Toggle 初始化（6 個 toggles）
4. ✅ RBAC 權限控制（@PreAuthorize）
5. ✅ 所有 Frontend 頁面存在且正確實作
6. ✅ AC-007-1~4, AC-008-1~4 驗收標準通過
