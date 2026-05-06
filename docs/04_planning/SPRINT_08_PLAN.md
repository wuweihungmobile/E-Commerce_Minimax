# Sprint 8 計劃 / Sprint 8 Plan

> **Sprint 編號**: Sprint 8
> **期間**: 2026-06-08 ~ 2026-06-21 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-05-03
> **更新日期**: 2026-05-03
> **基於**: Sprint 7 M17 完成 + Phase 2-A MoSCoW 優先序

---

## 🔴 人機協作確認點

### Sprint 7 總結摘要
**已完成**:
- M17 租戶/店鋪管理核心功能 ✅
- M17 Backend API（13 端點）✅
- M17 Frontend 頁面（開店申請/店鋪設定/Feature Toggle/Admin 管理）✅
- Feature Toggle 機制完成 ✅
- 編譯成功，235 測試全數通過 ✅

### M17 完成作為 M15 前置條件

| M17 完成項目 | M15 依賴說明 |
|-------------|-------------|
| Tenant/Store Management | M15 貼文需綁定 tenant_id |
| Feature Toggle (CMS_ENABLED) | M15 CMS RW* 權限依賴此 Toggle |
| RBAC 整合 | M15 角色權限矩陣（Seller/Host/StoreOwner/StoreStaff）|
| 多租戶隔離機制 | M15 媒體庫按 Tenant 分桶 |

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 8 |
| **開始日期** | 2026-06-08 |
| **結束日期** | 2026-06-21 |
| **Sprint 容量** | 30 SP |
| **規劃 SP** | 26 SP |
| **Buffer** | 4 SP (13%) |
| **團隊** | 2 人 Dev Team |

---

## 2. Sprint 目標

> **目標**: 完成 M15 CMS 內容管理系統核心功能（貼文 CRUD、嵌入購買卡片、媒體庫），為 M16 ERP 提供庫存與 CMS 內容整合基礎。

### 具體目標

#### M15 Backend API（完整清單，共 20 個端點）
1. **貼文列表** - GET /api/v2/dashboard/posts
2. **貼文詳情** - GET /api/v2/posts/:slug
3. **建立貼文** - POST /api/v2/dashboard/posts
4. **更新貼文** - PUT /api/v2/dashboard/posts/:id
5. **發布貼文** - POST /api/v2/dashboard/posts/:id/publish
6. **下架貼文** - DELETE /api/v2/dashboard/posts/:id/publish
7. **刪除貼文** - DELETE /api/v2/dashboard/posts/:id
8. **嵌入卡片** - GET /api/v2/listings/:id/card
9. **媒體上傳** - POST /api/v2/media/upload
10. **媒體列表** - GET /api/v2/dashboard/media
11. **刪除媒體** - DELETE /api/v2/dashboard/media/:id
12. **分類管理** - GET/POST/PUT/DELETE /api/v2/dashboard/post-categories

#### M15 Frontend (Dashboard)
1. **貼文列表頁** - /dashboard/posts
2. **貼文編輯器頁** - /dashboard/posts/new, /dashboard/posts/:id/edit
3. **嵌入卡片預覽** - 編輯器內即時預覽
4. **媒體庫頁面** - /dashboard/media

---

## 3. M15 需求確認摘要

### 3.1 核心功能說明 ✅ 已確認

| 功能 | 說明 | 優先級 |
|------|------|--------|
| 貼文 CRUD | 建立/編輯/刪除/發布貼文 | P0 |
| 富文本編輯 | Markdown 編輯器 | P0 |
| **嵌入購買卡片** | 在貼文中嵌入商品或房型的即時購買卡片 (含價格、庫存、加入購物車按鈕) | P0 |
| 媒體庫 | 上傳/管理圖片與影片，按 Tenant 隔離 | P0 |
| 分類與標籤 | 貼文分類管理與標籤搜尋 | P1 |

### 3.2 PostEmbed 機制 ✅ 已確認

```
<!-- 貼文 Markdown 內容 -->
這個月最推薦的 3C 好物！

{{embed:listing:550e8400-e29b-41d4-a716-446655440000}}

最近熱門的高雄民宿：

{{embed:listing:660e8400-e29b-41d4-a716-446655440001}}
```

**驗證規則**:
- 重複 `listing_id` → HTTP 400, E-4001, `EMBED_DUPLICATE_LISTING`
- `listing_id` 不存在或非 ACTIVE → HTTP 404

### 3.3 RBAC 權限（M15 相關）✅ 已確認

| 功能 | Seller | Host | StoreOwner | StoreStaff | Admin | SuperAdmin |
|------|--------|------|------------|------------|-------|------------|
| CMS 貼文 | RW* | RW* | RWD* | RW* | RWD | RWD |
| M15 媒體庫 | RW* | RW* | RWD* | RW* | RWD | RWD |

`*` = 受 `CMS_ENABLED` Feature Toggle 限制

---

## 4. User Stories 摘要

| US ID | 標題 | SP | 優先級 |
|-------|------|-----|--------|
| US-M15-001 | StoreOwner 建立 CMS 貼文 | 3 | P0 |
| US-M15-002 | StoreOwner 在貼文中嵌入商品/房型卡片 | 5 | P0 |
| US-M15-003 | StoreOwner 管理媒體庫 | 3 | P0 |
| US-M15-004 | 前台用戶瀏覽 CMS 貼文 | 2 | P0 |
| US-M15-005 | StoreOwner 管理貼文分類 | 2 | P1 |
| US-M15-006 | Admin 審核/管理店鋪貼文 | 3 | P1 |
| **合計** | | **18 SP** | |

詳細 User Stories 見: [SPRINT_08_USER_STORIES.md](docs/04_planning/SPRINT_08_USER_STORIES.md)

---

## 5. 任務分解

| 任務 ID | 任務名稱 | SP | 負責人 |
|---------|----------|-----|--------|
| Task-M15-001 | M15 資料庫 Migration 建立 | 3 | Backend |
| Task-M15-002 | Post/PostEmbed Service 實作 | 5 | Backend |
| Task-M15-003 | 嵌入卡片 API 實作 | 3 | Backend |
| Task-M15-004 | Post API 端點實作 | 3 | Backend |
| Task-M15-005 | 媒體庫 Service 實作 | 3 | Backend |
| Task-M15-006 | M15 Backend 整合測試 | 3 | QA/Backend |
| Task-M15-007 | M15 Frontend 貼文列表頁 | 3 | Frontend |
| Task-M15-008 | M15 Frontend 貼文編輯器頁面 | 3 | Frontend |
| Task-M15-009 | M15 Frontend 嵌入卡片預覽 | 2 | Frontend |
| Task-M15-010 | M15 Frontend 媒體庫頁面 | 2 | Frontend |
| **合計** | | **26 SP** | |

**裁剪後**: 30 SP 容量，26 SP 規劃，4 SP buffer (13%)

詳細任務分解見: [SPRINT_08_TASKS.md](docs/05_development/SPRINT_08_TASKS.md)

---

## 6. 測試規劃

### 6.1 Backend IT 覆蓋（完整版 - 20 IT）

| TC ID | 描述 | US | 優先級 |
|-------|------|-----|--------|
| IT-M15-001 | 建立貼文成功（所有必填欄位） | US-M15-001 | P0 |
| IT-M15-002 | 建立貼文必填欄位驗證 | US-M15-001 | P0 |
| IT-M15-003 | 嵌入商品卡片成功（格式正確） | US-M15-002 | P0 |
| IT-M15-004 | 嵌入房型卡片成功（格式正確） | US-M15-002 | P0 |
| IT-M15-005 | 重複嵌入相同 listing_id → E-4001 | US-M15-002 | P0 |
| IT-M15-006 | 嵌入不存在的 listing_id → 404 | US-M15-002 | P0 |
| IT-M15-007 | 發布貼文成功 | US-M15-001 | P0 |
| IT-M15-008 | 刪除已發布貼文 | US-M15-001 | P0 |
| IT-M15-009 | CMS_ENABLED=false 時不可建立貼文 | US-M15-001 | P0 |
| IT-M15-010 | 媒體上傳成功 | US-M15-003 | P0 |
| IT-M15-011 | 媒體列表查詢（按 tenant 分隔） | US-M15-003 | P0 |
| IT-M15-012 | 刪除媒體成功 | US-M15-003 | P0 |
| IT-M15-013 | 前台取得貼文列表（公開） | US-M15-004 | P0 |
| IT-M15-014 | 前台取得貼文詳情（包含嵌入卡片） | US-M15-004 | P0 |
| IT-M15-015 | 分類新增/列表/刪除 | US-M15-005 | P1 |
| IT-M15-016 | 非 StoreOwner 不可管理他店貼文 | US-M15-006 | P0 |
| IT-M15-017 | 嵌入 INACTIVE Listing 的卡片顯示「已下架」 | US-M15-002 | P0 |
| IT-M15-018 | Markdown 語法錯誤的 embed 標記自動忽略 | US-M15-002 | P1 |
| IT-M15-019 | 取得嵌入卡片（PRODUCT 類型） | US-M15-002 | P0 |
| IT-M15-020 | 取得嵌入卡片（ROOM 類型，MAINTENANCE 狀態） | US-M15-002 | P0 |
| **合計** | | | **20 IT** |

### 6.2 Frontend E2E 覆蓋

| AT ID | 描述 | 優先級 |
|-------|------|--------|
| AT-M15-001 | 建立並發布 CMS 貼文（含嵌入卡片） | P0 |
| AT-M15-002 | 編輯已發布貼文 | P0 |
| AT-M15-003 | 上傳和管理媒體庫 | P0 |
| AT-M15-004 | 前台瀏覽貼文並點擊嵌入卡片 | P0 |
| **合計** | | **4 AT** |

---

## 7. Phase 2-A 依賴關係

```
M17 租戶管理 (Sprint 7) ✅ ─┐
                            ├─> M15 CMS (Sprint 8) ← 當前
                            │
                            └─> M16 ERP (Sprint 9)
```

**M15 為何需要 M17 前置？**
- M15 `RW*` 權限依賴 `CMS_ENABLED` Feature Toggle
- M15 媒體庫按 Tenant 分桶隔離
- M15 貼文需關聯 tenant_id

---

## 8. 下一步行動

| 行動項 | 負責人 | 優先級 | 狀態 |
|--------|--------|--------|------|
| Sprint 8 Goals 確認 | PM | P0 | ✅ 待確認 |
| M15 詳細需求分析 | SA | P0 | ✅ 已完成 |
| M15 User Story 細化 | SA + BA | P0 | ✅ 已完成 |
| M15 技術評估 | SD | P1 | ✅ 已完成 |
| M15 工作量估算 | Dev | P1 | ✅ 已完成 |

---

**文件狀態**: ⚠️ 草稿（待 Sprint 8 Planning 會議確認）
**相關文件**:
- [SPRINT_08_USER_STORIES.md](docs/04_planning/SPRINT_08_USER_STORIES.md)
- [SPRINT_08_TASKS.md](docs/05_development/SPRINT_08_TASKS.md)