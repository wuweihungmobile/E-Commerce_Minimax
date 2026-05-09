# Sprint 7 任務分解與技術評估

> **Sprint**: Sprint 7
> **期間**: 2026-05-25 ~ 2026-06-07
> **版本**: v1.0
> **建立日期**: 2026-05-01

---

## 1. M17 技術可行性評估（SD 角色）

### 1.1 架構影響

| 項目 | 評估 | 風險 |
|------|------|------|
| 資料庫變更 | 需要新增 `tenants`, `tenant_feature_toggles`, `tenant_members` 三張表 | 中 |
| API 變更 | 新增 13 個 API 端點（§9.10-9.11） | 低 |
| 服務層變更 | 需要新增 TenantService, TenantFeatureToggleService | 低 |
| 現有功能影響 | 需要與 M03 (JWT/RBAC) 整合 | 中 |

### 1.2 技術風險

| 風險 | 等級 | 緩解措施 |
|------|------|----------|
| X-Tenant-ID 多租戶切換機制複雜 | 高 | 參考現有 TenantContextFilter 模式 |
| Feature Toggle × RBAC 整合可能影響現有 API | 中 | 確保 backward compatibility |
| 成員邀請 email 機制（Phase 2） | 低 | Phase 1 僅建立記錄 |

### 1.3 實作建議

1. **先建立資料模型**（Flyway Migration）
2. **實作核心 API**（Tenant CRUD + Feature Toggle）
3. **整合現有認證**（M03 JWT）
4. **最後實作成員管理**

---

## 2. Story Points 估算

### 2.1 估算標準

| SP | 複雜度 | 說明 |
|-----|--------|------|
| 1 | 簡單 | 1-2 小時，無新技術 |
| 2 | 中等 | 半天，單一技術點 |
| 3 | 普通 | 1 天，熟悉技術 |
| 5 | 複雜 | 2-3 天，多技術點 |
| 8 | 高 | 1 週，需拆分 |

### 2.2 M17 User Story SP 分配

| US ID | 標題 | 複雜度 | 不確定性 | SP |
|-------|------|--------|----------|-----|
| US-M17-001 | 網友申請開店 | 中 | 低 | 3 |
| US-M17-002 | Admin 審核開店申請 | 高 | 中 | 5 |
| US-M17-003 | Admin 管理 Feature Toggle | 中 | 低 | 3 |
| US-M17-004 | 店鋪更新 Profile | 低 | 低 | 2 |
| US-M17-005 | 查詢我的店鋪列表 | 低 | 低 | 1 |
| US-M17-006 | 店鋪成員邀請與管理 | 高 | 中 | 5 |
| US-M17-007 | Admin 暫停/恢復/終止店鋪 | 中 | 低 | 3 |
| **合計** | | | | **22 SP** |

---

## 3. 任務分解

### 3.1 Sprint 7 容量規劃

| 項目 | 數值 |
|------|------|
| Sprint 容量 | 30 SP |
| 規劃 SP | 22 SP |
| Buffer | 8 SP (27%) |

### 3.2 任務清單

#### Task-M17-001: M17 資料庫 Migration 建立

| 項目 | 內容 |
|------|------|
| **US** | 所有 M17 User Stories |
| **SP** | 3 |
| **預估工時** | 4h |
| **任務內容** | |
| - | 建立 `V001__create_tenants_table.sql` |
| - | 建立 `V002__create_tenant_feature_toggles_table.sql` |
| - | 建立 `V003__create_tenant_members_table.sql` |
| - | 建立初始 admin 租戶 |
| **依賴** | 無 |
| **負責人** | Backend |

#### Task-M17-002: TenantService 核心實作

| 項目 | 內容 |
|------|------|
| **US** | US-M17-001, US-M17-002, US-M17-004, US-M17-005, US-M17-007 |
| **SP** | 5 |
| **預估工時** | 8h |
| **任務內容** | |
| - | 建立 Tenant entity |
| - | 建立 TenantRepository |
| - | 實作 `applyForTenant()` - 開店申請 |
| - | 實作 `reviewTenant()` - 審核通過/不通過 |
| - | 實作 `updateTenantProfile()` - 更新 Profile |
| - | 實作 `getMyTenants()` - 查詢我的店鋪 |
| - | 實作 `updateTenantStatus()` - 暫停/恢復/終止 |
| - | 實作狀態機驗證 |
| **依賴** | Task-M17-001 |
| **負責人** | Backend |

#### Task-M17-003: Feature Toggle Service 實作

| 項目 | 內容 |
|------|------|
| **US** | US-M17-003 |
| **SP** | 3 |
| **預估工時** | 4h |
| **任務內容** | |
| - | 建立 TenantFeatureToggle entity |
| - | 建立 TenantFeatureToggleRepository |
| - | 實作 `initializeTogglesForTenant()` - 依 businessType 初始化 |
| - | 實作 `getToggles()` - 查詢 Toggle |
| - | 實作 `updateToggles()` - 更新 Toggle + audit_log |
| - | 整合 Redis 快取（TTL 60s） |
| **依賴** | Task-M17-001 |
| **負責人** | Backend |

#### Task-M17-004: Tenant API 端點實作

| 項目 | 內容 |
|------|------|
| **US** | US-M17-001, US-M17-002, US-M17-003, US-M17-004, US-M17-005, US-M17-007 |
| **SP** | 3 |
| **預估工時** | 6h |
| **任務內容** | |
| - | `POST /api/v2/tenants/apply` |
| - | `GET /api/v2/tenants/my` |
| - | `GET /api/v2/tenants/:id` |
| - | `PUT /api/v2/tenants/:id` |
| - | `GET /api/v2/admin/tenants` |
| - | `PUT /api/v2/admin/tenants/:id/review` |
| - | `PUT /api/v2/admin/tenants/:id/status` |
| - | `GET /api/v2/admin/tenants/:id/features` |
| - | `PUT /api/v2/admin/tenants/:id/features` |
| **依賴** | Task-M17-002, Task-M17-003 |
| **負責人** | Backend |

#### Task-M17-005: 租戶成員管理實作（Phase 1 簡化）

| 項目 | 內容 |
|------|------|
| **US** | US-M17-006 |
| **SP** | 3（裁剪後） |
| **預估工時** | 4h |
| **任務內容** | |
| - | 建立 TenantMember entity |
| - | 建立 TenantMemberRepository |
| - | 實作 `addMember()` - 直接新增成員（Phase 1，無 email 邀請） |
| - | 實作 `getMembers()` - 成員列表 |
| - | 實作 `updateMemberRole()` - 更新角色 |
| - | 實作 `removeMember()` - 移除成員 |
| - | API 端點實作 |
| **依賴** | Task-M17-001 |
| **負責人** | Backend |

**Phase 1 說明**: 不含 email 邀請機制，StoreOwner 直接輸入 userId 新增成員

#### Task-M17-006: M17 Backend 整合測試

| 項目 | 內容 |
|------|------|
| **US** | 所有 US |
| **SP** | 3 |
| **預估工時** | 6h |
| **任務內容** | |
| - | IT-M17-001: 開店申請成功 |
| - | IT-M17-002: 開店申請必填欄位驗證 |
| - | IT-M17-003: 重複申請失敗 |
| - | IT-M17-004: Admin 審核通過 + Feature Toggle 初始化 |
| - | IT-M17-005: Admin 審核不通過 |
| - | IT-M17-006: Feature Toggle 查詢/更新 |
| - | IT-M17-007: 成員邀請/管理 |
| **測試資料初始化** | |
| - | ✅ `V7__M17_Test_Data_Init.sql` 已建立 (2026-05-02) |
| - | 包含: ADMIN 用戶 (admin@nextkey.local / admin123) |
| - | 包含: PENDING_REVIEW 租戶 (bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb) |
| - | 包含: ACTIVE 租戶 (cccccccc-cccc-cccc-cccc-cccccccccccc) |
| - | 包含: 對應的 STORE_OWNER 用戶 |
| - | 包含: ACTIVE 租戶的 Feature Toggles |
| **依賴** | Task-M17-002, Task-M17-003, Task-M17-004, Task-M17-005 |
| **負責人** | QA/Backend |

#### Task-M17-007: M17 Frontend 開店申請頁面

| 項目 | 內容 |
|------|------|
| **US** | US-M17-001 |
| **SP** | 3 |
| **預估工時** | 4h |
| **任務內容** | |
| - | `/dashboard/apply` 頁面 |
| - | 申請表單元件 |
| - | 申請成功/失敗提示 |
| **依賴** | Task-M17-004 |
| **負責人** | Frontend |

#### Task-M17-008: M17 Frontend 店鋪設定頁面

| 項目 | 內容 |
|------|------|
| **US** | US-M17-004, US-M17-005 |
| **SP** | 3 |
| **預估工時** | 4h |
| **任務內容** | |
| - | `/dashboard/settings` 頁面 |
| - | 店鋪 Profile 編輯表單 |
| - | 店鋪資訊展示 |
| **依賴** | Task-M17-004 |
| **負責人** | Frontend |

#### Task-M17-009: M17 Frontend Feature Toggle 管理頁面

| 項目 | 內容 |
|------|------|
| **US** | US-M17-003 |
| **SP** | 3 |
| **預估工時** | 4h |
| **任務內容** | |
| - | `/dashboard/admin/features` 頁面 |
| - | Feature Toggle 列表/編輯元件 |
| - | 變更歷史展示（audit_log） |
| **依賴** | Task-M17-004 |
| **負責人** | Frontend |

#### Task-M17-010: M17 Frontend 成員管理頁面

| 項目 | 內容 |
|------|------|
| **US** | US-M17-006 |
| **SP** | 3 |
| **預估工時** | 4h |
| **任務內容** | |
| - | `/dashboard/settings/members` 頁面 |
| - | 成員列表元件 |
| - | 邀請成員/更新角色/移除成員 |
| **依賴** | Task-M17-005 |
| **負責人** | Frontend |

#### Task-M17-011: M17 Frontend Admin 租戶管理頁面

| 項目 | 內容 |
|------|------|
| **US** | US-M17-002, US-M17-007 |
| **SP** | 3 |
| **預估工時** | 4h |
| **任務內容** | |
| - | `/dashboard/admin/tenants` 頁面 |
| - | 租戶列表（篩選 PENDING_REVIEW 等） |
| - | 審核表單（通過/駁回） |
| - | 狀態管理（暫停/恢復/終止） |
| **依賴** | Task-M17-004 |
| **負責人** | Frontend |

---

## 4. Sprint 7 最終規劃

### 4.1 SP 分配（裁剪後）

| 任務 ID | 任務名稱 | SP | 負責人 |
|---------|----------|-----|--------|
| Task-M17-001 | M17 資料庫 Migration 建立 | 3 | Backend |
| Task-M17-002 | TenantService 核心實作 | 5 | Backend |
| Task-M17-003 | Feature Toggle Service 實作 | 3 | Backend |
| Task-M17-004 | Tenant API 端點實作 | 3 | Backend |
| Task-M17-005 | 租戶成員管理實作（Phase 1 簡化） | 3 | Backend |
| Task-M17-006 | M17 Backend 整合測試 | 3 | QA/Backend |
| Task-M17-007 | M17 Frontend 開店申請頁面 | 3 | Frontend |
| Task-M17-008 | M17 Frontend 店鋪設定頁面 | 3 | Frontend |
| Task-M17-009 | M17 Frontend Feature Toggle 管理頁面 | 3 | Frontend |
| Task-M17-010 | ~~M17 Frontend 成員管理頁面~~（延至 Sprint 8） | - | Frontend |
| Task-M17-011 | M17 Frontend Admin 租戶管理頁面 | 3 | Frontend |
| **合計** | | **26 SP** | |

### 4.2 User Stories SP 分配

| US ID | 標題 | SP（原估） | SP（裁剪） | 備註 |
|-------|------|-----------|-----------|------|
| US-M17-001 | 網友申請開店 | 3 | 3 | - |
| US-M17-002 | Admin 審核開店申請 | 5 | 5 | - |
| US-M17-003 | Admin 管理 Feature Toggle | 3 | 3 | - |
| US-M17-004 | 店鋪更新 Profile | 2 | 2 | - |
| US-M17-005 | 查詢我的店鋪列表 | 1 | 1 | - |
| US-M17-006 | 店鋪成員管理 | 5 | 3 | Phase 1 簡化，無 email 邀請 |
| US-M17-007 | Admin 暫停/恢復/終止店鋪 | 3 | 3 | - |
| **合計** | | **22 SP** | **20 SP** | |

### 4.2 容量評估

| 項目 | 數值 |
|------|------|
| Sprint 容量 | 30 SP |
| 規劃 SP | 26 SP |
| Buffer | 4 SP (13%) |

### 4.3 裁剪方案（30 SP 約束）

| 裁剪項目 | 理由 |
|----------|------|
| 成員管理 Frontend（Task-M17-010） | Phase 1 核心功能，可延後至 Sprint 8 |
| 成員管理 Backend 簡化（Task-M17-005 改為 3 SP） | Phase 1 僅需基本 CRUD，不含邀請 email 機制 |

**裁剪後 SP**: 30 SP

---

## 5. 測試規劃

### 5.1 M17 Backend IT 覆蓋（完整版）

| TC ID | 描述 | US | 優先級 |
|-------|------|-----|--------|
| IT-M17-001 | 開店申請成功（所有必填欄位） | US-M17-001 | P0 |
| IT-M17-002 | 開店申請必填欄位驗證 + businessType Enum 驗證 | US-M17-001 | P0 |
| IT-M17-003a | 重複申請失敗（PENDING_REVIEW 租戶存在） | US-M17-001 | P0 |
| IT-M17-003b | 重複申請失敗（ACTIVE 租戶存在） | US-M17-001 | P0 |
| IT-M17-003c | 用戶已有 TERMINATED 租戶不可申請新店 | US-M17-001 | P0 |
| IT-M17-004 | Admin 審核通過 + Feature Toggle 初始化（驗證所有 10 個 Toggle 值） | US-M17-002 | P0 |
| IT-M17-005 | Admin 審核不通過（需填寫 rejectionReason） | US-M17-002 | P0 |
| IT-M17-005a | 非 Admin 執行審核 → E-1003 ACCESS_DENIED | US-M17-002 | P0 |
| IT-M17-005b | 嘗試審核非 PENDING_REVIEW 租戶 → E-2011 INVALID_TENANT_STATUS | US-M17-002 | P0 |
| IT-M17-005c | 審核不通過時 rejectionReason 未填寫 → E-1001 VALIDATION_ERROR | US-M17-002 | P0 |
| IT-M17-006 | Feature Toggle 查詢 | US-M17-003 | P0 |
| IT-M17-007 | Feature Toggle 更新 + audit_log 驗證 | US-M17-003 | P0 |
| IT-M17-007a | 非 Admin 更新 Toggle → E-1003 ACCESS_DENIED | US-M17-003 | P0 |
| IT-M17-007b | Toggle 更新後 Redis TTL 驗證（立即生效） | US-M17-003 | P0 |
| IT-M17-008 | 店鋪 Profile 更新 | US-M17-004 | P0 |
| IT-M17-008a | 嘗試更新非 ACTIVE 店鋪 → E-2011 INVALID_TENANT_STATUS | US-M17-004 | P0 |
| IT-M17-008b | StoreOwner 嘗試更新他人店鋪 → E-2002 CROSS_TENANT_ACCESS_DENIED | US-M17-004 | P0 |
| IT-M17-009 | 查詢我的店鋪列表 | US-M17-005 | P0 |
| IT-M17-010 | 成員新增/列表/移除 | US-M17-006 | P1 |
| IT-M17-010a | 成員角色更新（StoreOwner → StoreStaff） | US-M17-006 | P1 |
| IT-M17-010b | StoreOwner 嘗試移除自己 → 應拒絕 | US-M17-006 | P1 |
| IT-M17-010c | 不可重複新增相同成員 | US-M17-006 | P1 |
| IT-M17-011 | Admin 暫停/恢復/終止店鋪 | US-M17-007 | P0 |
| IT-M17-011a | ACTIVE → SUSPENDED → ACTIVE 恢復流程驗證 | US-M17-007 | P0 |
| IT-M17-011b | SUSPENDED → TERMINATED 終止流程驗證 | US-M17-007 | P0 |
| IT-M17-011c | 不合法狀態變更（ACTIVE → TERMINATED）→ E-2011 INVALID_TENANT_STATUS | US-M17-007 | P0 |
| IT-M17-011d | 狀態變更寫入 audit_log 驗證 | US-M17-007 | P0 |
| **合計** | | | **24 IT** |

### 5.2 M17 Frontend E2E 覆蓋

| AT ID | 描述 | 優先級 |
|-------|------|--------|
| AT-M17-001 | 開店申請流程 | P0 |
| AT-M17-002 | Admin 審核開店申請 | P0 |
| AT-M17-003 | Feature Toggle 更新 | P0 |
| AT-M17-004 | 店鋪 Profile 更新 | P0 |
| **合計** | | **4 AT** |

---

**文件狀態**: ⚠️ 草稿
**待確認**: Sprint 7 Planning 會議
