# Sprint 3 Tasks / Sprint 3 工作分解

> **Sprint 編號**: Sprint 3
> **期間**: 2026-05-13 ~ 2026-05-26 (2 週)
> **總 SP**: 15 SP
> **更新日期**: 2026-04-25

---

## 📋 Task 總覽

| 狀態 | 數量 | SP |
|------|------|-----|
| 🔴 前期準備 | 3 (含8子任務) | 0 |
| 待實現 | 28 | 15 |
| ✅ 已完成 | 0 | 0 |

---

## 🔴 前期準備工作（Sprint 3 開始前必須完成）

### T-PRE-001: 安裝 Shadcn UI
**負責人**: Dev
**預估時間**: 0.5h
**狀態**: ✅ 已完成（2026-04-25）

| 子任務 | 描述 | 預估時間 | 狀態 |
|--------|------|----------|------|
| T-PRE-001-01 | 安裝 Shadcn UI 依賴 | 0.25h | ✅ 已完成 |
| T-PRE-001-02 | 建立 components.json 和 utils.ts | 0.25h | ✅ 已完成 |
| T-PRE-001-03 | 安裝 Button, Card, Input, Label, Select, Badge 元件 | - | ✅ 已完成 |

### T-PRE-002: 統一 API Endpoint
**負責人**: Dev
**預估時間**: 1h
**狀態**: ✅ 已完成（2026-04-25）

| 子任務 | 描述 | 預估時間 | 狀態 |
|--------|------|----------|------|
| T-PRE-002-01 | 確認 Backend `GET /v2/tenants` 即為"我的店鋪列表" | 0.25h | ✅ 已完成 |
| T-PRE-002-02 | 更新 Frontend API_ENDPOINTS 調整為 `/v2/tenants` | 0.25h | ✅ 已完成 |
| T-PRE-002-03 | 驗證 API endpoint 正確性 | 0.5h | ✅ 已完成 |

### T-PRE-003: 補充缺失的驗收標準
**負責人**: Dev
**預估時間**: 0.5h
**狀態**: ✅ 已完成（2026-04-25）

| 子任務 | 描述 | 預估時間 | 狀態 |
|--------|------|----------|------|
| T-PRE-003-01 | 補充 AC-007-4: 非 Admin 角色 403 阻擋 | 0.25h | ✅ 已完成 |
| T-PRE-003-02 | 補充 AC-008-4: 非 Admin 角色 403 阻擋 | 0.25h | ✅ 已完成 |

---

---

## 🔧 Backend Tasks

### US-M17-007: Admin 審核通過店鋪 (5 SP)

| Task ID | 描述 | 預估時間 | 依賴 | 狀態 |
|---------|------|----------|------|------|
| T-M17-007-01 | 實現 POST /admin/tenants/:id/approve 端點 | 2.5h | - | 待實現 |
| T-M17-007-02 | 實現 Tenant 狀態 PENDING → ACTIVE 轉換 | 1.5h | T-M17-007-01 | 待實現 |
| T-M17-007-03 | 實現 Feature Toggle 預設值初始化 | 1.5h | T-M17-007-02 | 待實現 |
| T-M17-007-04 | 整合測試：成功審核通過 | 1.5h | T-M17-007-03 | 待實現 |

**驗收標準**:
- [ ] AC-007-1: 審核通過開店申請（狀態變為 ACTIVE）
- [ ] AC-007-2: 初始化 Feature Toggle（RETAIL_ENABLED, CMS_ENABLED, ERP_ENABLED 預設為 true）
- [ ] AC-007-3: 多次審核回傳錯誤
- [ ] AC-007-4: 非 Admin 角色呼叫 approve API → 403 Forbidden

---

### US-M17-008: Admin 駁回店鋪申請 (2 SP)

| Task ID | 描述 | 預估時間 | 依賴 | 狀態 |
|---------|------|----------|------|------|
| T-M17-008-01 | 實現 POST /admin/tenants/:id/reject 端點 | 1.5h | - | 待實現 |
| T-M17-008-02 | 實現 Reason 欄位驗證和記錄 | 0.5h | T-M17-008-01 | 待實现 |
| T-M17-008-03 | 整合測試：成功駁回 | 1h | T-M17-008-02 | 待實現 |

**驗收標準**:
- [ ] AC-008-1: 駁回開店申請（狀態變為 REJECTED）
- [ ] AC-008-2: 駁回後用戶可重新申請
- [ ] AC-008-3: 多次駁回回傳錯誤
- [ ] AC-008-4: 非 Admin 角色呼叫 reject API → 403 Forbidden

---

## 🎨 Frontend Tasks

### FE-M17-001: 開店申請表單 (3 SP)

| Task ID | 描述 | 預估時間 | 依賴 | 狀態 |
|---------|------|----------|------|------|
| T-FE-001-01 | 更新 API_ENDPOINTS 新增 tenants.apply | 0.5h | - | 待實現 |
| T-FE-001-02 | 建立 `/tenant/apply` 頁面路由 | 0.5h | - | 待實現 |
| T-FE-001-03 | 實作申請表單元件（storeName, businessType, contactEmail, contactPhone） | 1.5h | T-FE-001-02 | 待實現 |
| T-FE-001-04 | 實作表單驗證和錯誤處理 | 1h | T-FE-001-03 | 待實現 |
| T-FE-001-05 | 串接 POST /v2/tenants/apply API | 1h | T-FE-001-04 | 待實現 |
| T-FE-001-06 | 申請成功後顯示成功訊息並引導至 /dashboard/tenants | 0.5h | T-FE-001-05 | 待實現 |

**頁面路由**: `/tenant/apply`
**依賴 Service**: AuthService（已有）

---

### FE-M17-002: 店鋪列表頁面 (2 SP)

| Task ID | 描述 | 預估時間 | 依賴 | 狀態 |
|---------|------|----------|------|------|
| T-FE-002-01 | 更新 API_ENDPOINTS 新增 tenants.myList | 0.5h | - | 待實現 |
| T-FE-002-02 | 建立 `/dashboard/tenants` 頁面路由 | 0.5h | - | 待實現 |
| T-FE-002-03 | 實作店鋪列表元件（卡片/表格） | 1h | T-FE-002-02 | 待實現 |
| T-FE-002-04 | 串接 GET /v2/tenants API | 1h | T-FE-002-03 | 待實現 |
| T-FE-002-05 | 多店鋪切換 UI（X-Tenant-ID Header） | 0.5h | T-FE-002-04 | 待實現 |

**頁面路由**: `/dashboard/tenants`
**依賴**: FE-M17-001, AuthService

---

### FE-M17-003: 店鋪詳情頁面 (2 SP)

| Task ID | 描述 | 預估時間 | 依賴 | 狀態 |
|---------|------|----------|------|------|
| T-FE-003-01 | 更新 API_ENDPOINTS 新增 tenants.detail | 0.5h | - | 待實現 |
| T-FE-003-02 | 建立 `/dashboard/tenants/[id]` 頁面路由 | 0.5h | - | 待實现 |
| T-FE-003-03 | 實作店鋪詳情元件（基本資訊、狀態標識） | 1h | T-FE-003-02 | 待實现 |
| T-FE-003-04 | 串接 GET /v2/tenants/:id API | 1h | T-FE-003-03 | 待實現 |
| T-FE-003-05 | 狀態標識和操作按鈕顯示邏輯（PENDING/ACTIVE/REJECTED） | 0.5h | T-FE-003-04 | 待實現 |
| T-FE-003-06 | 編輯按鈕引導至 /dashboard/tenants/:id/edit | 0.5h | T-FE-003-05 | 待實現 |

**頁面路由**: `/dashboard/tenants/[id]`
**依賴**: FE-M17-002

---

### FE-M17-004: 編輯店鋪頁面 (5 SP)

| Task ID | 描述 | 預估時間 | 依賴 | 狀態 |
|---------|------|----------|------|------|
| T-FE-004-01 | 更新 API_ENDPOINTS 新增 tenants.update | 0.5h | - | 待實現 |
| T-FE-004-02 | 建立 `/dashboard/tenants/[id]/edit` 頁面路由 | 0.5h | - | 待實現 |
| T-FE-004-03 | 實作編輯表單元件（預填當前值） | 2h | T-FE-004-02 | 待實現 |
| T-FE-004-04 | 串接 PUT /v2/tenants/:id API | 1h | T-FE-004-03 | 待實現 |
| T-FE-004-05 | StoreOwner 角色校驗（無權限導向 403） | 1.5h | T-FE-004-04 | 待實現 |
| T-FE-004-06 | 更新成功後引導回店鋪詳情 | 0.5h | T-FE-004-05 | 待實現 |

**頁面路由**: `/dashboard/tenants/[id]/edit`
**依賴**: FE-M17-003

---

## 📊 Task 統計

| Category | Tasks | SP |
|----------|-------|-----|
| Backend | 7 | 7 (US-M17-007: 5, US-M17-008: 2) |
| Frontend | 21 | 12 (FE-M17-001: 3, FE-M17-002: 2, FE-M17-003: 2, FE-M17-004: 5) |
| **Total** | **28** | **19 SP** |

### 時間估算

| Category | 小時 |
|----------|------|
| Backend | 10h (US-M17-007: 7h, US-M17-008: 3h) |
| Frontend | 18h (FE-M17-001: 4h, FE-M17-002: 3h, FE-M17-003: 4h, FE-M17-004: 7h) |
| **Total** | **28h** |

---

## 🔗 依賴關係圖

```
FE-M17-001 (開店申請)
    ↓
FE-M17-002 (店鋪列表) ←→ AuthService
    ↓
FE-M17-003 (店鋪詳情)
    ↓
FE-M17-004 (編輯店鋪)

US-M17-007 (Admin 審核) ←→ US-M17-008 (Admin 駁回)
```

---

## 📝 備註

1. **Frontend 優先順序**: FE-M17-001 → FE-M17-002 → FE-M17-003 → FE-M17-004
2. **Backend 可並行實作**: US-M17-007 和 US-M17-008 可同時實作
3. **API_ENDPOINTS 更新**: 所有 Frontend Task 都依賴 API_ENDPOINTS 更新

---

**最後更新**: 2026-04-25
