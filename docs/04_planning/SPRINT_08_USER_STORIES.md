# Sprint 8 User Stories — M15 內容管理系統 (CMS)

> **Sprint**: Sprint 8
> **模組**: M15 內容管理系統 (CMS)
> **版本**: v1.0
> **建立日期**: 2026-05-03
> **依據**: PRD §6.6, §9.12, §4.3-4.4

---

## User Story 總覽

| US ID | 標題 | 角色 | SP | 優先級 |
|-------|------|------|-----|--------|
| US-M15-001 | StoreOwner 建立 CMS 貼文 | StoreOwner | 3 | P0 |
| US-M15-002 | StoreOwner 在貼文中嵌入商品/房型卡片 | StoreOwner | 5 | P0 |
| US-M15-003 | StoreOwner 管理媒體庫 | StoreOwner | 3 | P0 |
| US-M15-004 | 前台用戶瀏覽 CMS 貼文 | Guest/Buyer | 2 | P0 |
| US-M15-005 | StoreOwner 管理貼文分類 | StoreOwner | 2 | P0 |
| US-M15-006 | Admin 審核/管理店鋪貼文 | Admin | 3 | P1 |

---

## US-M15-001: StoreOwner 建立 CMS 貼文

**ID**: US-M15-001
**標題**: 作為 StoreOwner，我想建立 CMS 貼文，以便透過內容行銷推廣我的商品與民宿
**優先級**: P0
**Story Points**: 3

### 驗收標準 (AC)

| AC ID | 標準描述 | 測試類型 |
|-------|----------|----------|
| AC-001 | StoreOwner 可建立新貼文（標題、內容、分類必填） | IT |
| AC-002 | 貼文內容支援 Markdown 格式 | IT |
| AC-003 | 成功建立後狀態為 DRAFT（草稿） | IT |
| AC-004 | 可直接發布貼文（狀態改為 PUBLISHED） | IT |
| AC-005 | 可將已發布的貼文下架（狀態改為 DRAFT） | IT |
| AC-006 | 可編輯已存在的貼文 | IT |
| AC-007 | 可刪除自己的貼文（僅 DRAFT 狀態或已下架） | IT |
| AC-008 | `CMS_ENABLED=false` 時不可建立/編輯貼文，回傳 `E-2004 Feature disabled for tenant` | IT |
| AC-009 | StoreOwner 不可編輯他店的貼文，回傳 `E-4031 Not authorized to operate this store` | IT |

### 技術備註

- **API**:
  - `POST /api/v2/dashboard/posts` (建立)
  - `PUT /api/v2/dashboard/posts/:id` (更新)
  - `DELETE /api/v2/dashboard/posts/:id` (刪除)
  - `POST /api/v2/dashboard/posts/:id/publish` (發布)
  - `DELETE /api/v2/dashboard/posts/:id/publish` (下架)
- **請求欄位**: title (String, 必填), content (String, 必填, Markdown), categoryId (UUID, 選填), tags (String[], 選填), featuredImageUrl (String, 選填)
- **Feature Toggle**: `CMS_ENABLED` 為 false 時，所有 RW* 操作被拒絕

---

## US-M15-002: StoreOwner 在貼文中嵌入商品/房型卡片

**ID**: US-M15-002
**標題**: 作為 StoreOwner，我想在貼文中嵌入商品/房型卡片，以便讀者直接購買
**優先級**: P0
**Story Points**: 5

### 驗收標準 (AC)

| AC ID | 標準描述 | 測試類型 |
|-------|----------|----------|
| AC-001 | 在 Markdown 內容中使用 `{{embed:listing:<listing_id>}}` 語法嵌入卡片 | IT |
| AC-002 | 嵌入卡片顯示即時價格、庫存/可用性、封面圖 | IT |
| AC-003 | 重複嵌入相同 listing_id → HTTP 400, E-4104, `EMBED_DUPLICATE_LISTING` | IT |
| AC-004 | 嵌入不存在的 listing_id → HTTP 404 | IT |
| AC-005 | 嵌入非 ACTIVE 狀態的 Listing → 卡片顯示「已下架」 | IT |
| AC-006 | 嵌入 ROOM 類型且日曆為 MAINTENANCE → `available=false`, `statusReason="under_maintenance"` | IT |
| AC-007 | Markdown 語法錯誤的 embed 標記自動忽略（warning log） | IT |

### 技術備註

- **嵌入語法**: `{{embed:listing:<listing_id>}}`
- **解析時機**: POST/PUT 貼文時解析並存入 `post_embeds` 表
- **卡片 API**: `GET /api/v2/listings/:id/card` 返回輕量卡片資訊
- **嵌入卡片Response**:
```json
{
  "listingId": "550e8400-...",
  "listingType": "PRODUCT",
  "title": "AirPods Pro 2",
  "coverImageUrl": "https://...",
  "basePrice": 7490,
  "currentPrice": 6990,
  "currency": "TWD",
  "availability": {
    "inStock": true,
    "availableQty": 42
  },
  "tenantName": "3C 達人小舖",
  "ctaUrl": "/products/550e8400-..."
}
```

---

## US-M15-003: StoreOwner 管理媒體庫

**ID**: US-M15-003
**標題**: 作為 StoreOwner，我想管理媒體庫，以便上傳和管理圖片與影片
**優先級**: P0
**Story Points**: 3

### 驗收標準 (AC)

| AC ID | 標準描述 | 測試類型 |
|-------|----------|----------|
| AC-001 | 可上傳圖片（JPG/PNG/GIF/WebP，最大 10MB） | IT |
| AC-002 | 可上傳影片（MP4/MOV，最大 100MB） | IT |
| AC-003 | 可上傳文檔（PDF，最大 5MB） | IT |
| AC-004 | 媒體列表按上傳時間倒序排列 | IT |
| AC-005 | 可刪除自己上傳的媒體（不被任何貼文引用） | IT |
| AC-006 | 媒體按 Tenant 隔離，不可見他店媒體 | IT |
| AC-007 | 存儲路徑: `/{tenant_id}/media/*` (S3/MinIO) | IT |

### 技術備註

- **API**:
  - `POST /api/v2/media/upload` (上傳)
  - `GET /api/v2/dashboard/media` (列表)
  - `DELETE /api/v2/dashboard/media/:id` (刪除)
- **檔案限制**: 圖片 10MB, 影片 100MB, 文檔 5MB
- **存儲**: S3/MinIO，按 Tenant 分桶

---

## US-M15-004: 前台用戶瀏覽 CMS 貼文

**ID**: US-M15-004
**標題**: 作為前台用戶（Guest/Buyer），我想瀏覽店鋪的 CMS 貼文，以便了解促銷資訊
**優先級**: P0
**Story Points**: 2

### 驗收標準 (AC)

| AC ID | 標準描述 | 測試類型 |
|-------|----------|----------|
| AC-001 | 可取得店鋪的已發布貼文列表 | IT |
| AC-002 | 可取得單篇貼文詳情（包含解析後的嵌入卡片） | IT |
| AC-003 | 已下架或草稿狀態的貼文不可被前台取得 | IT |
| AC-004 | 嵌入卡片顯示即時資訊（非過期快取） | IT |

### 技術備註

- **API**:
  - `GET /api/v2/posts` (公開列表，篩選 PUBLISHED 狀態)
  - `GET /api/v2/posts/:slug` (詳情，支援 slug 查詢)
- **slug 格式**: URL-friendly identifier（可為 title 或 UUID）

---

## US-M15-005: StoreOwner 管理貼文分類

**ID**: US-M15-005
**標題**: 作為 StoreOwner，我想管理貼文分類，以便組織內容
**優先級**: P1
**Story Points**: 2

### 驗收標準 (AC)

| AC ID | 標準描述 | 測試類型 |
|-------|----------|----------|
| AC-001 | 可新增分類（名稱、描述） | IT |
| AC-002 | 可編輯分類名稱/描述 | IT |
| AC-003 | 可刪除分類（無貼文關聯時） | IT |
| AC-004 | 分類按 Tenant 隔離 | IT |
| AC-005 | 刪除有貼文關聯的分類 → 回傳 error | IT |

### 技術備註

- **API**:
  - `GET /api/v2/dashboard/post-categories` (列表)
  - `POST /api/v2/dashboard/post-categories` (新增)
  - `PUT /api/v2/dashboard/post-categories/:id` (更新)
  - `DELETE /api/v2/dashboard/post-categories/:id` (刪除)

---

## US-M15-006: Admin 審核/管理店鋪貼文

**ID**: US-M15-006
**標題**: 作為 Admin，我想管理店鋪的貼文，以便監督內容合規性
**優先級**: P1
**Story Points**: 3

### 驗收標準 (AC)

| AC ID | 標準描述 | 測試類型 |
|-------|----------|----------|
| AC-001 | Admin 可查看所有店鋪的貼文列表 | IT |
| AC-002 | Admin 可對特定店鋪的貼文執行下架操作 | IT |
| AC-003 | Admin 可刪除違規貼文 | IT |
| AC-004 | Admin 操作需寫入 audit_log | IT |

### 技術備註

- **API**: Admin 專用端點（需 `ROLE_SUPER_ADMIN`）
- **Audit Log**: 記錄 admin_id, action, target_post_id, timestamp

---

## M15 Backend API 清單

| 方法 | 端點 | 對應 US | 備註 |
|------|------|---------|------|
| GET | `/api/v2/dashboard/posts` | US-M15-001 | 貼文列表（草稿+已發布） |
| POST | `/api/v2/dashboard/posts` | US-M15-001 | 建立貼文 |
| GET | `/api/v2/dashboard/posts/:id` | US-M15-001 | 貼文詳情 |
| PUT | `/api/v2/dashboard/posts/:id` | US-M15-001 | 更新貼文 |
| DELETE | `/api/v2/dashboard/posts/:id` | US-M15-001 | 刪除貼文 |
| POST | `/api/v2/dashboard/posts/:id/publish` | US-M15-001 | 發布貼文 |
| DELETE | `/api/v2/dashboard/posts/:id/publish` | US-M15-001 | 下架貼文 |
| GET | `/api/v2/listings/:id/card` | US-M15-002 | 嵌入卡片 API |
| GET | `/api/v2/posts` | US-M15-004 | 前台公開貼文列表 |
| GET | `/api/v2/posts/:slug` | US-M15-004 | 前台貼文詳情 |
| POST | `/api/v2/media/upload` | US-M15-003 | 媒體上傳 |
| GET | `/api/v2/dashboard/media` | US-M15-003 | 媒體列表 |
| DELETE | `/api/v2/dashboard/media/:id` | US-M15-003 | 刪除媒體 |
| GET | `/api/v2/dashboard/post-categories` | US-M15-005 | 分類列表 |
| POST | `/api/v2/dashboard/post-categories` | US-M15-005 | 新增分類 |
| PUT | `/api/v2/dashboard/post-categories/:id` | US-M15-005 | 更新分類 |
| DELETE | `/api/v2/dashboard/post-categories/:id` | US-M15-005 | 刪除分類 |

> **Phase 2 擴展**: 排程發布（`POST /api/v2/dashboard/posts/:id/schedule`）延至 Sprint 9+

---

## M15 Frontend 頁面需求

| 頁面 | 路徑 | 對應 US | 優先級 |
|------|------|---------|--------|
| 貼文列表頁 | `/dashboard/posts` | US-M15-001, US-M15-004 | P0 |
| 貼文編輯器頁 | `/dashboard/posts/new` | US-M15-001, US-M15-002 | P0 |
| 貼文編輯器頁 | `/dashboard/posts/:id/edit` | US-M15-001, US-M15-002 | P0 |
| 媒體庫頁面 | `/dashboard/media` | US-M15-003 | P0 |
| 分類管理 | `/dashboard/posts/categories` | US-M15-005 | P1 |

---

**文件狀態**: ✅ 草稿
**待確認**: Sprint 8 Planning 會議