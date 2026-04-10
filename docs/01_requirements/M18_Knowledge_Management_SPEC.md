# M18 知識管理模組 / Knowledge Management Module

> **模組狀態**: 新增（v0.9_R03）
> **建立日期**: 2026-04-09
> **作者**: Claude Code (SA Agent)
> **版本**: v1.0

---

## 1. 模組定位

### 1.1 基本資訊

| 屬性 | 說明 |
|------|------|
| **模組編號** | M18 |
| **模組名稱** | Knowledge Management System (知識管理系統) |
| **所屬子系統** | Platform Infrastructure |
| **核心能力** | 知識庫、FAQ、客服系統、媒體資產分類 |
| **依賴模組** | M03 (認證)、M17 (租戶) |
| **Phase 歸屬** | Phase 2-A（知識庫、FAQ）/ Phase 2-B（客服系統） |

### 1.2 功能總覽

| 功能群組 | 說明 | 優先級 | Phase |
|---------|------|--------|-------|
| **媒體中心** | 媒體資產上傳、分類、管理 | P0 | Phase 1（整合 M15） |
| **知識庫** | 文章/教程的分類、發布、搜尋 | P1 | Phase 2-A |
| **FAQ** | 常見問題分類、解答、管理 | P1 | Phase 2-A |
| **客服系統** | 工單提交、處理、回覆 | P2 | Phase 2-B |

---

## 2. 媒體中心 (Media Center)

### 2.1 功能定位

| 屬性 | 說明 |
|------|------|
| **功能群組** | 媒體中心 |
| **說明** | 統一管理所有媒體資產（圖片、影片、文檔），支援分類標籤 |
| **依賴** | M15 CMS 媒體庫（已存在，進行強化） |

### 2.2 功能描述

| 功能 | 說明 | 優先級 |
|------|------|--------|
| 媒體上傳 | 支援圖片（JPG/PNG/GIF/WebP）、影片（MP4/MOV）、文檔（PDF） | P0 |
| 媒體分類 | 支援自訂分類（如：商品圖、房型圖、部落格素材、廣告素材） | P0 |
| 媒體標籤 | 支援多標籤管理 | P1 |
| 媒體搜尋 | 依名稱、分類、標籤搜尋 | P1 |
| 媒體預覽 | 圖片/影片即時預覽 | P0 |
| 媒體刪除 | 軟刪除（保留歷史引用） | P0 |
| 使用統計 | 統計各媒體被引用的次數 | P2 |

### 2.3 資料模型

#### media_categories

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| tenant_id | UUID | FK → tenants.id (可為 NULL 表示全平台共用) |
| name | VARCHAR(100) | 分類名稱 |
| description | TEXT | 分類描述 |
| parent_id | UUID | FK → media_categories.id (上層分類，NULL 表示根分類) |
| sort_order | INTEGER | 排序順序 |
| created_at | TIMESTAMP | 建立時間 |
| updated_at | TIMESTAMP | 更新時間 |

#### media_assets (擴展現有表)

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| tenant_id | UUID | FK → tenants.id |
| category_id | UUID | FK → media_categories.id (可為 NULL) |
| tags | TEXT[] | 標籤陣列 |
| usage_count | INTEGER | 被引用次數（快取） |
| file_size | BIGINT | 檔案大小（bytes） |
| mime_type | VARCHAR(100) | MIME 類型 |
| width | INTEGER | 圖片寬度（影片為 NULL） |
| height | INTEGER | 圖片高度（影片為 NULL） |

### 2.4 API 規格

#### 媒體分類 API

| 方法 | 端點 | 說明 | 角色 |
|------|------|------|------|
| GET | `/api/v2/media/categories` | 取得分類列表 | Guest+ |
| POST | `/api/v2/dashboard/media/categories` | 建立分類 | StoreOwner |
| PUT | `/api/v2/dashboard/media/categories/:id` | 更新分類 | StoreOwner |
| DELETE | `/api/v2/dashboard/media/categories/:id` | 刪除分類（需確認無媒體引用） | StoreOwner |

#### 媒體資產 API

| 方法 | 端點 | 說明 | 角色 |
|------|------|------|------|
| GET | `/api/v2/media` | 媒體列表（分頁/篩選） | Guest+ |
| GET | `/api/v2/media/:id` | 媒體詳情 | Guest+ |
| POST | `/api/v2/dashboard/media` | 上傳媒體 | Seller/Host/StoreOwner |
| PUT | `/api/v2/dashboard/media/:id` | 更新媒體資訊（分類/標籤） | StoreOwner |
| DELETE | `/api/v2/dashboard/media/:id` | 軟刪除媒體 | StoreOwner |

---

## 3. 知識庫 (Knowledge Base)

### 3.1 功能定位

| 屬性 | 說明 |
|------|------|
| **功能群組** | 知識庫 |
| **說明** | 建立、管理知識庫文章（如：商家指南、平台規則、使用教學） |
| **受眾** | 商家（StoreOwner/StoreStaff）、買家（Buyer/Guest） |

### 3.2 功能描述

| 功能 | 說明 | 優先級 |
|------|------|--------|
| 文章管理 | 建立/編輯/刪除/發布文章 | P1 |
| 分類管理 | 文章分類（FAQ、使用教學、平台公告、政策） | P1 |
| 標籤管理 | 文章標籤 | P1 |
| 搜尋 | 關鍵字搜尋文章 | P1 |
| 瀏覽統計 | 文章瀏覽次數統計 | P2 |
| 評論 | 文章評論功能 | P2 |
| 版本管理 | 文章版本歷史 | P2 |

### 3.3 資料模型

#### knowledge_categories

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| name | VARCHAR(100) | 分類名稱 |
| slug | VARCHAR(100) | URL slug |
| description | TEXT | 分類描述 |
| icon | VARCHAR(50) | 圖示名稱 |
| sort_order | INTEGER | 排序順序 |
| created_at | TIMESTAMP | 建立時間 |

#### knowledge_articles

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| tenant_id | UUID | FK → tenants.id (NULL 表示全平台文章) |
| category_id | UUID | FK → knowledge_categories.id |
| author_id | UUID | FK → users.id |
| title | VARCHAR(200) | 文章標題 |
| slug | VARCHAR(200) | URL slug |
| content | TEXT | Markdown 內容 |
| excerpt | TEXT | 文章摘要 |
| cover_image_url | VARCHAR(500) | 封面圖 |
| status | ENUM | DRAFT / PUBLISHED / ARCHIVED |
| view_count | INTEGER | 瀏覽次數 |
| is_pinned | BOOLEAN | 是否置頂 |
| published_at | TIMESTAMP | 發布時間 |
| created_at | TIMESTAMP | 建立時間 |
| updated_at | TIMESTAMP | 更新時間 |

#### knowledge_article_tags

| 欄位 | 類型 | 說明 |
|------|------|------|
| article_id | UUID | FK → knowledge_articles.id |
| tag | VARCHAR(50) | 標籤 |

### 3.4 API 規格

| 方法 | 端點 | 說明 | 角色 |
|------|------|------|------|
| GET | `/api/v2/knowledge` | 文章列表 | Guest+ |
| GET | `/api/v2/knowledge/:slug` | 文章詳情 | Guest+ |
| GET | `/api/v2/knowledge/categories` | 分類列表 | Guest+ |
| GET | `/api/v2/knowledge/search?q=` | 搜尋文章 | Guest+ |
| POST | `/api/v2/dashboard/knowledge` | 建立文章 | StoreOwner/Admin |
| PUT | `/api/v2/dashboard/knowledge/:id` | 更新文章 | StoreOwner/Admin |
| DELETE | `/api/v2/dashboard/knowledge/:id` | 刪除文章 | StoreOwner/Admin |

---

## 4. FAQ / 幫助中心

### 4.1 功能定位

| 屬性 | 說明 |
|------|------|
| **功能群組** | FAQ |
| **說明** | 常見問題分類、解答、管理 |
| **受眾** | 全體用戶（Guest+） |

### 4.2 功能描述

| 功能 | 說明 | 優先級 |
|------|------|--------|
| FAQ 分類 | FAQ 問題分類（如：訂購問題、支付問題、退款問題、帳號問題） | P1 |
| FAQ 解答 | FAQ 問題與解答管理 | P1 |
| FAQ 搜尋 | 關鍵字搜尋 FAQ | P1 |
| FAQ 置頂 | 重要 FAQ 可置頂顯示 | P1 |
| 關聯推薦 | 根據用戶問題推薦相關 FAQ | P2 |

### 4.3 資料模型

#### faq_categories

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| name | VARCHAR(100) | 分類名稱 |
| slug | VARCHAR(100) | URL slug |
| icon | VARCHAR(50) | 圖示名稱 |
| sort_order | INTEGER | 排序順序 |
| is_active | BOOLEAN | 是否啟用 |

#### faqs

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| tenant_id | UUID | FK → tenants.id (NULL 表示全平台 FAQ) |
| category_id | UUID | FK → faq_categories.id |
| question | VARCHAR(500) | 問題 |
| answer | TEXT | 解答（Markdown） |
| is_pinned | BOOLEAN | 是否置頂 |
| sort_order | INTEGER | 排序順序 |
| view_count | INTEGER | 瀏覽次數 |
| is_active | BOOLEAN | 是否啟用 |
| created_at | TIMESTAMP | 建立時間 |
| updated_at | TIMESTAMP | 更新時間 |

### 4.4 API 規格

| 方法 | 端點 | 說明 | 角色 |
|------|------|------|------|
| GET | `/api/v2/faqs` | FAQ 列表（依分類） | Guest+ |
| GET | `/api/v2/faqs/:id` | FAQ 詳情 | Guest+ |
| GET | `/api/v2/faqs/categories` | 分類列表 | Guest+ |
| GET | `/api/v2/faqs/search?q=` | 搜尋 FAQ | Guest+ |
| POST | `/api/v2/dashboard/faqs` | 建立 FAQ | Admin |
| PUT | `/api/v2/dashboard/faqs/:id` | 更新 FAQ | Admin |
| DELETE | `/api/v2/dashboard/faqs/:id` | 刪除 FAQ | Admin |

---

## 5. 客服系統 (Customer Support)

### 5.1 功能定位

| 屬性 | 說明 |
|------|------|
| **功能群組** | 客服系統 |
| **說明** | 買家提交客服工單，店家/平台回覆處理 |
| **受眾** | 買家、店家、平台客服 |

### 5.2 功能描述

| 功能 | 說明 | 優先級 |
|------|------|--------|
| 工單提交 | 買家提交客服問題（類型、標題、描述、附件） | P2 |
| 工單列表 | 店家/平台檢視工單列表 | P2 |
| 工單處理 | 店家/平台回覆工單 | P2 |
| 工單狀態 | 開立 → 處理中 → 已解決/已關閉 | P2 |
| 工單分類 | 問題類型分類（商品問題、預訂問題、付款問題、技術問題） | P2 |
| 訊息記錄 | 買家與客服的對話記錄 | P2 |

### 5.3 資料模型

#### support_tickets

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| tenant_id | UUID | FK → tenants.id (關聯店家，NULL 表示平台工單) |
| ticket_number | VARCHAR(20) | 工單編號（如：TK-20260409-001） |
| category | ENUM | PRODUCT / BOOKING / PAYMENT / TECHNICAL / OTHER |
| subject | VARCHAR(200) | 問題主旨 |
| description | TEXT | 問題描述 |
| status | ENUM | OPEN / IN_PROGRESS / RESOLVED / CLOSED |
| priority | ENUM | LOW / NORMAL / HIGH / URGENT |
| customer_id | UUID | FK → users.id (提交者) |
| assigned_to | UUID | FK → users.id (處理者) |
| order_id | UUID | FK → orders.id (相關訂單，可為 NULL) |
| created_at | TIMESTAMP | 建立時間 |
| updated_at | TIMESTAMP | 更新時間 |
| resolved_at | TIMESTAMP | 解決時間 |

#### support_messages

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| ticket_id | UUID | FK → support_tickets.id |
| sender_id | UUID | FK → users.id |
| sender_type | ENUM | CUSTOMER / STAFF / SYSTEM |
| message | TEXT | 訊息內容 |
| attachments | JSONB | 附件列表 |
| created_at | TIMESTAMP | 發送時間 |

### 5.4 API 規格

| 方法 | 端點 | 說明 | 角色 |
|------|------|------|------|
| POST | `/api/v2/support/tickets` | 提交工單 | Buyer+ |
| GET | `/api/v2/support/tickets` | 我的工單列表 | Buyer+ |
| GET | `/api/v2/support/tickets/:id` | 工單詳情 | Buyer+ |
| POST | `/api/v2/support/tickets/:id/messages` | 發送訊息 | Buyer+ |
| GET | `/api/v2/dashboard/support/tickets` | 店家工單列表 | StoreOwner/StoreStaff |
| PUT | `/api/v2/dashboard/support/tickets/:id` | 更新工單狀態 | StoreOwner/StoreStaff/Admin |
| POST | `/api/v2/dashboard/support/tickets/:id/messages` | 回覆工單 | StoreOwner/StoreStaff/Admin |
| GET | `/api/v2/admin/support/tickets` | 平台工單列表 | Admin |
| PUT | `/api/v2/admin/support/tickets/:id/assign` | 指派工單 | Admin |

---

## 6. RBAC 權限矩陣（M18 相關）

| 功能 | Buyer | Seller | Host | StoreOwner | StoreStaff | Admin | SuperAdmin |
|------|-------|--------|------|------------|------------|-------|------------|
| **媒體中心** | | | | | | | |
| 瀏覽媒體 | — | R | R | R | R | R | R |
| 上傳媒體 | — | RW | RW | RWD | RW | RWD | RWD |
| 管理媒體分類 | — | — | — | RW | — | RWD | RWD |
| **知識庫** | | | | | | | |
| 瀏覽文章 | R | R | R | R | R | R | R |
| 建立文章 | — | — | — | RW | — | RWD | RWD |
| 管理文章 | — | — | — | RW | — | RWD | RWD |
| **FAQ** | | | | | | | |
| 瀏覽 FAQ | R | R | R | R | R | R | R |
| 管理 FAQ | — | — | — | — | — | RWD | RWD |
| **客服系統** | | | | | | | |
| 提交工單 | RW | — | — | — | — | — | — |
| 我的工單 | RW | — | — | — | — | — | — |
| 店家工單 | — | — | — | RW | RW | RW | RWD |
| 平台工單 | — | — | — | — | — | RWD | RWD |

---

## 7. 與其他模組的整合

### 7.1 與 M15 CMS 的整合

| 整合點 | 說明 |
|--------|------|
| 媒體統一管理 | M15 和 M18 共享 `media_assets` 表，統一媒體上傳介面 |
| 文章嵌入 | 知識庫文章可嵌入商品/房型購買卡片 |

### 7.2 與 M17 租戶管理的整合

| 整合點 | 說明 |
|--------|------|
| 租戶隔離 | 店家可管理自己的知識庫文章、FAQ、客服工單 |
| 跨店鋪 | 平台 FAQ 和知識庫文章可跨店鋪顯示 |

### 7.3 與 M05 訂單的整合

| 整合點 | 說明 |
|--------|------|
| 工單關聯 | 客服工單可關聯到具體訂單 |
| 快速查詢 | 店家可在訂單詳情頁直接建立客服工單 |

---

## 8. Phase 實作規劃

| Phase | 功能 | 優先級 |
|-------|------|--------|
| **Phase 1** | M15 媒體庫強化（分類+標籤） | P0 |
| **Phase 2-A** | 知識庫系統 + FAQ 系統 | P1 |
| **Phase 2-B** | 客服系統（工單） | P2 |

---

## 9. 修訂歷史

| 版本 | 日期 | 修改內容 | 作者 |
|------|------|---------|------|
| v1.0 | 2026-04-09 | 初始 M18 模組規格 | Claude Code (SA Agent) |

---

**文件結束**
