# Sprint 8 任務分解與技術評估

> **Sprint**: Sprint 8
> **期間**: 2026-06-08 ~ 2026-06-21
> **版本**: v1.0
> **建立日期**: 2026-05-03

---

## 1. M15 技術可行性評估（SD 角色）

### 1.1 架構影響

| 項目 | 評估 | 風險 |
|------|------|------|
| 資料庫變更 | 需要新增 `posts`, `post_embeds`, `post_categories`, `media_assets` 四張表 | 中 |
| API 變更 | 新增 17 個 API 端點 | 低 |
| 服務層變更 | 需要新增 PostService, PostEmbedService, MediaService | 中 |
| 現有功能影響 | 需與 M01/M02 Listing 整合獲取即時卡片資訊 | 低 |

### 1.2 技術風險

| 風險 | 等級 | 緩解措施 |
|------|------|----------|
| Markdown 解析複雜度 | 中 | 使用成熟 library（如 commonmark.js） |
| 嵌入卡片 Cascade 處理 | 中 | 確保 Listing INACTIVE 時卡片正確顯示「已下架」 |
| 媒體上傳進度追蹤 | 低 | 前端使用进度条组件 |

### 1.3 實作建議

1. **先建立資料模型**（Flyway Migration）
2. **實作 Post CRUD + Category 管理**
3. **實作 PostEmbed 解析與卡片 API**
4. **實作 Media 上傳功能**
5. **最後實作 Frontend**

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

### 2.2 M15 User Story SP 分配

| US ID | 標題 | 複雜度 | 不確定性 | SP |
|-------|------|--------|----------|-----|
| US-M15-001 | StoreOwner 建立 CMS 貼文 | 中 | 低 | 3 |
| US-M15-002 | StoreOwner 在貼文中嵌入商品/房型卡片 | 高 | 中 | 5 |
| US-M15-003 | StoreOwner 管理媒體庫 | 中 | 低 | 3 |
| US-M15-004 | 前台用戶瀏覽 CMS 貼文 | 低 | 低 | 2 |
| US-M15-005 | StoreOwner 管理貼文分類 | 低 | 低 | 2 |
| US-M15-006 | Admin 審核/管理店鋪貼文 | 中 | 低 | 3 |
| **合計** | | | | **18 SP** |

---

## 3. 任務分解

### 3.1 Sprint 8 容量規劃

| 項目 | 數值 |
|------|------|
| Sprint 容量 | 30 SP |
| 規劃 SP | 26 SP |
| Buffer | 4 SP (13%) |

### 3.2 任務清單

#### Task-M15-001: M15 資料庫 Migration 建立

| 項目 | 內容 |
|------|------|
| **US** | 所有 M15 User Stories |
| **SP** | 3 |
| **預估工時** | 4h |
| **任務內容** | |
| - | 建立 `V004__create_posts_table.sql` |
| - | 建立 `V005__create_post_embeds_table.sql` |
| - | 建立 `V006__create_post_categories_table.sql` |
| - | 建立 `V007__create_media_assets_table.sql` |
| - | 建立初始分類（預設分類） |
| **依賴** | M17 完成（tenant_id 外鍵） |
| **負責人** | Backend |

#### Task-M15-002: Post/PostEmbed Service 實作

| 項目 | 內容 |
|------|------|
| **US** | US-M15-001, US-M15-002, US-M15-004 |
| **SP** | 5 |
| **預估工時** | 8h |
| **任務內容** | |
| - | 建立 Post entity（含狀態機：DRAFT/PUBLISHED/ARCHIVED） |
| - | 建立 PostRepository |
| - | 建立 PostEmbed entity |
| - | 建立 PostEmbedRepository |
| - | 實作 `createPost()` - 建立貼文（含 embed 解析） |
| - | 實作 `updatePost()` - 更新貼文 |
| - | 實作 `publishPost()` - 發布貼文 |
| - | 實作 `unpublishPost()` - 下架貼文 |
| - | 實作 `deletePost()` - 刪除貼文 |
| - | 實作 `getPostBySlug()` - 前台取得貼文 |
| - | 實作 `embedParser()` - 解析 `{{embed:listing:<id>}}` 語法 |
| - | 實作 `validateEmbed()` - 檢查重複 ID 和存在性 |
| **依賴** | Task-M15-001 |
| **負責人** | Backend |

#### Task-M15-003: 嵌入卡片 API 實作

| 項目 | 內容 |
|------|------|
| **US** | US-M15-002, US-M15-004 |
| **SP** | 3 |
| **預估工時** | 4h |
| **任務內容** | |
| - | 建立 ListingCardResponse DTO |
| - | 實作 `getListingCard()` - 取得嵌入卡片資訊 |
| - | 整合 M01 Product 查詢（即時價格、庫存） |
| - | 整合 M02 Room 查詢（即時可用性、動態價格） |
| - | 處理 ROOM MAINTENANCE 狀態顯示 |
| - | 處理 INACTIVE Listing 卡片顯示「已下架」 |
| **依賴** | Task-M15-002, M01/M02 Listing Service |
| **負責人** | Backend |

#### Task-M15-004: Post API 端點實作

| 項目 | 內容 |
|------|------|
| **US** | US-M15-001, US-M15-004, US-M15-006 |
| **SP** | 3 |
| **預估工時** | 6h |
| **任務內容** | |
| - | `GET /api/v2/dashboard/posts` |
| - | `POST /api/v2/dashboard/posts` |
| - | `GET /api/v2/dashboard/posts/:id` |
| - | `PUT /api/v2/dashboard/posts/:id` |
| - | `DELETE /api/v2/dashboard/posts/:id` |
| - | `POST /api/v2/dashboard/posts/:id/publish` |
| - | `DELETE /api/v2/dashboard/posts/:id/publish` |
| - | `GET /api/v2/posts` (前台公開) |
| - | `GET /api/v2/posts/:slug` (前台詳情) |
| - | RBAC 整合（CMS_ENABLED Check） |
| **依賴** | Task-M15-002 |
| **負責人** | Backend |

#### Task-M15-005: 媒體庫 Service 實作

| 項目 | 內容 |
|------|------|
| **US** | US-M15-003 |
| **SP** | 3 |
| **預估工時** | 4h |
| **任務內容** | |
| - | 建立 MediaAsset entity |
| - | 建立 MediaAssetRepository |
| - | 實作 `uploadMedia()` - 處理檔案上傳（S3/MinIO） |
| - | 實作 `getMediaList()` - 媒體列表（分頁） |
| - | 實作 `deleteMedia()` - 刪除媒體 |
| - | 實作 `checkMediaUsage()` - 檢查是否被貼文引用 |
| - | 設定檔案大小限制（圖片 10MB, 影片 100MB, 文檔 5MB） |
| **依賴** | Task-M15-001 |
| **負責人** | Backend |

#### Task-M15-006: M15 Backend 整合測試

| 項目 | 內容 |
|------|------|
| **US** | 所有 US |
| **SP** | 3 |
| **預估工時** | 6h |
| **任務內容** | |
| - | IT-M15-001~020（20 個 IT） |
| - | 測試資料初始化 SQL |
| **測試案例** | |
| - | IT-M15-001: 建立貼文成功 |
| - | IT-M15-002: 建立貼文必填欄位驗證 |
| - | IT-M15-003/004: 嵌入商品/房型卡片 |
| - | IT-M15-005: 重複嵌入 → E-4001 |
| - | IT-M15-006: 嵌入不存在 → 404 |
| - | IT-M15-007: 發布/下架貼文 |
| - | IT-M15-008: 刪除貼文 |
| - | IT-M15-009: CMS_ENABLED=false 時不可操作 |
| - | IT-M15-010/011/012: 媒體上傳/列表/刪除 |
| - | IT-M15-013/014: 前台取得貼文 |
| - | IT-M15-015: 分類 CRUD |
| - | IT-M15-016: 非擁有者不可管理 |
| - | IT-M15-017/018: 嵌入卡片邊界條件 |
| - | IT-M15-019/020: 卡片 API 詳細測試 |
| **依賴** | Task-M15-002, Task-M15-003, Task-M15-004, Task-M15-005 |
| **負責人** | QA/Backend |

#### Task-M15-007: M15 Frontend 貼文列表頁

| 項目 | 內容 |
|------|------|
| **US** | US-M15-001 |
| **SP** | 3 |
| **預估工時** | 4h |
| **任務內容** | |
| - | `/dashboard/posts` 頁面 |
| - | 貼文列表元件（狀態篩選） |
| - | 新增貼文按鈕 |
| - | 發布/下架快捷操作 |
| **依賴** | Task-M15-004 |
| **負責人** | Frontend |

#### Task-M15-008: M15 Frontend 貼文編輯器頁面

| 項目 | 內容 |
|------|------|
| **US** | US-M15-001, US-M15-002 |
| **SP** | 3 |
| **預估工時** | 6h |
| **任務內容** | |
| - | `/dashboard/posts/new` 和 `/dashboard/posts/:id/edit` |
| - | Markdown 編輯器元件 |
| - | 嵌入卡片插入工具（搜尋 Listing） |
| - | 即時預覽功能 |
| - | 分類/標籤選擇器 |
| **依賴** | Task-M15-004, Task-M15-007 |
| **負責人** | Frontend |

#### Task-M15-009: M15 Frontend 嵌入卡片預覽

| 項目 | 內容 |
|------|------|
| **US** | US-M15-002 |
| **SP** | 2 |
| **預估工時** | 3h |
| **任務內容** | |
| - | 編輯器內即時預覽嵌入卡片 |
| - | 卡片元件（標題、價格、庫存/可用性、圖片） |
| - | 「已下架」狀態顯示 |
| **依賴** | Task-M15-003 |
| **負責人** | Frontend |

#### Task-M15-010: M15 Frontend 媒體庫頁面

| 項目 | 內容 |
|------|------|
| **US** | US-M15-003 |
| **SP** | 2 |
| **預估工時** | 3h |
| **任務內容** | |
| - | `/dashboard/media` 頁面 |
| - | 拖放上傳元件 |
| - | 媒體網格展示（縮圖） |
| - | 刪除確認對話框 |
| **依賴** | Task-M15-005 |
| **負責人** | Frontend |

---

## 4. Sprint 8 最終規劃

### 4.1 SP 分配

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

### 4.2 User Stories SP 分配

| US ID | 標題 | SP | 備註 |
|-------|------|-----|------|
| US-M15-001 | StoreOwner 建立 CMS 貼文 | 3 | - |
| US-M15-002 | StoreOwner 在貼文中嵌入商品/房型卡片 | 5 | - |
| US-M15-003 | StoreOwner 管理媒體庫 | 3 | - |
| US-M15-004 | 前台用戶瀏覽 CMS 貼文 | 2 | - |
| US-M15-005 | StoreOwner 管理貼文分類 | 2 | - |
| US-M15-006 | Admin 審核/管理店鋪貼文 | 3 | Phase 1 |
| **合計** | | **18 SP** | |

### 4.3 容量評估

| 項目 | 數值 |
|------|------|
| Sprint 容量 | 30 SP |
| 規劃 SP | 26 SP |
| Buffer | 4 SP (13%) |

---

## 5. 測試規劃

### 5.1 M15 Backend IT 覆蓋（完整版）

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

### 5.2 M15 Frontend E2E 覆蓋

| AT ID | 描述 | 優先級 |
|-------|------|--------|
| AT-M15-001 | 建立並發布 CMS 貼文（含嵌入卡片） | P0 |
| AT-M15-002 | 編輯已發布貼文 | P0 |
| AT-M15-003 | 上傳和管理媒體庫 | P0 |
| AT-M15-004 | 前台瀏覽貼文並點擊嵌入卡片 | P0 |
| **合計** | | **4 AT** |

---

## 6. 前置條件確認

### M17 已完成項目（M15 依賴）

| M17 完成項目 | M15 依賴說明 |
|-------------|-------------|
| ✅ Tenant/Store Management | M15 貼文需綁定 `tenant_id` |
| ✅ Feature Toggle (`CMS_ENABLED`) | M15 `RW*` 權限依賴此 Toggle |
| ✅ RBAC 整合 | M15 角色權限矩陣已建立 |
| ✅ 多租戶隔離機制 | M15 媒體庫按 Tenant 分桶 |

### 需要整合的現有模組

| 模組 | 整合點 |
|------|--------|
| M01 (Product) | 嵌入卡片 API：`getListingCard()` 查詢即時價格/庫存 |
| M02 (Room) | 嵌入卡片 API：查詢即時可用性/動態價格 |
| M03 (JWT/RBAC) | 認證與授權 |

---

**文件狀態**: ⚠️ 草稿
**待確認**: Sprint 8 Planning 會議