# E-Commerce System — 功能需求文檔 (FRD) v1.0

> **文檔類型**: FRD (Functional Requirements Document)
> **版本**: v1.0
> **依據**: E-Commerce_PRD_v1.0_Final.md
> **建立日期**: 2026-04-09
> **作者**: Amanda (SA-Analyst) + Beatrice (BA-Business-Analyst)
> **AISDLC 版本**: v0.09

---

## 📋 文檔元數據

| 項目 | 內容 |
|-----|------|
| **專案名稱** | E-Commerce B2B2C 多租戶電子商務平台 |
| **需求類型** | Standard (Greenfield) |
| **文檔狀態** | Draft |
| **System Analyst** | Amanda (SA-Analyst) |
| **Business Analyst** | Beatrice (BA-Business-Analyst) |
| **最後更新** | 2026-04-09 |

---

## 🔖 情境使用指引

**本 FRD 涵蓋所有 Phase 1 Must Have 模組的功能需求**

| 情境 | 涵蓋範圍 |
|------|---------|
| **Phase 1** | M01, M02, M03, M05, M06 (唯讀/取消), M12, M17 |
| **Phase 2** | M04, M06 (完整), M07, M08, M09, M10, M11, M15, M16, M18 |

---

## 📌 文檔追蹤

### 上游文檔
- **PRD 連結**: [E-Commerce_PRD_v1.0_Final.md](./E-Commerce_PRD_v1.0_Final.md)
- **需求來源**: E-Commerce PRD v0.9_R02 審視與用戶需求確認 (2026-04-09)

### 下游文檔
- **SRD 連結**: [docs/02_architecture/](../02_architecture/) (各模組 SRD)
- **API 規格**: [docs/02_architecture/API_Specifications/](../02_architecture/) (各模組 API 規格)
- **AT 連結**: [docs/03_testing/](../03_testing/) (驗收測試)

---

## 1. 模組概述 (Module Overview)

### 1.1 模組總覽

| 模組編號 | 模組名稱 | Phase 1 歸屬 | 優先級 | 說明 |
|---------|---------|-------------|--------|------|
| **M01** | 商品中心 | Phase 1 | P0 | 商品列表、分類、搜尋 |
| **M02** | 房源中心 | Phase 1 | P0 | 房源列表、地區/日期/人數過濾 |
| **M03** | 會員與權限系統 | Phase 1 | P0 | 多角色登入、RBAC、JWT 認證 |
| **M05** | 訂單履約系統 | Phase 1 | P0 | 訂單狀態機、取消與資源釋放 |
| **M06** | 預訂與日曆鎖定 | Phase 1 (唯讀) | P0 | 日期格查詢、取消（不含 POST 建立） |
| **M12** | 動態定價引擎 | Phase 1 | **Must Have** | 平假日調價、早鳥/長住折扣、手動覆蓋 |
| **M17** | 租戶/店鋪管理 | Phase 1 | P0 | 開店申請、店鋪審核、Feature Toggle |

### 1.2 五大子系統

```
┌─────────────────────────────────────────────────────────┐
│                    B2B2C Platform                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │ E-Commerce  │  │  B&B Engine │  │       CMS       │  │
│  │   Core      │←→│  (Booking   │←→│  (Content Mgmt) │  │
│  │ (Retail +   │  │  + Dynamic  │  │   + Embedded    │  │
│  │  Order)     │  │   Pricing)  │  │   Buy Cards)    │  │
│  └──────┬──────┘  └──────┬──────┘  └────────┬────────┘  │
│         │                │                   │           │
│         └────────┬───────┘                   │           │
│                  ▼                           │           │
│  ┌──────────────────────────┐                │           │
│  │     ERP (進銷存)          │←───────────────┘           │
│  │  Purchase → Stock → Sale │                            │
│  └──────────────────────────┘                            │
│                                                          │
│  ┌────────────────────────────────────────────────────┐   │
│  │  Knowledge Management                               │   │
│  │  (Media Center │ Knowledge Base │ FAQ │ Support)  │   │
│  └────────────────────────────────────────────────────┘   │
│                                                          │
│  ┌──────────────────────────────────────────────────┐    │
│  │  Platform Infrastructure                          │    │
│  │  Multi-Tenancy │ RBAC │ Auth │ Payment │ Audit    │    │
│  └──────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

### 1.3 Phase 1 技術架構

| 層級 | 技術 | 備註 |
|------|------|------|
| 前端 | Next.js 15 (App Router) + TypeScript + Tailwind CSS | SSR + SSG |
| 後端 | Spring Boot 3.2 (Java 21) + Clean Architecture + DDD | 多租戶感知 |
| 資料庫 | PostgreSQL 18 | Shared Schema + Tenant ID 隔離 |
| 快取 | Redis 7 | 購物車 / 分佈式鎖 / 限流 / 定價快取 |
| DB 遷移 | Flyway | 所有 Schema 變更走 Migration |
| E2E 測試 | Playwright | 端到端自動化測試 |
| CI/CD | GitHub Actions | 自動化構建與測試 |

---

## 2. Phase 1 共同需求

### 2.1 多租戶隔離機制

#### 2.1.1 TenantContext 選取機制

**BR-GEN-001: 多租戶上下文選擇**

| 項目 | 內容 |
|------|------|
| **Business Rule ID** | BR-GEN-001 |
| **對應 PRD 章節** | §4.4 / §7.5 |
| **優先級** | P0 (Must Have) |
| **類型** | 權限控制 |

**規則描述**：
當用戶隸屬於多個 Tenant 時（如 David Lin 同時經營民宿與 3C 代購），**必須**透過 `X-Tenant-ID` Header 明確指定操作目標租戶。

**觸發時機**：
- 用戶 API 請求進入 `TenantContextFilter` 時
- 用戶隸屬於多個 `tenant_members` 記錄

**驗證邏輯**：
```
IF (X-Tenant-ID Header 存在) THEN
  IF (tenant_id ∈ user.tenant_members) THEN
    SET current_tenant = tenant_id
  ELSE
    RETURN 403 E-2003 TENANT_CONTEXT_AMBIGUOUS
  END IF
ELSE IF (user.tenant_members.size = 1) THEN
  SET current_tenant = user.tenant_members[0]
ELSE
  RETURN 403 E-2003 TENANT_CONTEXT_AMBIGUOUS
END IF
```

**錯誤處理**：
- **錯誤碼**: E-2003
- **錯誤訊息**: `TENANT_CONTEXT_AMBIGUOUS: 請明確指定操作目標租戶 (X-Tenant-ID Header)`
- **HTTP Status**: 403 Forbidden

**測試案例參考**：
- TC-GEN-001-1: 單一租戶用戶，無 Header，正常處理
- TC-GEN-001-2: 多租戶用戶，有效 X-Tenant-ID，正常處理
- TC-GEN-001-3: 多租戶用戶，無效 X-Tenant-ID，回傳 403

---

#### 2.1.2 資料隔離策略

**BR-GEN-002: Repository 層租戶過濾**

| 項目 | 內容 |
|------|------|
| **Business Rule ID** | BR-GEN-002 |
| **對應 PRD 章節** | §4.2 |
| **優先級** | P0 (Must Have) |
| **類型** | 資料隔離 |

**規則描述**：
所有業務 Entity 必須攜帶 `tenant_id`，Repository 層透過 Hibernate Filter 自動注入 `WHERE tenant_id = :currentTenantId` 條件。

**實作約束**：
- 所有業務表必須建立 `(tenant_id, ...)` 複合索引
- Hibernate `@Filter` 註解自動過濾
- 跨租戶查詢僅限平台 Admin（需特殊權限 `CROSS_TENANT_READ`）

---

### 2.2 認證與授權機制

#### 2.2.1 JWT 雙令牌機制

**BR-AUTH-001: JWT 存取權杖**

| 項目 | 內容 |
|------|------|
| **Business Rule ID** | BR-AUTH-001 |
| **對應 PRD 章節** | §6.2.1 / §7.3 |
| **優先級** | P0 (Must Have) |
| **類型** | 認證 |

**規則描述**：
- Access Token 有效期限 ≤ 30 分鐘
- Refresh Token 有效期限 ≤ 30 天
- JWT 必須簽名（使用 RS256 或 HS256）
- 所有 API 請求必須包含有效的 JWT Token（除公開端點外）

**驗證邏輯**：
```
IF (endpoint IN public_endpoints) THEN
  ALLOW
ELSE IF (Authorization Header 缺失) THEN
  RETURN 401 Unauthorized
ELSE IF (JWT 簽名無效) THEN
  RETURN 401 Token Invalid
ELSE IF (JWT 過期) THEN
  RETURN 401 Token Expired
ELSE
  EXTRACT userId, roles, tenantId FROM JWT
  SET TenantContext = tenantId
  CHECK RBAC permission
END IF
```

---

#### 2.2.2 RBAC 角色權限

**BR-AUTH-002: 角色權限控制**

| 項目 | 內容 |
|------|------|
| **Business Rule ID** | BR-AUTH-002 |
| **對應 PRD 章節** | §7.3 |
| **優先級** | P0 (Must Have) |
| **類型** | 授權 |

**角色定義**：

| 角色 | 說明 | Tenant 綁定 |
|------|------|-----------|
| Guest | 未登入訪客，可瀏覽商品與房源 | 否 |
| Buyer | 已登入買家，可購買商品與預訂民宿 | 否 |
| Seller | 零售賣家 | 是 |
| Host | 民宿房東 | 是 |
| StoreOwner | 店鋪擁有者 | 是 |
| StoreStaff | 店鋪員工 | 是 |
| Admin | 平台運營人員 | 否 |
| SuperAdmin | 平台最高管理員 | 否 |

**🔴 人機協作確認點**:
- [ ] 角色定義已獲利害關係人確認
- [ ] 權限矩陣已獲 PM/PO 確認
- [ ] 多租戶場景已測試驗證

---

### 2.3 Feature Toggle 機制

**BR-FT-001: 功能開關控制**

| 項目 | 內容 |
|------|------|
| **Business Rule ID** | BR-FT-001 |
| **對應 PRD 章節** | §4.4 / §7.5 |
| **優先級** | P0 (Must Have) |
| **類型** | 功能控制 |

**Feature Toggle 預設值**：

| Feature Key | 說明 | 預設值 (新店鋪) |
|-------------|------|----------------|
| `RETAIL_ENABLED` | 可上架實體商品 | true |
| `BOOKING_ENABLED` | 可上架民宿房間 | false |
| `CMS_ENABLED` | 可發布 CMS 貼文 | true |
| `ERP_ENABLED` | 可使用進銷存管理 | true |
| `DYNAMIC_PRICING_ENABLED` | 可使用動態定價 | false |
| `PROMO_ENABLED` | 可建立促銷活動 | false |

**驗證時機**：
所有 B 端 API（`/api/v2/dashboard/*`）進入 Controller 後，在執行業務邏輯**之前**，由 Service 層主動查詢 `tenant_feature_toggles` 表。

**錯誤處理**：
- **錯誤碼**: E-2020
- **錯誤訊息**: `FEATURE_DISABLED_FOR_TENANT: [功能名稱]功能尚未啟用，請聯繫平台管理員。`
- **HTTP Status**: 403 Forbidden

---

## 3. M01 商品中心 (Product Center)

### 3.1 模組概述

| 項目 | 內容 |
|------|------|
| **模組編號** | M01 |
| **模組名稱** | 商品中心 (Product Center) |
| **所屬子系統** | Retail Engine |
| **核心能力** | 商品列表、分類、搜尋（基於 Listing 抽象） |
| **依賴模組** | M03 (認證), M17 (租戶) |
| **Phase 歸屬** | Phase 1 |

### 3.2 功能總覽

| 功能 | 說明 | 優先級 | Phase |
|------|------|--------|-------|
| 商品列表 | 跨租戶商品列表查詢（分頁/篩選） | P0 | Phase 1 |
| 商品分類 | 商品分類查詢與瀏覽 | P0 | Phase 1 |
| 商品搜尋 | 關鍵字搜尋商品 | P0 | Phase 1 |
| 商品詳情 | 取得商品詳細資訊 | P0 | Phase 1 |
| 商品上架 | Seller/Host 建立商品 Listing | P0 | Phase 1 |
| 商品編輯 | 更新商品資訊 | P0 | Phase 1 |
| 商品下架 | 軟刪除（status → INACTIVE） | P0 | Phase 1 |
| SKU 管理 | SKU 規格與庫存管理 | P1 | Phase 2 |

### 3.3 主要用戶

| 用戶角色 | 描述 | 主要需求 | 使用頻率 |
|---------|------|---------|---------|
| Guest | 未登入訪客 | 瀏覽商品列表與詳情 | 瀏覽 |
| Buyer | 已登入買家 | 搜尋商品、加入購物車 | 購買決策 |
| Seller | 零售賣家 | 上架商品、管理庫存 | 每日管理 |
| StoreOwner | 店鋪擁有者 | 上架/編輯/下架商品 | 每日管理 |

### 3.4 資料模型

#### 3.4.1 統一 Listing 模型

```
listings (統一抽象表)
├── id: UUID (PK)
├── tenant_id: UUID (FK → tenants.id, NOT NULL)
├── listing_type: ENUM('PRODUCT', 'ROOM')
├── title: VARCHAR(200)
├── description: TEXT
├── cover_image_url: VARCHAR(500)
├── status: ENUM('DRAFT', 'ACTIVE', 'INACTIVE', 'DELETED')
├── owner_id: UUID (FK → users.id)
├── base_price: DECIMAL(12,2)
├── currency: VARCHAR(3) DEFAULT 'TWD'
├── tags: TEXT[]
├── created_at: TIMESTAMP
├── updated_at: TIMESTAMP
└── INDEX (tenant_id, listing_type, status)
```

#### 3.4.2 Product 特化模型

```
products (繼承 listing_id)
├── listing_id: UUID (FK → listings.id, UNIQUE)
├── category: VARCHAR(50)
├── brand: VARCHAR(100)
├── weight_grams: INTEGER
├── dimensions_cm: VARCHAR(50)
└── INDEX (listing_id)

product_skus
├── id: UUID (PK)
├── product_listing_id: UUID (FK → listings.id)
├── sku_code: VARCHAR(50) UNIQUE
├── spec_name: VARCHAR(100)
├── price_override: DECIMAL(12,2) -- NULL 表示使用 listing.base_price
├── status: ENUM('ACTIVE', 'INACTIVE')
└── INDEX (product_listing_id)

product_inventory
├── sku_id: UUID (FK → product_skus.id, UNIQUE)
├── total_qty: INTEGER DEFAULT 0
├── reserved_qty: INTEGER DEFAULT 0
├── available_qty: INTEGER GENERATED AS (total_qty - reserved_qty)
├── version: BIGINT DEFAULT 0 -- 樂觀鎖
└── updated_at: TIMESTAMP
```

### 3.5 User Stories

---

#### US-M01-001: 商品搜尋

**Story 描述**:
- **As a** Guest/Buyer
- **I want to** 搜尋商品 by 關鍵字
- **So that** 找到我想要的商品

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 5

**業務價值**:
提升用戶找到目標商品的效率，增加購買轉化率。

**技術實現概要**:
- 使用 PostgreSQL LIKE 查詢（Phase 1）
- Phase 2+ 迁移至 ElasticSearch 全文檢索

---

#### Acceptance Criteria

**AC-M01-001-1**: 關鍵字搜尋
**Given** 用戶在搜尋框輸入關鍵字 "AirPods"
**When** 點擊搜尋按鈕
**Then** 返回所有 title 或 description 包含 "AirPods" 的 ACTIVE 商品

**測試資料**:
- 輸入: `keyword: "AirPods"`
- 預期輸出: `[{"id": "...", "title": "AirPods Pro 2", "status": "ACTIVE", ...}]`

---

**AC-M01-001-2**: 分頁結果
**Given** 搜尋結果超過 20 筆
**When** 用戶請求第一頁
**Then** 只返回前 20 筆，並提供分頁資訊

**測試資料**:
- 輸入: `page: 1, pageSize: 20`
- 預期輸出: `{ items: [...], total: 45, page: 1, pageSize: 20 }`

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| 空白關鍵字 | 回傳全部商品（分頁） | 返回第一頁商品列表 |
| 無搜尋結果 | 回傳空陣列 | `{ items: [], total: 0 }` |
| 特殊字元 | 忽略或轉義 | 正常處理 |
| 搜尋超時 | 回傳錯誤 | E-5001 SEARCH_TIMEOUT |

---

#### 依賴與假設

**依賴項**:
- M03 認證系統正常運作
- listings.status = 'ACTIVE' 的商品才能被搜尋到

**假設條件**:
- Phase 1 使用 PostgreSQL LIKE 查詢，暫不支援 ElasticSearch

---

**🔴 人機協作確認點** (US-M01-001):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 邊界條件已涵蓋
- [ ] BA 已驗證需求正確性

---

#### US-M01-002: 商品上架

**Story 描述**:
- **As a** Seller
- **I want to** 上架新商品
- **So that** 讓買家可以看到並購買我的商品

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 8

**業務價值**:
Seller 可以快速上架商品，開始銷售。

**技術實現概要**:
1. 驗證 Seller 角色與 RETAIL_ENABLED Feature Toggle
2. 建立 Listing (status = 'DRAFT')
3. 建立 Product 特化資料
4. 建立 Product SKU（如有規格）
5. 設定初始庫存（透過 ERP 採購單或直接設定）

---

#### Acceptance Criteria

**AC-M01-002-1**: 基本上架流程
**Given** Seller 已登入且 RETAIL_ENABLED = true
**When** 提交商品上架請求（包含 title, description, base_price, category）
**Then** 建立 Listing (status = 'DRAFT') 並回傳商品 ID

**測試資料**:
```json
{
  "title": "iPhone 15 Pro",
  "description": "最新款 iPhone",
  "basePrice": 42900,
  "category": "3C",
  "coverImageUrl": "https://..."
}
```

---

**AC-M01-002-2**: 發布商品
**Given** Seller 擁有 DRAFT 狀態的商品
**When** 請求發布商品 (PUT /api/v2/dashboard/listings/:id/publish)
**Then** 將 Listing status 改為 'ACTIVE'，商品可在前台搜尋到

**測試資料**:
- 輸入: `listing_id: "uuid"`, `action: "publish"`
- 預期輸出: `{ id: "uuid", status: "ACTIVE" }`

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| RETAIL_ENABLED = false | 阻擋上架，回傳 E-2020 | Feature Disabled 錯誤 |
| title 空白 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| base_price ≤ 0 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| 重複上架 | 允許（同一商品可有多個 Listing） | 正常建立 |

---

#### 依賴與假設

**依賴項**:
- M03 認證與授權
- M17 tenant_feature_toggles 查詢

**假設條件**:
- Phase 1 SKU 為可選欄位，單一 SKU 商品無需建立 product_skus

---

**🔴 人機協作確認點** (US-M01-002):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] Feature Toggle 整合已確認
- [ ] BA 已驗證需求正確性

---

#### US-M01-003: 商品詳情

**Story 描述**:
- **As a** Guest/Buyer
- **I want to** 查看商品詳情
- **So that** 了解商品的完整資訊以便決定是否購買

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 2

---

#### Acceptance Criteria

**AC-M01-003-1**: 查看商品詳情
**Given** 商品 ID 存在且狀態為 ACTIVE
**When** 用戶請求取得商品詳情
**Then** 回傳商品完整資訊（標題、描述、價格、庫存、圖片）

**測試資料**:
- 輸入: `listing_id: "uuid"`
- 預期輸出: `{ id: "...", title: "...", description: "...", basePrice: ..., status: "ACTIVE", ... }`

---

**AC-M01-003-2**: 查看他人店鋪商品
**Given** 商品屬於其他店鋪
**When** 用戶請求取得商品詳情
**Then** 回傳商品公開資訊（不包含內部管理資訊）

---

**AC-M01-003-3**: 查看不存在的商品
**Given** 商品 ID 不存在
**When** 用戶請求取得商品詳情
**Then** 回傳 404 錯誤

**測試資料**:
- 輸入: `listing_id: "non-existent-uuid"`
- 預期錯誤: `E-4041 LISTING_NOT_FOUND`

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| 商品已被軟刪除 (DELETED) | 阻擋 | E-4041 LISTING_NOT_FOUND |
| 商品狀態為 INACTIVE | 回傳基本資訊 | 可瀏覽但無法購買 |

---

**🔴 人機協作確認點** (US-M01-003):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 隱私資訊已確認（不暴露內部管理資訊）
- [ ] BA 已驗證需求正確性

---

#### US-M01-004: 商品編輯

**Story 描述**:
- **As a** Seller
- **I want to** 編輯我店鋪的商品資訊
- **So that** 可以更新商品內容、價格或庫存

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 5

---

#### Acceptance Criteria

**AC-M01-004-1**: 編輯商品基本資訊
**Given** Seller 擁有該商品的擁有權
**When** 提交商品編輯請求（title, description, basePrice）
**Then** 更新商品資訊並回傳更新後的商品

**測試資料**:
```json
{
  "title": "iPhone 15 Pro (更新版)",
  "description": "全新上市",
  "basePrice": 39900
}
```

---

**AC-M01-004-2**: 編輯商品庫存
**Given** Seller 擁有該商品的擁有權
**When** 透過 ERP 或直接設定更新庫存
**Then** 更新 product_inventory 記錄

**測試資料**:
- 輸入: `{ skuId: "sku-uuid", totalQty: 100 }`
- 預期輸出: `{ skuId: "sku-uuid", totalQty: 100, availableQty: 100 }`

---

**AC-M01-004-3**: 無權編輯他人商品
**Given** 商品不屬於當前用戶的店鋪
**When** 提交商品編輯請求
**Then** 回傳 403 錯誤

**測試資料**:
- 預期錯誤: `E-4031 ACCESS_DENIED`

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| basePrice ≤ 0 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| title 空白 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| 編輯 DELETED 狀態商品 | 阻擋 | E-4001 商品已刪除 |
| 庫存不足時編輯 | 警告但允許 | 顯示庫存不足提示 |

---

#### 依賴與假設

**依賴項**:
- M03 認證與授權
- M17 tenant_feature_toggles 查詢

---

**🔴 人機協作確認點** (US-M01-004):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 庫存編輯邏輯已確認
- [ ] BA 已驗證需求正確性

---

#### US-M01-005: 商品下架

**Story 描述**:
- **As a** Seller
- **I want to** 將商品下架（軟刪除）
- **So that** 商品不再於前台顯示

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 3

---

#### Acceptance Criteria

**AC-M01-005-1**: 將 ACTIVE 商品下架
**Given** 商品狀態為 ACTIVE
**When** Seller 請求下架商品
**Then** 將商品狀態改為 INACTIVE

**測試資料**:
- 輸入: `listing_id: "uuid"`, `action: "deactivate"`
- 預期輸出: `{ id: "uuid", status: "INACTIVE" }`

---

**AC-M01-005-2**: 將 INACTIVE 商品重新上架
**Given** 商品狀態為 INACTIVE
**When** Seller 請求上架商品
**Then** 將商品狀態改為 ACTIVE

**測試資料**:
- 輸入: `listing_id: "uuid"`, `action: "activate"`
- 預期輸出: `{ id: "uuid", status: "ACTIVE" }`

---

**AC-M01-005-3**: 永久刪除商品
**Given** 商品狀態為 INACTIVE
**When** Seller 請求永久刪除商品
**Then** 將商品狀態改為 DELETED（不可逆）

**測試資料**:
- 輸入: `listing_id: "uuid"`, `action: "delete"`
- 預期輸出: `{ id: "uuid", status: "DELETED" }`

---

**AC-M01-005-4**: 刪除已有待處理訂單的商品
**Given** 商品有待處理訂單（CREATED, SHIPPING）
**When** Seller 請求永久刪除商品
**Then** 回傳警告，需確認是否強制刪除

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| DELETED 狀態商品 | 阻擋 | E-4001 已刪除商品無法操作 |
| 已有完成訂單的商品 | 允許下架 | 保留歷史訂單記錄 |
| 多人同時操作同一商品 | 樂觀鎖防護 | E-4091 CONFLICT |

---

#### 依賴與假設

**依賴項**:
- M05 訂單系統（檢查是否有待處理訂單）

**假設條件**:
- Phase 1 只做軟刪除（status → DELETED），不實際刪除資料

---

**🔴 人機協作確認點** (US-M01-005):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 刪除保護機制已確認
- [ ] BA 已驗證需求正確性

---

### 3.6 業務規則

#### BR-M01-001: 商品狀態流轉

| 項目 | 內容 |
|------|------|
| **Business Rule ID** | BR-M01-001 |
| **對應 Feature** | F-M01-001 |
| **優先級** | P0 |
| **類型** | 狀態轉換 |

**規則描述**：
商品 Listing 狀態流轉：`DRAFT` → `ACTIVE` → `INACTIVE` → `DELETED`

| 目前狀態 | 目標狀態 | 允許 | 觸發角色 |
|----------|----------|------|---------|
| DRAFT | ACTIVE | O | Seller/StoreOwner |
| ACTIVE | INACTIVE | O | Seller/StoreOwner/Admin |
| INACTIVE | ACTIVE | O | Seller/StoreOwner |
| INACTIVE | DELETED | O | Seller/StoreOwner/Admin |
| DELETED | 任何狀態 | X | — |

**驗證邏輯**：
```
IF (current_status = 'DELETED') THEN
  RETURN Error("已刪除商品無法變更狀態")
END IF
```

---

#### BR-M01-002: 商品庫存計算

| 項目 | 內容 |
|------|------|
| **Business Rule ID** | BR-M01-002 |
| **對應 Feature** | F-M01-002 |
| **優先級** | P0 |
| **類型** | 計算規則 |

**規則描述**：
`available_qty = total_qty - reserved_qty`

- ERP 入庫 → `total_qty += inbound_qty`
- 訂單建立 → `reserved_qty += order_qty` (樂觀鎖)
- 訂單取消 → `reserved_qty -= order_qty`
- 訂單完成 → `total_qty -= order_qty; reserved_qty -= order_qty`

---

### 3.7 API 規格概要

| API Endpoint | 方法 | 用途 | 角色 |
|-------------|------|------|------|
| `/api/v2/listings` | GET | 商品列表（分頁/篩選） | Guest+ |
| `/api/v2/listings/:id` | GET | 商品詳情 | Guest+ |
| `/api/v2/listings/search` | GET | 關鍵字搜尋 | Guest+ |
| `/api/v2/categories` | GET | 分類列表 | Guest+ |
| `/api/v2/dashboard/listings` | GET | 店鋪商品列表 | Seller+ |
| `/api/v2/dashboard/listings` | POST | 建立商品 | Seller+ |
| `/api/v2/dashboard/listings/:id` | PUT | 更新商品 | Seller+ |
| `/api/v2/dashboard/listings/:id` | DELETE | 下架商品 | Seller+ |

**詳細 API 規格**: 見 [docs/02_architecture/API_M01_Product_Center.md](../02_architecture/API_M01_Product_Center.md)

---

## 4. M02 房源中心 (Listing Center)

### 4.1 模組概述

| 項目 | 內容 |
|------|------|
| **模組編號** | M02 |
| **模組名稱** | 房源中心 (Listing Center) |
| **所屬子系統** | Booking Engine |
| **核心能力** | 房源列表、地區/日期/人數過濾、room_calendar |
| **依賴模組** | M03 (認證), M12 (動態定價), M17 (租戶) |
| **Phase 歸屬** | Phase 1 |

### 4.2 功能總覽

| 功能 | 說明 | 優先級 | Phase |
|------|------|--------|-------|
| 房源列表 | 跨租戶房源列表查詢（分頁/篩選） | P0 | Phase 1 |
| 地區搜尋 | 按地區/關鍵字搜尋房源 | P0 | Phase 1 |
| 日期過濾 | 可用日期範圍過濾 | P0 | Phase 1 |
| 人數篩選 | 按入住人數過濾房源 | P0 | Phase 1 |
| 房源詳情 | 取得房源詳細資訊與定價日曆 | P0 | Phase 1 |
| 房源上架 | Host 建立房源 Listing | P0 | Phase 1 |
| 房源編輯 | 更新房源資訊 | P0 | Phase 1 |
| 房源下架 | 軟刪除（status → INACTIVE） | P0 | Phase 1 |

### 4.3 主要用戶

| 用戶角色 | 描述 | 主要需求 | 使用頻率 |
|---------|------|---------|---------|
| Guest | 未登入訪客 | 瀏覽房源列表與詳情 | 瀏覽 |
| Buyer | 已登入買家 | 搜尋可用房源、查看日曆與價格 | 預訂決策 |
| Host | 民宿房東 | 上架房源、管理日曆與定價 | 每日管理 |
| StoreOwner | 店鋪擁有者 | 上架/編輯/下架房源 | 每日管理 |

### 4.4 資料模型

#### 4.4.1 Room 特化模型

```
rooms (繼承 listing_id)
├── listing_id: UUID (FK → listings.id, UNIQUE)
├── location: VARCHAR(200)
├── latitude: DOUBLE
├── longitude: DOUBLE
├── max_guests: INTEGER
├── amenities: TEXT[]
├── check_in_time: TIME DEFAULT '15:00'
├── check_out_time: TIME DEFAULT '11:00'
└── INDEX (listing_id)

room_calendar (日曆 — 每房每天一列)
├── id: UUID (PK)
├── room_listing_id: UUID (FK → listings.id)
├── calendar_date: DATE
├── status: ENUM('AVAILABLE', 'BOOKED', 'BLOCKED', 'MAINTENANCE')
├── price: DECIMAL(12,2) -- 當日實際價格
├── booking_id: UUID (FK → bookings.id, NULLABLE)
├── UNIQUE (room_listing_id, calendar_date) -- 杜絕 Overbooking
└── INDEX (room_listing_id, calendar_date, status)
```

---

### 4.5 User Stories

---

#### US-M02-001: 房源搜尋與過濾

**Story 描述**:
- **As a** Guest/Buyer
- **I want to** 依地區、日期、人數搜尋可用房源
- **So that** 找到符合我需求的民宿

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 8

**業務價值**:
用戶可以快速找到符合時間和人數需求的可用房源。

---

#### Acceptance Criteria

**AC-M02-001-1**: 基本搜尋
**Given** 用戶在首頁輸入搜尋條件
**When** 點擊搜尋按鈕
**Then** 返回可用房源列表（只顯示有 AVAILABLE 日期格的房源）

**測試資料**:
```json
{
  "location": "高雄",
  "checkInDate": "2026-05-01",
  "checkOutDate": "2026-05-03",
  "guests": 2
}
```

---

**AC-M02-001-2**: 日期衝突過濾
**Given** 搜尋範圍內有任何一天被預訂
**When** 該房源不應出現在搜尋結果中
**Then** 系統自動排除該房源

**測試資料**:
- 輸入: `checkInDate: "2026-05-01", checkOutDate: "2026-05-03"`
- 預期: Room A（5/2 已被預訂）不出現在結果中

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| checkOutDate ≤ checkInDate | 驗證失敗 | E-4001 VALIDATION_ERROR |
| guests > max_guests | 該房源不出現 | 自動過濾 |
| 搜尋範圍 > 30 天 | 限制最大範圍 | E-4001 VALIDATION_ERROR |
| 沒有可用房源 | 回傳空陣列 | `{ items: [], total: 0 }` |

---

**🔴 人機協作確認點** (US-M02-001):
- [ ] 搜尋條件完整
- [ ] 過濾邏輯正確
- [ ] 效能可接受
- [ ] BA 已驗證需求正確性

---

#### US-M02-002: 查看房源日曆與價格

**Story 描述**:
- **As a** Buyer
- **I want to** 查看特定房源的日曆與動態價格
- **So that** 選擇最佳入住日期

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 5

**業務價值**:
用戶可以直觀看到未來 90 天的可用性與價格，輔助預訂決策。

---

#### Acceptance Criteria

**AC-M02-002-1**: 日曆查詢
**Given** 用戶查看房源詳情頁
**When** 請求日曆資料 (GET /api/v2/listings/:id/calendar?start=...&end=...)
**Then** 返回該日期範圍內的 room_calendar 記錄

**測試資料**:
- 輸入: `start: "2026-05-01", end: "2026-05-31"`
- 預期輸出: `[{date: "2026-05-01", status: "AVAILABLE", price: 2500}, ...]`

---

**AC-M02-002-2**: 動態價格顯示
**Given** 日曆請求返回
**When** 日期為週末或旺季
**Then** price 應為動態計算後的價格（含倍率加成）

**測試資料**:
- 預期: 週末 price = base_price × weekendMultiplier (如 1.3)
- 預期: 平日 price = base_price × weekdayMultiplier (如 1.0)

---

**🔴 人機協作確認點** (US-M02-002):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 動態定價整合已確認
- [ ] BA 已驗證需求正確性

---

#### US-M02-003: 房源上架

**Story 描述**:
- **As a** Host
- **I want to** 上架新房源
- **So that** 讓買家可以看到並預訂我的房源

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 8

**業務價值**:
Host 可以快速上架房源，開始接受預訂。

**技術實現概要**:
1. 驗證 Host 角色與 BOOKING_ENABLED Feature Toggle
2. 建立 Listing (status = 'DRAFT', type = 'ROOM')
3. 建立 room_calendar 初始記錄（可用日期）
4. 設定初始庫存（房間數量）

---

#### Acceptance Criteria

**AC-M02-003-1**: 基本上架流程
**Given** Host 已登入且 BOOKING_ENABLED = true
**When** 提交房源上架請求（title, description, base_price, max_guests, location）
**Then** 建立 Listing (status = 'DRAFT', type = 'ROOM') 並回傳房源 ID

**測試資料**:
```json
{
  "title": "墾丁海景民宿 - 豪華雙人房",
  "description": "面海景觀，步行至沙灘 5 分鐘",
  "basePrice": 2500,
  "maxGuests": 4,
  "location": "屏東縣恆春鎮",
  "roomCount": 3
}
```

---

**AC-M02-003-2**: 發布房源
**Given** Host 擁有 DRAFT 狀態的房源
**When** 請求發布房源 (PUT /api/v2/dashboard/listings/:id/status)
**Then** 將 Listing status 改為 'ACTIVE'，房源可在前台被搜尋到

**測試資料**:
- 輸入: `listing_id: "uuid"`, `action: "publish"`
- 預期輸出: `{ id: "uuid", status: "ACTIVE" }`

---

**AC-M02-003-3**: 初始日曆建立
**Given** Host 建立新房源
**When** 系統建立房源記錄後
**Then** 自動建立未來 90 天的 room_calendar 記錄（預設都為 AVAILABLE）

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| BOOKING_ENABLED = false | 阻擋上架，回傳 E-2020 | Feature Disabled 錯誤 |
| title 空白 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| base_price ≤ 0 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| max_guests ≤ 0 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| 重複上架 | 允許（同一房源可有多個 Listing） | 正常建立 |

---

#### 依賴與假設

**依賴項**:
- M03 認證與授權
- M17 tenant_feature_toggles 查詢
- M12 動態定價（用於計算日曆價格）

**假設條件**:
- Phase 1 room_calendar 初始為 90 天

---

**🔴 人機協作確認點** (US-M02-003):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] Feature Toggle 整合已確認
- [ ] BA 已驗證需求正確性

---

#### US-M02-004: 房源編輯

**Story 描述**:
- **As a** Host
- **I want to** 編輯我店鋪的房源資訊
- **So that** 可以更新房源內容、價格或設定

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 5

---

#### Acceptance Criteria

**AC-M02-004-1**: 編輯房源基本資訊
**Given** Host 擁有該房源的擁有權
**When** 提交房源編輯請求（title, description, base_price, max_guests）
**Then** 更新房源資訊並回傳更新後的房源

**測試資料**:
```json
{
  "title": "墾丁海景民宿 - 豪華雙人房（海景升級版）",
  "description": "全新裝修，免費 Wi-Fi",
  "basePrice": 2800,
  "maxGuests": 4
}
```

---

**AC-M02-004-2**: 編輯日曆設定
**Given** Host 擁有該房源的擁有權
**When** 請求封鎖特定日期 (PUT /api/v2/dashboard/listings/:id/calendar)
**Then** 更新 room_calendar 記錄

**測試資料**:
```json
{
  "updates": [
    { "date": "2026-05-01", "status": "BLOCKED", "reason": "房間清潔中" },
    { "date": "2026-05-02", "status": "BLOCKED", "reason": "房間清潔中" }
  ]
}
```

---

**AC-M02-004-3**: 無權編輯他人房源
**Given** 房源不屬於當前用戶的店鋪
**When** 提交房源編輯請求
**Then** 回傳 403 錯誤

**測試資料**:
- 預期錯誤: `E-4031 ACCESS_DENIED`

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| base_price ≤ 0 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| title 空白 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| 封鎖已有預訂的日期 | 警告但允許 | 需通知已預訂房客 |
| 編輯 DELETED 狀態房源 | 阻擋 | E-4001 房源已刪除 |

---

#### 依賴與假設

**依賴項**:
- M03 認證與授權
- M12 動態定價（編輯 base_price 影響動態價格計算）

---

**🔴 人機協作確認點** (US-M02-004):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 日曆編輯邏輯已確認
- [ ] BA 已驗證需求正確性

---

#### US-M02-005: 房源下架

**Story 描述**:
- **As a** Host
- **I want to** 將房源下架（軟刪除）
- **So that** 房源不再於前台顯示，但保留歷史預訂記錄

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 3

---

#### Acceptance Criteria

**AC-M02-005-1**: 將 ACTIVE 房源下架
**Given** 房源狀態為 ACTIVE
**When** Host 請求下架房源
**Then** 將房源狀態改為 INACTIVE

**測試資料**:
- 輸入: `listing_id: "uuid"`, `action: "deactivate"`
- 預期輸出: `{ id: "uuid", status: "INACTIVE" }`

---

**AC-M02-005-2**: 將 INACTIVE 房源重新上架
**Given** 房源狀態為 INACTIVE
**When** Host 請求上架房源
**Then** 將房源狀態改為 ACTIVE

**測試資料**:
- 輸入: `listing_id: "uuid"`, `action: "activate"`
- 預期輸出: `{ id: "uuid", status: "ACTIVE" }`

---

**AC-M02-005-3**: 永久刪除房源
**Given** 房源狀態為 INACTIVE
**When** Host 請求永久刪除房源
**Then** 將房源狀態改為 DELETED（不可逆）

**測試資料**:
- 輸入: `listing_id: "uuid"`, `action: "delete"`
- 預期輸出: `{ id: "uuid", status: "DELETED" }`

---

**AC-M02-005-4**: 刪除已有未完成預訂的房源
**Given** 房源有待處理預訂（BOOKED 狀態）
**When** Host 請求永久刪除房源
**Then** 回傳警告，需確認是否強制刪除

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| DELETED 狀態房源 | 阻擋 | E-4001 已刪除房源無法操作 |
| 已有待處理預訂的房源 | 允許下架 | 保留預訂直到預訂完成 |
| 多人同時操作同一房源 | 樂觀鎖防護 | E-4091 CONFLICT |

---

#### 依賴與假設

**依賴項**:
- M06 預訂系統（檢查是否有待處理預訂）

**假設條件**:
- Phase 1 只做軟刪除（status → DELETED），不實際刪除資料

---

**🔴 人機協作確認點** (US-M02-005):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 刪除保護機制已確認
- [ ] BA 已驗證需求正確性

---

### 4.6 業務規則

#### BR-M02-001: Overbooking 防止

| 項目 | 內容 |
|------|------|
| **Business Rule ID** | BR-M02-001 |
| **對應 Feature** | F-M02-001 |
| **優先級** | P0 |
| **類型** | 狀態轉換 / 資料一致性 |

**規則描述**：
同一 `room_listing_id + calendar_date` 只能有一筆 `BOOKED` 狀態記錄。透過資料庫 `UNIQUE (room_listing_id, calendar_date)` 約束確保。

**驗證邏輯**：
```
-- 預訂時檢查
IF (room_calendar WHERE room_listing_id = :id AND calendar_date = :date AND status = 'BOOKED' AND booking_id != :currentBooking) EXISTS THEN
  RETURN Error("E-1010: 該日期已被預訂")
END IF
```

---

#### BR-M02-002: room_calendar 狀態轉換矩陣

| 目前狀態 | 目標狀態 | 允許 | 說明 |
|----------|----------|------|------|
| AVAILABLE | BLOCKED | O | 房東主動封鎖 |
| AVAILABLE | MAINTENANCE | O | 臨時維護 |
| AVAILABLE | BOOKED | X | 必須透過預訂 API |
| BLOCKED | AVAILABLE | O | 解封 |
| BLOCKED | MAINTENANCE | O | |
| BOOKED | AVAILABLE | X | 需先取消預訂 |
| BOOKED | BLOCKED | X | 需先取消預訂 |
| BOOKED | MAINTENANCE | O | 緊急維護，需通知房客 |
| MAINTENANCE | AVAILABLE | O | 維護結束恢復 |
| MAINTENANCE | BOOKED | X | 需先改為 AVAILABLE |

---

### 4.7 API 規格概要

| API Endpoint | 方法 | 用途 | 角色 |
|-------------|------|------|------|
| `/api/v2/listings?type=ROOM` | GET | 房源列表（分頁/篩選） | Guest+ |
| `/api/v2/listings/:id` | GET | 房源詳情 | Guest+ |
| `/api/v2/listings/:id/calendar` | GET | 日曆與價格查詢 | Guest+ |
| `/api/v2/dashboard/listings` | GET | 店鋪房源列表 | Host+ |
| `/api/v2/dashboard/listings` | POST | 建立房源 | Host+ |
| `/api/v2/dashboard/listings/:id` | PUT | 更新房源 | Host+ |
| `/api/v2/dashboard/listings/:id/status` | PUT | 更新房源狀態 | Host+ |

**詳細 API 規格**: 見 [docs/02_architecture/API_M02_Listing_Center.md](../02_architecture/API_M02_Listing_Center.md)

---

## 5. M03 會員與權限系統 (Member & Auth)

### 5.1 模組概述

| 項目 | 內容 |
|------|------|
| **模組編號** | M03 |
| **模組名稱** | 會員與權限系統 (Member & Auth System) |
| **所屬子系統** | Platform Infrastructure |
| **核心能力** | 會員登入、RBAC、JWT 雙令牌 |
| **依賴模組** | — (基礎模組) |
| **Phase 歸屬** | Phase 1 |

### 5.2 功能總覽

| 功能 | 說明 | 優先級 | Phase |
|------|------|--------|-------|
| 會員註冊 | Email + 密碼註冊 | P0 | Phase 1 |
| 會員登入 | Email + 密碼登入，回傳 JWT | P0 | Phase 1 |
| JWT 驗證 | 所有 API 的 JWT 驗證 | P0 | Phase 1 |
| Refresh Token | 刷新 Access Token | P0 | Phase 1 |
| 會員登出 | 失效 Refresh Token | P0 | Phase 1 |
| 密碼修改 | 已登入會員修改密碼 | P1 | Phase 2 |
| OAuth2 第三方登入 | Google/Facebook 登入 | P1 | Phase 2 |
| KYC 實名認證 | 身分證驗證 | P1 | Phase 2 |

### 5.3 主要用戶

| 用戶角色 | 描述 | 主要需求 | 使用頻率 |
|---------|------|---------|---------|
| Guest | 未登入訪客 | 註冊、登入 | 一次性 |
| Buyer | 已登入買家 | 維持登入狀態、購物 | 每日 |
| Seller | 零售賣家 | 登入後台管理 | 每日 |
| Host | 民宿房東 | 登入後台管理 | 每日 |
| Admin | 平台管理員 | 全站管理 | 每日 |

### 5.4 資料模型

#### 5.4.1 User 模型

```
users
├── id: UUID (PK)
├── email: VARCHAR(255) UNIQUE
├── password_hash: VARCHAR(255) -- bcrypt
├── user_type: ENUM('BUYER', 'SELLER', 'HOST', 'ADMIN', 'SUPERADMIN')
├── status: ENUM('ACTIVE', 'SUSPENDED', 'DELETED')
├── email_verified: BOOLEAN DEFAULT false
├── created_at: TIMESTAMP
├── updated_at: TIMESTAMP
└── INDEX (email)

user_profiles
├── id: UUID (PK)
├── user_id: UUID (FK → users.id, UNIQUE)
├── display_name: VARCHAR(100)
├── phone: VARCHAR(20)
├── avatar_url: VARCHAR(500)
├── id_number: VARCHAR(20) -- KYC, Phase 2
├── id_card_front_url: VARCHAR(500) -- KYC, Phase 2
├── id_card_back_url: VARCHAR(500) -- KYC, Phase 2
└── created_at: TIMESTAMP

tenant_members (多租戶關聯)
├── id: UUID (PK)
├── user_id: UUID (FK → users.id)
├── tenant_id: UUID (FK → tenants.id)
├── role: ENUM('OWNER', 'STAFF') -- OWNER = StoreOwner, STAFF = StoreStaff
├── invited_by: UUID (FK → users.id)
├── joined_at: TIMESTAMP
└── UNIQUE (user_id, tenant_id)
```

---

### 5.5 User Stories

---

#### US-M03-001: 會員註冊

**Story 描述**:
- **As a** Guest
- **I want to** 使用 Email 註冊成為會員
- **So that** 可以購買商品或預訂民宿

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 3

---

#### Acceptance Criteria

**AC-M03-001-1**: 成功註冊
**Given** Guest 提交有效的註冊資訊
**When** 系統驗證資料格式正確且 Email 未被註冊
**Then** 建立 User 記錄並回傳成功

**測試資料**:
```json
{
  "email": "user@example.com",
  "password": "SecurePass123",
  "userType": "BUYER"
}
```

---

**AC-M03-001-2**: Email 已被註冊
**Given** 提交的 Email 已被其他用戶使用
**When** 提交註冊請求
**Then** 回傳錯誤，不建立新帳號

**測試資料**:
- 輸入: `email: "existing@example.com"`
- 預期錯誤: `E-3001 EMAIL_ALREADY_EXISTS`

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| Email 格式無效 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| 密碼太弱 | 驗證失敗 | E-4001 VALIDATION_ERROR (密碼需 8+ 字元) |
| Email 已存在 | 阻擋 | E-3001 EMAIL_ALREADY_EXISTS |
| 伺服器錯誤 | 回滾 | E-5000 INTERNAL_ERROR |

---

**🔴 人機協作確認點** (US-M03-001):
- [ ] 驗證規則完整
- [ ] 密碼強度要求合理
- [ ] 錯誤訊息適當
- [ ] BA 已驗證需求正確性

---

#### US-M03-002: 會員登入

**Story 描述**:
- **As a** 已註冊會員
- **I want to** 使用 Email 和密碼登入
- **So that** 可以訪問個人功能和進行交易

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 5

---

#### Acceptance Criteria

**AC-M03-002-1**: 成功登入
**Given** 會員輸入正確的 Email 和密碼
**When** 提交登入請求
**Then** 回傳 Access Token 和 Refresh Token

**測試資料**:
```json
{
  "email": "user@example.com",
  "password": "SecurePass123"
}
```
**預期輸出**:
```json
{
  "accessToken": "eyJhbG...",
  "refreshToken": "eyJhbG...",
  "expiresIn": 1800,
  "tokenType": "Bearer"
}
```

---

**AC-M03-002-2**: 錯誤密碼
**Given** 會員輸入正確的 Email 但錯誤的密碼
**When** 提交登入請求
**Then** 回傳 401，不洩露是 Email 還是密碼錯誤

**測試資料**:
- 輸入: `password: "WrongPassword"`
- 預期錯誤: `E-3002 INVALID_CREDENTIALS`

---

**AC-M03-002-3**: 帳號被停用
**Given** 會員帳號狀態為 SUSPENDED 或 DELETED
**When** 提交登入請求
**Then** 回傳 403，帳號無法登入

**測試資料**:
- 輸入: `status: "SUSPENDED"`
- 預期錯誤: `E-3003 ACCOUNT_SUSPENDED`

---

**🔴 人機協作確認點** (US-M03-002):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 錯誤處理已確認（不洩露具體錯誤原因）
- [ ] BA 已驗證需求正確性

---

#### US-M03-003: JWT 刷新

**Story 描述**:
- **As a** 已登入會員
- **I want to** 在 Access Token 過期前刷新權杖
- **So that** 可以繼續使用系統而不需要重新登入

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 3

---

#### Acceptance Criteria

**AC-M03-003-1**: 成功刷新 Access Token
**Given** 會員持有有效的 Refresh Token
**When** 提交刷新請求 (POST /api/v2/auth/refresh)
**Then** 回傳新的 Access Token 和 Refresh Token（原 Refresh Token 廢棄）

**測試資料**:
```json
{
  "refreshToken": "eyJhbG..."
}
```
**預期輸出**:
```json
{
  "accessToken": "eyJhbG... (new)",
  "refreshToken": "eyJhbG... (new)",
  "expiresIn": 1800,
  "tokenType": "Bearer"
}
```

---

**AC-M03-003-2**: Refresh Token 已過期
**Given** 會員持有的 Refresh Token 已過期
**When** 提交刷新請求
**Then** 回傳 401，需重新登入

**測試資料**:
- 預期錯誤: `E-1001 JWT_INVALID`

---

**AC-M03-003-3**: Refresh Token 已被使用
**Given** 會員持有的 Refresh Token 已被使用過
**When** 提交刷新請求
**Then** 回傳 401，並使所有相關 Token 失效（安全考量）

**測試資料**:
- 預期錯誤: `E-1001 JWT_INVALID`

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| Refresh Token 格式無效 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| Refresh Token 已被使用 | 安全回滾 | E-1001 JWT_INVALID |
| Refresh Token 過期 | 回傳 401 | E-1001 JWT_EXPIRED |
| 請求頻率過高 | Rate Limit | E-4291 RATE_LIMIT_EXCEEDED |

---

#### 依賴與假設

**依賴項**:
- BR-M03-001: JWT Token 有效期

**假設條件**:
- Refresh Token 存儲在 Redis，TTL = 30 天
- 每次刷新都頒發新的 Refresh Token（Rotation）

---

**🔴 人機協作確認點** (US-M03-003):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] Token Rotation 機制已確認
- [ ] BA 已驗證需求正確性

---

#### US-M03-004: 會員登出

**Story 描述**:
- **As a** 已登入會員
- **I want to** 登出系統
- **So that** 清除登入狀態，保護帳戶安全

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 2

---

#### Acceptance Criteria

**AC-M03-004-1**: 成功登出
**Given** 會員已登入
**When** 提交登出請求 (POST /api/v2/auth/logout)
**Then** 使 Refresh Token 失效，回傳成功

**測試資料**:
- 輸入: `{ "refreshToken": "eyJhbG..." }`
- 預期輸出: `{ "message": "Logout successful" }`

---

**AC-M03-004-2**: 登出後使用舊 Token
**Given** 會員已登出（Refresh Token 已失效）
**When** 使用原 Access Token 發送請求
**Then** Access Token 仍有效直到過期，但刷新將失敗

---

**AC-M03-004-3**: 多設備登出
**Given** 會員在多個設備登入
**When** 在任一設備登出
**Then** 只使該設備的 Refresh Token 失效（不影響其他設備）

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| Refresh Token 已失效 | 正常處理 | 回傳成功（ idempotency ） |
| Refresh Token 格式無效 | 驗證失敗 | E-4001 VALIDATION_ERROR |

---

#### 依賴與假設

**依賴項**:
- BR-M03-001: JWT Token 有效期

**假設條件**:
- Phase 1 只支援單一 Refresh Token（不支援多設備同時登入）
- 未來 Phase 2 可支援多設備管理和遠端登出

---

**🔴 人機協作確認點** (US-M03-004):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 多設備場景已確認
- [ ] BA 已驗證需求正確性

---

#### US-M03-005: 取得當前用戶資訊

**Story 描述**:
- **As a** 已登入會員
- **I want to** 取得當前登入用戶的資訊
- **So that** 查看個人資料和帳戶狀態

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 2

---

#### Acceptance Criteria

**AC-M03-005-1**: 取得用戶基本資訊
**Given** 會員已登入（持有有效 Access Token）
**When** 請求取得當前用戶資訊 (GET /api/v2/auth/me)
**Then** 回傳用戶基本資訊（不含密碼）

**測試資料**:
**預期輸出**:
```json
{
  "id": "user-uuid-001",
  "email": "user@example.com",
  "userType": "BUYER",
  "status": "ACTIVE",
  "profile": {
    "displayName": "王小明",
    "phone": "0912345678",
    "avatarUrl": "https://..."
  },
  "tenants": [
    {
      "tenantId": "tenant-uuid-001",
      "storeName": "我的數位商店",
      "role": "OWNER"
    }
  ]
}
```

---

**AC-M03-005-2**: Token 過期
**Given** 會員的 Access Token 已過期
**When** 請求取得當前用戶資訊
**Then** 回傳 401，需刷新或重新登入

**測試資料**:
- 預期錯誤: `E-1001 JWT_EXPIRED`

---

**AC-M03-005-3**: 取得他人用戶資訊
**Given** 用戶嘗試取得其他用戶的資訊
**When** 請求 (GET /api/v2/users/:id)
**Then** 回傳 403，無法取得他人資訊

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| Token 無效 | 阻擋 | E-1001 JWT_INVALID |
| Token 過期 | 阻擋 | E-1001 JWT_EXPIRED |
| 用戶已被刪除 | 阻擋 | E-4041 USER_NOT_FOUND |

---

#### 依賴與假設

**依賴項**:
- M17 tenant_members（用於查詢用戶所屬租戶）

---

**🔴 人機協作確認點** (US-M03-005):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 隱私資訊保護已確認
- [ ] BA 已驗證需求正確性

---

### 5.6 業務規則

#### BR-M03-001: JWT Token 有效期

| 項目 | 內容 |
|------|------|
| **Business Rule ID** | BR-M03-001 |
| **優先級** | P0 |
| **類型** | 認證 |

**規則描述**：
- Access Token 有效期限：30 分鐘 (1800 秒)
- Refresh Token 有效期限：30 天
- Refresh Token 只能使用一次（使用後廢棄，需頒發新的 Refresh Token）

---

#### BR-M03-002: 密碼雜湊

| 項目 | 內容 |
|------|------|
| **Business Rule ID** | BR-M03-002 |
| **優先級** | P0 |
| **類型** | 安全 |

**規則描述**：
- 使用 bcrypt 加密（salt round ≥ 12）
- 密碼長度：8-128 字元
- 密碼需包含：大小寫字母、數字

---

### 5.7 API 規格概要

| API Endpoint | 方法 | 用途 | 角色 |
|-------------|------|------|------|
| `/api/v2/auth/register` | POST | 會員註冊 | Guest |
| `/api/v2/auth/login` | POST | 會員登入 | Guest |
| `/api/v2/auth/refresh` | POST | 刷新 Access Token | Guest+ |
| `/api/v2/auth/logout` | POST | 會員登出 | Buyer+ |
| `/api/v2/auth/me` | GET | 取得當前用戶資訊 | Buyer+ |

**詳細 API 規格**: 見 [docs/02_architecture/API_M03_Auth.md](../02_architecture/API_M03_Auth.md)

---

## 6. M05 訂單履約系統 (Order Fulfillment)

### 6.1 模組概述

| 項目 | 內容 |
|------|------|
| **模組編號** | M05 |
| **模組名稱** | 訂單履約系統 (Order Fulfillment System) |
| **所屬子系統** | Retail Engine |
| **核心能力** | 訂單建立、狀態機、庫存扣減、取消與資源釋放 |
| **依賴模組** | M01 (商品), M03 (認證), M04 (購物車), M07 (金流) |
| **Phase 歸屬** | Phase 1 |

### 6.2 功能總覽

| 功能 | 說明 | 優先級 | Phase |
|------|------|--------|-------|
| 訂單建立 | 從購物車或直接購買建立訂單 | P0 | Phase 1 |
| 訂單查詢 | 買家/賣家查詢訂單列表與詳情 | P0 | Phase 1 |
| 訂單狀態機 | CREATED → SHIPPING → DELIVERED → COMPLETED | P0 | Phase 1 |
| 庫存扣減 | 訂單建立時原子性扣減庫存 | P0 | Phase 1 |
| 訂單取消 | 買家取消訂單，釋放庫存 | P0 | Phase 1 |
| 支付（Mock） | Phase 1 Payment Mock，自動狀態推進 | P0 | Phase 1 |
| 退貨退款 | 退貨與退款處理 | P1 | Phase 2 |

### 6.3 資料模型

```
orders
├── id: UUID (PK)
├── tenant_id: UUID (FK → tenants.id)
├── order_number: VARCHAR(20) UNIQUE -- 如 ORD-20260409-001
├── user_id: UUID (FK → users.id) -- 買家
├── status: ENUM('CREATED', 'PAID', 'SHIPPING', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'REFUNDING', 'REFUNDED')
├── total_amount: DECIMAL(12,2)
├── currency: VARCHAR(3) DEFAULT 'TWD'
├── payment_method: VARCHAR(50) -- Phase 1: 'MOCK'
├── payment_id: VARCHAR(100) -- Phase 1: 'mock_xxx'
├── idempotency_key: UUID -- 防重複
├── created_at: TIMESTAMP
├── updated_at: TIMESTAMP
└── INDEX (tenant_id, user_id, status)

order_items
├── id: UUID (PK)
├── order_id: UUID (FK → orders.id)
├── listing_id: UUID (FK → listings.id)
├── sku_id: UUID (FK → product_skus.id, NULLABLE)
├── quantity: INTEGER
├── unit_price: DECIMAL(12,2)
├── subtotal: DECIMAL(12,2)
└── INDEX (order_id)

order_state_log
├── id: UUID (PK)
├── order_id: UUID (FK → orders.id)
├── sequence: INTEGER
├── from_status: ENUM -- NULL for initial
├── to_status: ENUM
├── reason: VARCHAR(200)
├── operator_id: UUID (FK → users.id)
├── created_at: TIMESTAMP
└── INDEX (order_id)
```

---

### 6.4 User Stories

---

#### US-M05-001: 建立訂單

**Story 描述**:
- **As a** Buyer
- **I want to** 建立訂單購買商品
- **So that** 完成購物流程並等待商品配送

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 8

**業務價值**:
買家可以快速完成訂單建立，系統自動扣減庫存。

**技術實現概要**:
1. 驗證 Buyer 角色
2. 檢查 idempotency_key 防止重複建立
3. 原子性扣減庫存（BR-M05-002）
4. 建立 Order 和 OrderItem 記錄
5. 建立 order_state_log（sequence = 1, toStatus = 'CREATED'）
6. Payment Mock 直接標記為已支付

---

#### Acceptance Criteria

**AC-M05-001-1**: 成功建立訂單
**Given** Buyer 已登入且商品有足夠庫存
**When** 提交訂單建立請求
**Then** 建立訂單並回傳訂單資訊

**測試資料**:
```json
{
  "idempotencyKey": "uuid-001",
  "items": [
    { "listingId": "listing-uuid-001", "skuId": "sku-uuid-001", "quantity": 2 }
  ],
  "paymentMethod": "MOCK"
}
```
**預期輸出**:
```json
{
  "orderId": "order-uuid-001",
  "orderNumber": "ORD-20260409-001",
  "status": "CREATED",
  "totalAmount": 85800,
  "createdAt": "2026-04-09T10:00:00.000Z"
}
```

---

**AC-M05-001-2**: 庫存不足
**Given** Buyer 嘗試購買的商品庫存不足
**When** 提交訂單建立請求
**Then** 回傳錯誤，不建立訂單

**測試資料**:
- 預期錯誤: `E-5003 INSUFFICIENT_INVENTORY`

---

**AC-M05-001-3**: 重複訂單（idempotency）
**Given** 使用相同的 idempotencyKey 再次提交請求
**When** 系統已存在相同 idempotencyKey 的訂單
**Then** 回傳已存在的訂單，不建立新訂單

---

**AC-M05-001-4**: 惡意重複下單防護
**Given** 惡意用戶嘗試同時多次提交相同訂單
**When** 庫存在臨界值
**Then** 透過樂觀鎖確保只有一次成功

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| idempotencyKey 空白 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| 商品不存在 | 阻擋 | E-4041 LISTING_NOT_FOUND |
| SKU 不屬於該商品 | 阻擋 | E-4001 VALIDATION_ERROR |
| 庫存不足 | 阻擋 | E-5003 INSUFFICIENT_INVENTORY |
| 訂單金額計算錯誤 | 阻擋 | E-5004 ORDER_AMOUNT_MISMATCH |

---

#### 依賴與假設

**依賴項**:
- M01 商品中心（庫存查詢）
- M03 認證與授權
- BR-M05-002 庫存扣減防護

**假設條件**:
- Phase 1 使用 Payment Mock，訂單直接標記為 CREATED(=PAID)
- Phase 1 不支援部分取消

---

**🔴 人機協作確認點** (US-M05-001):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 庫存扣減邏輯已確認
- [ ] BA 已驗證需求正確性

---

#### US-M05-002: 查詢訂單列表

**Story 描述**:
- **As a** Buyer
- **I want to** 查詢我的訂單列表
- **So that** 追蹤我的購買歷史和訂單狀態

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 3

---

#### Acceptance Criteria

**AC-M05-002-1**: 取得買家訂單列表
**Given** Buyer 已登入
**When** 請求取得訂單列表 (GET /api/v2/orders)
**Then** 回傳該買家的所有訂單（分頁）

**測試資料**:
**預期輸出**:
```json
{
  "items": [
    {
      "orderId": "order-uuid-001",
      "orderNumber": "ORD-20260409-001",
      "status": "CREATED",
      "totalAmount": 85800,
      "createdAt": "2026-04-09T10:00:00.000Z"
    }
  ],
  "pagination": {
    "page": 1,
    "pageSize": 20,
    "total": 5
  }
}
```

---

**AC-M05-002-2**: 篩選訂單狀態
**Given** Buyer 請求特定狀態的訂單
**When** 加上 status filter
**Then** 只回傳符合狀態的訂單

**測試資料**:
- 輸入: `?status=CREATED`
- 預期輸出: 只包含 CREATED 狀態的訂單

---

**AC-M05-002-3**: 取得賣家訂單列表
**Given** Seller 已登入
**When** 請求取得賣家訂單列表 (GET /api/v2/dashboard/orders)
**Then** 回傳該賣家店鋪的所有訂單

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| 無訂單 | 回傳空陣列 | `{ items: [], pagination: {...} }` |
| 跨租戶查詢 | 阻擋 | E-4031 ACCESS_DENIED |

---

**🔴 人機協作確認點** (US-M05-002):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 分頁邏輯已確認
- [ ] BA 已驗證需求正確性

---

#### US-M05-003: 查詢訂單詳情

**Story 描述**:
- **As a** Buyer
- **I want to** 查看訂單的詳細資訊
- **So that** 了解訂單內容和物流進度

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 2

---

#### Acceptance Criteria

**AC-M05-003-1**: 取得買家訂單詳情
**Given** Buyer 已登入且訂單屬於該買家
**When** 請求取得訂單詳情 (GET /api/v2/orders/:id)
**Then** 回傳訂單完整資訊

**測試資料**:
**預期輸出**:
```json
{
  "orderId": "order-uuid-001",
  "orderNumber": "ORD-20260409-001",
  "status": "SHIPPING",
  "totalAmount": 85800,
  "paymentMethod": "MOCK",
  "items": [
    {
      "listingId": "listing-uuid-001",
      "skuId": "sku-uuid-001",
      "title": "iPhone 15 Pro",
      "quantity": 2,
      "unitPrice": 42900,
      "subtotal": 85800
    }
  ],
  "stateLogs": [
    { "sequence": 1, "toStatus": "CREATED", "createdAt": "..." },
    { "sequence": 2, "fromStatus": "CREATED", "toStatus": "SHIPPING", "createdAt": "..." }
  ]
}
```

---

**AC-M05-003-2**: 無權查看他人訂單
**Given** 買家嘗試查看不屬於自己的訂單
**When** 請求取得訂單詳情
**Then** 回傳 403 錯誤

**測試資料**:
- 預期錯誤: `E-4031 ACCESS_DENIED`

---

**AC-M05-003-3**: 訂單不存在
**Given** 買家請求查看不存在的訂單
**When** 請求取得訂單詳情
**Then** 回傳 404 錯誤

**測試資料**:
- 預期錯誤: `E-4041 ORDER_NOT_FOUND`

---

**🔴 人機協作確認點** (US-M05-003):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 隱私資訊保護已確認
- [ ] BA 已驗證需求正確性

---

#### US-M05-004: 取消訂單

**Story 描述**:
- **As a** Buyer
- **I want to** 取消我的訂單
- **So that** 在必要時終止交易並恢復庫存

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 5

---

#### Acceptance Criteria

**AC-M05-004-1**: 買家取消訂單
**Given** Buyer 擁有 CREATED 狀態的訂單
**When** 提交取消請求 (POST /api/v2/orders/:id/cancel)
**Then** 將訂單狀態改為 CANCELLED，釋放庫存

**測試資料**:
```json
{
  "reason": "不想要了"
}
```

---

**AC-M05-004-2**: 釋放庫存
**Given** 訂單被取消
**When** 系統處理取消請求
**Then** 執行 BR-M05-003 釋放已預留的庫存

**驗證**:
- `reserved_qty` 減少 `order_qty`
- `available_qty` 恢復

---

**AC-M05-004-3**: 無法取消 SHIPPING 狀態訂單
**Given** 買家嘗試取消 SHIPPING 狀態的訂單
**When** 提交取消請求
**Then** 回傳錯誤，無法取消

**測試資料**:
- 預期錯誤: `E-4006 ORDER_CANNOT_CANCEL`

---

**AC-M05-004-4**: 超時取消限制
**Given** 買家在時限後嘗試取消訂單
**When** 系統檢查取消時限
**Then** 回傳錯誤，需聯繫客服或 Admin

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| 訂單已是 CANCELLED | 阻擋 | E-4006 ORDER_ALREADY_CANCELLED |
| 訂單狀態為 SHIPPING | 阻擋 | E-4006 ORDER_CANNOT_CANCEL |
| 訂單狀態為 DELIVERED | 阻擋 | E-4006 ORDER_CANNOT_CANCEL |
| 庫存釋放失敗 | 交易回滾 | E-5005 INVENTORY_RELEASE_FAILED |

---

#### 依賴與假設

**依賴項**:
- BR-M05-003 訂單取消庫存釋放

**假設條件**:
- Phase 1 買家只能在 CREATED 狀態時取消訂單

---

**🔴 人機協作確認點** (US-M05-004):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 庫存釋放邏輯已確認
- [ ] BA 已驗證需求正確性

---

#### US-M05-005: 更新訂單狀態

**Story 描述**:
- **As a** Seller
- **I want to** 更新訂單狀態（發貨、送達）
- **So that** 推進訂單履約流程

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 3

---

#### Acceptance Criteria

**AC-M05-005-1**: 賣家發貨
**Given** Seller 擁有 CREATED 狀態的訂單
**When** 請求發貨 (PUT /api/v2/dashboard/orders/:id/status)
**Then** 將訂單狀態改為 SHIPPING

**測試資料**:
```json
{
  "status": "SHIPPING",
  "trackingNumber": "SHIP-12345"
}
```

---

**AC-M05-005-2**: 確認送達
**Given** 物流或 Seller 確認商品已送達
**When** 請求更新狀態為 DELIVERED
**Then** 將訂單狀態改為 DELIVERED

---

**AC-M05-005-3**: 買家確認完成
**Given** Buyer 確認收到商品
**When** 請求更新狀態為 COMPLETED
**Then** 將訂單狀態改為 COMPLETED，並釋放庫存（從 reserved 轉為實際扣減）

---

**AC-M05-005-4**: 狀態機校驗
**Given** 嘗試執行不合法的狀態轉換
**When** 請求更新訂單狀態
**Then** 回傳錯誤，不允許該操作

**測試資料**:
- 預期錯誤: `E-4007 INVALID_ORDER_STATE_TRANSITION`

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| 非訂單擁有者 | 阻擋 | E-4031 ACCESS_DENIED |
| 非法狀態轉換 | 阻擋 | E-4007 INVALID_ORDER_STATE_TRANSITION |
| 訂單已取消 | 阻擋 | E-4006 ORDER_ALREADY_CANCELLED |

---

#### 依賴與假設

**依賴項**:
- BR-M05-001 Phase 1 訂單狀態機

---

**🔴 人機協作確認點** (US-M05-005):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 狀態機邏輯已確認
- [ ] BA 已驗證需求正確性

---

### 6.5 業務規則

#### BR-M05-001: Phase 1 訂單狀態機

| 項目 | 內容 |
|------|------|
| **Business Rule ID** | BR-M05-001 |
| **優先級** | P0 |
| **類型** | 狀態轉換 |

**Phase 1 零售路徑（Payment Mock 等效）**：
```
CREATED(=PAID) → SHIPPING → DELIVERED → COMPLETED
```

- Payment Mock 在 `POST /api/orders` 時直接達到 `CREATED(=PAID)` 等效狀態
- `payment.received` 事件在 Phase 1 不存在
- `order_state_log.sequence = 1, toStatus = "CREATED"`（而非 `CONFIRMED`）

**狀態轉換矩陣**：

| 目前狀態 | 目標狀態 | 允許 | 觸發角色 |
|----------|----------|------|---------|
| CREATED | SHIPPING | O | Seller |
| CREATED | CANCELLED | O | Buyer (限時), Admin |
| SHIPPING | DELIVERED | O | Seller/物流 |
| SHIPPING | CANCELLED | X | — |
| DELIVERED | COMPLETED | O | Buyer (自動) |
| DELIVERED | REFUNDING | O | Buyer |
| CANCELLED | — | 終態 | — |
| REFUNDING | REFUNDED | O | Admin/系統 |
| REFUNDING | CANCELLED | O | Admin |

---

#### BR-M05-002: 庫存扣減防護

| 項目 | 內容 |
|------|------|
| **Business Rule ID** | BR-M05-002 |
| **優先級** | P0 |
| **類型** | 資料一致性 |

**規則描述**：
訂單建立時，必須原子性扣減庫存（樂觀鎖防護）。

**驗證邏輯**：
```sql
UPDATE product_inventory
SET reserved_qty = reserved_qty + :orderQty,
    version = version + 1
WHERE sku_id = :skuId
  AND available_qty >= :orderQty
  AND version = :expectedVersion
-- 若影響行數 = 0，表示庫存不足或版本衝突
```

---

#### BR-M05-003: 訂單取消庫存釋放

| 項目 | 內容 |
|------|------|
| **Business Rule ID** | BR-M05-003 |
| **優先級** | P0 |
| **類型** | 補償機制 |

**規則描述**：
訂單取消時，必須釋放已預留的庫存。

**驗證邏輯**：
```sql
UPDATE product_inventory
SET reserved_qty = reserved_qty - :orderQty,
    version = version + 1
WHERE sku_id = :skuId
```

---

### 6.5 API 規格概要

| API Endpoint | 方法 | 用途 | 角色 |
|-------------|------|------|------|
| `/api/v2/orders` | POST | 建立訂單 | Buyer+ |
| `/api/v2/orders` | GET | 買家訂單列表 | Buyer+ |
| `/api/v2/orders/:id` | GET | 訂單詳情 | Buyer+ |
| `/api/v2/orders/:id/cancel` | POST | 取消訂單 | Buyer+ |
| `/api/v2/dashboard/orders` | GET | 賣家訂單列表 | Seller+ |
| `/api/v2/dashboard/orders/:id/status` | PUT | 更新訂單狀態 | Seller+ |

**詳細 API 規格**: 見 [docs/02_architecture/API_M05_Order.md](../02_architecture/API_M05_Order.md)

---

## 7. M12 動態定價引擎 (Dynamic Pricing Engine)

### 7.1 模組概述

| 項目 | 內容 |
|------|------|
| **模組編號** | M12 |
| **模組名稱** | 動態定價引擎 (Dynamic Pricing Engine) |
| **所屬子系統** | Booking Engine |
| **核心能力** | 平假日調價、旺季加價、早鳥/長住折扣、手動覆蓋 |
| **依賴模組** | M02 (房源), M03 (認證), M17 (租戶) |
| **Phase 歸屬** | **Phase 1 (Must Have)** |

### 7.2 功能總覽

| 功能 | 說明 | 優先級 | Phase |
|------|------|--------|-------|
| 平假日調價 | 週六/日/國定假日自動套用倍率 | P0 | Phase 1 |
| 季度/旺季加價 | 指定日期範圍套用倍率 | P0 | Phase 1 |
| 早鳥優惠 | 提前 N 天預訂享折扣 | P1 | Phase 1 |
| 長住折扣 | 連住 N 晚享折扣 | P1 | Phase 1 |
| 手動覆蓋 | 特定日期人工設定固定價格 | P0 | Phase 1 |
| 末班車優惠 | 入住前 N 天未訂出的降價 | P2 | Phase 2 |
| 定價預覽 | 房東在後台預覽未來 90 天定價日曆 | P0 | Phase 1 |

### 7.3 資料模型

```
pricing_rules
├── id: UUID (PK)
├── tenant_id: UUID (FK → tenants.id)
├── room_listing_id: UUID (FK → listings.id, NULLABLE) -- NULL = 適用全部房間
├── rule_type: ENUM('WEEKDAY_WEEKEND', 'SEASONAL', 'EARLY_BIRD', 'LONG_STAY', 'MANUAL_OVERRIDE', 'LAST_MINUTE')
├── rule_name: VARCHAR(100)
├── priority: INTEGER DEFAULT 0
├── config: JSONB
├── valid_from: DATE
├── valid_to: DATE
├── is_active: BOOLEAN DEFAULT true
├── created_at: TIMESTAMP
└── INDEX (tenant_id, room_listing_id, is_active)
```

**config JSONB 範例**：

```json
// WEEKDAY_WEEKEND
{ "weekdayMultiplier": 1.0, "weekendMultiplier": 1.3 }

// SEASONAL
{ "multiplier": 1.5, "description": "春節旺季" }

// EARLY_BIRD
{ "daysInAdvance": 30, "discountPercent": 15 }

// LONG_STAY
{ "minNights": 7, "discountPercent": 10 }

// MANUAL_OVERRIDE
{ "fixedPrice": 3500, "reason": "跨年特價" }
```

---

### 7.4 價格計算邏輯

#### 7.4.1 價格計算路徑

```
最終價格 = applyRules(base_price, applicable_rules_sorted_by_priority)

規則疊加順序（由低到高優先級）：
1. 基礎價 (listing.base_price)
2. 平假日調價 (WEEKDAY_WEEKEND)
3. 季度/旺季加價 (SEASONAL)
4. 早鳥優惠 (EARLY_BIRD) — 提前 N 天預訂的折扣
5. 長住折扣 (LONG_STAY) — 連住 N 晚的折扣
6. 末班車優惠 (LAST_MINUTE) — 入住前 N 天的折扣
7. 人工手動覆蓋 (MANUAL_OVERRIDE) — 最高優先級

最終價格 = max(calculated_price, 0) -- 不可為負
```

---

### 7.5 User Stories

---

#### US-M12-001: 設定平假日調價規則

**Story 描述**:
- **As a** Host
- **I want to** 設定週末和平日的不同價格倍率
- **So that** 週末可以有更高的收益

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 5

---

#### Acceptance Criteria

**AC-M12-001-1**: 設定週末倍率
**Given** Host 在後台建立 WEEKDAY_WEEKEND 規則
**When** 規則設為 weekdayMultiplier=1.0, weekendMultiplier=1.3
**Then** 所有房間的週末價格自動套用 base_price × 1.3

**測試資料**:
```json
{
  "ruleType": "WEEKDAY_WEEKEND",
  "ruleName": "標準週末調價",
  "weekdayMultiplier": 1.0,
  "weekendMultiplier": 1.3,
  "validFrom": "2026-01-01",
  "validTo": "2026-12-31"
}
```

---

**AC-M12-001-2**: 同類型規則覆蓋
**Given** 房間已有一條 active WEEKDAY_WEEKEND 規則
**When** Host 建立新規則並確認覆蓋
**Then** 舊規則設為 is_active=false，新規則設為 is_active=true

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| 同一 room_listing_id 同類型規則已存在 | 提示覆蓋確認 | 回傳 400 E-4001 |
| valid_from > valid_to | 驗證失敗 | E-4001 VALIDATION_ERROR |
| 倍率 ≤ 0 | 驗證失敗 | E-4001 VALIDATION_ERROR |

---

**🔴 人機協作確認點** (US-M12-001):
- [ ] 規則優先級正確
- [ ] 覆蓋邏輯合理
- [ ] 歷史記錄可查
- [ ] BA 已驗證需求正確性

---

#### US-M12-002: 手動覆蓋特定日期價格

**Story 描述**:
- **As a** Host
- **I want to** 針對特定日期（如跨年）手動設定固定價格
- **So that** 可以因應特殊活動或需求調整價格

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 3

---

#### Acceptance Criteria

**AC-M12-002-1**: 設定固定價格
**Given** Host 選擇特定日期並設定 MANUAL_OVERRIDE 規則
**When** 規則的 fixedPrice = 5000
**Then** 該日期的 room_calendar.price = 5000（無視其他規則）

**測試資料**:
```json
{
  "ruleType": "MANUAL_OVERRIDE",
  "ruleName": "跨年特價",
  "fixedPrice": 5000,
  "reason": "跨年活動",
  "roomListingId": "uuid",
  "calendarDates": ["2026-12-31"]
}
```

---

**🔴 人機協作確認點** (US-M12-002):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] MANUAL_OVERRIDE 優先級已確認
- [ ] BA 已驗證需求正確性

---

#### US-M12-003: 查詢定價規則列表

**Story 描述**:
- **As a** Host
- **I want to** 查詢我店鋪的所有定價規則
- **So that** 管理和審視目前的定價策略

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 2

---

#### Acceptance Criteria

**AC-M12-003-1**: 取得所有定價規則
**Given** Host 已登入
**When** 請求取得定價規則列表 (GET /api/v2/dashboard/pricing/rules)
**Then** 回傳該店鋪的所有定價規則

**測試資料**:
**預期輸出**:
```json
{
  "rules": [
    {
      "ruleId": "rule-001",
      "ruleType": "WEEKDAY_WEEKEND",
      "ruleName": "標準週末調價",
      "isActive": true,
      "priority": 10,
      "roomListingId": "listing-uuid-001",
      "roomListingTitle": "墾丁海景民宿 - 豪華雙人房",
      "validFrom": "2026-01-01",
      "validTo": "2026-12-31",
      "createdAt": "2026-04-01T00:00:00.000Z"
    }
  ]
}
```

---

**AC-M12-003-2**: 按房源篩選規則
**Given** Host 指定特定的 roomListingId
**When** 請求取得該房源的定價規則
**Then** 只回傳該房源適用的規則

**測試資料**:
- 輸入: `?listingId=listing-uuid-001`
- 預期輸出: 只包含該房源的規則

---

**🔴 人機協作確認點** (US-M12-003):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 篩選邏輯已確認
- [ ] BA 已驗證需求正確性

---

#### US-M12-004: 設定早鳥優惠規則

**Story 描述**:
- **As a** Host
- **I want to** 設定早鳥優惠
- **So that** 激勵客人提前預訂，增加營收預測準確性

**Story 狀態**: Draft
**優先級**: P1
**Story Points**: 5

---

#### Acceptance Criteria

**AC-M12-004-1**: 設定早鳥折扣
**Given** Host 建立 EARLY_BIRD 規則
**When** 規則設為提前 7 天預訂享 10% 折扣
**Then** 系統自動計算符合條件的預訂

**測試資料**:
```json
{
  "ruleType": "EARLY_BIRD",
  "ruleName": "早鳥優惠 7 天前",
  "daysInAdvance": 7,
  "discountPercent": 10,
  "validFrom": "2026-01-01",
  "validTo": "2026-12-31"
}
```

---

**AC-M12-004-2**: 早鳥折扣計算
**Given** 客人入住前 10 天預訂（符合 7 天條件）
**When** 系統計算最終價格
**Then** 套用 10% 折扣

**測試資料**:
- basePrice: 2500
- 入住天數: 2
- 提前天數: 10（符合條件）
- 折扣: 10%
- 預期 totalPrice: 4500（原價 5000 × 0.9）

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| discountPercent ≤ 0 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| discountPercent > 50 | 警告但允許 | 顯示警告提示 |
| daysInAdvance ≤ 0 | 驗證失敗 | E-4001 VALIDATION_ERROR |

---

**🔴 人機協作確認點** (US-M12-004):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 折扣計算邏輯已確認
- [ ] BA 已驗證需求正確性

---

#### US-M12-005: 設定長住折扣規則

**Story 描述**:
- **As a** Host
- **I want to** 設定長住折扣
- **So that** 激勵客人連續入住，提高住房率

**Story 狀態**: Draft
**優先級**: P1
**Story Points**: 5

---

#### Acceptance Criteria

**AC-M12-005-1**: 設定長住折扣
**Given** Host 建立 LONG_STAY 規則
**When** 規則設為連住 7 天享 10% 折扣
**Then** 系統自動計算符合條件的預訂

**測試資料**:
```json
{
  "ruleType": "LONG_STAY",
  "ruleName": "長住折扣 7 天",
  "minNights": 7,
  "discountPercent": 10,
  "validFrom": "2026-01-01",
  "validTo": "2026-12-31"
}
```

---

**AC-M12-005-2**: 長住折扣計算
**Given** 客人入住 10 晚（符合 7 天條件）
**When** 系統計算最終價格
**Then** 套用 10% 折扣

**測試資料**:
- basePrice: 2500
- 入住天數: 10
- 符合條件: 是（10 ≥ 7）
- 折扣: 10%
- 預期: 10晚 × 2500 × 0.9 = 22500

---

**AC-M12-005-3**: 階梯式長住折扣
**Given** Host 設定多條長住折扣規則
**When** 客人入住天數符合多個規則
**Then** 套用最高折扣的規則

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| minNights ≤ 0 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| discountPercent ≤ 0 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| 折扣同時觸發多規則 | 套用最高折扣 | 不可疊加 |

---

**🔴 人機協作確認點** (US-M12-005):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 階梯式折扣邏輯已確認
- [ ] BA 已驗證需求正確性

---

#### US-M12-006: 刪除定價規則

**Story 描述**:
- **As a** Host
- **I want to** 刪除不再適用的定價規則
- **So that** 保持定價策略的準確性

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 2

---

#### Acceptance Criteria

**AC-M12-006-1**: 刪除定價規則
**Given** Host 擁有某條 is_active=true 的規則
**When** 請求刪除該規則 (DELETE /api/v2/dashboard/pricing/rules/:id)
**Then** 將規則設為 is_active=false

---

**AC-M12-006-2**: 規則刪除後價格重算
**Given** 規則被刪除
**When** 系統處理刪除請求
**Then** 重新計算受影響日期的 room_calendar.price

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| 規則不存在 | 阻擋 | E-4041 RULE_NOT_FOUND |
| 規則不屬於該店鋪 | 阻擋 | E-4031 ACCESS_DENIED |
| 規則已刪除 | 阻擋 | E-4041 RULE_NOT_FOUND |

---

**🔴 人機協作確認點** (US-M12-006):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 軟刪除機制已確認
- [ ] BA 已驗證需求正確性

---

#### US-M12-007: 預覽定價日曆

**Story 描述**:
- **As a** Host
- **I want to** 預覽未來 90 天的定價日曆
- **So that** 了解定價策略的實際效果

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 3

---

#### Acceptance Criteria

**AC-M12-007-1**: 預覽未來 90 天定價
**Given** Host 在後台查看房源定價
**When** 請求預覽日曆 (GET /api/v2/dashboard/listings/:id/pricing-preview)
**Then** 回傳未來 90 天的日期、狀態和計算後價格

**測試資料**:
**預期輸出**:
```json
{
  "listingId": "listing-uuid-001",
  "listingTitle": "墾丁海景民宿 - 豪華雙人房",
  "basePrice": 2500,
  "calendar": [
    {
      "date": "2026-05-01",
      "dayOfWeek": "FRIDAY",
      "status": "AVAILABLE",
      "basePrice": 2500,
      "calculatedPrice": 3250,
      "appliedRules": [
        { "ruleId": "rule-weekend", "ruleName": "標準週末調價", "multiplier": 1.3 }
      ]
    },
    {
      "date": "2026-05-02",
      "dayOfWeek": "SATURDAY",
      "status": "AVAILABLE",
      "basePrice": 2500,
      "calculatedPrice": 3250,
      "appliedRules": [
        { "ruleId": "rule-weekend", "ruleName": "標準週末調價", "multiplier": 1.3 }
      ]
    }
  ]
}
```

---

**AC-M12-007-2**: 已預訂日期標記
**Given** 某日期已有預訂
**When** 預覽該日期
**Then** status 為 BOOKED，price 顯示為預訂時的價格

---

**🔴 人機協作確認點** (US-M12-007):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 預覽邏輯已確認
- [ ] BA 已驗證需求正確性

---

### 7.6 業務規則

#### BR-M12-001: pricing_rules 數量約束

| 項目 | 內容 |
|------|------|
| **Business Rule ID** | BR-M12-001 |
| **優先級** | P0 |
| **類型** | 約束 |

**規則描述**：
- 每間房上限：每個 room_listing_id 最多 **50 條** is_active = true 的定價規則
- 同類型唯一：每個 room_listing_id 每種 rule_type 最多 **1 條** is_active = true 的規則

---

#### BR-M12-002: 動態定價計算觸發機制

| 觸發時機 | 說明 |
|---------|------|
| **即時計算** | pricing_rules 新增/修改/刪除時，系統立即重新計算受影響日期範圍的 room_calendar.price |
| **每日凌晨重算** | 每日凌晨 03:00（UTC+8）系統執行一次全量 room_calendar.price 重新計算 |
| **已預訂日期格保護** | 已 BOOKED 狀態的 room_calendar 記錄不受 pricing_rules 變更影響 |

---

### 7.7 API 規格概要

| API Endpoint | 方法 | 用途 | 角色 |
|-------------|------|------|------|
| `/api/v2/dashboard/pricing-rules` | GET | 取得定價規則列表 | Host+ |
| `/api/v2/dashboard/pricing-rules` | POST | 建立定價規則 | Host+ |
| `/api/v2/dashboard/pricing-rules/:id` | PUT | 更新定價規則 | Host+ |
| `/api/v2/dashboard/pricing-rules/:id` | DELETE | 刪除定價規則 | Host+ |
| `/api/v2/dashboard/listings/:id/pricing-preview` | GET | 預覽未來 90 天定價日曆 | Host+ |

**詳細 API 規格**: 見 [docs/02_architecture/API_M12_Dynamic_Pricing.md](../02_architecture/API_M12_Dynamic_Pricing.md)

---

## 8. M17 租戶/店鋪管理 (Tenant Management)

### 8.1 模組概述

| 項目 | 內容 |
|------|------|
| **模組編號** | M17 |
| **模組名稱** | 租戶/店鋪管理 (Tenant Management) |
| **所屬子系統** | Platform Infrastructure |
| **核心能力** | 網友開店申請、店鋪審核、Feature Toggle |
| **依賴模組** | M03 (認證), M14 (平台管理) |
| **Phase 歸屬** | Phase 1 |

### 8.2 功能總覽

| 功能 | 說明 | 優先級 | Phase |
|------|------|--------|-------|
| 開店申請 | 網友提交店鋪名稱、描述、經營類型等資訊 | P0 | Phase 1 |
| 店鋪審核 | Admin 審核開店申請（通過/駁回） | P0 | Phase 1 |
| Feature Toggle | Admin 控制各租戶可用功能 | P0 | Phase 1 |
| 店鋪 Profile | 店鋪名稱、Logo、描述、聯絡方式 | P0 | Phase 1 |
| 抽成設定 | 按租戶設定平台抽成比例 | P1 | Phase 2 |
| 店鋪暫停/終止 | Admin 違規處置 | P1 | Phase 2 |

### 8.3 資料模型

```
tenants
├── id: UUID (PK)
├── tenant_code: VARCHAR(50) UNIQUE -- 如 STORE-001
├── name: VARCHAR(200)
├── slug: VARCHAR(100) UNIQUE -- URL-friendly
├── business_type: ENUM('RETAIL_ONLY', 'BOOKING_ONLY', 'HYBRID')
├── status: ENUM('PENDING_REVIEW', 'ACTIVE', 'REJECTED', 'SUSPENDED', 'TERMINATED')
├── commission_rate: DECIMAL(5,4) DEFAULT 0.05
├── logo_url: VARCHAR(500)
├── description: TEXT
├── contact_email: VARCHAR(255)
├── contact_phone: VARCHAR(20)
├── created_at: TIMESTAMP
├── updated_at: TIMESTAMP
└── INDEX (status)

tenant_feature_toggles
├── id: UUID (PK)
├── tenant_id: UUID (FK → tenants.id)
├── feature_key: VARCHAR(50)
├── is_enabled: BOOLEAN DEFAULT false
├── updated_at: TIMESTAMP
└── UNIQUE (tenant_id, feature_key)
```

---

### 8.4 User Stories

---

#### US-M17-001: 開店申請

**Story 描述**:
- **As a** Guest
- **I want to** 申請開店
- **So that** 可以成為平台的 Seller 或 Host

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 5

---

#### Acceptance Criteria

**AC-M17-001-1**: 提交開店申請
**Given** Guest 填寫開店申請表單
**When** 提交所有必填資訊
**Then** 建立 Tenant 記錄 (status = 'PENDING_REVIEW') 並通知 Admin

**測試資料**:
```json
{
  "storeName": "雅琪民宿",
  "storeDescription": "高雄苓雅區精美短租套房",
  "businessType": "BOOKING_ONLY",
  "contactEmail": "yachi@example.com",
  "contactPhone": "0912345678"
}
```

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| storeName 空白 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| businessType 無效 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| contactEmail 格式無效 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| 用戶已有進行中的申請 | 阻擋 | E-4002 PENDING_APPLICATION_EXISTS |

---

**🔴 人機協作確認點** (US-M17-001):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 申請流程已確認
- [ ] BA 已驗證需求正確性

---

#### US-M17-002: 店鋪詳情

**Story 描述**:
- **As a** Guest/Buyer
- **I want to** 查看店鋪的公開資訊
- **So that** 了解店鋪的基本資料和統計資訊

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 2

---

#### Acceptance Criteria

**AC-M17-002-1**: 查看店鋪公開資訊
**Given** 用戶請求取得店鋪資訊
**When** 店鋪狀態為 ACTIVE
**Then** 回傳店鋪公開資訊

**測試資料**:
**預期輸出**:
```json
{
  "tenantId": "tenant-uuid-001",
  "storeName": "我的數位商店",
  "storeDescription": "專營 3C 產品與周邊配件",
  "businessType": "RETAIL_ONLY",
  "status": "ACTIVE",
  "contactEmail": "contact@mystore.com",
  "logoUrl": "https://...",
  "coverImageUrl": "https://...",
  "member": {
    "displayName": "王小明",
    "avatarUrl": "https://..."
  },
  "stats": {
    "listingCount": 45,
    "totalSales": 1250000,
    "rating": 4.8
  }
}
```

---

**AC-M17-002-2**: 查看不存在的店鋪
**Given** 請求的店鋪 ID 不存在
**When** 用戶請求取得店鋪資訊
**Then** 回傳 404 錯誤

**測試資料**:
- 預期錯誤: `E-4041 TENANT_NOT_FOUND`

---

**🔴 人機協作確認點** (US-M17-002):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 隱私資訊保護已確認
- [ ] BA 已驗證需求正確性

---

#### US-M17-003: 更新店鋪資訊

**Story 描述**:
- **As a** StoreOwner
- **I want to** 更新我店鋪的資訊
- **So that** 可以修改店鋪名稱、描述或聯絡方式

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 3

---

#### Acceptance Criteria

**AC-M17-003-1**: 更新店鋪基本資訊
**Given** StoreOwner 擁有該店鋪
**When** 提交更新請求
**Then** 更新店鋪資訊並回傳更新後的資料

**測試資料**:
```json
{
  "storeName": "我的數位商店（更新版）",
  "storeDescription": "專營最新 3C 產品",
  "contactEmail": "new-contact@mystore.com",
  "contactPhone": "+886-988-888-888",
  "logoUrl": "https://...",
  "coverImageUrl": "https://..."
}
```

---

**AC-M17-003-2**: 無權更新他人店鋪
**Given** 用戶嘗試更新不屬於自己的店鋪
**When** 提交更新請求
**Then** 回傳 403 錯誤

**測試資料**:
- 預期錯誤: `E-4031 ACCESS_DENIED`

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| storeName 空白 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| storeName 已被使用 | 驗證失敗 | E-4091 STORE_NAME_EXISTS |
| contactEmail 格式無效 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| 店鋪狀態非 ACTIVE | 允許更新 | 正常更新 |

---

**🔴 人機協作確認點** (US-M17-003):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 驗證規則已確認
- [ ] BA 已驗證需求正確性

---

#### US-M17-004: 取得我的店鋪列表

**Story 描述**:
- **As a** StoreOwner
- **I want to** 取得我所屬的店鋪列表
- **So that** 管理多個店鋪或切換租戶上下文

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 2

---

#### Acceptance Criteria

**AC-M17-004-1**: 取得用戶所屬店鋪列表
**Given** StoreOwner 擁有多个店鋪
**When** 請求取得店鋪列表 (GET /api/v2/tenants)
**Then** 回傳該用戶所屬的所有店鋪

**測試資料**:
**預期輸出**:
```json
{
  "tenants": [
    {
      "tenantId": "tenant-uuid-001",
      "storeName": "我的數位商店",
      "businessType": "RETAIL_ONLY",
      "status": "ACTIVE",
      "role": "OWNER",
      "memberCount": 3,
      "features": {
        "RETAIL_ENABLED": true,
        "BOOKING_ENABLED": false
      },
      "createdAt": "2026-04-01T10:00:00.000Z"
    },
    {
      "tenantId": "tenant-uuid-002",
      "storeName": "墾丁海景民宿",
      "businessType": "BOOKING_ONLY",
      "status": "ACTIVE",
      "role": "OWNER",
      "memberCount": 2,
      "features": {
        "RETAIL_ENABLED": false,
        "BOOKING_ENABLED": true
      },
      "createdAt": "2026-04-05T10:00:00.000Z"
    }
  ]
}
```

---

**AC-M17-004-2**: 用戶無店鋪
**Given** 用戶尚未申請開店
**When** 請求取得店鋪列表
**Then** 回傳空陣列

**測試資料**:
- 預期輸出: `{ "tenants": [] }`

---

**🔴 人機協作確認點** (US-M17-004):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 多租戶場景已確認
- [ ] BA 已驗證需求正確性

---

#### US-M17-005: 功能開關查詢

**Story 描述**:
- **As a** StoreOwner
- **I want to** 查詢我店鋪的功能開關狀態
- **So that** 了解目前已啟用的功能

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 2

---

#### Acceptance Criteria

**AC-M17-005-1**: 查詢功能開關狀態
**Given** StoreOwner 已登入
**When** 請求取得功能開關列表 (GET /api/v2/dashboard/tenants/features)
**Then** 回傳該店鋪的所有功能開關狀態

**測試資料**:
**預期輸出**:
```json
{
  "tenantId": "tenant-uuid-001",
  "features": [
    {
      "featureKey": "RETAIL_ENABLED",
      "featureName": "零售功能",
      "description": "可上架實體商品",
      "isEnabled": true,
      "enabledAt": "2026-04-01T10:00:00.000Z",
      "requestedAt": null
    },
    {
      "featureKey": "BOOKING_ENABLED",
      "featureName": "民宿預訂功能",
      "description": "可上架民宿房間",
      "isEnabled": false,
      "enabledAt": null,
      "requestedAt": "2026-04-01T10:00:00.000Z"
    }
  ]
}
```

---

**🔴 人機協作確認點** (US-M17-005):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 功能說明已確認
- [ ] BA 已驗證需求正確性

---

#### US-M17-006: 申請功能開關

**Story 描述**:
- **As a** StoreOwner
- **I want to** 申請啟用特定功能（如民宿預訂）
- **So that** 擴展店鋪業務範圍

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 3

---

#### Acceptance Criteria

**AC-M17-006-1**: 申請啟用功能
**Given** StoreOwner 擁有店鋪
**When** 提交功能開關申請 (PUT /api/v2/dashboard/tenants/features/:feature)
**Then** 系統記錄申請，等待 Admin 審核

**測試資料**:
```json
{
  "enabled": true
}
```
**預期輸出**:
```json
{
  "featureKey": "BOOKING_ENABLED",
  "previousState": false,
  "newState": false,
  "status": "PENDING_APPROVAL"
}
```

---

**AC-M17-006-2**: 不需審核的功能
**Given** 功能預設值為 true
**When** StoreOwner 申請啟用
**Then** 功能直接啟用

---

**AC-M17-006-3**: 已啟用的功能再次申請
**Given** 功能已啟用
**When** StoreOwner 再次申請啟用
**Then** 回傳成功但狀態不變

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| featureKey 無效 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| 功能已是啟用狀態 | 正常處理 | 回傳成功 |
| 店鋪狀態非 ACTIVE | 阻擋 | E-4001 STORE_NOT_ACTIVE |

---

#### 依賴與假設

**依賴項**:
- BR-M17-002 Feature Toggle 初始化

---

**🔴 人機協作確認點** (US-M17-006):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 審核流程已確認
- [ ] BA 已驗證需求正確性

---

#### US-M17-007: Admin 審核通過店鋪

**Story 描述**:
- **As an** Admin
- **I want to** 審核通過新開店申請
- **So that** 店鋪可以正式上線運營

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 3

---

#### Acceptance Criteria

**AC-M17-007-1**: 審核通過開店申請
**Given** 店鋪申請狀態為 PENDING_REVIEW
**When** Admin 提交審核通過 (POST /api/v2/admin/tenants/:id/approve)
**Then** 將店鋪狀態改為 ACTIVE，並初始化 Feature Toggle

**測試資料**:
```json
{
  "approvedFeatures": ["BOOKING_ENABLED"],
  "notes": "審核通過，預設開啟基礎功能"
}
```

---

**AC-M17-007-2**: 初始化 Feature Toggle
**Given** 店鋪審核通過
**When** 系統處理審核通過
**Then** 根據 BR-M17-002 初始化所有 Feature Toggle

---

**AC-M17-007-3**: 多次審核
**Given** 店鋪狀態已為 ACTIVE
**When** Admin 再次審核
**Then** 回傳錯誤，狀態已是 ACTIVE

**測試資料**:
- 預期錯誤: `E-4001 STORE_ALREADY_APPROVED`

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| 店鋪不存在 | 阻擋 | E-4041 TENANT_NOT_FOUND |
| 店鋪狀態非 PENDING_REVIEW | 阻擋 | E-4001 INVALID_STORE_STATUS |
| 核准無效的功能 | 警告但允許 | 顯示警告 |

---

**🔴 人機協作確認點** (US-M17-007):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] Feature Toggle 初始化已確認
- [ ] BA 已驗證需求正確性

---

#### US-M17-008: Admin 駁回店鋪申請

**Story 描述**:
- **As an** Admin
- **I want to** 駁回不符合規範的開店申請
- **So that** 確保平台店鋪品質

**Story 狀態**: Draft
**優先級**: P0
**Story Points**: 2

---

#### Acceptance Criteria

**AC-M17-008-1**: 駁回開店申請
**Given** 店鋪申請狀態為 PENDING_REVIEW
**When** Admin 提交駁回 (POST /api/v2/admin/tenants/:id/reject)
**Then** 將店鋪狀態改為 REJECTED，並記錄原因

**測試資料**:
```json
{
  "reason": "營業執照已過期，請重新上傳有效證件"
}
```

---

**AC-M17-008-2**: 駁回後重新申請
**Given** 店鋪已被駁回
**When** 用戶嘗試重新申請
**Then** 允許重新建立申請（新建記錄）

---

**AC-M17-008-3**: 多次駁回
**Given** 店鋪狀態已為 REJECTED
**When** Admin 再次駁回
**Then** 回傳錯誤，狀態已是 REJECTED

**測試資料**:
- 預期錯誤: `E-4001 STORE_ALREADY_REJECTED`

---

#### 邊界條件與異常處理

| 條件 | 處理方式 | 預期行為 |
|------|---------|---------|
| reason 空白 | 驗證失敗 | E-4001 VALIDATION_ERROR |
| 店鋪狀態非 PENDING_REVIEW | 阻擋 | E-4001 INVALID_STORE_STATUS |

---

**🔴 人機協作確認點** (US-M17-008):
- [ ] Story 描述清晰完整
- [ ] Acceptance Criteria 可測試
- [ ] 駁回原因記錄已確認
- [ ] BA 已驗證需求正確性

---

### 8.5 業務規則

#### BR-M17-001: 租戶生命週期

| 項目 | 內容 |
|------|------|
| **Business Rule ID** | BR-M17-001 |
| **優先級** | P0 |
| **類型** | 狀態轉換 |

**狀態流轉**：
```
[申請開店] → PENDING_REVIEW → [Admin 審核] → ACTIVE → [運營中]
                                    │                      │
                                    ▼                      ▼
                               REJECTED              SUSPENDED (違規)
                                                          │
                                                          ▼
                                                     TERMINATED
```

---

#### BR-M17-002: Feature Toggle 初始化

| 項目 | 內容 |
|------|------|
| **Business Rule ID** | BR-M17-002 |
| **優先級** | P0 |
| **類型** | 初始化 |

**規則描述**：
當 `tenants.status` 從 `PENDING_REVIEW` → `ACTIVE` 時，系統自動以預設值初始化該 Tenant 的所有 Feature Toggle 紀錄。

**預設值**：

| Feature Key | 預設值 |
|-------------|--------|
| RETAIL_ENABLED | true |
| BOOKING_ENABLED | false |
| CMS_ENABLED | true |
| ERP_ENABLED | true |
| DYNAMIC_PRICING_ENABLED | false |
| PROMO_ENABLED | false |

---

### 8.6 API 規格概要

| API Endpoint | 方法 | 用途 | 角色 |
|-------------|------|------|------|
| `/api/v2/tenants/apply` | POST | 提交開店申請 | Guest |
| `/api/v2/tenants/:id` | GET | 取得店鋪資訊 | Guest+ |
| `/api/v2/dashboard/tenant/profile` | GET/PUT | 取得/更新店鋪 Profile | StoreOwner |
| `/api/v2/admin/tenants` | GET | Admin: 店鋪列表 | Admin |
| `/api/v2/admin/tenants/:id/approve` | POST | Admin: 核准開店 | Admin |
| `/api/v2/admin/tenants/:id/reject` | POST | Admin: 駁回開店 | Admin |
| `/api/v2/admin/tenants/:id/toggles` | PUT | Admin: 更新 Feature Toggle | Admin |

**詳細 API 規格**: 見 [docs/02_architecture/API_M17_Tenant_Management.md](../02_architecture/API_M17_Tenant_Management.md)

---

## 9. 非功能性需求 (Non-Functional Requirements)

### 9.1 效能需求

| NFR ID | 需求描述 | 目標值 | 優先級 |
|--------|---------|--------|--------|
| NFR-PERF-001 | API 回應時間 (p95) | < 200ms | P0 |
| NFR-PERF-002 | 搜尋查詢回應時間 (p95) | < 500ms | P0 |
| NFR-PERF-003 | 系統吞吐量 | > 1000 req/s | P0 |
| NFR-PERF-004 | 併發使用者 | > 500 concurrent users | P0 |

---

### 9.2 安全需求

| NFR ID | 需求描述 | 目標值 | 優先級 |
|--------|---------|--------|--------|
| NFR-SEC-001 | JWT 加密 | RS256 或 HS256 | P0 |
| NFR-SEC-002 | 密碼加密 | bcrypt (salt round ≥ 12) | P0 |
| NFR-SEC-003 | HTTPS 強制 | 所有 HTTP → HTTPS | P0 |
| NFR-SEC-004 | Rate Limiting | 登入: 5 次/分鐘; 一般 API: 10 次/秒 | P0 |

---

### 9.3 可用性需求

| NFR ID | 需求描述 | 目標值 | 優先級 |
|--------|---------|--------|--------|
| NFR-AVAIL-001 | 系統 SLA | 99.9% | P0 |
| NFR-AVAIL-002 | 錯誤率 | < 0.1% (正常負載) | P0 |

---

## 10. 測試需求 (Testing Requirements)

### 10.1 測試範圍

**單元測試**:
- 覆蓋率目標: > 80%
- 重點測試: 業務邏輯、狀態機、庫存扣減

**整合測試**:
- API 端點測試
- 資料庫互動測試
- Redis 緩存測試

**E2E 測試**:
- 關鍵用戶流程：註冊→登入→搜尋→加入購物車→下單→支付

---

## 11. 附錄 (Appendix)

### 11.1 詞彙表

| 術語 | 定義 |
|------|------|
| Listing | 統一商品/房源抽象模型 |
| Tenant | 租戶（店鋪） |
| SKU | 庫存單位 (Stock Keeping Unit) |
| Booking Slot | 房源日期格（已被 room_calendar 取代） |
| Settlement | 結算 |

### 11.2 參考資料

- [E-Commerce_PRD_v1.0_Final.md](./E-Commerce_PRD_v1.0_Final.md)
- [M18_Knowledge_Management_SPEC.md](./M18_Knowledge_Management_SPEC.md)

### 11.3 變更記錄

| 日期 | 版本 | 變更內容 | 作者 |
|------|------|---------|--------|
| 2026-04-09 | v1.0 | 初始 FRD | Amanda (SA) + Beatrice (BA) |

---

## ✅ 文檔完成檢查清單

### FRD 品質檢查
- [ ] 所有必填欄位已完成
- [ ] 每個 User Story 都有完整的 AC
- [ ] 資料模型與業務規則明確
- [ ] 流程圖清晰易懂
- [ ] 所有 🔴 確認點已標注
- [ ] 測試需求完整

### AISDLC 流程檢查
- [ ] 已由 SA Agent (Amanda) 主導撰寫
- [ ] 已由 BA Agent (Beatrice) 驗證
- [ ] 與 PRD 追蹤鏈完整
- [ ] SRD/API 規格已規劃
- [ ] AT 測試已對應

---

**文檔所有者**: Amanda (SA-Analyst)
**最後審查日期**: 2026-04-09
**下一次審查日期**: 待定

---

**文件結束**
