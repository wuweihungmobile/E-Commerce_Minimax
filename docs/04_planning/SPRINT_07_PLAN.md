# Sprint 7 計劃 / Sprint 7 Plan

> **Sprint 編號**: Sprint 7
> **期間**: 2026-05-25 ~ 2026-06-07 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.1
> **建立日期**: 2026-05-01
> **更新日期**: 2026-05-01
> **基於**: Sprint 6 完成 + Phase 1 完成 + Phase 2-A MoSCoW 優先序

---

## 🔴 人機協作確認點

### Sprint 6 總結摘要
**已完成**:
- DEF-001 環境建設 ✅ (統一端點 + Feature Toggle + 測試資料)
- M01/M02 Frontend 商品/房源管理頁面 ✅
- M01/M02 Backend API 測試 ✅ (25 TC 100% Pass)
- M12 動態定價 ✅ (9 UT + 8 IT 100% Pass)
- Booking E2E 測試 ✅ (13 AT 100% Pass)

### Phase 1 完成狀態

| 模組 | 功能 | 狀態 |
|------|------|------|
| M03 | 會員系統核心 (JWT + RBAC) | ✅ 已完成 |
| M01 | 商品中心核心 (列表/詳情/搜尋) | ✅ 已完成 |
| M02 | 房源中心核心 (列表/日期格查詢) | ✅ 已完成 |
| M05 | 訂單履約最小子集 (建立/狀態機/取消) | ✅ 已完成 |
| M06 | 預訂 (查詢/取消，無 POST 建立) | ✅ 已完成 |
| M12 | 動態定價引擎 | ✅ 已完成 (Sprint 6) |

### Phase 2-A MoSCoW 優先序（v1.0）

| 優先序 | 模組 | 功能 | Sprint 7 範圍 |
|:----:|------|------|--------------|
| 1. | M17 | 租戶/店鋪管理 ★ 搶先實作 | ✅ 第一優先 |
| 2. | M12 | 動態定價引擎 | ✅ 已完成 |
| 3. | M15 | CMS 內容管理 | 規劃中 |
| 4. | M16 | ERP 進銷存 | 規劃中 |

> **重要**: M17 必須在其他 Phase 2-A 模組前實作，因為 M15/M16 的 RBAC `*` 限制依賴 M17 的 Feature Toggle 查詢。

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 7 |
| **開始日期** | 2026-05-25 |
| **結束日期** | 2026-06-07 |
| **Sprint 容量** | 30 SP |
| **規劃 SP** | 26 SP |
| **Buffer** | 4 SP (13%) |
| **團隊** | 2 人 Dev Team |

> **QA 驗證備註**: 原文件 Section 1 有兩處計算錯誤，已修正。Task 合計 26 SP，Buffer = 30 - 26 = 4 SP (13%)。

---

## 2. Sprint 目標

> **目標**: 完成 M17 租戶/店鋪管理核心功能（開店申請/審核/Feature Toggle），為 M15 CMS 和 M16 ERP 提供 RBAC 基礎設施。

### 具體目標

#### M17 Backend API（完整清單，共 13 個端點）
1. **開店申請 API** - POST /api/v2/tenants/apply
2. **我的店鋪列表** - GET /api/v2/tenants/my
3. **店鋪詳情** - GET /api/v2/tenants/:id
4. **更新店鋪 Profile** - PUT /api/v2/tenants/:id
5. **租戶列表（Admin）** - GET /api/v2/admin/tenants
6. **審核開店申請** - PUT /api/v2/admin/tenants/:id/review
7. **暫停/恢復/終止** - PUT /api/v2/admin/tenants/:id/status
8. **Feature Toggle 查詢** - GET /api/v2/admin/tenants/:id/features
9. **Feature Toggle 更新** - PUT /api/v2/admin/tenants/:id/features
10. **成員列表** - GET /api/v2/tenants/:id/members（Phase 1）
11. **新增成員** - POST /api/v2/tenants/:id/members（Phase 1）
12. **更新成員角色** - PUT /api/v2/tenants/:id/members/:userId/role（Phase 1）
13. **移除成員** - DELETE /api/v2/tenants/:id/members/:userId（Phase 1）

#### M17 Frontend (Dashboard)
6. **開店申請頁面** - /dashboard/apply
7. **店鋪設定頁面** - /dashboard/settings
8. **Feature Toggle 管理頁面** - /dashboard/admin/features
9. **Admin 租戶管理頁面** - /dashboard/admin/tenants

---

## 3. M17 需求確認摘要

### 3.1 審核流程狀態機 ✅ 已確認

```
PENDING_REVIEW ──┬──→ ACTIVE (審核通過)
                 │
                 └──→ REJECTED (審核不通過)

ACTIVE ─────────→ SUSPENDED (違規暫停)
SUSPENDED ──────→ ACTIVE (恢復)
               ──→ TERMINATED (永久終止)
```

**狀態定義**:
| 狀態 | 說明 |
|------|------|
| PENDING_REVIEW | 網友提交開店申請，等待 Admin 審核 |
| ACTIVE | 審核通過，店鋪正常運營 |
| REJECTED | 審核未通過 |
| SUSPENDED | 因違規被暫停，商品下架、訂單暫停受理 |
| TERMINATED | 永久關閉 |

### 3.2 Feature Toggle 初始值 ✅ 已確認

當 `tenants.status` 從 `PENDING_REVIEW` → `ACTIVE` 時，自動依 businessType 初始化：

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

### 3.3 開店申請表單欄位 ✅ 已確認

| 欄位 | 類型 | 必填 | 說明 |
|------|------|------|------|
| storeName | String | ✅ | 店鋪名稱 |
| storeDescription | String | ❌ | 店鋪描述 |
| businessType | Enum | ✅ | RETAIL_ONLY / BOOKING_ONLY / HYBRID |
| contactEmail | String | ✅ | 聯絡 Email |
| contactPhone | String | ❌ | 聯絡電話 |

---

## 4. User Stories 摘要

| US ID | 標題 | SP | 優先級 |
|-------|------|-----|--------|
| US-M17-001 | 網友申請開店 | 3 | P0 |
| US-M17-002 | Admin 審核開店申請 | 5 | P0 |
| US-M17-003 | Admin 管理 Feature Toggle | 3 | P0 |
| US-M17-004 | 店鋪更新 Profile | 2 | P0 |
| US-M17-005 | 查詢我的店鋪列表 | 1 | P0 |
| US-M17-006 | 店鋪成員管理（Phase 1 簡化） | 3 | P1 (Backend) |
| US-M17-007 | Admin 暫停/恢復/終止店鋪 | 3 | P1 |
| **合計** | | **20 SP**（裁剪後） | |

**裁剪說明**: US-M17-006 從 5 SP 裁剪至 3 SP，Phase 1 不含 email 邀請機制

詳細 User Stories 見: [SPRINT_07_USER_STORIES.md](docs/04_planning/SPRINT_07_USER_STORIES.md)

---

## 5. 任務分解

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
| **合計** | | **26 SP**（裁剪後） | |

**裁剪後**: 30 SP 容量，26 SP 規劃，4 SP buffer (13%)

詳細任務分解見: [SPRINT_07_TASKS.md](docs/05_development/SPRINT_07_TASKS.md)

---

## 6. 測試規劃

### 6.1 Backend IT 覆蓋（完整版 - 24 IT）

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
| IT-M17-010a | 成員角色更新 | US-M17-006 | P1 |
| IT-M17-010b | StoreOwner 嘗試移除自己 → 應拒絕 | US-M17-006 | P1 |
| IT-M17-010c | 不可重複新增相同成員 | US-M17-006 | P1 |
| IT-M17-011 | Admin 暫停/恢復/終止店鋪 | US-M17-007 | P0 |
| IT-M17-011a | ACTIVE → SUSPENDED → ACTIVE 恢復流程驗證 | US-M17-007 | P0 |
| IT-M17-011b | SUSPENDED → TERMINATED 終止流程驗證 | US-M17-007 | P0 |
| IT-M17-011c | 不合法狀態變更（ACTIVE → TERMINATED）→ E-2011 INVALID_TENANT_STATUS | US-M17-007 | P0 |
| IT-M17-011d | 狀態變更寫入 audit_log 驗證 | US-M17-007 | P0 |
| **合計** | | | | **24 IT** |

### 6.2 Frontend E2E 覆蓋

> **QA 驗證備註**: Sprint 7 AT 數量為 4 個（原 5 個含 Sprint 8 延續項目 AT-M17-005 已移除）。

| AT ID | 描述 | 優先級 |
|-------|------|--------|
| AT-M17-001 | 開店申請流程 | P0 |
| AT-M17-002 | Admin 審核開店申請 | P0 |
| AT-M17-003 | Feature Toggle 更新 | P0 |
| AT-M17-004 | 店鋪 Profile 更新 | P0 |
| **合計** | | **4 AT**（不含 Sprint 8 延續） |

---

## 7. Phase 2-A 依賴關係

```
M17 租戶管理 (Sprint 7) ─┐
                         ├─> M15 CMS (Sprint 8) ─┐
                         │                       ├─> M16 ERP (Sprint 9)
                         │                       │
                         └───────────────────────┘
                                 ↓
                    所有 Phase 2-A 模組依賴 M17 的 Feature Toggle
```

**為什麼 M17 必須第一個實作？**
- M15 (CMS) 的 `RW*` 依賴 `CMS_ENABLED` Toggle
- M16 (ERP) 的 `RW*` 依賴 `ERP_ENABLED` Toggle
- 無 M17 的 Toggle 查詢機制，其他模組無法正確實作 Feature Toggle 檢查

---

## 8. 下一步行動

| 行動項 | 負責人 | 優先級 | 狀態 |
|--------|--------|--------|------|
| Sprint 7 Goals 確認 | PM | P0 | ✅ 待確認 |
| M17 詳細需求分析 | SA | P0 | ✅ 已完成 |
| M17 User Story 細化 | SA + BA | P0 | ✅ 已完成 |
| M17 技術評估 | SD | P1 | ✅ 已完成 |
| M17 工作量估算 | Dev | P1 | ✅ 已完成 |

---

**文件狀態**: ⚠️ 草稿（待 Sprint 7 Planning 會議確認）
**相關文件**:
- [SPRINT_07_USER_STORIES.md](docs/04_planning/SPRINT_07_USER_STORIES.md)
- [SPRINT_07_TASKS.md](docs/05_development/SPRINT_07_TASKS.md)