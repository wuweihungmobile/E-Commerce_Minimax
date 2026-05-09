# Sprint 7 User Stories — M17 租戶/店鋪管理

> **Sprint**: Sprint 7
> **模組**: M17 租戶/店鋪管理
> **版本**: v1.0
> **建立日期**: 2026-05-01
> **依據**: PRD §6.8, §9.10-9.11, §4.3-4.4

---

## User Story 總覽

| US ID | 標題 | 角色 | SP | 優先級 |
|-------|------|------|-----|--------|
| US-M17-001 | 網友申請開店 | Buyer+ | 3 | P0 |
| US-M17-002 | Admin 審核開店申請 | Admin | 5 | P0 |
| US-M17-003 | Admin 管理 Feature Toggle | Admin | 3 | P0 |
| US-M17-004 | 店鋪更新 Profile | StoreOwner | 2 | P0 |
| US-M17-005 | 查詢我的店鋪列表 | StoreOwner | 1 | P0 |
| US-M17-006 | 店鋪成員邀請與管理 | StoreOwner | 5 | P1 |
| US-M17-007 | Admin 暫停/恢復/終止店鋪 | Admin | 3 | P1 |

---

## US-M17-001: 網友申請開店

**ID**: US-M17-001
**標題**: 作為 Buyer+，我想申請開店，以便在平台上經營自己的店鋪
**優先級**: P0
**Story Points**: 3

### 驗收標準 (AC)

| AC ID | 標準描述 | 測試類型 |
|-------|----------|----------|
| AC-001 | 使用者提交開店申請表單（storeName, businessType, contactEmail 必填） | IT |
| AC-002 | 申請成功後，租戶狀態為 `PENDING_REVIEW` | IT |
| AC-003 | 申請失敗時（必填欄位缺失）回傳 `E-1001 VALIDATION_ERROR` | IT |
| AC-004 | 同一用戶不可重複申請（已有 PENDING_REVIEW/ACTIVE 租戶）回傳 `E-2010 TENANT_ALREADY_EXISTS` | IT |
| AC-005 | businessType 必須是 RETAIL_ONLY/BOOKING_ONLY/HYBRID 之一，否則回傳 `E-1001 VALIDATION_ERROR` | IT |
| AC-006 | 用戶已有 TERMINATED 店鋪不可申請新店 | IT |

### 技術備註

- **API**: `POST /api/v2/tenants/apply`
- **請求欄位**: storeName (String, 必填), storeDescription (String, 選填), businessType (Enum: RETAIL_ONLY/BOOKING_ONLY/HYBRID, 必填), contactEmail (String, 必填), contactPhone (String, 選填)
- **businessType 決定初始 Feature Toggle**:
  - `RETAIL_ONLY` → `RETAIL_ENABLED=true`, `BOOKING_ENABLED=false`
  - `BOOKING_ONLY` → `RETAIL_ENABLED=false`, `BOOKING_ENABLED=true`
  - `HYBRID` → `RETAIL_ENABLED=true`, `BOOKING_ENABLED=true`
- **錯誤碼**:
  - `E-1001 VALIDATION_ERROR`: 必填欄位缺失或 businessType 無效
  - `E-2010 TENANT_ALREADY_EXISTS`: 已有 PENDING_REVIEW/ACTIVE 租戶，或已有 TERMINATED 租戶

---

## US-M17-002: Admin 審核開店申請

**ID**: US-M17-002
**標題**: 作為 Admin，我想審核店鋪申請，以便通過或駁回新店鋪
**優先級**: P0
**Story Points**: 5

### 驗收標準 (AC)

| AC ID | 標準描述 | 測試類型 |
|-------|----------|----------|
| AC-001 | Admin 可查看所有 `PENDING_REVIEW` 狀態的租戶列表 | IT |
| AC-002 | Admin 審核通過（`PUT /admin/tenants/:id/review` with `status=ACTIVE`） | IT |
| AC-003 | Admin 審核不通過（`status=REJECTED`，需填寫 `rejectionReason`） | IT |
| AC-004 | 審核通過後，自動初始化該租戶的 Feature Toggle（依 businessType） | IT |
| AC-005 | 只能審核 `PENDING_REVIEW` 狀態的租戶，其他狀態回傳 `E-2011 INVALID_TENANT_STATUS` | IT |
| AC-006 | 只能由 Admin 角色執行，否則回傳 `E-1003 ACCESS_DENIED` | IT |
| AC-007 | 審核不通過時 `rejectionReason` 必填（最大 500 字），否則回傳 `E-1001 VALIDATION_ERROR` | IT |

### 技術備註

- **API**: `PUT /api/v2/admin/tenants/:id/review`
- **請求欄位**: status (ACTIVE/REJECTED), rejectionReason (String, 當 status=REJECTED 時必填)
- **狀態機**: `PENDING_REVIEW` → `ACTIVE` 或 `REJECTED`
- **Feature Toggle 初始化時序**: 見 PRD §4.4 Specification

### 狀態機定義

```
PENDING_REVIEW ──┬──→ ACTIVE (審核通過)
                 │
                 └──→ REJECTED (審核不通過)

ACTIVE ─────────→ SUSPENDED (違規暫停，可登入但無法操作，商品下架)
SUSPENDED ──────→ ACTIVE (恢復正常)
               ──→ TERMINATED (永久終止，帳號鎖定，不可再登入)
```

**狀態說明**:
| 狀態 | 登入權限 | 商品上架 | 訂單受理 |
|------|---------|---------|---------|
| PENDING_REVIEW | ❌ | ❌ | ❌ |
| ACTIVE | ✅ | ✅ | ✅ |
| REJECTED | ❌ | ❌ | ❌ |
| SUSPENDED | ✅ (唯讀) | ❌ | ❌ |
| TERMINATED | ❌ | ❌ | ❌ |

---

## US-M17-003: Admin 管理 Feature Toggle

**ID**: US-M17-003
**標題**: 作為 Admin，我想管理各租戶的 Feature Toggle，以便控制店鋪功能
**優先級**: P0
**Story Points**: 3

### 驗收標準 (AC)

| AC ID | 標準描述 | 測試類型 |
|-------|----------|----------|
| AC-001 | Admin 可查詢指定租戶的所有 Feature Toggle | IT |
| AC-002 | Admin 可更新指定租戶的 Feature Toggle | IT |
| AC-003 | 更新 Toggle 時寫入 `audit_logs` | IT |
| AC-004 | Toggle 變更後立即生效（Redis 60s TTL） | IT |
| AC-005 | 只能由 Admin 角色執行 | IT |

### 技術備註

- **API**:
  - `GET /api/v2/admin/tenants/:id/features`
  - `PUT /api/v2/admin/tenants/:id/features`
- **Feature Keys**: RETAIL_ENABLED, BOOKING_ENABLED, CMS_ENABLED, ERP_ENABLED, DYNAMIC_PRICING_ENABLED, PROMO_ENABLED, MAX_PRODUCTS, MAX_ROOMS, MAX_POSTS, COMMISSION_RATE
- **預設值** (新店鋪):

| Feature Key | RETAIL_ONLY | BOOKING_ONLY | HYBRID |
|-------------|-------------|--------------|---------|
| RETAIL_ENABLED | true | false | true |
| BOOKING_ENABLED | false | true | true |
| CMS_ENABLED | true | true | true |
| ERP_ENABLED | true | true | true |
| DYNAMIC_PRICING_ENABLED | false | false | false |
| PROMO_ENABLED | false | false | false |
| MAX_PRODUCTS | 100 | 0 | 100 |
| MAX_ROOMS | 0 | 20 | 20 |
| MAX_POSTS | 50 | 50 | 50 |
| COMMISSION_RATE | 0.05 | 0.05 | 0.05 |

---

## US-M17-004: 店鋪更新 Profile

**ID**: US-M17-004
**標題**: 作為 StoreOwner，我想更新店鋪 Profile，以便維護店鋪資訊
**優先級**: P0
**Story Points**: 2

### 驗收標準 (AC)

| AC ID | 標準描述 | 測試類型 |
|-------|----------|----------|
| AC-001 | StoreOwner 可更新自己店鋪的 Profile（名稱、描述、聯絡方式） | IT |
| AC-002 | 只能更新 `ACTIVE` 狀態的店鋪 | IT |
| AC-003 | StoreOwner 不可更新他人店鋪，回傳 `E-2002 CROSS_TENANT_ACCESS_DENIED` | IT |

### 技術備註

- **API**: `PUT /api/v2/tenants/:id`
- **可更新欄位**: storeName, storeDescription, logoUrl, contactEmail, contactPhone

---

## US-M17-005: 查詢我的店鋪列表

**ID**: US-M17-005
**標題**: 作為 StoreOwner，我想查詢我的店鋪列表，以便管理我的店鋪
**優先級**: P0
**Story Points**: 1

### 驗收標準 (AC)

| AC ID | 標準描述 | 測試類型 |
|-------|----------|----------|
| AC-001 | StoreOwner 可查詢自己所屬的所有租戶列表 | IT |
| AC-002 | 回傳資料包含租戶狀態、businessType、創建時間 | IT |

### 技術備註

- **API**: `GET /api/v2/tenants/my`

---

## US-M17-006: 店鋪成員邀請與管理

**ID**: US-M17-006
**標題**: 作為 StoreOwner，我想管理店鋪成員，以便共同管理店鋪
**優先級**: P1
**Story Points**: 5 → **Phase 1 裁剪為 3 SP**

### Phase 1 範圍說明
**重要**: Phase 1 簡化成員管理，**不含 email 邀請機制**：
- StoreOwner 可直接新增成員（輸入 userId）
- 成員直接加入，無需接受邀請
- Phase 2（ Sprint 8+）將擴展為完整 email 邀請流程

### 驗收標準 (AC)

| AC ID | 標準描述 | 測試類型 |
|-------|----------|----------|
| AC-001 | StoreOwner 可直接新增成員（輸入 userId） | IT |
| AC-002 | 可查看店鋪成員列表 | IT |
| AC-003 | StoreOwner 可更新成員角色（StoreOwner/StoreStaff/Seller/Host） | IT |
| AC-004 | StoreOwner 可移除成員（不可移除自己） | IT |
| AC-005 | 成員不可為自己店的創始 StoreOwner | IT |
| AC-006 | 不可重複新增相同成員 | IT |

### 技術備註

- **API**:
  - `POST /api/v2/tenants/:id/members` (直接新增成員，Phase 1)
  - `GET /api/v2/tenants/:id/members`
  - `PUT /api/v2/tenants/:id/members/:userId/role`
  - `DELETE /api/v2/tenants/:id/members/:userId`
- **角色枚舉**: StoreOwner, StoreStaff, Seller, Host
- **權限矩陣**:
  | 角色 | 可新增 | 可移除他人 | 可修改他人角色 |
  |------|--------|-----------|---------------|
  | StoreOwner | ✅ | ✅ | ✅ |
  | StoreStaff | ❌ | ❌ | ❌ |

---

## US-M17-007: Admin 暫停/恢復/終止店鋪

**ID**: US-M17-007
**標題**: 作為 Admin，我想暫停、恢復或終止店鋪，以便管理違規店鋪
**優先級**: P1
**Story Points**: 3

### 驗收標準 (AC)

| AC ID | 標準描述 | 測試類型 |
|-------|----------|----------|
| AC-001 | Admin 可將 `ACTIVE` 店鋪改為 `SUSPENDED` | IT |
| AC-002 | Admin 可將 `SUSPENDED` 店鋪恢復為 `ACTIVE` | IT |
| AC-003 | Admin 可將 `SUSPENDED` 店鋪終止為 `TERMINATED` | IT |
| AC-004 | 狀態變更寫入 `audit_logs` | IT |
| AC-005 | `SUSPENDED` 店鋪的 Listing 自動下架 | IT |
| AC-006 | `TERMINATED` 店鋪不可再登入 | IT |

### 技術備註

- **API**: `PUT /api/v2/admin/tenants/:id/status`
- **狀態變更**: `ACTIVE` → `SUSPENDED` → `TERMINATED`

---

## M17 Backend API 清單

| 方法 | 端點 | 對應 US | 備註 |
|------|------|---------|------|
| POST | `/api/v2/tenants/apply` | US-M17-001 | 開店申請 |
| GET | `/api/v2/tenants/my` | US-M17-005 | 我的店鋪列表 |
| GET | `/api/v2/tenants/:id` | US-M17-004 | 店鋪詳情 |
| PUT | `/api/v2/tenants/:id` | US-M17-004 | 更新店鋪 Profile |
| GET | `/api/v2/admin/tenants` | US-M17-002 | 租戶列表（Admin） |
| PUT | `/api/v2/admin/tenants/:id/review` | US-M17-002 | 審核開店申請 |
| PUT | `/api/v2/admin/tenants/:id/status` | US-M17-007 | 暫停/恢復/終止 |
| GET | `/api/v2/admin/tenants/:id/features` | US-M17-003 | 查詢 Feature Toggle |
| PUT | `/api/v2/admin/tenants/:id/features` | US-M17-003 | 更新 Feature Toggle |
| GET | `/api/v2/tenants/:id/members` | US-M17-006 | 成員列表（Phase 1） |
| POST | `/api/v2/tenants/:id/members` | US-M17-006 | 直接新增成員（Phase 1） |
| PUT | `/api/v2/tenants/:id/members/:userId/role` | US-M17-006 | 更新成員角色（Phase 1） |
| DELETE | `/api/v2/tenants/:id/members/:userId` | US-M17-006 | 移除成員（Phase 1） |

> **Phase 2 擴展**: `POST /api/v2/tenants/:id/members/invite`（email 邀請機制）延至 Sprint 8

---

## M17 Frontend 頁面需求

| 頁面 | 路徑 | 對應 US | 優先級 |
|------|------|---------|--------|
| 開店申請頁面 | `/dashboard/apply` | US-M17-001 | P0 |
| 店鋪設定頁面 | `/dashboard/settings` | US-M17-004, US-M17-005 | P0 |
| Feature Toggle 管理頁面 | `/dashboard/admin/features` | US-M17-003 | P0 |
| 成員管理頁面 | `/dashboard/settings/members` | US-M17-006 | P1 |
| Admin 租戶管理頁面 | `/dashboard/admin/tenants` | US-M17-002, US-M17-007 | P0 |

---

**文件狀態**: ✅ 草稿
**待確認**: Sprint 7 Planning 會議
