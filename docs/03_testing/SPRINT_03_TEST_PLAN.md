# Sprint 3 測試計劃 / Sprint 3 Test Plan

> **Sprint 編號**: Sprint 3
> **期間**: 2026-05-13 ~ 2026-05-26 (2 週)
> **測試經理**: QA (Quincy)
> **更新日期**: 2026-04-25

---

## 1. 測試範圍

### 1.1 Backend APIs

| API | 方法 | 端點 | 負責人 |
|-----|------|------|--------|
| Admin Approve Tenant | POST | /v2/admin/tenants/:id/approve | Dev/QA |
| Admin Reject Tenant | POST | /v2/admin/tenants/:id/reject | Dev/QA |

### 1.2 Frontend Pages

| 頁面 | 路由 | 功能 | 負責人 |
|------|------|------|--------|
| 開店申請表單 | /tenant/apply | 申請表單提交 | Dev/QA |
| 店鋪列表 | /dashboard/tenants | 店鋪列表查詢 | Dev/QA |
| 店鋪詳情 | /dashboard/tenants/[id] | 店鋪資訊顯示 | Dev/QA |
| 編輯店鋪 | /dashboard/tenants/[id]/edit | 店鋪資訊更新 | Dev/QA |

---

## 2. 測試類型分佈

| 測試類型 | 數量 | 負責人 | 總時間 |
|----------|------|--------|--------|
| Backend 單元測試 (UT) | 8 | Dev | 3h |
| Backend 整合測試 (IT) | 4 | Dev/QA | 4h |
| Backend API E2E 測試 | 11 | QA | 5h |
| Frontend 單元測試 (UT) | 6 | Dev | 3h |
| Frontend 整合測試 (IT) | 4 | Dev/QA | 3h |
| Frontend E2E 測試 | 4 | QA | 4h |
| **總計** | **37** | - | **22h** |

> ✅ QA (Quincy) 已更新：同步 TC_M17_Tenant.md v1.6 的 37 項測試案例
> ✅ 新增 UT-M17-007-02 (Feature Toggle 初始化)
> ✅ 新增 API-M17-007-03/04, API-M17-008-03/04 (非PENDING/非Admin)
> ✅ 新增 API-M17-010~013 (StoreOwner/STORE_STAFF RBAC E2E)

---

## 3. Backend 測試案例

### 3.1 US-M17-007: Admin 審核通過店鋪

#### UT-M17-007-01: AdminService.approveTenant 單元測試

| 測試案例 | 輸入 | 預期結果 | 狀態 |
|---------|------|----------|------|
| 正常審核通過 | PENDING tenant, valid admin | status = ACTIVE, approvedAt set | 待實現 |
| 非 PENDING 狀態 | ACTIVE tenant | 拋出 InvalidStoreStatusException | 待實現 |
| 多次審核 | 第二次審核已通過的 tenant | 拋出 StoreAlreadyApprovedException | 待實現 |

#### IT-M17-007-01: Admin Approve 整合測試

| 測試案例 | 描述 | 預期結果 | 狀態 |
|---------|------|----------|------|
| IT-M17-007-01 | Admin 審核 PENDING 店鋪 | 200, status=ACTIVE | 待實現 |
| IT-M17-007-02 | 非 Admin 角色審核 | 403 Forbidden | 待實現 |
| IT-M17-007-03 | 審核不存在的店鋪 | 404 TENANT_NOT_FOUND | 待實現 |

#### UT-M17-007-02: AdminService.approveTenant Feature Toggle 初始化測試（QA 新增）

| 測試案例 | 輸入 | 預期結果 | 狀態 |
|---------|------|----------|------|
| 正常審核通過，驗證 Feature Toggle 初始化 | PENDING tenant, valid admin | 6 個 Feature Toggle 正確初始化：RETAIL=true, BOOKING=false, CMS=true, ERP=true, DYNAMIC_PRICING=false, PROMO=false | 待實現 |

#### API-M17-007: API E2E 測試

| 測試案例 ID | 描述 | API | 預期結果 | 狀態 |
|-------------|------|-----|----------|------|
| API-M17-007-01 | Admin 審核通過店鋪 | POST /admin/tenants/:id/approve | 200, status=ACTIVE | 待實現 |
| API-M17-007-02 | 審核後 Feature Toggle 初始化 | POST /admin/tenants/:id/approve | RETAIL_ENABLED=true | 待實現 |
| API-M17-007-03 | 非 PENDING 狀態審核 | POST /admin/tenants/:id/approve | 400 INVALID_STORE_STATUS | 待實現 |
| API-M17-007-04 | 非 Admin 角色審核 | POST /admin/tenants/:id/approve | 403 Forbidden | 待實現 |

---

### 3.2 US-M17-008: Admin 駁回店鋪申請

#### UT-M17-008-01: AdminService.rejectTenant 單元測試

| 測試案例 | 輸入 | 預期結果 | 狀態 |
|---------|------|----------|------|
| 正常駁回 | PENDING tenant, reason | status = REJECTED, reason set | 待實現 |
| 空白 reason | PENDING tenant, empty reason | 拋出 ValidationException | 待實现 |
| 非 PENDING 狀態 | ACTIVE tenant | 拋出 InvalidStoreStatusException | 待實現 |

#### IT-M17-008-01: Admin Reject 整合測試

| 測試案例 | 描述 | 預期結果 | 狀態 |
|---------|------|----------|------|
| IT-M17-008-01 | Admin 駁回 PENDING 店鋪 | 200, status=REJECTED | 待實現 |
| IT-M17-008-02 | 空白 reason 欄位 | 400 VALIDATION_ERROR | 待實現 |
| IT-M17-008-03 | 非 Admin 角色駁回 | 403 Forbidden | 待實現 |
| IT-M17-008-04 | 駁回後用戶可重新申請 | FE-M17-001 申請成功，status=PENDING | 待實現 |

#### API-M17-008: API E2E 測試

| 測試案例 ID | 描述 | API | 預期結果 | 狀態 |
|-------------|------|-----|----------|------|
| API-M17-008-01 | Admin 駁回店鋪 | POST /admin/tenants/:id/reject | 200, status=REJECTED | 待實現 |
| API-M17-008-02 | 駁回後 reason 可供查看 | GET /tenants/:id | reason 欄位有值 | 待實現 |
| API-M17-008-03 | 空白 reason 拒絕 | POST /admin/tenants/:id/reject | 400 VALIDATION_ERROR | 待實現 |
| API-M17-008-04 | 非 Admin 角色駁回 | POST /admin/tenants/:id/reject | 403 Forbidden | 待實現 |

---

## 4. Frontend 測試案例

### 4.1 FE-M17-001: 開店申請表單

#### FE-UT-001: 表單元件單元測試

| 測試案例 | 描述 | 預期結果 | 狀態 |
|---------|------|----------|------|
| FE-UT-001-01 | 必填欄位驗證 - storeName 空白 | 顯示錯誤訊息 | 待實現 |
| FE-UT-001-02 | 必填欄位驗證 - businessType 未選 | 顯示錯誤訊息 | 待實現 |
| FE-UT-001-03 | Email 格式驗證 - 無效格式 | 顯示錯誤訊息 | 待實現 |
| FE-UT-001-04 | 表單提交 loading 狀態 | button 顯示 loading, disabled | 待實現 |

#### FE-IT-001: 開店申請整合測試

| 測試案例 | 描述 | 預期結果 | 狀態 |
|---------|------|----------|------|
| FE-IT-001-01 | 成功申請後引導至店鋪列表 | router.push('/dashboard/tenants') | 待實現 |
| FE-IT-001-02 | 申請失敗顯示錯誤訊息 | 顯示 API error message | 待實現 |
| FE-IT-001-03 | 已申請用戶再次申請 | 顯示 "已有進行中的申請" 錯誤 | 待實現 |

#### FE-E2E-001: 開店申請 E2E 測試

| 測試案例 ID | 描述 | 步驟 | 預期結果 | 狀態 |
|-------------|------|------|----------|------|
| FE-E2E-001-01 | 完整開店申請流程 | 填寫表單 → 提交 → 檢查店鋪列表 | 新店鋪出現 | 待實現 |

---

### 4.2 FE-M17-002: 店鋪列表頁面

#### FE-UT-002: 店鋪列表元件單元測試

| 測試案例 | 描述 | 預期結果 | 狀態 |
|---------|------|----------|------|
| FE-UT-002-01 | 無店鋪時顯示空狀態 | 顯示 "尚無店鋪" 訊息 | 待實現 |
| FE-UT-002-02 | 有店鋪時顯示店鋪卡片 | 顯示店鋪名稱、狀態 | 待實現 |
| FE-UT-002-03 | 多店鋪時顯示切換器 | 顯示 X-Tenant-ID 切換 | 待實現 |

#### FE-IT-002: 店鋪列表整合測試

| 測試案例 | 描述 | 預期結果 | 狀態 |
|---------|------|----------|------|
| FE-IT-002-01 | 成功載入店鋪列表 | 顯示店鋪資料 | 待實現 |
| FE-IT-002-02 | 點擊店鋪卡片進入詳情 | router.push('/dashboard/tenants/:id') | 待實現 |
| FE-IT-002-03 | API 失敗顯示錯誤 | 顯示錯誤訊息 | 待實现 |

#### FE-E2E-002: 店鋪列表 E2E 測試

| 測試案例 ID | 描述 | 步驟 | 預期結果 | 狀態 |
|-------------|------|------|----------|------|
| FE-E2E-002-01 | 查看店鋪列表流程 | 登入 → 進入店鋪列表 → 點擊店鋪 | 進入詳情頁 | 待實現 |

---

### 4.3 FE-M17-003: 店鋪詳情頁面

#### FE-UT-003: 店鋪詳情元件單元測試

| 測試案例 | 描述 | 預期結果 | 狀態 |
|---------|------|----------|------|
| FE-UT-003-01 | 顯示店鋪基本資訊 | storeName, description 等 | 待實現 |
| FE-UT-003-02 | PENDING 狀態顯示特定標識 | 黃色 "審核中" 標籤 | 待實現 |
| FE-UT-003-03 | ACTIVE 狀態顯示操作按鈕 | 編輯按鈕可見 | 待實現 |
| FE-UT-003-04 | 404 時顯示錯誤頁面 | 顯示 "店鋪不存在" | 待實現 |

#### FE-IT-003: 店鋪詳情整合測試

| 測試案例 | 描述 | 預期結果 | 狀態 |
|---------|------|----------|------|
| FE-IT-003-01 | 成功載入店鋪詳情 | 顯示完整資訊 | 待實現 |
| FE-IT-003-02 | 點擊編輯按鈕進入編輯頁 | router.push('/dashboard/tenants/:id/edit') | 待實現 |
| FE-IT-003-03 | 無權限查看他人店鋪 | 403 或 404 | 待實現 |

#### FE-E2E-003: 店鋪詳情 E2E 測試

| 測試案例 ID | 描述 | 步驟 | 預期結果 | 狀態 |
|-------------|------|------|----------|------|
| FE-E2E-003-01 | 查看並編輯店鋪流程 | 店鋪列表 → 點擊店鋪 → 點擊編輯 | 進入編輯頁 | 待實現 |

---

### 4.4 FE-M17-004: 編輯店鋪頁面

#### FE-UT-004: 編輯表單元件單元測試

| 測試案例 | 描述 | 預期結果 | 狀態 |
|---------|------|----------|------|
| FE-UT-004-01 | 表單預填當前值 | storeName = "当前名称" | 待實現 |
| FE-UT-004-02 | 部分欄位更新成功 | 只改 description | 待實現 |
| FE-UT-004-03 | 非 Owner 訪問編輯頁 | 403 或 redirect | 待實現 |

#### FE-IT-004: 編輯店鋪整合測試

| 測試案例 | 描述 | 預期結果 | 狀態 |
|---------|------|----------|------|
| FE-IT-004-01 | 成功更新店鋪 | 200, 返回更新後資料 | 待實現 |
| FE-IT-004-02 | 更新失敗顯示錯誤 | 顯示錯誤訊息 | 待實現 |
| FE-IT-004-03 | 更新成功後引導回詳情 | router.push('/dashboard/tenants/:id') | 待實现 |

#### FE-E2E-004: 編輯店鋪 E2E 測試

| 測試案例 ID | 描述 | 步驟 | 預期結果 | 狀態 |
|-------------|------|------|----------|------|
| FE-E2E-004-01 | 完整編輯店鋪流程 | 詳情頁 → 編輯 → 修改 → 保存 → 返回詳情 | 資料已更新 | 待實現 |

---

## 5. 測試環境

### 5.1 環境需求

| 環境 | 用途 | 狀態 |
|------|------|------|
| Local Dev | 本地開發測試 | ✅ 已就緒 |
| Dev API | http://localhost:8080/api | ✅ 已就緒 |
| Frontend Dev | http://localhost:3000 | ✅ 已就緒 |
| Test Database | PostgreSQL 18 (Docker) | ✅ 已就緒 |

### 5.2 測試資料準備

| 測試資料 | 建立方式 | 用途 |
|----------|----------|------|
| Admin User | Seed Script | Admin API 測試 |
| StoreOwner User | Seed Script | Frontend 登入 |
| PENDING Tenant | Seed Script | 審核測試 |
| ACTIVE Tenant | Seed Script | 詳情/編輯測試 |

---

## 6. 測試執行計劃

### 6.1 第一週測試

| 日期 | 測試重點 |
|------|----------|
| Day 1 (05/13) | Backend UT - AdminService |
| Day 2 (05/14) | Backend IT - US-M17-007 |
| Day 3 (05/15) | Backend IT - US-M17-008 |
| Day 4 (05/16) | Backend API E2E |
| Day 5 (05/17) | Frontend UT - FE-M17-001 |

### 6.2 第二週測試

| 日期 | 測試重點 |
|------|----------|
| Day 8 (05/20) | Frontend IT - FE-M17-002/003 |
| Day 9 (05/21) | Frontend IT - FE-M17-004 |
| Day 10 (05/22) | Frontend E2E |
| Day 11 (05/23) | Bug Fix, Regression |
| Day 12 (05/24) | Sprint Review + Final Sign-off |

---

## 7. 測試交付物

| 交付物 | 格式 | 負責人 |
|--------|------|--------|
| 測試腳本 | JUnit (Backend), Jest (Frontend) | Dev |
| E2E 測試報告 | console output / HTML | QA |
| Bug Report | GitHub Issues | QA |
| Test Summary | Markdown | QA |

---

## 8. 風險與緩解

| 風險 | 可能性 | 影響 | 緩解措施 |
|------|--------|------|----------|
| Frontend E2E 環境不穩定 | 中 | 中 | 使用 Playwright, 穩定性優先 |
| API 規格變更 | 低 | 高 | 每日 Standup 同步 |
| 測試資料準備延遲 | 低 | 中 | Day 0 即準備測試資料 |

---

**最後更新**: 2026-04-25
