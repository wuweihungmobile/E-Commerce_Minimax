# M17 租戶管理測試案例 / Tenant Management Test Cases

> **模組**: M17 租戶/店鋪管理
> **版本**: v1.7
> **建立日期**: 2026-04-10
> **依據**: API_M17_Tenant.md, SRD_Database_Schema.md, SRD_System_Architecture.md
> **測試框架**: JUnit 5 + Mockito (UT), Spring Boot Test (IT), REST Assured (API)
> **Sprint 2 範圍**: US-M17-001 ~ US-M17-006
> **Sprint 3 範圍**: US-M17-007, US-M17-008 (Admin APIs)

---

## 📋 測試案例總覽

| 測試類型 | P0 | P1 | P2 | 小計 | Sprint | 狀態 |
|----------|----|----|----|------|--------|------|
| UT | 4 | 3 | 2 | 9 | Sprint 2 | ⏳ 待實現 |
| IT | 2 | 2 | 1 | 5 | Sprint 2 | ⏳ 待實現 |
| API | 3 | 4 | 2 | 9 | Sprint 2 | ✅ 已實現 (14項) |
| **Sprint 2 合計** | 9 | 9 | 5 | **23** | | |
| Admin IT | 2 | 1 | 0 | 3 | Sprint 3 | ⏳ 待實現 |
| Admin API | 4 | 3 | 0 | 7 | Sprint 3 | ⏳ 待實現 |
| RBAC E2E (StoreOwner/STORE_STAFF) | 4 | 0 | 0 | 4 | Sprint 3-A | 📋 新增 |
| **總合計** | 19 | 13 | 5 | **37** | | |

---

## 1. 單元測試 (Unit Tests)

### 1.1 TenantContext ThreadLocal 管理

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| UT-M17-001 | TenantContext設定與取得 | P0 | 無 | 1. TenantContext.setTenantId(tenantId)<br>2. TenantContext.getTenantId() | 回傳設定的 tenantId |
| UT-M17-002 | TenantContext跨執行緒隔離 | P0 | 無 | 1. 主執行緒設定 Tenant A<br>2. 新建子執行緒取得 TenantContext | 子執行緒應為 null 或隔離 |
| UT-M17-003 | TenantContext清除 | P0 | 已有 TenantContext | 1. 設定 TenantContext<br>2. 呼叫 clear() | TenantContext 已清除，getTenantId() 為 null |
| UT-M17-004 | TenantContext未設定時取得 | P1 | 無 | 1. 未設定直接取得 | 拋出 TenantContextNotFoundException |

### 1.2 租戶申請業務邏輯

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| UT-M17-005 | 租戶申請-storeName唯一性檢查 | P0 | 資料庫無此店名 | 1. 申請新店鋪 "我的商店" | 申請成功 |
| UT-M17-006 | 租戶申請-storeName重複檢查 | P1 | 資料庫已有 "我的商店" | 1. 申請新店鋪 "我的商店" | 拋出 StoreNameAlreadyExistsException |
| UT-M17-007 | 租戶申請-businessType預設值 | P2 | 無 | 1. 不指定 businessType 申請 | businessType 預設為 RETAIL_ONLY |

### 1.3 Feature Toggle 邏輯

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| UT-M17-008 | FeatureToggle查詢-已啟用 | P1 | BOOKING_ENABLED = true | 1. 查詢 BOOKING_ENABLED 狀態 | isEnabled = true |
| UT-M17-009 | FeatureToggle查詢-未啟用 | P1 | BOOKING_ENABLED = false | 1. 查詢 BOOKING_ENABLED 狀態 | isEnabled = false |

---

## 2. 整合測試 (Integration Tests)

### 2.1 租戶申請流程

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| IT-M17-001 | 租戶申請-成功提交 | P0 | 資料庫乾淨 | 1. POST /api/v2/tenants/apply<br>2. 驗證 tenants 表有新記錄 | 201, status = PENDING |
| IT-M17-002 | 租戶申請-storeName重複 | P0 | 已有相同 storeName | 1. POST /api/v2/tenants/apply (相同 storeName) | 409 Conflict |
| IT-M17-003 | 租戶申請-必填欄位驗證 | P1 | 無 | 1. POST /api/v2/tenants/apply (缺少 storeName) | 400 Bad Request |

### 2.2 Admin 審核流程 (Sprint 3)

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 | 狀態 |
|-------|-------------|--------|----------|----------|----------|------|
| IT-M17-004 | Admin審核-通過申請 | P0 | 租戶狀態 = PENDING | 1. POST /api/v2/admin/tenants/{id}/approve<br>2. 驗證狀態變為 ACTIVE | 200, status = ACTIVE | ⏳ 待實現 |
| IT-M17-005 | Admin審核-駁回申請 | P0 | 租戶狀態 = PENDING | 1. POST /api/v2/admin/tenants/{id}/reject<br>2. 提供 reason | 200, status = REJECTED | ⏳ 待實現 |
| IT-M17-006 | Admin審核-非Admin角色 | P1 | StoreOwner 角色 | 1. StoreOwner 嘗試呼叫 approve API | 403 Forbidden | ⏳ 待實現 |
| IT-M17-007-02 | Admin審核-通過時Feature Toggle初始化 | P0 | 租戶狀態 = PENDING | 1. POST /api/v2/admin/tenants/{id}/approve<br>2. 驗證 6 個 Feature Toggle 預設值：RETAIL=true, BOOKING=false, CMS=true, ERP=true, DYNAMIC_PRICING=false, PROMO=false | 200, status = ACTIVE, toggles 正確初始化 | 📋 新增 |

> **Note**: IT-M17-004, IT-M17-005, IT-M17-006, IT-M17-007-02 屬於 Sprint 3 (US-M17-007, US-M17-008)

### 2.3 Feature Toggle 更新

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| IT-M17-007 | FeatureToggle-申請啟用功能 | P1 | BOOKING_ENABLED = false | 1. PUT /api/v2/dashboard/tenants/features/BOOKING_ENABLED<br>Body: {"enabled": true} | 200, status = PENDING_APPROVAL |
| IT-M17-008 | FeatureToggle-Admin核准功能 | P2 | 申請狀態 = PENDING | 1. Admin 核准 BOOKING_ENABLED | BOOKING_ENABLED = true |

---

## 3. API E2E 測試 (API E2E Tests)

### 3.1 租戶申請 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M17-001 | POST /api/v2/tenants/apply-成功 | P0 | 無 | 1. POST /api/v2/tenants/apply<br>Body: {"storeName":"新商店","businessType":"RETAIL_ONLY","contactEmail":"test@test.com"} | 201, data.status = PENDING | ✅ 已實現 |
| API-M17-002 | POST /api/v2/tenants/apply-缺少必填 | P0 | 無 | 1. POST /api/v2/tenants/apply (缺少 storeName) | 400, errors 包含 storeName 錯誤 | ✅ 已實現 |
| API-M17-003 | POST /api/v2/tenants/apply-Email格式錯誤 | P1 | 無 | 1. POST /api/v2/tenants/apply<br>Body: {"storeName":"商店","contactEmail":"invalid"} | 400, errors 包含 email 錯誤 | ✅ 已實現 |

### 3.2 租戶查詢 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M17-004 | GET /api/v2/tenants-取得我的店鋪列表 | P0 | 已登入且有店鋪 | 1. GET /api/v2/tenants<br>Header: Authorization: Bearer {token} | 200, data.tenants 包含店鋪列表 | ✅ 已實現 |
| API-M17-005 | GET /api/v2/tenants/:id-店鋪詳情 | P0 | 已登入且有店鋪 | 1. GET /api/v2/tenants/{tenantId} | 200, data 包含 storeName, status 等 | ✅ 已實現 |
| API-M17-006 | GET /api/v2/tenants/:id-不存在的店鋪 | P1 | 無 | 1. GET /api/v2/tenants/invalid-uuid | 404, message="Store not found" | ✅ 已實現 |

### 3.3 租戶更新 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M17-012 | PUT /api/v2/tenants/:id-更新店鋪成功 | P1 | StoreOwner + 已登入 | 1. PUT /api/v2/tenants/{id}<br>2. Body: {"storeName":"新名稱"} | 200, data.storeName = "新名稱" | ✅ 已實現 |
| API-M17-013 | PUT /api/v2/tenants/:id-非Owner403 | P0 | 另一用戶 Token | 1. PUT /api/v2/tenants/{id} (非 Owner) | 403 Forbidden | ✅ 已實現 |
| API-M17-014 | PUT /api/v2/tenants/:id-部分更新 | P2 | StoreOwner + 已登入 | 1. PUT /api/v2/tenants/{id}<br>2. Body: {"description":"新描述"} (只更新一個欄位) | 200, 其他欄位不變 | ✅ 已實現 |

### 3.4 功能開關更新 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M17-015 | PUT /api/v2/dashboard/tenants/features/:feature-非StoreOwner403 | P0 | 一般會員 Token | 1. PUT /api/v2/dashboard/tenants/features/BOOKING_ENABLED<br>Body: {"enabled": true} | 403 Forbidden |

### 3.5 Admin 店鋪管理 API (Sprint 3)

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 | 狀態 |
|-------|-------------|--------|----------|----------|----------|------|
| API-M17-007 | GET /api/v2/admin/tenants-店鋪列表 | P0 | Admin 登入 | 1. GET /api/v2/admin/tenants<br>Header: Authorization: Bearer {adminToken} | 200, data.items 包含所有店鋪 | ⏳ 待實現 |
| API-M17-008 | POST /api/v2/admin/tenants/:id/approve-成功 | P0 | Admin + 待審核租戶 | 1. POST /api/v2/admin/tenants/{id}/approve | 200, data.status = ACTIVE | ⏳ 待實現 |
| API-M17-009 | POST /api/v2/admin/tenants/:id/reject-成功 | P1 | Admin + 待審核租戶 | 1. POST /api/v2/admin/tenants/{id}/reject<br>Body: {"reason":"資料不全"} | 200, data.status = REJECTED | ⏳ 待實現 |
| API-M17-007-03 | POST /api/v2/admin/tenants/:id/approve-非PENDING狀態 | P1 | Admin + ACTIVE 租戶 | 1. POST /api/v2/admin/tenants/{id}/approve | 400, error.code = E_2005 | 📋 新增 |
| API-M17-007-04 | POST /api/v2/admin/tenants/:id/approve-非Admin角色 | P0 | BUYER 用戶 | 1. BUYER Token 呼叫 approve API | 403 Forbidden | 📋 新增 |
| API-M17-008-03 | POST /api/v2/admin/tenants/:id/reject-空白reason | P1 | Admin + PENDING 租戶 | 1. POST /api/v2/admin/tenants/{id}/reject<br>Body: {"reason": ""} | 400 VALIDATION_ERROR | 📋 新增 |
| API-M17-008-04 | POST /api/v2/admin/tenants/:id/reject-非Admin角色 | P0 | BUYER 用戶 | 1. BUYER Token 呼叫 reject API | 403 Forbidden | 📋 新增 |

> **Note**: API-M17-007, API-M17-008, API-M17-009 屬於 Sprint 3 (US-M17-007, US-M17-008)

### 3.6 StoreOwner/STORE_STAFF RBAC E2E 測試 (Sprint 3-A 補漏)

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 | 狀態 |
|-------|-------------|--------|----------|----------|----------|------|
| API-M17-018 | POST /api/v2/admin/tenants/:id/approve-StoreOwner角色 | P0 | StoreOwner 登入 | 1. StoreOwner Token 呼叫 approve API | 403 Forbidden | 📋 新增 |
| API-M17-019 | POST /api/v2/admin/tenants/:id/approve-STORE_STAFF角色 | P0 | STORE_STAFF 登入 | 1. STORE_STAFF Token 呼叫 approve API | 403 Forbidden | 📋 新增 |
| API-M17-020 | POST /api/v2/admin/tenants/:id/reject-StoreOwner角色 | P0 | StoreOwner 登入 | 1. StoreOwner Token 呼叫 reject API | 403 Forbidden | 📋 新增 |
| API-M17-021 | POST /api/v2/admin/tenants/:id/reject-STORE_STAFF角色 | P0 | STORE_STAFF 登入 | 1. STORE_STAFF Token 呼叫 reject API | 403 Forbidden | 📋 新增 |

### 3.7 Feature Toggle API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M17-016 | GET /api/v2/dashboard/tenants/features-查詢 | P1 | StoreOwner 登入 | 1. GET /api/v2/dashboard/tenants/features | 200, data.features 包含所有開關 | ✅ 已實現 |
| API-M17-017 | PUT /api/v2/dashboard/tenants/features/:feature-申請 | P1 | StoreOwner 登入 | 1. PUT /api/v2/dashboard/tenants/features/DYNAMIC_PRICING_ENABLED<br>Body: {"enabled": true} | 200, data.status = PENDING_APPROVAL | ✅ 已實現 |
| API-M17-015 | PUT /api/v2/dashboard/tenants/features/:feature-非StoreOwner | P0 | 一般會員 Token | 1. PUT /api/v2/dashboard/tenants/features/BOOKING_ENABLED<br>Body: {"enabled": true} | 403 Forbidden | ✅ 已實現 |

---

## 4. 多租戶資料隔離測試

### 4.1 租戶資料隔離驗證

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| ISO-001 | Tenant A看不到Tenant B的店鋪 | P0 | 兩個獨立租戶 | 1. 以 Tenant A Token 查詢店鋪列表<br>2. 驗證結果不包含 Tenant B | 結果只包含 Tenant A 的店鋪 |
| ISO-002 | Tenant A看不到Tenant B的商品 | P0 | 兩個獨立租戶各有商品 | 1. 以 Tenant A Token 查詢商品<br>2. 驗證結果不包含 Tenant B 商品 | 結果只包含 Tenant A 的商品 |
| ISO-003 | Tenant A看不到Tenant B的訂單 | P0 | 兩個獨立租戶各有訂單 | 1. 以 Tenant A Token 查詢訂單<br>2. 驗證結果不包含 Tenant B 訂單 | 結果只包含 Tenant A 的訂單 |
| ISO-004 | 跨租戶存取-無權限 | P1 | Tenant A Token | 1. 以 Tenant A Token 嘗試存取 Tenant B 資源 | 403 Forbidden 或 404 Not Found |

---

## 📝 關鍵驗證點總結

| 驗證項目 | 測試案例 | 優先級 |
|---------|---------|--------|
| 多租戶資料隔離 (Tenant A 看不到 Tenant B 資料) | ISO-001, ISO-002, ISO-003 | P0 |
| Feature Toggle 狀態正確反映 | UT-M17-008, UT-M17-009, API-M17-016 | P0 |
| 租戶狀態機: PENDING → APPROVED → ACTIVE | IT-M17-001, IT-M17-004, IT-M17-005 | P0 |
| 店鋪申請流程完整 | API-M17-001, IT-M17-001, IT-M17-004 | P0 |

---

## 📝 租戶狀態流轉圖

```
       PENDING ──────► APPROVED ──────► ACTIVE
           │              │               │
           ▼              ▼               ▼
       REJECTED      SUSPENDED        SUSPENDED
```

---

**文件版本**: AISDLC v0.09
**測試框架**: JUnit 5 + Mockito, Spring Boot Test, REST Assured
**最後更新**: 2026-04-26

## 📝 文件修訂紀錄

| 版本 | 日期 | 修改內容 |
|------|------|----------|
| v1.0 | 2026-04-10 | 初始版本 |
| v1.1 | 2026-04-22 | 新增 Sprint 2/3 範圍標記，Admin API 測試案例標記為 Sprint 3 |
| v1.2 | 2026-04-22 | 新增 US-M17-003/006 測試缺口修補：API-M17-012~015 (更新店鋪、功能開關角色校驗) |
| v1.3 | 2026-04-24 | Sprint 2 測試驗證完成：14/14 API E2E 測試通過 (TenantControllerE2ETest)，新增 US-M17-004 正向成功測試，修復 Feature Toggle requiresApproval 邏輯 |
| v1.4 | 2026-04-24 | 新增 Sprint 3 測試狀態標記（IT-M17-004~006, API-M17-007~009）|
| v1.5 | 2026-04-25 | 新增 IT-M17-007-02：Admin審核通過時Feature Toggle初始化測試（6個 toggles 預設值驗證） |
| v1.6 | 2026-04-26 | **Sprint 3-A 補漏**：新增 API-M17-007-03/04, API-M17-008-03/04 (非PENDING狀態/非Admin角色測試)，新增 API-M17-010~013 (StoreOwner/STORE_STAFF RBAC E2E 測試)，更新測試案例總數為 37 項 |
| v1.7 | 2026-04-26 | 修復章節編號衝突（3.5/3.6 → 3.5/3.6/3.7）、修復 Feature Toggle API ID 重複（API-M17-010/011 → API-M17-016/017）；修復 RBAC E2E ID 衝突（API-M17-010~013 → API-M17-018~021） |
