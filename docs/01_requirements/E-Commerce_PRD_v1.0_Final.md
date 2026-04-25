# E-Commerce System — 規格文件 v1.0

> **狀態**: PRD 改善版（基於 v0.9_R02）
> **版本**: v1.0
> **建立日期**: 2026-03-23
> **升版日期**: 2026-04-09
> **負責人**: 衍墨 (SDD-Director) + Victoria (PM-PO) + Amanda (SA-Analyst)
> **版本變更**:
> - v0.9 → v0.9_R02：Loop 1 迭代修正（基於 PM QA Iteration 1）
> - R02_Loop_01 → Loop_02：Victoria Loop 2 修改（16 項 QA 回應全部採納、12 項 Test Cases 新增）
> - R02_Loop_02 → Final：Victoria Loop 3 修改（QA Loop 3 最終覆核通過，零 blocker，Sprint Planning 可正式入場）
> - **v0.9_R02 → v1.0：用戶需求確認（2026-04-09）：M12 改為 Phase 1 Must Have、新增 M18 知識管理模組**
> - **v0.9 → v1.0：九項改善已套用（見 §21 勘誤記錄）**

---

## 目錄

- [1. 系統概述](#1-系統概述)
- [2. 專案目錄結構](#2-專案目錄結構)
- [3. 架構設計 (Architecture Design)](#3-架構設計-architecture-design)
- [4. 多租戶架構 (Multi-Tenancy)](#4-多租戶架構-multi-tenancy)
- [5. 統一商品/服務模型 (Unified Listing Model)](#5-統一商品服務模型-unified-listing-model)
- [6. 核心功能模組 (Core Functional Modules)](#6-核心功能模組-core-functional-modules)
- [7. 使用者角色、人物誌與 RBAC 權限矩陣](#7-使用者角色人物誌與-rbac-權限矩陣)
- [8. 資料模型 (Data Model / ERD)](#8-資料模型-data-model--erd)
- [9. API 設計](#9-api-設計)
- [10. 前端路由](#10-前端路由)
- [11. 元件設計 (Component Design)](#11-元件設計-component-design)
- [12. 使用者流程 (User Flows)](#12-使用者流程-user-flows)
- [13. Phase 1 範圍（v0.6 — 規格驅動起步）](#13-phase-1-範圍v06--規格驅動起步)
- [14. Phase 2+ 規劃與範圍](#14-phase-2-規劃與範圍)
- [15. M05 訂單模組獨立 SDD 規格](#15-m05-訂單模組獨立-sdd-規格)
- [16. OpenAPI 錯誤回應與 API Schema](#16-openapi-錯誤回應與-api-schema)
- [17. 勘誤與補正記錄（Errata）](#17-勘誤與補正記錄errata)
- [18. 需求追蹤矩陣（RTM）](#18-需求追蹤矩陣rtm)
- [19. Sprint Planning 入場門票與凍結簽核](#19-sprint-planning-入場門票與凍結簽核)
- [20. 自我糾錯檢核 (Self-Correction Checklist)](#20-自我糾錯檢核-self-correction-checklist)
- [21. 規格版本控制](#21-規格版本控制)

---

## 1. 系統概述

### 1.1 專案定位（v0.9 更新）

**B2B2C 多租戶電子商務平台**，同時支援：

| 能力 | 說明 |
|------|------|
| **平台自營** | 平台擁有者自己上架實體商品與民宿房間 |
| **網友開店** | 網友可申請開設店鋪，使用平台全部功能（受後台 Feature Toggle 控制） |
| **實體商品銷售** | 3C、生活用品等，SKU 庫存扣減 — 對照 PChome 24h / 蝦皮 |
| **民宿房間預訂** | 短租住宿，日曆動態定價 — 對照 Airbnb 類似的短租模式 |
| **內容驅動銷售** | CMS 貼文可嵌入商品/房型購買卡片 |
| **一體化進銷存** | B 端入庫作業直接連動 C 端可售量 |

### 1.2 Phase 1 技術架構（當前建置）

| 層級 | 技術 | 備註 |
|------|------|------|
| 前端 | Next.js 15 (App Router) + TypeScript + Tailwind CSS | SSR + SSG |
| 後端 | Spring Boot 3.2 (Java 21) + Clean Architecture + DDD | 多租戶感知 |
| 資料庫 | PostgreSQL 18 | Shared Schema + Tenant ID 隔離 |
| 快取 | Redis 7 | 購物車 / 分佈式鎖 / 限流 / 定價快取 |
| DB 遷移 | Flyway | 所有 Schema 變更走 Migration |
| E2E 測試 | Playwright | 端到端自動化測試 |
| CI/CD | GitHub Actions | 自動化構建與測試 |

### 1.3 Phase 2+ 未來基礎設施（預留）

> 以下為 Phase 2+ 規劃採用的技術，目前 **不在 Phase 1 範圍內**，預先告知避免規格與實作脫鉤。

| 層級 | 技術 | 對應功能 |
|------|------|---------|
| 地理空間搜尋 | PostGIS（PostgreSQL 擴展） | M02 LBS 地圖模式搜尋 |
| 全文搜尋引擎 | ElasticSearch | M01 商品/房源全文檢索 |
| 訊息隊列 | RabbitMQ / Redis Stream | M07 Saga Pattern、M09 非同步通知、M10 WebSocket 事件 |
| 物件儲存 | S3 / MinIO（CDN） | M08 圖文評論多媒體、M01 商品圖片、CMS 媒體庫（按 Tenant 分桶） |

### 1.4 五大子系統（v1.0 更新）

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

**子系統互動邊界：**

| 呼叫方 | 被呼叫方 | 互動描述 |
|--------|---------|---------|
| E-Commerce Core | ERP | 訂單建立時呼叫 ERP 扣減可售量；取消時回滾 |
| B&B Engine | ERP | 預訂建立時鎖定日期格；取消時釋放 |
| CMS | E-Commerce Core | 嵌入商品卡片時查詢商品即時價格與庫存 |
| CMS | B&B Engine | 嵌入房型卡片時查詢房間即時可用性與動態價格 |
| ERP | E-Commerce Core | 入庫完成後自動更新 C 端可售量 |
| Knowledge Management | Platform Infra | 提供知識庫、FAQ、客服系統 |
| Platform Infra | 全部 | 提供 Tenant 上下文、JWT 認證、RBAC 鑑權 |

---

## 1.5 資料隱私與合規（v0.9 新增）

> **說明**：本節定義 NextKeyAI E-Commerce System 的會員資料隱私保護原則與合規框架。目標遵循台灣個資法 + PCI-DSS（支付資料）。

### 1.5.1 個資保護原則

| 原則 | 說明 |
|------|------|
| **最小收集** | 僅收集業務必要之個人資料，不可收集與服務無關的資訊 |
| **加密儲存** | KYC 敏感欄位（`id_number`、KYC 證件圖片 URL）使用 AES-256 加密，密鑰由專屬 KMS 管理 |
| **存取控制** | KYC 資料僅限 SuperAdmin + KYC 審核員讀取，一般 API 無法取得 |
| **保留期限** | KYC 未通過者，資料保留 30 日後自動清除；通過者保留至帳戶終止後 5 年 |
| **會員權利** | 會員可申請資料 Export（Phase 2）、帳戶刪除（Right to be Forgotten，Phase 2） |

### 1.5.2 敏感資料存取控制

| 資料類型 | 加密強度 | 可存取角色 | 備註 |
|---------|---------|-----------|------|
| `user_profiles.id_number` | AES-256 | SuperAdmin, KYC Reviewer | 加密儲存，解密需 KMS |
| `user_profiles.id_card_front_url` | AES-256 | SuperAdmin, KYC Reviewer | S3 路徑加密 |
| `user_profiles.id_card_back_url` | AES-256 | SuperAdmin, KYC Reviewer | S3 路徑加密 |
| 一般會員資料 | — | 本人, Admin | email, phone 等 |

---

## 2. 專案目錄結構

```
E-Commerce/
├── .github/               # CI/CD (GitHub Actions)
├── docs/                  # 專案文件、規格、API 文件
├── scripts/               # 跨專案實用腳本
├── tests/                 # E2E 端到端測試 (Playwright)
├── config/                # Nginx / Docker 等設定
├── backend/               # Spring Boot (Clean Architecture + DDD)
│   └── src/main/java/com/nextkey/ecommerce/
│       ├── api/           # Controllers, Filters, Routes
│       ├── core/          # Services, Use Cases (retail/booking/order)
│       ├── domain/        # Entities, DTOs, Repository interfaces
│       ├── infrastructure/# JPA, Redis, Third-party APIs
│       └── shared/        # Utils, Constants, GlobalExceptionHandler
└── frontend/             # Next.js (App Router)
    └── src/
        ├── app/           # Pages (shop/booking/admin)
        ├── components/    # Pure UI (按鈕/輸入框/Modal)
        ├── features/      # Business components (日曆/購物車)
        ├── lib/           # Axios setup, utilities
        ├── services/      # API calls to backend
        ├── store/         # Zustand/Redux state
        └── types/         # TypeScript interfaces
```

---

## 3. 架構設計 (Architecture Design)

### 3.1 後端分層架構（Clean Architecture + DDD + Multi-Tenancy）

```
com.nextkey.ecommerce/
├── api/                          # Presentation Layer
│   ├── controller/
│   │   ├── admin/                # 平台管理 API
│   │   ├── storefront/           # C 端前台 API
│   │   └── dashboard/            # B 端店鋪後台 API
│   ├── filter/
│   │   ├── JwtAuthFilter.java
│   │   └── TenantContextFilter.java   # ★ 租戶上下文注入
│   └── dto/
├── core/                         # Application Layer (Use Cases)
│   ├── auth/                     # M03 認證服務
│   ├── listing/                  # ★ 統一商品/服務 Use Cases
│   ├── retail/                   # M01 零售特化邏輯
│   ├── booking/                  # M02/M06 預訂特化邏輯
│   ├── pricing/                  # ★ M12 動態定價引擎
│   ├── order/                    # M05 訂單履約
│   ├── cms/                      # ★ M15 內容管理
│   ├── erp/                      # ★ M16 進銷存
│   ├── tenant/                   # ★ M17 租戶/店鋪管理
│   └── cart/                     # M04 購物車
├── domain/                       # Domain Layer (Entities + Repository Interfaces)
│   ├── model/
│   │   ├── tenant/               # Tenant, TenantFeatureToggle
│   │   ├── listing/              # Listing (統一抽象)
│   │   ├── product/              # Product, ProductSku, ProductInventory
│   │   ├── room/                 # Room, RoomCalendar, PricingRule
│   │   ├── order/                # Order, OrderItem, OrderStateLog
│   │   ├── cms/                  # Post, PostEmbed, MediaAsset
│   │   ├── erp/                  # PurchaseOrder, StockLedger, StockMovement
│   │   └── user/                 # User, UserProfile, UserRole
│   └── repository/               # Repository Interfaces
├── infrastructure/               # Infrastructure Layer
│   ├── persistence/              # JPA Repositories
│   ├── cache/                    # Redis
│   ├── storage/                  # S3 / MinIO (圖片/媒體)
│   └── external/                 # Third-party API (金流/物流)
└── shared/                       # Cross-cutting Concerns
    ├── tenant/                   # TenantContext (ThreadLocal)
    ├── exception/
    └── util/
```

### 3.2 多租戶資料流

```
HTTP Request
    │
    ▼
[JwtAuthFilter] → 驗證 JWT → 提取 userId + roles
    │
    ▼
[TenantContextFilter] → 由 userId 查詢所屬 tenantId → 寫入 TenantContext (ThreadLocal)
    │
    ▼
[Controller] → 業務邏輯
    │
    ▼
[Repository] → 所有查詢自動附加 WHERE tenant_id = :currentTenantId
    │
    ▼
[Hibernate Filter / @TenantScope] → 確保資料隔離
```

### 3.3 核心設計原則（整合 v0.8 §4.5 CTO 級別設計原則 + v0.9 新增）

> **1. 雙軌庫存機制 (Dual-Track Inventory) 與高併發防護**
> * **零售 (數量維度)**：基於 `SKU_ID` 的數量扣減。日常併發使用資料庫樂觀鎖 (Optimistic Lock) `UPDATE ... WHERE version = X`；秒殺情境強制將庫存同步至 Redis，透過 Lua Script 實現原子性預扣 (Check-and-Set)，後步非同步寫入資料庫。
> * **預訂 (時間維度)**：基於 `Room_ID + Date` 的唯一性鎖定。資料庫層必須將「每一天」視為一筆獨立紀錄（v0.8 Table: `booking_slots` → **v0.9 已重命名為 `room_calendar`，新增 `price` 欄位支援動態定價**），利用 Database Unique Key 確保底層絕對互斥，拒絕使用 `start_date` 與 `end_date` 的字串範圍交集查詢。

> **2. 混合訂單與 Saga 分散式交易 (Distributed Transactions)**
> * 當訂單包含「實體商品」+「民宿預訂」時，需採用 Saga Pattern 處理。
> * `saga_events` 表記錄每個步驟的執行狀態；`compensation_log` 表記錄補償行動（庫存回滾、日期格釋放）。
> * 若「庫存扣減」成功但「金流扣款」失敗，狀態機自動觸發補償機制 (Compensation Action)，確保最終一致性 (Eventual Consistency)。

> **3. 防彈狀態機與 API 冪等性 (Bulletproof State Machine & Idempotency)**
>
> **Phase 1 狀態機（Payment Mock，等效宣告）**：
> - **零售**：`CREATED(=PAID)` -> `SHIPPING` -> `DELIVERED` -> `COMPLETED`。Payment Mock 在 `POST /api/orders` 時直接達到 `CREATED(=PAID)` 等效狀態，`payment.received` 事件不存在，`CONFIRMED` 狀態不在 Phase 1 零售路徑中。
> - **預訂**：`CREATED(=PAID)` -> `CHECKED_IN` -> `CHECKED_OUT` -> `COMPLETED`。Phase 1 `CONFIRMED` 等效於 `CREATED(=PAID)`，`CHECKED_IN` 前置抵達後自動確認，無獨立 CONFIRMED 步驟。
> - **`payment.received` 事件、`PAID` 狀態、`CONFIRMED` 狀態在 Phase 1 不存在，嚴禁實作。**
> - **`order_state_log` 初始記錄**：`order_state_log.sequence = 1, toStatus = "CREATED"`（而非 `CONFIRMED`）。
> 所有狀態變更禁止硬編碼，需透過狀態引擎嚴格流轉：
> * **零售路徑（Phase 2+）**：`CREATED` -> `PAID` -> `SHIPPING` -> `DELIVERED` -> `COMPLETED`。逆向加入 `REFUNDING` 與 `REFUNDED`。
> * **預訂路徑（Phase 2+）**：`CREATED` -> `PAID` -> `CONFIRMED` -> `CHECKED_IN` -> `CHECKED_OUT` -> `COMPLETED`。
> * **Phase 1 零售路徑（Payment Mock 等效）**：`CREATED(=PAID)` -> `SHIPPING` -> `DELIVERED` -> `COMPLETED`。Phase 1 支付環節為模擬流程，CREATED 狀態建立時即等同於已支付（Payment Mock），無需等待真實金流回調。**`payment.received` 事件在 Phase 1 不存在，`CONFIRMED` 狀態不在 Phase 1 零售路徑中。**
> * **Phase 1 預訂路徑（Payment Mock 等效）**：`CREATED(=PAID)` -> `CHECKED_IN` -> `CHECKED_OUT` -> `COMPLETED`。Phase 1 `CONFIRMED` 狀態等效於 `CREATED(=PAID)`（Payment Mock 自動完成狀態推進，無獨立 CONFIRMED 步驟），`CHECKED_IN` 前置抵達後自動確認。
> * **冪等性設計**：所有涉及資金或狀態變更的 API (如 Pay, Cancel)，Header 必須夾帶客戶端生成的 `Idempotency-Key`（**格式約束：UUID v4，36 字元長度**）。Gateway 層透過 Redis 快取攔截 24 小時內的重複請求，徹底防止因網路 Timeout 導致的「重複扣款」或「重複退房」。

> **4. 服務降級與限流策略 (Rate Limiting & Downgrade)**
> * 面對跨年搶房或雙 11 大促，API Gateway 需啟動令牌桶 (Token Bucket) 限流。
> * 觸發系統瓶頸時，自動降級非核心服務 (如：暫停發送行銷簡訊、延遲評價更新、關閉複雜推薦算法)，全力保障「下單」與「支付」主鏈路的存活。

> **5. Tenant 感知（v0.9 新增）**
> * 所有業務 Entity 攜帶 `tenant_id`；Repository 層透過 Hibernate Filter 自動隔離。

> **6. 統一 Listing 抽象（v0.9 新增）**
> * Product 與 Room 均繼承 `Listing` 基礎模型，CMS/搜尋/前台可統一處理。

> **7. ERP → C 端連動（v0.9 新增）**
> * B 端入庫（StockMovement INBOUND）→ 自動更新 ProductInventory.availableQty → C 端即時反映。

> **8. 多租戶 TenantContext 選取機制（v0.9_R02_Loop_01 新增）**
> * 當用戶隸屬於多個 Tenant 時（如 David Lin 同時經營民宿與 3C 代購），**必須**透過 `X-Tenant-ID` Header 明確指定操作目標租戶。
> * `TenantContextFilter` 驗證該 Header 的 `tenant_id` 是否在用戶的 `tenant_members` 清單內；不在清單內則回傳 `E-2003 TENANT_CONTEXT_AMBIGUOUS`（403 Forbidden）。
> * 若 Header 缺失且用戶有多個 Tenant 成員紀錄，系統回傳 `E-2003 TENANT_CONTEXT_AMBIGUOUS`，要求客戶端明確指定。
> * 具體行為定義見 `§7.5` Feature Toggle × RBAC 整合規範。

---

## 4. 多租戶架構 (Multi-Tenancy)

### 4.1 租戶模型

| 概念 | 說明 |
|------|------|
| **System Tenant** | 平台自身，擁有最高權限。**[Constraint]** `tenant_id` 必須為固定 UUID 常數：`00000000-0000-0000-0000-000000000001` |
| **User Tenant (店鋪)** | 網友申請開設的店鋪，每個店鋪分配獨立 `tenant_id` (UUID v4) |
| **Feature Toggle** | 後台管理員可控制每個 Tenant 可使用的功能模組 |

### 4.2 資料隔離策略

**[Specification] Shared Schema + Tenant ID Column**

```
策略: 所有業務資料表共享同一 PostgreSQL Schema
隔離: 每張業務表包含 tenant_id 欄位 (NOT NULL)
過濾: Hibernate @Filter 或 Spring Data JPA @Query 自動注入 tenant_id 條件
索引: 所有業務表必須建立 (tenant_id, ...) 複合索引
```

**隔離範圍：**

| 資源 | 隔離方式 |
|------|---------|
| 商品資料 | `products.tenant_id`（透過 `listings.tenant_id`） |
| 房間資料 | `rooms.tenant_id`（透過 `listings.tenant_id`） |
| 訂單資料 | `orders.tenant_id` |
| CMS 貼文 | `posts.tenant_id` |
| 媒體庫/圖片 | S3 路徑: `/{tenant_id}/media/*` |
| 進銷存台帳 | `stock_movements.tenant_id` |
| 購物車 | Redis Key: `cart:{tenant_id}:{user_id}` |

**跨租戶查詢（僅限平台 Admin）：**

| 場景 | 處理方式 |
|------|---------|
| 前台商品列表 (C 端瀏覽) | 查詢所有 `tenant.status = ACTIVE` 的商品，不限單一 tenant |
| 前台房源列表 (C 端瀏覽) | 同上，查詢所有啟用店鋪的房源 |
| 平台管理台 (Admin) | Admin 可跨租戶查詢，需特殊權限 `CROSS_TENANT_READ` |
| 店鋪後台 (Seller/Host) | 僅限查詢自身 tenant 資料 |

### 4.3 租戶生命週期

```
[申請開店] → PENDING_REVIEW → [Admin 審核] → ACTIVE → [運營中]
                                    │                      │
                                    ▼                      ▼
                               REJECTED              SUSPENDED (違規)
                                                          │
                                                          ▼
                                                     TERMINATED
```

| 狀態 | 說明 |
|------|------|
| PENDING_REVIEW | 網友提交開店申請，等待 Admin 審核 |
| ACTIVE | 審核通過，店鋪正常運營 |
| REJECTED | 審核未通過 |
| SUSPENDED | 因違規被暫停，商品下架、訂單暫停受理 |
| TERMINATED | 永久關閉 |

### 4.4 Feature Toggle 機制

**[Specification] 租戶功能控制矩陣**

平台管理員可透過 `tenant_feature_toggles` 表控制每個租戶可使用的功能：

| Feature Key | 說明 | 預設值 (新店鋪) |
|-------------|------|----------------|
| `RETAIL_ENABLED` | 可上架實體商品 | true |
| `BOOKING_ENABLED` | 可上架民宿房間 | false |
| `CMS_ENABLED` | 可發布 CMS 貼文 | true |
| `ERP_ENABLED` | 可使用進銷存管理 | true |
| `DYNAMIC_PRICING_ENABLED` | 可使用動態定價 | false |
| `PROMO_ENABLED` | 可建立促銷活動 | false |
| `MAX_PRODUCTS` | 商品上架上限 | 100 |
| `MAX_ROOMS` | 房間上架上限 | 20 |
| `MAX_POSTS` | 貼文發布上限 | 50 |
| `COMMISSION_RATE` | 平台抽成比例 | 0.05 (5%) |

**[Specification] Feature Toggle × RBAC 整合實作方式（v0.9_R02_Loop_01 新增）**

> `§7.3` RBAC 矩陣中以 `*` 標注的權限（如 Host 的 M02 房源管理 `RW*`）表示該權限**受 Feature Toggle 限制**。`BOOKING_ENABLED` 控制 Host 的房源上架權限；`RETAIL_ENABLED` 控制 Seller 的商品上架權限。

**`\*` 標注的實作方式：**
1. **驗證時機**：所有 B 端 API（`/api/v2/dashboard/*`）進入 Controller 後，在執行業務邏輯**之前**，由 Service 層主動查詢 `tenant_feature_toggles` 表。
2. **驗證邏輯**：
   - Host 建立/更新 Room Listing → 查詢 `BOOKING_ENABLED`，若為 `false` → 拋 `E-2020 FEATURE_DISABLED_FOR_TENANT`，不寫入資料庫。
   - Seller 建立/更新 Product Listing → 查詢 `RETAIL_ENABLED`，若為 `false` → 拋 `E-2020 FEATURE_DISABLED_FOR_TENANT`，不寫入資料庫。
   - StoreOwner/Seller 建立促銷活動 → 查詢 `PROMO_ENABLED`。
   - StoreOwner/Seller 使用 ERP 功能 → 查詢 `ERP_ENABLED`。
3. **錯誤碼**：`E-2020 FEATURE_DISABLED_FOR_TENANT`，HTTP 403，訊息：「房源管理功能尚未啟用，請聯繫平台管理員。」

**具體實作約束**：
- Feature Toggle 驗證在 Repository 層或 Service 層執行均可，但**不得**在 Controller 層執行（避免 business logic 洩漏至表現層）。
- Toggle 狀態建議快取至 Redis，TTL 60 秒，避免每次 API 呼叫均穿透資料庫。
- Feature Toggle 的變更（即 `tenant_feature_toggles` 表寫入）需經 Admin 角色授權，並寫入 `audit_logs`。

---

## 5. 統一商品/服務模型 (Unified Listing Model)

### 5.1 設計目標

**[Specification] 自我糾錯檢核 SC-001：「商品」Entity 如何同時相容實體物品與虛擬房型？**

答案：引入 **Listing** 作為統一抽象層，Product 與 Room 各自擁有專屬資料表，但共享 `listings` 基礎欄位。CMS 嵌入卡片和前台搜尋均面向 Listing 抽象層操作。

### 5.2 商品類型定義（繼承 v0.8 §3）

#### [Specification] 實體商品（Product）

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | 主鍵 |
| name | String | 商品名稱 |
| description | Text | 商品描述 |
| price | Decimal | 單價 |
| category | Enum | 分類（3C/生活/食品...） |
| stock | Integer | 庫存數量 |
| images | String[] | 圖片 URL 列表 |
| sellerId | UUID | 賣家 ID |
| createdAt | Timestamp | 上架時間 |
| updatedAt | Timestamp | 更新時間 |

#### [Specification] 民宿房間（Room）

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | 主鍵 |
| name | String | 房間名稱 |
| description | Text | 房間描述 |
| location | String | 地址/地區 |
| latitude/longitude | Double | 地理座標（Phase 2+ PostGIS LBS 用，Phase 1 先以字串儲存） |
| pricePerNight | Decimal | 每晚價格 |
| maxGuests | Integer | 最大入住人數 |
| amenities | String[] | 設施清單（WiFi/停車...） |
| images | String[] | 圖片 URL 列表 |
| hostId | UUID | 房东 ID |
| createdAt | Timestamp | 上架時間 |
| updatedAt | Timestamp | 更新時間 |

### 5.3 Listing 基礎模型（v0.9 統一抽象層）

```
listings (統一抽象表)
├── id: UUID (PK)
├── tenant_id: UUID (FK → tenants.id, NOT NULL)
├── listing_type: ENUM('PRODUCT', 'ROOM')
├── title: VARCHAR(200)
├── description: TEXT
├── cover_image_url: VARCHAR(500)
├── status: ENUM('DRAFT', 'ACTIVE', 'INACTIVE', 'DELETED')
├── owner_id: UUID (FK → users.id)  -- 上架者
├── base_price: DECIMAL(12,2)        -- 基礎價格 (Product 為售價, Room 為基礎每晚價)
├── currency: VARCHAR(3) DEFAULT 'TWD'
├── tags: TEXT[]                      -- 標籤 (搜尋/分類用)
├── created_at: TIMESTAMP
├── updated_at: TIMESTAMP
└── INDEX (tenant_id, listing_type, status)
```

### 5.4 Product 特化模型（實體商品）

```
products (繼承 listing_id)
├── listing_id: UUID (FK → listings.id, UNIQUE)
├── category: VARCHAR(50)
├── brand: VARCHAR(100)
├── weight_grams: INTEGER
├── dimensions_cm: VARCHAR(50)        -- "LxWxH"
└── INDEX (listing_id)

product_skus (SKU 規格)
├── id: UUID (PK)
├── product_listing_id: UUID (FK → listings.id)
├── sku_code: VARCHAR(50) UNIQUE
├── spec_name: VARCHAR(100)           -- 例: "紅色/128GB"
├── price_override: DECIMAL(12,2)     -- NULL 表示使用 listing.base_price
├── status: ENUM('ACTIVE', 'INACTIVE')
└── INDEX (product_listing_id)

product_inventory (SKU 庫存 — 與 ERP 連動)
├── sku_id: UUID (FK → product_skus.id, UNIQUE)
├── total_qty: INTEGER DEFAULT 0       -- 總庫存 (ERP 入庫累加)
├── reserved_qty: INTEGER DEFAULT 0    -- 已預留 (訂單鎖定)
├── available_qty: INTEGER GENERATED ALWAYS AS (total_qty - reserved_qty) STORED
├── version: BIGINT DEFAULT 0          -- 樂觀鎖版本號
└── updated_at: TIMESTAMP
```

**庫存公式：** `available_qty = total_qty - reserved_qty`

- ERP 入庫 → `total_qty += inbound_qty`
- 訂單建立 → `reserved_qty += order_qty` (樂觀鎖 `WHERE version = :v`)
- 訂單取消 → `reserved_qty -= order_qty`
- 訂單完成 → `total_qty -= order_qty; reserved_qty -= order_qty`

**[Specification] 並發入庫鎖定策略（v0.9 新增）**

> **問題**：如果同一 SKU 同時有兩張採購單入庫，樂觀鎖的 `version` 欄位何時遞增？

`product_inventory.version` 在**每次庫存異動（包括 INBOUND/OUTBOUND/RESERVE/RELEASE/ADJUST_PLUS/ADJUST_MINUS）**寫入後自動遞增。

**並發入庫場景處理**：
1. 兩張採購單同時確認收貨 → 各自產生 StockMovement (INBOUND)
2. 各自讀取 `version = V`
3. 各自嘗試 `WHERE version = V`
4. 先寫入者成功，後寫入者因 version 不匹配而失敗（樂觀鎖衝突）
5. 應用層捕獲 OptimisticLockException，重試機制觸發

> **Phase 1 假設**：Phase 1 每筆訂單僅包含一個 SKU，故一個 OUTBOUND movement 對應一個 order_item。Phase 2 多 SKU 訂單的出貨拆單邏輯見 Phase 2 規格。`stock_movements.order_item_id` 欄位 Phase 1 為 NULL。

### 5.5 Room 特化模型（民宿房間 + 動態定價）

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

room_calendar (日曆 — 每房每天一列，取代 v0.8 的 booking_slots)
├── id: UUID (PK)
├── room_listing_id: UUID (FK → listings.id)
├── calendar_date: DATE
├── status: ENUM('AVAILABLE', 'BOOKED', 'BLOCKED', 'MAINTENANCE')
├── price: DECIMAL(12,2)              -- ★ 當日實際價格 (動態定價計算後的結果)
├── booking_id: UUID (FK → bookings.id, NULLABLE)
├── UNIQUE (room_listing_id, calendar_date)  -- 關鍵：杜絕 Overbooking
└── INDEX (room_listing_id, calendar_date, status)

pricing_rules (動態定價規則)
├── id: UUID (PK)
├── tenant_id: UUID (FK → tenants.id)
├── room_listing_id: UUID (FK → listings.id, NULLABLE)  -- NULL = 適用該租戶全部房間
├── rule_type: ENUM('WEEKDAY_WEEKEND', 'SEASONAL', 'EARLY_BIRD', 'LONG_STAY', 'MANUAL_OVERRIDE', 'LAST_MINUTE')
├── rule_name: VARCHAR(100)
├── priority: INTEGER DEFAULT 0        -- 優先順序 (越大越優先)
├── config: JSONB                      -- 規則參數 (見下方)
├── valid_from: DATE
├── valid_to: DATE
├── is_active: BOOLEAN DEFAULT true
├── created_at: TIMESTAMP
└── INDEX (tenant_id, room_listing_id, is_active)
```

> **v0.8 → v0.9 表名映射：**
> - `booking_slots` → `room_calendar`（新增 `price` 欄位支援動態定價）
> - `seasonal_pricing` → 已吸收至 `pricing_rules` JSONB config（rule_type = SEASONAL）
> - `room_blackout_dates` → 已吸收至 `room_calendar` status = BLOCKED

#### 5.5.1 pricing_rules 數量約束（v0.9 新增）

> **說明**：為避免房東建立過多相互衝突的定價規則，系統對 pricing_rules 實行以下約束：

| 約束 | 說明 |
|------|------|
| 每間房上限 | 每個 `room_listing_id` 最多 **50 條** `is_active = true` 的定價規則 |
| 同類型唯一 | 每個 `room_listing_id` 每種 `rule_type` 最多 **1 條** `is_active = true` 的規則 |
| 衝突處理 | 當房東建立新規則時，若 `rule_type` 已存在 active 規則，系統提示「即將覆蓋現有規則，確認？」 |

**[Specification] 覆蓋行為定義（v0.9_R02_Loop_01 新增）**

> 當房東確認覆蓋新規則時，系統的具體處理行為如下：
> 1. 將同一 `rule_type` 的舊 active 規則設為 `is_active = false`（**軟刪除**），`updated_at` 更新為當前時間。
> 2. 新規則寫入資料庫，`is_active = true`。
> 3. 舊規則保留在資料庫中（`is_active = false`），支援歷史查詢與審計需求。
> 4. **不執行** hard delete（硬刪除），確保所有定價計算可追溯。
> 5. 若房東選擇「取消」覆蓋，系統保留舊規則不做任何變更。

**[Test Cases] pricing_rules 覆蓋驗證**

| 編號 | 測試案例 | GIVEN | WHEN | THEN |
|------|---------|-------|------|------|
| TC-PR-001 | 同類型規則覆蓋 — 軟刪除舊規則 | Room1 已有一條 active `WEEKDAY_WEEKEND` 規則 RuleA | Host 建立新規則 RuleB（`rule_type=WEEKDAY_WEEKEND`），確認覆蓋 | RuleA 的 `is_active = false`，RuleB 的 `is_active = true`，定價計算由 RuleB 決定 |
| TC-PR-002 | 覆蓋後歷史可查 | RuleA 已覆蓋為 `is_active = false` | 查詢 Room1 所有定價規則 | 回傳 RuleA 與 RuleB，RuleA 標注為 `is_active: false` |
| TC-PR-003 | 時間範圍重疊衝突檢核 | Room1 已有 active WEEKDAY_WEEKEND 規則（valid_from=2026-01-01, valid_to=2026-12-31） | Host 建立另一條 WEEKDAY_WEEKEND 規則（valid_from=2026-06-01, valid_to=2026-08-31） | 系統回傳 `400 E-4001`，`message: "新規則與現有同類型規則時間範圍重疊，請先編輯現有規則的有效期間"` |
| TC-PR-004 | 覆蓋後歷史 gap 期間回退 base_price | Room1 的 WEEKDAY_WEEKEND 規則 RuleA（2026 全年）被軟刪除，RuleB 為新規則（2026 下半年） | 2026-03-15 查詢定價日曆 | 使用 `listing.base_price`（因為 RuleB 的 valid_from=2026-06-01，覆蓋前日期無 active 規則） |

#### 5.5.2 動態定價計算觸發機制（v0.9 新增）

> **說明**：定義 pricing_rules 變更時，何時重新計算 `room_calendar.price`。

| 觸發時機 | 說明 |
|---------|------|
| **即時計算** | 當 `pricing_rules` 新增/修改/刪除時，系統立即重新計算受影響日期範圍（`valid_from` ~ `valid_to`）的 `room_calendar.price`，寫入資料庫 |
| **每日凌晨重算** | 每日凌晨 03:00（UTC+8）系統執行一次全量 `room_calendar.price` 重新計算，確保所有 active 規則均已套用 |
| **已預訂日期格保護** | 已處於 `BOOKED` 狀態的 `room_calendar` 記錄，**不受 pricing_rules 變更影響**（已確認的價格不浮動）；`AVAILABLE` / `BLOCKED` 狀態的記錄隨新規則即時更新 |
| **MAINTENANCE 日期格保護（v0.9_R02_Loop_01 新增）** | 已處於 `MAINTENANCE` 狀態的 `room_calendar` 記錄，**不受 pricing_rules 即時重新計算影響**，保留上一次 `AVAILABLE` 時期的 `price`，或統一設為 `NULL`（表示該日期不可預訂）。MAINTENANCE 狀態解除後，若無新定價規則則恢復為 `listing.base_price`。 |

#### 5.5.3 MAINTENANCE 狀態定義（v0.9 新增）

| 狀態 | 說明 | 與 BLOCKED 的差異 |
|------|------|------------------|
| `AVAILABLE` | 可正常預訂 | — |
| `BOOKED` | 已有人預訂 | — |
| `BLOCKED` | 房東主動封鎖（長期不開放出租） | 長期封鎖，無預訂 |
| `MAINTENANCE` | 臨時維護（如：裝修、清潔） | 臨時性，未來可恢復 |

> **MAINTENANCE 行為（v0.9_R02_Loop_01 深化）**：若已 `BOOKED` 的日期被房東標記為 `MAINTENANCE`，系統行為如下：
> 1. `room_calendar.status` 改為 `MAINTENANCE`，`booking_id` **保留不清除**。
> 2. `bookings` 表中該筆 Booking 的 `status_flags`（JSONB 欄位）新增 `under_maintenance: true`。
> 3. M09（通知系統，Phase 2）未上線前，於 Admin Dashboard 顯示該 Booking 的維護警告，供管理員人工通知房客。
> 4. 該日期格的 `room_calendar.price` 依 §5.5.2 MAINTENANCE 日期格保護規則處理（保留上一次 AVAILABLE 時期的價格或設為 NULL）。
> 5. MAINTENANCE 狀態解除後，`room_calendar.status` 恢復為 `BOOKED`，`status_flags.under_maintenance` 清除，價格重新計算。

**[Specification] room_calendar 狀態轉換矩陣（v0.9_R02_Loop_02 新增）**

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

**[Specification] MAINTENANCE 狀態轉換時的價格處理（v0.9_R02_Loop_02 新增）**

當 `room_calendar.status` 從 `AVAILABLE` 改為 `MAINTENANCE` 時，`price` 設為 `NULL`（表示不可預訂）。MAINTENANCE 解除後（改回 `AVAILABLE`），`price` 恢復為當時有效的動態定價（若無 pricing_rules，則使用 `listing.base_price`）。

**[Specification] Phase 2-A MAINTENANCE Booking 空白期替代方案（v0.9_R02_Loop_02 新增）**

M09（訊息通知中心，Phase 2）未上線前，Admin Dashboard 新增 `MaintenanceWarnings` 列表（Phase 2-A 實作），顯示所有 `under_maintenance: true` 的 Booking，包含 Booking ID、入住日期、房客 Email（由 Admin 複製後手動通知）。若入住日期在 24 小時內，Dashboard 顯示紅色緊急標記，並提示 Admin 應主動聯繫房客。

**[Specification] 動態定價引擎 — 價格計算路徑**

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

最終價格 = max(calculated_price, 0)  -- 不可為負
```

**pricing_rules.config JSONB 範例：**

```json
// WEEKDAY_WEEKEND
{ "weekdayMultiplier": 1.0, "weekendMultiplier": 1.3 }

// SEASONAL
{ "multiplier": 1.5, "description": "春節旺季" }

// EARLY_BIRD（早鳥優惠）
{ "daysInAdvance": 30, "discountPercent": 15 }

// LONG_STAY（長住折扣）
{ "minNights": 7, "discountPercent": 10 }

// LAST_MINUTE
{ "daysBeforeCheckIn": 3, "discountPercent": 20 }

// MANUAL_OVERRIDE
{ "fixedPrice": 3500, "reason": "跨年特價" }
```

### 5.6 統一 Listing 查詢介面

前台 C 端和 CMS 嵌入卡片均透過統一介面查詢：

| 查詢場景 | API | 行為 |
|---------|-----|------|
| 前台搜尋 | `GET /api/v2/listings?type=PRODUCT&keyword=...` | 跨租戶查詢 ACTIVE listings |
| 商品詳情 | `GET /api/v2/listings/:id` | 返回 Listing 基礎 + Product/Room 特化資訊 |
| CMS 嵌入 | `GET /api/v2/listings/:id/card` | 返回輕量卡片資訊 (標題、價格、庫存/可用性、封面圖) |
| 店鋪後台 | `GET /api/v2/dashboard/listings` | 僅返回當前 Tenant 的 listings |

---

## 6. 核心功能模組 (Core Functional Modules)

### 6.1 模組總覽（v0.9 更新）

v0.9 在 v0.8 的 M01–M14 基礎上新增 M15–M17 三個模組，並將 M12 動態定價從 Phase 2 提前至 Phase 2-A。

| 子系統 | 模組 | 名稱 | v0.9 變更 |
|--------|------|------|----------|
| **Shared Core** | M03 | 會員與權限系統 | 擴展 RBAC 矩陣，新增 Tenant 角色 |
| | M07 | 金流與結算系統 | 不變 |
| | M08 | 評價與社交互動 | 不變 |
| | M09 | 訊息通知中心 | 不變 |
| | M10 | 即時通訊 (IM) | 不變 |
| **Retail Engine** | M01 | 商品中心 | ★ 改為基於 Listing 抽象 |
| | M04 | 購物車與促銷 | 不變 |
| | M05 | 訂單履約系統 | 不變（v0.8 SDD 完整保留） |
| | M11 | 物流追蹤 | 不變 |
| **Booking Engine** | M02 | 房源中心 | ★ 改為基於 Listing 抽象 + room_calendar |
| | M06 | 預訂與日曆鎖定 | 不變 |
| | M12 | 動態定價引擎 | ★ 從 Phase 2 提前至 Phase 2-A |
| **Operations** | M13 | 商家工作台 | ★ 升級為多租戶店鋪後台 |
| | M14 | 平台總管理台 | ★ 新增租戶管理 + Feature Toggle |
| **CMS (New)** | **M15** | **內容管理系統** | ★ 全新模組 |
| **ERP (New)** | **M16** | **進銷存管理** | ★ 全新模組 |
| **Tenant (New)** | **M17** | **租戶/店鋪管理** | ★ 全新模組 |

### 6.2 通用核心模組 (Shared Core)（繼承 v0.8 §4.1）

| 模組編號 | 模組名稱 | 功能描述 | 優先級 | 技術重點與邏輯 |
| :--- | :--- | :--- | :---: | :--- |
| **M03** | **會員與權限系統** | 多角色登入 (Buyer/Seller/Host/Admin)、RBAC 權限控管；**Phase 2**：OAuth2 第三方登入、KYC 實名認證。v0.9 擴展：新增 StoreOwner/StoreStaff/SuperAdmin 角色，綁定 Tenant。 | P0 | JWT + Refresh Token 雙令牌機制；分散式 Session 管理。KYC 與 OAuth2 屬 Phase 2 擴展功能，不在 M03 P0 核心交付範圍內。 |
| **M07** | **金流與結算系統** | 多金流整合 (Stripe, LinePay)、平台分帳 (Split Payment)、自動化退款流、交易手續費計算。 | P0 | 採用 Saga Pattern 處理分散式交易一致性；支援「平台抽成」邏輯。 |

#### 6.2.1 結算系統（Platform Settlement）（v0.9 新增）

> **說明**：結算系統定義商家收益結算週期、結算公式與提現機制。

**結算週期**：每週一結算上一週（週一 00:00 至週日 23:59）已完成（COMPLETED）訂單的收益。

**結算公式**：
```
商家結算金額 = Σ(order.total_amount × (1 - commission_rate)) - 退款金額
```

**結算單生成**：系統每週一自動生成 `settlement_statements` 表記錄，供 Admin 和 StoreOwner 查閱。

**提現機制（Phase 2-B）**：支援商家發起提現申請，款項匯入登記的銀行帳戶。

**爭議處理**：退款訂單從當週結算金額中扣除。

**[Specification] 跨結算週期退款處理機制（v0.9_R02_Loop_01 新增）**

當已 `PAID` 的結算單涉及退款時，系統按以下規則處理：

| 場景 | 處理方式 |
|------|---------|
| 結算單狀態 `PENDING`（未結算） | 該筆退款直接從當週 `total_refunds` 扣除，不產生獨立單據 |
| 結算單狀態 `APPROVED` 或 `PAID`（已結算） | 系統自動生成 `adjustment_statement`（調整單），類型為 `REFUND_DEDUCTION`，狀態 `PENDING`，於下一結算週期一併結算 |
| `PAID` 結算單逆轉 | 需 SuperAdmin + 財務長共同授權，並產生 `CREDIT_NOTE`（貸項通知單）以沖銷原結算金額 |

**[Specification] CREDIT_NOTE 實體歸屬（v0.9_R02_Loop_02 新增）**

採用**方案 A**：CREDIT_NOTE 為獨立於 `settlement_statements` 的新表 `credit_notes`（schema 見 §8.2.10）。`settlement_statements.status` 新增 `REVERSED` 狀態——當原 PAID 結算單被 CREDIT_NOTE 沖銷後，原單狀態改為 `REVERSED`。CREDIT_NOTE 包含 `original_statement_id` FK 關聯原結算單。

**[Specification] 結算單狀態逆轉權限（v0.9_R02_Loop_01 新增）**

| 當前狀態 | 可重建狀態 | 所需權限 |
|---------|-----------|---------|
| `PENDING` | 任何狀態可重建 | Admin |
| `APPROVED` | `PENDING`（需重建） | Admin 授權 |
| `PAID` | 不可直接逆轉，產生 Credit Note | SuperAdmin + 財務長雙重授權 |

**[Specification] 結算單 Admin 審核流程（v0.9_R02_Loop_01 新增）**

結算單經過以下審核流程：`PENDING` → `PENDING_REVIEW` → `APPROVED` / `REJECTED` → `PAID` / `FAILED`。當 Admin 審核不通過時，`status = REJECTED`，`rejection_reason` 填寫駁回原因。

> **Phase 1 假設**：Phase 1 為 Payment Mock，無真實金流結算需求，結算系統功能預留至 Phase 2-B。
| **M08** | **評價與社交互動** | 圖文評論、星級評分、商家回覆、評論權重算法 (過濾水軍)、檢舉機制。 | P2 | 非同步更新商品/房源綜合評分；支援多媒體 CDN 存儲（Phase 2+）。 |
| **M09** | **訊息通知中心** | 訂單狀態變更通知、行銷推播、系統公告。支援 Email, SMS, App Push。 | P1 | **Phase 2 實作**：MQ（RabbitMQ/Redis Stream）實現非同步發送；支援通知模板化。 |
| **M10** | **即時通訊 (IM)** | 買家與賣家/房東一對一私訊、常見問題快捷回覆、圖片與位置傳送。 | P2 | 基於 WebSocket 或 MQTT 協定（Phase 2+）；訊息持久化存儲與未讀計數。 |

### 6.3 零售引擎模組 (Retail Engine)（繼承 v0.8 §4.2）

| 模組編號 | 模組名稱 | 功能描述 | 優先級 | 技術重點與邏輯 |
| :--- | :--- | :--- | :---: | :--- |
| **M01** | **商品中心** | 商品列表、分類、搜尋（Phase 1 核心）。**Phase 2+**：SKU 多規格管理、組合包 (Bundle)、ElasticSearch 全文檢索。v0.9：改為基於 Listing 抽象模型。 | P0 | Phase 1 基礎 CRUD + 分頁篩選搜尋；ElasticSearch 屬 Phase 2+ 擴展功能，不列入核心描述。 |
| **M04** | **購物車與促銷** | 購物車快取、優惠券套用、滿額折扣/免運計算、限時搶購邏輯。 | P0 | Redis Hash 存儲購物車資料；促銷引擎 (Rule Engine) 排除優惠衝突。 |
| **M05** | **訂單履約系統** | 訂單狀態機、取消與資源釋放（**Phase 1 最小子集**）；逆向物流處理、電子發票開立、拆單（**Phase 2**）。 | P0 | **狀態機 (State Machine)** 嚴格控管狀態流轉；Phase 1 僅支援單一商品類型訂單。 |
| **M11** | **物流追蹤** | 運費模板、第三方物流 API 介接 (黑貓/順豐/超商)、物流軌跡實時查詢。 | P1 | 異質物流介面封裝 (Strategy Pattern)；預估到貨時間計算。 |

### 6.4 預訂引擎模組 (Booking Engine)（繼承 v0.8 §4.3）

| 模組編號 | 模組名稱 | 功能描述 | 優先級 | 技術重點與邏輯 |
| :--- | :--- | :--- | :---: | :--- |
| **M02** | **房源中心** | 列表搜尋：地區/日期/人數過濾（Phase 1 核心）。**Phase 2+**：LBS 地圖模式（PostGIS）、設施過濾、景點距離計算。v0.9：改為基於 Listing 抽象 + room_calendar。 | P0 | Phase 1 基礎過濾搜尋；PostGIS LBS 屬 Phase 2+ 擴展功能，不列入核心描述。 |
| **M06** | **預訂與日曆鎖定** | 日期格查詢（**Phase 1**）；實時庫存鎖定 (Date Slot)、加購服務（**Phase 2**）。 | P0 | **Redis 分佈式鎖** 防止重複預訂 (Overbooking)；日曆格索引優化。入住人數差異定價屬 Phase 2 M12 動態定價範圍。**Phase 1 僅開放查詢與取消，不含 POST 建立預訂**。 |
| **M12** | **動態定價引擎** | 平假日自動調價、早鳥優惠、長住折扣、特定日期人工調價。v0.9：從 Phase 2 提前至 Phase 2-A。 | P1 | 價格計算路徑：`基礎價 * 期間係數 + 稅費 + 清潔費`。詳見 §5.5 定價規則。 |

### 6.5 營運與後台管理 (Operation & Admin)（繼承 v0.8 §4.4）

| 模組編號 | 模組名稱 | 功能描述 | 優先級 | 技術重點與邏輯 |
| :--- | :--- | :--- | :---: | :--- |
| **M13** | **商家工作台** | 零售賣家與民宿房東各自的儀表板。包含營收分析、訂單處理、庫存預警。v0.9：升級為多租戶店鋪後台。 | P1 | 前端數據可視化 (ECharts/Recharts)；導出報表非同步處理。 |
| **M14** | **平台總管理台** | 全站參數配置、用戶/商家審核、違規內容下架、財務對帳單生成。v0.9：新增租戶管理 + Feature Toggle。 | P1 | 完整操作審計日誌 (Audit Log)；高權限 API 需二次驗證。 |

### 6.6 M15 內容管理系統 (CMS) — v0.9 新增模組

#### 6.6.1 模組定位

| 屬性 | 說明 |
|------|------|
| 模組編號 | M15 |
| 模組名稱 | Content Management System (CMS) |
| 所屬子系統 | CMS |
| 核心能力 | 貼文發布、商品/房型嵌入卡片、媒體庫管理 |
| 依賴模組 | M03 (認證)、M01 (商品)、M02 (房源)、M17 (租戶) |

#### 6.6.2 功能描述

| 功能 | 說明 | 優先級 |
|------|------|--------|
| 貼文 CRUD | 建立/編輯/刪除/發布貼文 | P0 |
| 富文本編輯 | Markdown / Rich Text 編輯器 | P0 |
| **嵌入購買卡片** | 在貼文中嵌入商品或房型的即時購買卡片 (含價格、庫存、加入購物車按鈕) | P0 |
| 排程發布 | 設定未來時間自動發布 | P1 |
| 媒體庫 | 上傳/管理圖片與影片，按 Tenant 隔離 | P0 |
| 分類與標籤 | 貼文分類管理與標籤搜尋 | P1 |
| SEO 優化 | Meta title/description 自訂 | P2 |

#### 6.6.3 嵌入購買卡片設計

**[Specification] PostEmbed 機制**

貼文內容中透過特殊標記嵌入商品/房型卡片：

```markdown
<!-- 貼文 Markdown 內容 -->
這個月最推薦的 3C 好物！

{{embed:listing:550e8400-e29b-41d4-a716-446655440000}}

最近熱門的高雄民宿：

{{embed:listing:660e8400-e29b-41d4-a716-446655440001}}
```

前端解析 `{{embed:listing:<listing_id>}}` 標記後，向 API 請求卡片資料並渲染為互動式購買元件。

**嵌入卡片渲染資料 (API Response)：**

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

**[Specification] ROOM 類型嵌入卡片在 MAINTENANCE 狀態時的顯示行為（v0.9_R02_Loop_01 新增）**

當 `room_calendar.status = MAINTENANCE` 時，`GET /api/v2/listings/:id/card` API 的 ROOM 類型卡片 Response：
- `availability.available` = `false`（不開放預訂）
- `availability.statusReason` = `"under_maintenance"`
- `ctaUrl` = `"/contact"`（聯繫客服，而非直接預訂連結）
- `currentPrice` = `null`（MAINTENANCE 日期格無定價）

**[Specification] 嵌入卡片 Cascade 處理（v0.9_R02_Loop_02 新增）**

當 `post_embeds` 關聯的 Listing 狀態為 `INACTIVE` 或 `DELETED` 時：
- `GET /api/v2/listings/:id/card` API：`availability.available = false`，`statusReason = "listing_unavailable"`，`ctaUrl = "/stores/{slug}"`（導向店鋪而非 404）
- `GET /api/v2/posts/:slug` 解析時自動跳過該嵌入位置，不回傳 404；具體嵌入位置顯示「此商品已下架」提示

**[Specification] PostEmbed Markdown 語法錯誤處理（v0.9_R02_Loop_02 新增）**

系統解析 Markdown 內容時，遇格式錯誤的 `{{embed:...}}` 語法，自動忽略並記錄 warning log，不阻斷寫入；格式正確但 `listing_id` 不存在或非 ACTIVE 時，回傳嵌入卡片層級的錯誤（不阻斷整篇 Post 寫入），具體嵌入位置顯示「此商品已下架」提示。

**[Specification] PostEmbed 嵌入語法重複校驗（v0.9_R02_Loop_01 新增）**

當 `POST /api/v2/dashboard/posts` 或 `PUT /api/v2/dashboard/posts/:id` 寫入貼文時，系統解析 Markdown 內容中的所有 `{{embed:listing:<listing_id>}}` 標記：
- 若發現重複的 `listing_id`（同一 Listing 被嵌入兩次以上），在寫入資料庫**之前**回傳錯誤：
  - HTTP Status: `400`
  - Error Code: `E-4001`
  - Message: `EMBED_DUPLICATE_LISTING: listing_id {id} appears more than once in this post content`
- 若 `listing_id` 對應的 Listing 不存在或非 ACTIVE 狀態，回傳 `404 E-1001`。

> **前端編輯器 UX 建議（非 PRD 規格約束）**：前端 Markdown 編輯器可在用戶輸入時即時解析語法，遇重複 ID 時顯示 UI 警告提示，以提升編輯體驗。此為元件層級的 UX 優化建議，不寫入後端 API 規格約束。
```

### 6.7 M16 進銷存管理 (ERP) — v0.9 新增模組

#### 6.7.1 模組定位

| 屬性 | 說明 |
|------|------|
| 模組編號 | M16 |
| 模組名稱 | Enterprise Resource Planning — 進銷存 |
| 所屬子系統 | ERP |
| 核心能力 | 採購入庫、庫存台帳、出庫（訂單/調撥）、庫存盤點 |
| 依賴模組 | M03 (認證)、M01 (商品)、M17 (租戶) |

#### 6.7.2 功能描述

| 功能 | 說明 | 優先級 |
|------|------|--------|
| 採購入庫 | 記錄供應商進貨，建立採購單，入庫後自動更新可售量 | P0 |
| 庫存台帳 | 按 SKU 查看即時庫存、庫存異動記錄 | P0 |
| 出庫管理 | 訂單出貨時自動出庫；支援手動出庫（報廢/調撥） | P0 |
| 庫存盤點 | 定期盤點校正，產生盤盈/盤虧記錄 | P1 |
| 庫存預警 | 低庫存 / 過期預警通知 | P1 |
| 供應商管理 | 維護供應商基本資訊 | P2 |
| **採購審批流程** | StoreOwner 建立採購單（DRAFT）→ 提交審批（SUBMITTED）→ 收貨（RECEIVED/PARTIAL_RECEIVED）。StoreStaff 若有採購權限亦可提交。StoreOwner 可設定每筆採購單的金額上限；超過上限需 SuperAdmin 核准。 | P0 |

#### 6.7.3 ERP → C 端連動機制

**[Specification] 一體化庫存連動**

```
B 端入庫流程：
1. Seller/Host 在後台建立採購單 (PurchaseOrder)
2. 收貨確認 → 產生入庫異動 (StockMovement type=INBOUND)
3. StockMovement 事件觸發 → product_inventory.total_qty += inbound_qty
4. available_qty 自動重算 → C 端商品頁即時反映新庫存

C 端扣減流程：
1. 買家下單 → 產生出庫預留 (StockMovement type=RESERVE)
2. product_inventory.reserved_qty += order_qty
3. 訂單出貨 → StockMovement type=OUTBOUND
4. product_inventory.total_qty -= qty; reserved_qty -= qty

取消回滾：
1. 訂單取消 → StockMovement type=RELEASE
2. product_inventory.reserved_qty -= order_qty

> **Phase 1 假設**：Phase 1 每筆訂單僅包含一個 SKU，故一個 OUTBOUND movement 對應一個 order_item。Phase 2 多 SKU 訂單的出貨拆單邏輯見 Phase 2 規格。`stock_movements.order_item_id` 欄位 Phase 1 為 NULL（保留供 Phase 2 使用）。
```

#### 6.7.4 庫存異動類型 (StockMovement Types)

| 類型 | 方向 | 說明 |
|------|------|------|
| INBOUND | +total_qty | 採購入庫 |
| OUTBOUND | -total_qty, -reserved_qty | 訂單出貨 |
| RESERVE | +reserved_qty | 訂單預留 |
| RELEASE | -reserved_qty | 訂單取消釋放 |
| ADJUST_PLUS | +total_qty | 盤盈調整 |
| ADJUST_MINUS | -total_qty | 盤虧調整 |
| TRANSFER_OUT | -total_qty | 調撥出庫 |
| TRANSFER_IN | +total_qty | 調撥入庫 |
| SCRAP | -total_qty | 報廢出庫 |

### 6.8 M17 租戶/店鋪管理 — v0.9 新增模組

#### 6.8.1 模組定位

| 屬性 | 說明 |
|------|------|
| 模組編號 | M17 |
| 模組名稱 | Tenant & Store Management |
| 所屬子系統 | Platform Infrastructure |
| 核心能力 | 網友開店申請、店鋪審核、Feature Toggle、抽成設定 |
| 依賴模組 | M03 (認證)、M14 (平台管理) |

#### 6.8.2 功能描述

| 功能 | 說明 | 優先級 |
|------|------|--------|
| 開店申請 | 網友提交店鋪名稱、描述、經營類型等資訊 | P0 |
| 店鋪審核 | Admin 審核開店申請（通過/駁回） | P0 |
| Feature Toggle | Admin 控制各租戶可用功能 | P0 |
| 店鋪 Profile | 店鋪名稱、Logo、描述、聯絡方式 | P0 |
| 抽成設定 | 按租戶設定平台抽成比例 | P1 |
| 店鋪暫停/終止 | Admin 違規處置 | P1 |
| 店鋪數據看板 | 租戶級營收/訂單/流量統計 | P2 |

**[Specification] tenant_feature_toggles 初始化時序（v0.9_R02_Loop_02 新增）**

當 `tenants.status` 從 `PENDING_REVIEW` -> `ACTIVE` 時，系統自動以 `§4.4` 的預設值初始化該 Tenant 的所有 Feature Toggle 紀錄（`RETAIL_ENABLED=true`, `BOOKING_ENABLED=false` 等），**無需 Admin 手動建立**。

### 6.9 M12 動態定價引擎（v1.0 更新：Phase 1 Must Have）

**v1.0 變更：** 用戶需求確認 M12 動態定價是 B&B 核心功能，必須在 Phase 1 交付。

| 功能 | 說明 | 優先級 |
|------|------|--------|
| 平假日調價 | 週六/日/國定假日自動套用倍率 | P0 |
| 季度/旺季加價 | 指定日期範圍套用倍率 | P0 |
| 早鳥優惠 | 提前 N 天預訂享折扣 | P1 |
| 長住折扣 | 連住 N 晚享折扣 | P1 |
| 手動覆蓋 | 特定日期人工設定固定價格 | P0 |
| 末班車優惠 | 入住前 N 天未訂出的降價 | P2 |
| 定價預覽 | 房東在後台預覽未來 90 天定價日曆 | P0 |

> **MoSCoW 定位**：M12 在 §13.5 MoSCoW 表中已調整為 **Must Have（Phase 1 交付）**，序號提升至第一位。

### 6.10 M18 知識管理系統（v1.0 新增）

| 屬性 | 說明 |
|------|------|
| **模組編號** | M18 |
| **模組名稱** | Knowledge Management System |
| **所屬子系統** | Platform Infrastructure |
| **核心能力** | 知識庫、FAQ、客服系統、媒體資產分類 |
| **依賴模組** | M03 (認證)、M17 (租戶) |
| **Phase 歸屬** | Phase 1（媒體中心）/ Phase 2-A（知識庫、FAQ）/ Phase 2-B（客服系統） |

#### M18 功能群組

| 功能群組 | 說明 | Phase |
|---------|------|-------|
| 媒體中心 | 媒體資產上傳、分類、管理（強化 M15） | Phase 1 |
| 知識庫 | 文章/教程的分類、發布、搜尋 | Phase 2-A |
| FAQ | 常見問題分類、解答、管理 | Phase 2-A |
| 客服系統 | 工單提交、處理、回覆 | Phase 2-B |

> **詳細規格**：見 `M18_Knowledge_Management_SPEC.md`

#### M18 功能描述

| 功能 | 說明 | 優先級 |
|------|------|--------|
| 媒體上傳 | 支援圖片（JPG/PNG/GIF/WebP）、影片（MP4/MOV）、文檔（PDF） | P0 |
| 媒體分類 | 支援自訂分類（如：商品圖、房型圖、部落格素材） | P0 |
| 媒體標籤 | 支援多標籤管理 | P1 |
| 知識庫文章 | 建立/編輯/刪除/發布文章 | P1 |
| FAQ 管理 | FAQ 問題分類、解答、管理 | P1 |
| 客服工單 | 工單提交、處理、回覆 | P2 |

#### RBAC 權限（M18 相關）

| 功能 | Buyer | StoreOwner | StoreStaff | Admin | SuperAdmin |
|------|-------|------------|------------|-------|------------|
| 瀏覽媒體/知識庫/FAQ | R | R | R | R | R |
| 上傳媒體 | — | RW | RW | RWD | RWD |
| 管理知識庫文章 | — | RW | — | RWD | RWD |
| 管理 FAQ | — | — | — | RWD | RWD |
| 提交/處理工單 | RW | RW | RW | RWD | RWD |

---

## 7. 使用者角色、人物誌與 RBAC 權限矩陣

### 7.1 角色定義（v0.9 更新）

| 角色 | 說明 | v0.9 變更 |
|------|------|----------|
| Guest | 未登入訪客，可瀏覽商品與房間 | 不變 |
| Buyer | 已登入買家，可購買商品與預訂民宿 | 不變 |
| Seller | 零售賣家（屬於某個 Tenant） | ★ 綁定 Tenant |
| Host | 民宿房東（屬於某個 Tenant） | ★ 綁定 Tenant |
| **StoreOwner** | **店鋪擁有者，可管理自己的 Tenant 下所有功能** | ★ 新增 |
| **StoreStaff** | **店鋪員工，由 StoreOwner 邀請，受限權限** | ★ 新增 |
| Admin | 平台運營人員（全站管理） | ★ 擴展租戶管理權限 |
| SuperAdmin | 平台最高管理員 | ★ 新增 |

### 7.2 核心用戶人物誌

#### [Persona] Buyer — 購物者（首次購物流程驗證）（繼承 v0.8）

| 欄位 | 內容 |
|------|------|
| **Name** | 陳怡君（Cindy Chen） |
| **Role** | 購物者（Buyer） |
| **Demographics** | 女性，28歲，新竹科技業上班族，單身，月收入 NTD 55,000，智慧型手機依賴度高，平均每週網購 2-3 次 |
| **Goals** | 1. 在最短時間內找到想要的商品；2. 確認商品價格與規格是否符合期待；3. 順利完成首次訂單並獲得訂單確認；4. 即時掌握訂單出貨進度 |
| **Pain Points** | 1. 搜尋結果太多雜訊，難以快速找到合適商品；2. 註冊流程冗長導致中途放棄；3. 下單後不確定訂單是否成功；4. 無法即時看到出貨狀態；5. 不信任陌生電商平台的安全性 |
| **Current Journey** | 1. 在 Google 搜尋商品關鍵字；2. 進入陌生電商網站；3. 瀏覽商品列表（此階段流失率最高）；4. 點擊商品詳情頁；5. 嘗試註冊帳號（若流程複雜則離開）；6. 選擇商品規格並加入購物車；7. 嘗試結帳（若需金流填寫則再度猶豫）；8. 完成支付；9. 等待收貨 |
| **How Our System Helps** | 1. M01 商品中心提供快速、篩選精準的搜尋體驗；2. M03 簡化註冊流程（Email + 密碼，3步完成）；3. M05 訂單狀態機提供即時透明的狀態更新；4. Phase 1 模擬支付減少買家對陌生平台的支付疑慮；5. 訂單取消流程簡單，降低後悔成本 |

#### [Persona] Seller — 賣家（商品上架與訂單處理）（繼承 v0.8）

| 欄位 | 內容 |
|------|------|
| **Name** | 王建宏（Ken Wang） |
| **Role** | 賣家（Seller） |
| **Demographics** | 男性，38歲，台北地區3C商品經銷商老闆，經營線上通路5年，管理2名員工，月營業額 NTD 600,000 |
| **Goals** | 1. 快速上架新商品並觸及新買家；2. 即時掌握訂單狀態，避免漏單；3. 庫存與訂單同步更新，杜絕超賣；4. 在新平台建立穩定營收基礎 |
| **Pain Points** | 1. 多平台庫存需手動更新，常因時間差導致超賣；2. 訂單狀態需逐一登入各平台查詢，耗時且易漏；3. 買家常因等待過久放棄訂單；4. 新平台操作介面陌生，上架效率低；5. 平台抽成與廣告費用不透明 |
| **Current Journey** | **（9 步驟，含障礙/痛感標註）**<br>1. 工廠進貨後拍攝商品圖片<br>2. 登入電商平台後台填寫商品資訊<br>3. 等待平台審核<br>4. 訂單產出後複製物流資訊至物流系統<br>5. 填寫出貨單並通知買家<br>6. 追蹤物流進度<br>7. 對帳與結算<br>**痛感：多平台庫存需手動更新，常因時間差導致超賣**<br>**痛感：訂單狀態需逐一登入各平台查詢，耗時且易漏** |
| **How Our System Helps** | 1. M01 商品中心提供清晰的上架介面與即時預覽（減少步驟 2 操作時間）；2. M05 訂單狀態機自動驅動流程，減少人工追蹤負擔（緩解步驟 4~6 痛感）；3. 庫存在下單時原子性扣減，杜絕超賣（緩解步驟 1 痛感）；4. Phase 1 先驗證核心下單流程，確認系統穩定性後再擴展功能 |

#### [Persona] Host — 房東（房源管理與日曆操作）（繼承 v0.8）

| 欄位 | 內容 |
|------|------|
| **Name** | 劉雅琪（Yachi Liu） |
| **Role** | 房東（Host） |
| **Demographics** | 女性，32歲，高雄苓雅區，擁有並管理 6 間短租套房，每間月營收平均 NTD 28,000，兼職經營，管理時間零碎 |
| **Goals** | 1. 即時更新房源日曆，避免重複預訂（Overbooking）；2. 快速回覆旅客諮詢；3. 在新平台上曝光房源並獲得穩定訂單；4. 輕鬆管理各房源的可用性與價格 |
| **Pain Points** | 1. 在 Airbnb、Booking.com 與其他平台間手動同步日曆，常因漏更新導致超賣；2. 即時訊息來自多個平台，回覆不及時導致差評；3. 無法根據供需動態調整價格；4. 取消預訂時需手動釋放日期格 |
| **Current Journey** | 1. 接到旅客預訂通知；2. 手動登入平台更新日曆（各平台分開操作）；3. 回覆旅客問題；4. 旅客抵達前發送入住指引；5. 退房後檢查房源並更新日曆為可用；6. 月底對帳 |
| **How Our System Helps** | 1. M02 房源中心提供集中的日曆管理介面，手機即可操作；2. M06 日期格鎖定機制（Redis 分散式鎖 + 資料庫 Unique Key）杜絕 Overbooking；3. M05 預訂狀態機自動驅動入住/退房流程；4. Phase 1 先驗證房源搜尋與日曆查詢的核心體驗 |

#### [Persona] StoreOwner — 網友店主（v0.9 新增）

| 欄位 | 內容 |
|------|------|
| **Name** | 林志偉（David Lin） |
| **Role** | 店鋪擁有者（StoreOwner） |
| **Demographics** | 男性，35歲，台中地區民宿業者兼任 3C 代購，管理 3 間民宿 + 小型 3C 電商，月營業額約 NTD 200,000 |
| **Goals** | 1. 在單一平台同時經營民宿與電商；2. 透過 CMS 貼文推廣自己的商品與民宿；3. 精確掌握進銷存，避免庫存差異；4. 民宿價格能根據旺季自動調整 |
| **Pain Points** | 1. 目前民宿用 Airbnb、電商用蝦皮，分散管理效率極低；2. 庫存靠 Excel 手動記錄，常發生帳實不符；3. 無法在推文中直接附帶購買連結；4. 民宿定價需逐日手動調整 |
| **Current Journey** | 1. 早上核對 Airbnb 與蝦皮賣場；2. 手動抄寫庫存與訂單至 Excel；3. 若發生超賣需緊急聯絡買家取消；4. 在社群宣傳時需附上兩條不同的點擊網址；5. 每晚手動比對雙邊營收對帳 |
| **How Our System Helps** | 1. M17 一站式開店，同時經營 Retail + B&B；2. M16 進銷存自動連動 C 端庫存；3. M15 CMS 嵌入購買卡片直接轉化；4. M12 動態定價引擎自動平假日調價 |

### 7.3 RBAC 權限矩陣

**[Specification] 功能模組 × 角色 權限矩陣**

> **⚠️ Phase 1 RBAC 標注**：以下矩陣中灰色底色模組（M12/M15/M16/M17）屬於 Phase 2-A。**Phase 1 實作請僅參考 M01/M02/M03/M05/M06(查詢) 列**。Phase 1 RBAC 不應出現 Phase 2+ 模組的授權。
>
> R = Read, W = Write (Create/Update), D = Delete, X = Execute (特殊操作)
> `—` = 無權限, `*` = 受 Feature Toggle 限制
> **★ = Phase 2-A 模組（Phase 1 實作請忽略）**

| 功能模組 | Guest | Buyer | Seller | Host | StoreOwner | StoreStaff | Admin | SuperAdmin |
|----------|-------|-------|--------|------|------------|------------|-------|------------|
| **M01 商品瀏覽** | R | R | R | R | R | R | R | R |
| **M01 商品管理** | — | — | RW* | — | RWD* | RW* | RWD | RWD |
| **M02 房源瀏覽** | R | R | R | R | R | R | R | R |
| **M02 房源管理** | — | — | — | RW* | RWD* | RW* | RWD | RWD |
| **M03 認證** | X | X | X | X | X | X | X | X |
| **M03 角色管理** | — | — | — | — | R | — | RWD | RWD |
| **M04 購物車** | — | RWD | — | — | — | — | — | — |
| **M05 訂單 (買家)** | — | RX | — | — | — | — | R | R |
| **M05 訂單 (賣家)** | — | — | RX | RX | RX | R | RX | RX |
| **M06 預訂管理** | — | R | — | RX* | RX* | R* | RX | RX |
| **M12 動態定價** ★ | — | — | — | RW* | RWD* | R* | RWD | RWD |
| **M15 CMS 貼文** ★ | R | R | RW* | RW* | RWD* | RW* | RWD | RWD |
| **M15 媒體庫** | — | — | RW* | RW* | RWD* | RW* | RWD | RWD |
| **M16 ERP 進銷存** | — | — | RW* | — | RWD* | RW* | R | RWD |
| **M17 開店申請** | — | X | — | — | R | — | RX | RX |
| **M17 店鋪管理** | — | — | R | R | RWD | R | RWD | RWD |
| **M17 Feature Toggle** | — | — | — | — | R | — | RW | RWD |
| **M14 平台管理** | — | — | — | — | — | — | RW | RWD |

### 7.4 Tenant 內角色階層

```
StoreOwner (店主)
├── 可邀請 StoreStaff
├── 可分配 Seller / Host 角色給 Staff
├── 可管理店鋪 Profile
├── 可查看 Feature Toggle 狀態
└── 可查看店鋪營收報表

StoreStaff (店員)
├── 由 StoreOwner 邀請加入
├── 權限由 StoreOwner 分配
└── 不可邀請其他 Staff 或修改店鋪 Profile

> **Phase 1 StoreStaff 細粒度權限**：Phase 1 StoreStaff 權限由系統預設（不可自訂），具體權限依 RBAC 矩陣 (§7.3) 中 StoreStaff 列定義。**Phase 2 再開放細粒度配置**（StoreOwner 可透過 `PUT /api/v2/tenants/:id/members/:userId/permissions` 自訂 Staff 的具體模組權限）。
```

#### 7.4.1 Buyer → StoreOwner 角色授予流程 (Role Granting Spec)
- **觸發條件**：一般買家 (`Buyer`) 提交開店申請並經 Admin 審核通過 (`ACTIVE`)。
- **資料表變更**：
  1. 產生新 `tenants` 紀錄。
  2. 於 `tenant_members` 表新增紀錄，關聯 `user_id`、`tenant_id`，設定角色為 `StoreOwner`。
- **JWT 狀態轉換**：
  系統強制用戶重新授權（或以 Refresh Token 換發新建 Access Token）。新 Token 的 JWT Payload 內，`roles` 陣列將包含 `StoreOwner`，且附帶所屬的 `tenant_id` Claim，供 Gateway/Filter 識別。

### 7.5 Feature Toggle × RBAC 整合規範（v0.9_R02_Loop_01 新增）

#### [Specification] `X-Tenant-ID` Header 選取機制

當用戶隸屬於多個 Tenant（如 David Lin 同時擁有民宿店鋪和 3C 代購店鋪），所有 B 端 API 請求**必須**透過 `X-Tenant-ID` Header 明確指定操作目標租戶：

| Header | 說明 | 範例 |
|--------|------|------|
| `X-Tenant-ID` | 明確指定當前操作的目標租戶 UUID | `X-Tenant-ID: 550e8400-e29b-41d4-a716-446655440000` |

**`TenantContextFilter` 行為定義**：
1. 解析 `X-Tenant-ID` Header。
2. 以 `userId`（從 JWT 取得）查詢 `tenant_members` 表，取得用戶所屬的所有 `tenant_id` 清單。
3. 驗證 Header 中的 `tenant_id` 是否在清單內：
   - **在清單內** → 寫入 `TenantContext`（ThreadLocal），後續 Repository 自動附加 `WHERE tenant_id = :currentTenantId`。
   - **不在清單內** → 回傳 `403 E-2002 CROSS_TENANT_ACCESS_DENIED`，不寫入 TenantContext。
   - **Header 缺失且用戶有多個 Tenant 成員記錄** → 回傳 `400 E-2003 TENANT_CONTEXT_AMBIGUOUS`，訊息：「您的帳號隸屬於多個店鋪，請透過 `X-Tenant-ID` Header 明確指定操作目標。」
   - **Header 缺失但用戶僅有一個 Tenant** → 自動使用該唯一 Tenant ID（向後相容單店鋪用戶）。

#### [Specification] Feature Toggle × RBAC `*` 標注實作方式

`§7.3` RBAC 矩陣中以 `*` 標注的權限（如 Host 的 `M02 房源管理 RW*`）的實作方式：

| RBAC `*` 標注 | 對應 Feature Toggle | 實作行為 |
|---------------|-------------------|---------|
| Host `M02 房源管理 RW*` | `BOOKING_ENABLED` | Service 層在建檔前查詢 `tenant_feature_toggles`，若 `BOOKING_ENABLED = false` → 拋 `E-2020 FEATURE_DISABLED_FOR_TENANT` |
| Seller `M01 商品管理 RW*` | `RETAIL_ENABLED` | Service 層在建檔前查詢，若 `RETAIL_ENABLED = false` → 拋 `E-2020 FEATURE_DISABLED_FOR_TENANT` |
| Host/Seller `M12 動態定價 RW*` | `DYNAMIC_PRICING_ENABLED` | Service 層在建規則前查詢 |
| StoreOwner `M16 ERP RW*` | `ERP_ENABLED` | Service 層在建採購單前查詢 |
| StoreOwner `M15 CMS RW*` | `CMS_ENABLED` | Service 層在建貼文前提問 |

**驗證時機**：Service 層（在 Repository 層或 Controller 層均可，但不允許在 Controller 之外執行 business logic 的架構中，建議 Service 層）。

**錯誤格式**：
```json
{
  "error": {
    "code": "E-2020",
    "message": "房源管理功能尚未啟用，請聯繫平台管理員。",
    "details": [{ "field": "listingType", "issue": "BOOKING_ENABLED is false for this tenant" }],
    "requestId": "...",
    "timestamp": "..."
  }
}
```

#### [Test Cases] 多租戶 TenantContext 驗證

| 編號 | 測試案例 | GIVEN | WHEN | THEN |
|------|---------|-------|------|------|
| TC-MT-001 | 單租戶用戶不傳 Header | UserA 僅隸屬 TenantA | `GET /api/v2/dashboard/orders`（無 X-Tenant-ID） | 系統自動使用 TenantA，正常返回資料 |
| TC-MT-002 | 多租戶用戶明確指定正確 Header | UserA 隸屬 TenantA 和 TenantB | `GET /api/v2/dashboard/orders` + `X-Tenant-ID: TenantA` | 僅返回 TenantA 的訂單 |
| TC-MT-003 | 多租戶用戶指定不在清單的 Header | UserA 隸屬 TenantA | `GET /api/v2/dashboard/orders` + `X-Tenant-ID: TenantC` | 403 `E-2002 CROSS_TENANT_ACCESS_DENIED` |
| TC-MT-004 | 多租戶用戶未指定 Header | UserA 隸屬 TenantA 和 TenantB | `GET /api/v2/dashboard/orders`（無 X-Tenant-ID） | 400 `E-2003 TENANT_CONTEXT_AMBIGUOUS` |
| TC-MT-005 | BOOKING_ENABLED=false 時嘗試建立房源 | TenantX 的 `BOOKING_ENABLED = false` | Host 嘗試 `POST /api/v2/dashboard/listings`（`listingType=ROOM`） | 403 `E-2020 FEATURE_DISABLED_FOR_TENANT`，無資料寫入 |

---

## 8. 資料模型 (Data Model / ERD)

### 8.1 資料表總覽（v0.9 完整版）

#### 8.1.1 Platform Infrastructure

| 資料表 | 模組 | 說明 | v0.9 |
|--------|------|------|------|
| `tenants` | M17 | 租戶/店鋪主表 | ★ 新增 |
| `tenant_feature_toggles` | M17 | 租戶功能開關 | ★ 新增 |
| `tenant_members` | M17 | 租戶成員（角色綁定） | ★ 新增 |
| `users` | M03 | 會員帳號主表 | 不變 |
| `user_profiles` | M03 | 會員資料延伸（完整欄位定義見 §8.3） | 不變 |
| `jwt_blacklist` | M03 | JWT 登出黑名單 | 不變 |
| `oauth_accounts` | M03 | OAuth2 帳號 (Phase 3) | 不變 |
| `kyc_applications` | M03 | KYC 申請記錄（Phase 2） | 不變 |
| `audit_logs` | M14 | 全站操作審計日誌 | 不變 |
| `admin_config` | M14 | 全站參數配置 | 不變 |
| `idempotency_keys` | 全域 | 冪等性 Key (TTL 24h) | 不變 |

#### 8.1.2 Listing & Commerce

| 資料表 | 模組 | 說明 | v0.9 |
|--------|------|------|------|
| `listings` | M01/M02 | 統一商品/服務抽象表 | ★ 新增 |
| `products` | M01 | 實體商品特化表 | ★ 改為關聯 listings |
| `product_skus` | M01 | SKU 規格表 | ★ 改為關聯 listings |
| `product_inventory` | M01/M16 | SKU 庫存 (與 ERP 連動) | ★ 新增 |
| `categories` | M01 | 商品分類（支援樹狀階層） | 不變 |
| `rooms` | M02 | 民宿房間特化表 | ★ 改為關聯 listings |
| `room_calendar` | M02/M06 | 日曆格 (取代 v0.8 `booking_slots`，新增動態價格) | ★ 新增 |
| `pricing_rules` | M12 | 動態定價規則 (吸收 v0.8 `seasonal_pricing`) | ★ 新增 |
| `bookings` | M06 | 民宿預訂主表 | 不變 |
| `orders` | M05 | 零售訂單主表 | 不變 |
| `order_items` | M05 | 零售訂單明細 | 不變 |
| `order_state_log` | M05 | 狀態機日誌 | 不變 |
| `cart_items` | M04 | 購物車 (Phase 2) | 不變 |
| `promotions` | M04 | 促銷活動 (Phase 2) | 不變 |

#### 8.1.3 Payment & Saga

| 資料表 | 模組 | 說明 | v0.9 |
|--------|------|------|------|
| `payments` | M07 | 金流交易記錄 | 不變 |
| `payment_splits` | M07 | 分帳記錄（平台抽成/賣家分潤） | 不變 |
| `refund_requests` | M07 | 退款請求與處理日誌 | 不變 |
| `saga_events` | M07 | Saga 步驟事件日誌（完整欄位定義見 §8.4） | 不變 |
| `compensation_log` | M07 | 補償行動日誌（完整欄位定義見 §8.5） | 不變 |

#### 8.1.4 CMS

| 資料表 | 模組 | 說明 | v0.9 |
|--------|------|------|------|
| `posts` | M15 | CMS 貼文主表 | ★ 新增 |
| `post_embeds` | M15 | 貼文嵌入卡片關聯 | ★ 新增 |
| `post_categories` | M15 | 貼文分類 | ★ 新增 |
| `media_assets` | M15 | 媒體庫資產 | ★ 新增 |

#### 8.1.5 ERP (進銷存)

| 資料表 | 模組 | 說明 | v0.9 |
|--------|------|------|------|
| `suppliers` | M16 | 供應商主表 | ★ 新增 |
| `purchase_orders` | M16 | 採購單主表 | ★ 新增 |
| `purchase_order_items` | M16 | 採購單明細 | ★ 新增 |
| `stock_movements` | M16 | 庫存異動記錄 (所有進出庫流水) | ★ 新增 |
| `settlement_statements` | M07 | 結算單 (每週結算) | ★ 新增 |

#### 8.1.6 Social & Communication (Phase 2+)

| 資料表 | 模組 |
|--------|------|
| `reviews` | M08 |
| `booking_reviews` | M08 |
| `notifications` | M09 |
| `notification_templates` | M09 |
| `chat_rooms` | M10 |
| `chat_messages` | M10 |
| `logistics_orders` | M11 |
| `logistics_tracking` | M11 |
| `revenue_reports` | M13 |

### 8.2 新增資料表欄位定義

#### 8.2.1 tenants

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| name | VARCHAR(100) | 店鋪名稱 (UNIQUE) |
| slug | VARCHAR(100) | URL slug (UNIQUE) |
| description | TEXT | 店鋪描述 |
| logo_url | VARCHAR(500) | 店鋪 Logo |
| owner_id | UUID | FK → users.id |
| status | ENUM | PENDING_REVIEW / ACTIVE / REJECTED / SUSPENDED / TERMINATED |
| business_type | ENUM | RETAIL_ONLY / BOOKING_ONLY / HYBRID |
| commission_rate | DECIMAL(4,3) | 平台抽成比例 (0.000 ~ 1.000) |
| contact_email | VARCHAR(200) | 聯絡 Email |
| contact_phone | VARCHAR(20) | 聯絡電話 |
| rejection_reason | TEXT | 駁回原因 |
| created_at | TIMESTAMP | 建立時間 |
| updated_at | TIMESTAMP | 更新時間 |

#### 8.2.2 tenant_feature_toggles

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| tenant_id | UUID | FK → tenants.id |
| feature_key | VARCHAR(50) | 功能鍵 (見 §4.4) |
| feature_value | VARCHAR(200) | 值 (true/false 或數值) |
| updated_by | UUID | FK → users.id (Admin) |
| updated_at | TIMESTAMP | 更新時間 |
| UNIQUE | (tenant_id, feature_key) | |

#### 8.2.3 tenant_members

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| tenant_id | UUID | FK → tenants.id |
| user_id | UUID | FK → users.id |
| role | ENUM | STORE_OWNER / STORE_STAFF / SELLER / HOST |
| invited_by | UUID | FK → users.id |
| status | ENUM | INVITED / ACTIVE / REMOVED |
| joined_at | TIMESTAMP | 加入時間 |
| UNIQUE | (tenant_id, user_id) | |

#### 8.2.4 posts (CMS)

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| tenant_id | UUID | FK → tenants.id |
| author_id | UUID | FK → users.id |
| title | VARCHAR(200) | 貼文標題 |
| slug | VARCHAR(200) | URL slug |
| content | TEXT | 貼文內容 (Markdown，含 `{{embed:...}}` 標記) |
| excerpt | VARCHAR(500) | 摘要 |
| cover_image_url | VARCHAR(500) | 封面圖 |
| category_id | UUID | FK → post_categories.id |
| status | ENUM | DRAFT / SCHEDULED / PUBLISHED / ARCHIVED |
| scheduled_at | TIMESTAMP | 排程發布時間 |
| published_at | TIMESTAMP | 實際發布時間 |
| seo_title | VARCHAR(70) | SEO 標題 |
| seo_description | VARCHAR(160) | SEO 描述 |
| view_count | INTEGER DEFAULT 0 | 瀏覽次數 |
| created_at | TIMESTAMP | 建立時間 |
| updated_at | TIMESTAMP | 更新時間 |
| INDEX | (tenant_id, status, published_at DESC) | |

#### 8.2.5 post_embeds

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| post_id | UUID | FK → posts.id |
| listing_id | UUID | FK → listings.id, **ON DELETE CASCADE** |
| embed_type | ENUM | PRODUCT_CARD / ROOM_CARD |
| position | INTEGER | 在貼文中的位置順序 |
| UNIQUE | (post_id, listing_id) | 同一貼文不重複嵌入 |
| **tenant_id 約束** | UUID | FK -> tenants.id, NOT NULL, INDEX | 間接隔離（透過 posts.tenant_id） |

#### 8.2.6 media_assets

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| tenant_id | UUID | FK → tenants.id |
| uploader_id | UUID | FK → users.id |
| file_name | VARCHAR(200) | 原始檔名 |
| file_url | VARCHAR(500) | 儲存路徑 (S3: `/{tenant_id}/media/{id}.ext`) |
| file_type | ENUM | IMAGE / VIDEO / DOCUMENT |
| mime_type | VARCHAR(50) | MIME 類型 |
| file_size_bytes | BIGINT | 檔案大小 |
| alt_text | VARCHAR(200) | 替代文字 (Accessibility) |
| created_at | TIMESTAMP | 上傳時間 |
| INDEX | (tenant_id, file_type) | |

#### 8.2.7 suppliers (ERP)

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| tenant_id | UUID | FK → tenants.id |
| name | VARCHAR(100) | 供應商名稱 |
| contact_name | VARCHAR(50) | 聯絡人 |
| contact_phone | VARCHAR(20) | 電話 |
| contact_email | VARCHAR(200) | Email |
| address | TEXT | 地址 |
| status | ENUM | ACTIVE / INACTIVE |
| created_at | TIMESTAMP | 建立時間 |
| INDEX | (tenant_id) | |

#### 8.2.8 purchase_orders (ERP)

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| tenant_id | UUID | FK → tenants.id |
| po_number | VARCHAR(30) | 採購單號 (Tenant 內唯一) |
| supplier_id | UUID | FK → suppliers.id |
| status | ENUM | DRAFT / SUBMITTED / PARTIAL_RECEIVED / RECEIVED / CANCELLED |
| total_amount | DECIMAL(12,2) | 採購總額 |
| currency | VARCHAR(3) DEFAULT 'TWD' | 幣別 |
| notes | TEXT | 備註 |
| ordered_at | TIMESTAMP | 下單時間 |
| expected_at | DATE | 預計到貨日 |
| received_at | TIMESTAMP | 實際收貨時間 |

#### 8.2.9 settlement_statements（結算單 — v0.9 新增）

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| tenant_id | UUID | FK → tenants.id |
| statement_number | VARCHAR(30) | 結算單號 (Tenant 內唯一，取代 v0.9_R02 前錯誤的 `po_number`) |
| period_start | DATE | 結算週期開始日期 |
| period_end | DATE | 結算週期結束日期 |
| total_orders | INTEGER | 結算週期內已完成訂單數 |
| total_gmv | DECIMAL(14,2) | 結算週期內總 GMV |
| total_refunds | DECIMAL(14,2) | 結算週期內退款總金額 |
| commission_amount | DECIMAL(14,2) | 平台抽成金額 |
| net_settlement_amount | DECIMAL(14,2) | 商家應結算金額 |
| currency | VARCHAR(3) DEFAULT 'TWD' | 幣別 |
| status | ENUM | PENDING / PENDING_REVIEW / APPROVED / REJECTED / PAID / FAILED |
| generated_at | TIMESTAMP | 結算單生成時間 |
| reviewed_at | TIMESTAMP | Admin 審核時間 |
| reviewed_by | UUID | FK → users.id（Admin） |
| rejection_reason | TEXT | 審核駁回原因（當 status = REJECTED 時填寫） |
| approved_at | TIMESTAMP | 審核通過時間 |
| paid_at | TIMESTAMP | 實際付款時間 |
| notes | TEXT | 備註 |
| created_by | UUID | FK → users.id |
| created_at | TIMESTAMP | 建立時間 |
| updated_at | TIMESTAMP | 更新時間 |
| INDEX | (tenant_id, period_start DESC) | |
| UNIQUE | (tenant_id, statement_number) | |

> **v0.9_R02_Loop_01 schema 修正說明**：`statement_number` 取代原 v0.9 的 `po_number`（複製錯誤）；`status` ENUM 新增 `PENDING_REVIEW` 與 `REJECTED`；`rejection_reason` 欄位與 `REJECTED` 狀態對應（原 v0.9 有欄位無狀態）；`reviewed_by` 新增；`UNIQUE` 約束修正為 `(tenant_id, statement_number)`。

#### 8.2.10 credit_notes（結算單沖銷 — v0.9_R02_Loop_02 新增）

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| tenant_id | UUID | FK → tenants.id, NOT NULL |
| original_statement_id | UUID | FK → settlement_statements.id（被沖銷的原結算單） |
| credit_note_number | VARCHAR(30) | 貸項通知單號（Tenant 內唯一） |
| type | ENUM | CREDIT_NOTE（固定） |
| amount | DECIMAL(14,2) | 沖銷金額（負值） |
| reason | TEXT | 沖銷原因 |
| issued_by | UUID | FK → users.id |
| issued_at | TIMESTAMP | 開立時間 |
| status | ENUM | PENDING / APPLIED |
| INDEX | (tenant_id, issued_at DESC) | |
| UNIQUE | (tenant_id, credit_note_number) | |

#### 8.2.11 purchase_order_items

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| purchase_order_id | UUID | FK → purchase_orders.id |
| sku_id | UUID | FK → product_skus.id |
| ordered_qty | INTEGER | 訂購數量 |
| received_qty | INTEGER DEFAULT 0 | 已收貨數量 |
| unit_cost | DECIMAL(12,2) | 進貨單價 |
| subtotal | DECIMAL(12,2) | 小計 |

#### 8.2.10 stock_movements (ERP)

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | PK |
| tenant_id | UUID | FK → tenants.id |
| sku_id | UUID | FK → product_skus.id |
| order_item_id | UUID | FK → order_items.id (nullable) — **Phase 1 為 NULL，Phase 2 多 SKU 訂單需關聯此欄位** |
| movement_type | ENUM | INBOUND / OUTBOUND / RESERVE / RELEASE / ADJUST_PLUS / ADJUST_MINUS / TRANSFER_OUT / TRANSFER_IN / SCRAP |
| quantity | INTEGER | 異動數量 (正值) |
| reference_type | VARCHAR(30) | 關聯單據類型: PURCHASE_ORDER / ORDER / MANUAL / STOCKTAKE |
| reference_id | UUID | 關聯單據 ID |
| before_total_qty | INTEGER | 異動前總庫存 |
| after_total_qty | INTEGER | 異動後總庫存 |
| notes | TEXT | 備註 |
| operated_by | UUID | FK → users.id |
| created_at | TIMESTAMP | |
| INDEX | (tenant_id, sku_id, created_at DESC) | |

> **Phase 1 假設**：Phase 1 每筆訂單僅包含一個 SKU，故一個 OUTBOUND movement 對應一個 order_item。`order_item_id` 欄位 Phase 1 為 NULL，保留供 Phase 2 多 SKU 訂單拆單追蹤使用。

### 8.3 繼承 v0.8 的欄位定義

#### [Specification] user_profiles 完整欄位定義

| 欄位 | 類型 | 說明 |
|------|------|------|
| user_id | UUID | 主鍵，關聯 users.id |
| phone | VARCHAR(20) | 電話號碼 |
| avatar_url | VARCHAR(500) | 大頭貼 URL |
| bio | TEXT | 自我介紹 |
| address | TEXT | 收貨地址 |
| date_of_birth | DATE | 出生日期 |
| **real_name** | VARCHAR(100) | 真實姓名（KYC 用） |
| **id_number** | VARCHAR(20) | 身分證字號（KYC 用，加密儲存） |
| **id_card_front_url** | VARCHAR(500) | 身分證正面圖片 URL（KYC 用） |
| **id_card_back_url** | VARCHAR(500) | 身分證背面圖片 URL（KYC 用） |
| **kyc_status** | ENUM | KYC 審核狀態：`PENDING / APPROVED / REJECTED` |
| **kyc_submitted_at** | TIMESTAMP | KYC 提交時間 |
| **kyc_reviewed_at** | TIMESTAMP | KYC 審核時間 |
| **kyc_rejection_reason** | TEXT | 駁回原因 |
| updated_at | TIMESTAMP | 更新時間 |

### 8.4 saga_events 欄位定義（繼承 v0.8）

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | BIGSERIAL | 主鍵 |
| saga_id | UUID | 所屬 Saga 實例 ID（跨所有步驟） |
| step_name | VARCHAR(100) | 步驟名稱（如 `reserve-inventory`、`charge-payment`） |
| status | ENUM | `STARTED / COMPLETED / FAILED / COMPENSATING / COMPENSATED` |
| payload | JSONB | 步驟輸入資料 |
| result | JSONB | 步驟執行結果 |
| error_message | TEXT | 失敗時的錯誤訊息 |
| created_at | TIMESTAMP | 執行時間 |

### 8.5 compensation_log 欄位定義（繼承 v0.8）

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | BIGSERIAL | 主鍵 |
| saga_id | UUID | 所屬 Saga 實例 ID |
| step_name | VARCHAR(100) | 被補償的原始步驟 |
| action | VARCHAR(100) | 補償動作（如 `release-slot`、`restore-stock`） |
| status | ENUM | `SUCCESS / FAILED` |
| executed_at | TIMESTAMP | 補償執行時間 |
| details | JSONB | 補償細節（釋放了哪些資源） |

> **關鍵設計**：`room_calendar` 必須以 `(room_listing_id, calendar_date)` 作為 Unique Key，杜絕 Overbooking。
> **現有遷移腳本**：V1~V4 已涵蓋 Phase 1~2 所需資料表；`saga_events`、`compensation_log`、`oauth_accounts`、`kyc_applications` 等表待 Phase 2 實作時新增 Migration 檔案。

### 8.6 ERD 關係描述

```
tenants ──1:N──→ tenant_feature_toggles
tenants ──1:N──→ tenant_members
tenants ──1:N──→ listings
tenants ──1:N──→ posts
tenants ──1:N──→ media_assets
tenants ──1:N──→ suppliers
tenants ──1:N──→ purchase_orders
tenants ──1:N──→ stock_movements
tenants ──1:N──→ pricing_rules
tenants ──1:N──→ orders

users ──1:N──→ tenant_members (一個用戶可加入多個店鋪)
users ──1:1──→ user_profiles

listings ──1:0..1──→ products (PRODUCT 類型)
listings ──1:0..1──→ rooms (ROOM 類型)
listings ──1:N──→ product_skus (透過 products)
listings ──1:N──→ room_calendar (透過 rooms)
listings ──1:N──→ post_embeds

product_skus ──1:1──→ product_inventory
product_skus ──1:N──→ stock_movements
product_skus ──1:N──→ purchase_order_items

rooms ──1:N──→ room_calendar
room_calendar ──N:1──→ bookings

posts ──1:N──→ post_embeds
post_embeds ──N:1──→ listings

purchase_orders ──1:N──→ purchase_order_items
purchase_orders ──N:1──→ suppliers

orders ──N:1──→ tenants
orders ──1:N──→ order_items
orders ──1:N──→ order_state_log
```

### 8.7 自我糾錯檢核 SC-002

**檢核：多租戶架構下，各「網友店鋪」的圖片庫與進銷存資料是否確保了邏輯隔離？**

✅ **圖片庫隔離**：`media_assets.tenant_id` 欄位 + S3 路徑按 `/{tenant_id}/media/` 分桶，Repository 自動附加 tenant_id 過濾。

✅ **進銷存隔離**：`stock_movements.tenant_id`、`purchase_orders.tenant_id`、`suppliers.tenant_id` 均有 NOT NULL 約束 + 複合索引，Hibernate Filter 自動注入。

✅ **跨租戶防護**：所有 B 端 API (dashboard/*) 強制從 TenantContext 取得 tenant_id，拒絕客戶端傳入 tenant_id 參數。

---

## 9. API 設計

### 9.1 API 版本策略

v0.9 新增 API 統一使用 `/api/v2/` 前綴，v0.8 既有 API 保持 `/api/v1/` 不變。

### 9.2 M03 會員與權限系統（完整 API，含 Phase 2）（繼承 v0.8 §7.1）

| 方法 | 端點 | 說明 | Phase |
|------|------|------|-------|
| `POST` | `/api/auth/register` | 會員註冊 | Phase 1 |
| `POST` | `/api/auth/login` | 會員登入（回傳 JWT + Refresh Token） | Phase 1 |
| `POST` | `/api/auth/refresh` | 刷新 Access Token | Phase 1 |
| `POST` | `/api/auth/logout` | 登出（JWT 加入黑名單） | Phase 1 |
| `GET/PUT` | `/api/users/me` | 取得/更新個人資料 | Phase 1 |
| `POST` | `/api/auth/kyc/start` | 開始 KYC 流程（上傳證件） | Phase 2 |
| `GET` | `/api/auth/kyc/status` | 查詢 KYC 審核狀態 | Phase 2 |
| `GET` | `/api/auth/oauth/:provider` | OAuth2 第三方登入（redirect） | Phase 2 |
| `POST` | `/api/auth/oauth/:provider/callback` | OAuth2 Callback（綁定或新建帳號） | Phase 2 |

### 9.3 M01 商品中心（繼承 v0.8 §7.2）

| 方法 | 端點 | 說明 | Phase |
|------|------|------|-------|
| `GET` | `/api/products` | 商品列表（分頁/篩選/搜尋） | Phase 1 |
| `GET` | `/api/products/:id` | 商品詳情 | Phase 1 |
| `POST` | `/api/products` | 新增商品（僅 Seller/Admin） | Phase 2 |
| `PUT/DELETE` | `/api/products/:id` | 更新/刪除商品 | Phase 2 |

### 9.4 M02 房源中心（繼承 v0.8 §7.3）

| 方法 | 端點 | 說明 | Phase |
|------|------|------|-------|
| `GET` | `/api/rooms` | 房間列表（分頁/地區/日期/人數篩選） | Phase 1 |
| `GET` | `/api/rooms/:id` | 房間詳情（含可用日曆） | Phase 1 |
| `POST` | `/api/rooms` | 新增房間（僅 Host/Admin） | Phase 2 |
| `PUT/DELETE` | `/api/rooms/:id` | 更新/刪除房間 | Phase 2 |
| `GET` | `/api/rooms/:id/slots` | 查詢指定房間的日期格（可用/已預訂） | Phase 1 |
| `GET` | `/api/rooms/nearby` | LBS 附近房源搜尋 | Phase 2+（需 PostGIS） |

### 9.5 M04 購物車（繼承 v0.8 §7.4）

| 方法 | 端點 | 說明 | Phase |
|------|------|------|-------|
| `GET` | `/api/cart` | 取得當前購物車 | Phase 2 |
| `POST` | `/api/cart/items` | 加入商品到購物車 | Phase 2 |
| `PUT` | `/api/cart/items/:id` | 更新購物車項目數量 | Phase 2 |
| `DELETE` | `/api/cart/items/:id` | 移除購物車項目 | Phase 2 |
| `DELETE` | `/api/cart` | 清空購物車 | Phase 2 |

### 9.5.1 M04 促銷模組 API（v0.9 新增）

| 方法 | 端點 | 說明 | 角色 |
|------|------|------|------|
| `GET` | `/api/v2/dashboard/promotions` | 促銷活動列表 | Seller/StoreOwner |
| `POST` | `/api/v2/dashboard/promotions` | 建立促銷活動 | Seller/StoreOwner |
| `PUT` | `/api/v2/dashboard/promotions/:id` | 更新促銷活動 | Seller/StoreOwner |
| `DELETE` | `/api/v2/dashboard/promotions/:id` | 刪除促銷活動 | StoreOwner |

> **promoCode 驗證邏輯**：M05 訂單建立時，系統在校驗 `totalAmount` 之後、寫入訂單之前，依以下順序驗證促銷碼：
> 1. 檢查促銷碼是否存在且狀態為 ACTIVE
> 2. 檢查是否在有效時間範圍內（valid_from ~ valid_to）
> 3. 檢查是否已達使用上限（max_uses）
> 4. 套用折扣（按 discount_type 計算）：PERCENTAGE（百分比折扣）或 FIXED_AMOUNT（固定金額折扣）
> 5. 折扣後金額不得為負

### 9.6 M05 零售訂單履約（Phase 1 最小子集）（v1.0 更新）

| 方法 | 端點 | 說明 | Phase |
|------|------|------|-------|
| `POST` | `/api/v2/orders` | 建立訂單（含冪等性 Key，**Phase 1 Payment Mock 直接推進狀態至 CREATED(=PAID)**） | **Phase 1** |
| `GET` | `/api/v2/orders` | 買家/商家訂單列表（**Seller 可透過 `?merchantId={id}` 查詢店鋪訂單**；Buyer 查詢本人訂單） | **Phase 1** |
| `GET` | `/api/v2/orders/:id` | 訂單詳情 | **Phase 1** |
| `PUT` | `/api/v2/orders/:id/cancel` | 取消訂單（觸發狀態機，Phase 1 以 CREATED(=PAID) 為主要取消觸發點） | **Phase 1** |
| `GET` | `/api/v2/orders/:id/state-log` | 查詢狀態機變更日誌 | **Phase 1** |

> **注意**：Phase 1 訂單僅支援單一類型（`RETAIL` 或 `BOOKING`），禁止混合訂單。支付環節為 Payment Mock，不串接真實金流。

### 9.7 M06 民宿預訂（v1.0 更新）

> **重要說明**：Phase 1 的 M06 **僅開放查詢與取消**，不含 POST 建立預訂。建立預訂（`POST /api/v2/bookings`）屬 Phase 2 範圍。

| 方法 | 端點 | 說明 | Phase |
|------|------|------|-------|
| `GET` | `/api/v2/bookings` | 買家預訂列表 | **Phase 1** |
| `GET` | `/api/v2/bookings/:id` | 預訂詳情 | **Phase 1** |
| `PUT` | `/api/v2/bookings/:id/cancel` | 取消預訂（釋放日期格） | **Phase 1** |
| `GET` | `/api/v2/bookings/:id/state-log` | 查詢預訂狀態機日誌 | **Phase 1** |
| `POST` | `/api/v2/bookings` | 建立預訂（含日期格鎖定，冪等性 Key） | **Phase 2** |
| `PUT` | `/api/v2/bookings/:id` | 修改已確認的預訂（時間、地點等） | **Phase 2** |
| `POST` | `/api/v2/bookings/:id/remind` | 設定/發送預訂提醒 | **Phase 2** |

> **注意**：支付模擬化（Payment Mock）同 M05。

### 9.8 M07 金流（繼承 v0.8 §7.7）

| 方法 | 端點 | 說明 | Phase |
|------|------|------|-------|
| `POST` | `/api/payments/initiate` | 發起支付（建立 Payment 紀錄） | Phase 2 |
| `POST` | `/api/payments/confirm` | 支付確認 callback | Phase 2 |
| `POST` | `/api/payments/refund` | 申請退款 | Phase 2 |
| `GET` | `/api/payments/:id` | 查詢支付狀態 | Phase 2 |
| `GET` | `/api/payments/splits/:orderId` | 查詢分帳紀錄 | Phase 2 |
| `GET` | `/api/saga/events` | 查詢 Saga 事件日誌（Admin）。Saga Pattern 支撐 M05（零售訂單）/ M06（民宿預訂）/ M07（金流）三模組的跨服務一致性。 | Phase 2 |

### 9.9 M08~M14 摘要（繼承 v0.8 §7.8）

| 群組 | 前綴 | 對應模組 | Phase |
|------|------|---------|-------|
| Reviews | `GET/POST/PUT/DELETE /api/reviews/*` | M08 | P2 |
| Notifications | `GET /api/notifications/*` | M09 | Phase 2 |
| Messages | `GET/POST /api/messages/*` | M10 | Phase 2 |
| Logistics | `GET/POST /api/logistics/*` | M11 | Phase 2 |
| Pricing | `GET /api/pricing/*` | M12 | Phase 2 |
| SellerDashboard | `GET /api/seller/*` | M13 | Phase 2 |
| HostDashboard | `GET /api/host/*` | M13 | Phase 2 |
| Admin | `GET/POST/PUT/DELETE /api/admin/*` | M14 | Phase 2 |

### 9.10 M17 租戶管理 API（v1.0 更新）

#### 9.10.1 開店申請表單欄位（v1.0 新增）

`POST /api/v2/tenants/apply` 申請表單欄位：

| 欄位 | 類型 | 必填 | 說明 |
|------|------|------|------|
| storeName | String | ✅ | 店鋪名稱 |
| storeDescription | String | ❌ | 店鋪描述 |
| businessType | Enum | ✅ | RETAIL_ONLY / BOOKING_ONLY / HYBRID |
| contactEmail | String | ✅ | 聯絡 Email |
| contactPhone | String | ❌ | 聯絡電話 |
| businessLicenseUrl | String | ❌ | 營業執照 URL（Phase 2） |
| idNumber | String | ❌ | 身分證字號（Phase 2 KYC） |

#### 9.10.2 租戶管理 API

| 方法 | 端點 | 說明 | 角色 |
|------|------|------|------|
| `POST` | `/api/v2/tenants/apply` | 網友申請開店（欄位定義見 §9.10.1） | Buyer+ |
| `GET` | `/api/v2/tenants/my` | 查詢我的店鋪列表 | StoreOwner |
| `GET` | `/api/v2/tenants/:id` | 查詢店鋪詳情 | StoreOwner/Admin |
| `PUT` | `/api/v2/tenants/:id` | 更新店鋪 Profile | StoreOwner |
| `GET` | `/api/v2/admin/tenants` | (Admin) 租戶列表 | Admin |
| `PUT` | `/api/v2/admin/tenants/:id/review` | (Admin) 審核開店申請 | Admin |
| `PUT` | `/api/v2/admin/tenants/:id/status` | (Admin) 暫停/恢復/終止 | Admin |
| `GET` | `/api/v2/admin/tenants/:id/features` | (Admin) 查詢租戶 Feature Toggle | Admin |
| `PUT` | `/api/v2/admin/tenants/:id/features` | (Admin) 更新 Feature Toggle | Admin |

### 9.11 M17 租戶成員 API（v0.9 新增）

| 方法 | 端點 | 說明 | 角色 |
|------|------|------|------|
| `POST` | `/api/v2/tenants/:id/members/invite` | 邀請成員加入店鋪 | StoreOwner |
| `GET` | `/api/v2/tenants/:id/members` | 查詢店鋪成員列表 | StoreOwner/Admin |
| `PUT` | `/api/v2/tenants/:id/members/:userId/role` | 更新成員角色 | StoreOwner |
| `DELETE` | `/api/v2/tenants/:id/members/:userId` | 移除成員 | StoreOwner |

### 9.12 統一 Listing API（v0.9 新增）

| 方法 | 端點 | 說明 | 角色 |
|------|------|------|------|
| `GET` | `/api/v2/listings` | 前台商品/房源統一搜尋（跨租戶） | Guest+ |
| `GET` | `/api/v2/listings/:id` | Listing 詳情（含 Product/Room 特化） | Guest+ |
| `GET` | `/api/v2/listings/:id/card` | 輕量嵌入卡片資料（CMS 用） | Guest+ |
| `POST` | `/api/v2/dashboard/listings` | 店鋪後台：建立 Listing | Seller/Host/StoreOwner |
| `PUT` | `/api/v2/dashboard/listings/:id` | 店鋪後台：更新 Listing | Seller/Host/StoreOwner |
| `DELETE` | `/api/v2/dashboard/listings/:id` | 店鋪後台：刪除 Listing | StoreOwner |
| `GET` | `/api/v2/dashboard/listings` | 店鋪後台：我的 Listing 列表 | Seller/Host/StoreOwner |
| `POST` | `/api/v2/dashboard/listings/:id/images` | 上傳 Listing 圖片（復用 M15 媒體上傳端點） | Seller/Host/StoreOwner |
| `DELETE` | `/api/v2/dashboard/listings/:id/images/:imageId` | 刪除 Listing 圖片 | StoreOwner |

### 9.13 M12 動態定價 API（v0.9 新增）

| 方法 | 端點 | 說明 | 角色 |
|------|------|------|------|
| `GET` | `/api/v2/rooms/:id/pricing-calendar` | 查詢房間未來 N 天定價日曆 (C 端) | Guest+ |
| `GET` | `/api/v2/dashboard/pricing-rules` | 查詢我的定價規則 | Host/StoreOwner |
| `POST` | `/api/v2/dashboard/pricing-rules` | 建立定價規則 | Host/StoreOwner |

> **後置條件**：系統在校驗新規則寫入前，統計同一 `room_listing_id` 的 `is_active = true` 規則數量；若已達 50 條上限，回傳 `E-4001 RULE_LIMIT_EXCEEDED`，`message: "每間房的定價規則上限為 50 條，請先停用舊規則後再新增"`，不回寫資料庫。
| `PUT` | `/api/v2/dashboard/pricing-rules/:id` | 更新定價規則 | Host/StoreOwner |
| `DELETE` | `/api/v2/dashboard/pricing-rules/:id` | 刪除定價規則 | StoreOwner |
| `POST` | `/api/v2/dashboard/rooms/:id/pricing-preview` | 預覽未來 90 天計算後價格 | Host/StoreOwner |
| `POST` | `/api/v2/dashboard/rooms/:id/calendar-override` | 手動覆蓋特定日期價格 | Host/StoreOwner |

### 9.14 Payment Mock API 實作細節（v1.0 新增）

> **說明**：Phase 1 支付環節為 Mock，需明確定義實作方式。

| 項目 | 內容 |
|------|------|
| Mock 模式 | 系統自動將訂單狀態推進至 CREATED(=PAID) |
| Mock 回應格式 | `{ "paymentId": "mock_xxx", "status": "SUCCESS", "mock": true }` |
| 模擬失敗 | 透過 `X-Mock-Fail: true` Header 模擬支付失敗（用於測試） |
| Phase 1 退款 | 不支援，始終回傳 E-4013 |

> **Phase 1 退款行為**：Phase 1 退款功能不支援。當買家嘗試申請退款時，系統回傳錯誤碼 `E-4013`，訊息：「退款功能尚未開放，請聯繫客服」。

### 9.15 M15 CMS API（v0.9 新增）

| 方法 | 端點 | 說明 | 角色 |
|------|------|------|------|
| `GET` | `/api/v2/posts` | 前台：公開貼文列表 | Guest+ |
| `GET` | `/api/v2/posts/:slug` | 前台：貼文詳情 (含解析嵌入卡片) | Guest+ |
| `GET` | `/api/v2/posts/categories` | 貼文分類列表 | Guest+ |
| `POST` | `/api/v2/dashboard/posts` | 店鋪後台：建立貼文 | Seller/Host/StoreOwner |
| `PUT` | `/api/v2/dashboard/posts/:id` | 店鋪後台：更新貼文 | Seller/Host/StoreOwner |
| `DELETE` | `/api/v2/dashboard/posts/:id` | 店鋪後台：刪除貼文 | StoreOwner |
| `PUT` | `/api/v2/dashboard/posts/:id/publish` | 發布/排程發布 | Seller/Host/StoreOwner |
| `POST` | `/api/v2/dashboard/media` | 上傳媒體 | Seller/Host/StoreOwner |
| `GET` | `/api/v2/dashboard/media` | 媒體庫列表 | Seller/Host/StoreOwner |
| `DELETE` | `/api/v2/dashboard/media/:id` | 刪除媒體 | StoreOwner |

### 9.15 M16 ERP 進銷存 API（v0.9 新增）

| 方法 | 端點 | 說明 | 角色 |
|------|------|------|------|
| `GET` | `/api/v2/dashboard/inventory` | 庫存台帳（按 SKU 列表） | Seller/StoreOwner |
| `GET` | `/api/v2/dashboard/inventory/:skuId` | 單一 SKU 庫存詳情 + 異動記錄 | Seller/StoreOwner |
| `GET` | `/api/v2/dashboard/inventory/alerts` | 低庫存預警列表 | Seller/StoreOwner |
| `POST` | `/api/v2/dashboard/purchase-orders` | 建立採購單（狀態：DRAFT） | Seller/StoreOwner |
| `GET` | `/api/v2/dashboard/purchase-orders` | 採購單列表 | Seller/StoreOwner |
| `GET` | `/api/v2/dashboard/purchase-orders/:id` | 採購單詳情 | Seller/StoreOwner |
| `PUT` | `/api/v2/dashboard/purchase-orders/:id` | 更新採購單（僅限 DRAFT 狀態） | Seller/StoreOwner |
| `PUT` | `/api/v2/dashboard/purchase-orders/:id/submit` | 提交採購單（觸發 DRAFT → SUBMITTED） | Seller/StoreOwner |
| `PUT` | `/api/v2/dashboard/purchase-orders/:id/receive` | 確認收貨（觸發 SUBMITTED → RECEIVED/PARTIAL_RECEIVED，StockMovement INBOUND） | Seller/StoreOwner |
| `PUT` | `/api/v2/dashboard/purchase-orders/:id/cancel` | 取消採購單（v0.9_R02_Loop_01 新增；限定 DRAFT / SUBMITTED 狀態；PARTIAL_RECEIVED / RECEIVED 不可取消） | Seller/StoreOwner |

> **取消不可場景（v0.9_R02_Loop_02 新增）**：`PARTIAL_RECEIVED` 不可直接取消，需先完成所有收貨後再走 RECEIVED 流程或走獨立退貨流程。
> **取消後庫存處理**：若部分品項已 `received_qty > 0`，`product_inventory.total_qty` 不回滾（已入庫貨物保留），`purchase_order_items.received_qty` 保留作為歷史。
> **取消後通知**：M09 未上線前，於 StoreOwner Dashboard 顯示警告：「請手動通知供應商取消採購」。

| `PUT` | `/api/v2/dashboard/purchase-orders/:id/receive` | 確認收貨（觸發 SUBMITTED -> RECEIVED/PARTIAL_RECEIVED，StockMovement INBOUND） | Seller/StoreOwner |

> **Transaction Atomic 保障（v0.9_R02_Loop_02 新增）**：`StockMovement (INBOUND)` 寫入與 `product_inventory.total_qty += inbound_qty` 必須在同一 DB Transaction 內完成（Spring `@Transactional`）。若失敗，整個收貨操作完整回滾，`purchase_orders.status` 保持 `SUBMITTED`，不回寫任何庫存。
| `POST` | `/api/v2/dashboard/stock-movements` | 手動庫存異動（調整/報廢/調撥） | StoreOwner |
| `GET` | `/api/v2/dashboard/stock-movements` | 庫存異動記錄查詢 | Seller/StoreOwner |

> **Phase 1 `stock_movements.order_item_id` 均為 `null`**：API Response 中該欄位顯示為 `null`，前端渲染為「—」。
| `GET` | `/api/v2/dashboard/suppliers` | 供應商列表 | Seller/StoreOwner |
| `POST` | `/api/v2/dashboard/suppliers` | 新增供應商 | Seller/StoreOwner |
| `PUT` | `/api/v2/dashboard/suppliers/:id` | 更新供應商 | Seller/StoreOwner |

### 9.16 店鋪後台統一 Dashboard API（v0.9 新增）

| 方法 | 端點 | 說明 | 角色 |
|------|------|------|------|
| `GET` | `/api/v2/dashboard/overview` | 店鋪總覽（營收/訂單/庫存摘要） | StoreOwner/StoreStaff |
| `GET` | `/api/v2/dashboard/orders` | 店鋪訂單列表 | StoreOwner/StoreStaff |
| `GET` | `/api/v2/dashboard/bookings` | 店鋪預訂列表 | StoreOwner/StoreStaff |
| `GET` | `/api/v2/dashboard/revenue` | 營收報表 | StoreOwner |

### 9.17 全域 API 規範（繼承 v0.8 §7.9 + v0.9 擴展 + Loop 1 修正）

| 規則 | 說明 |
|------|------|
| `X-Idempotency-Key` | 所有 POST/PUT/DELETE 必帶，24h Redis 攔截 |
| `X-Tenant-ID` | **B 端 API（`/api/v2/dashboard/*`）必須攜帶**。單租戶用戶：系統自動使用唯一 Tenant ID（向後相容）；多租戶用戶：**必須**明確指定，否則回傳 `E-2003 TENANT_CONTEXT_AMBIGUOUS`（400）；不存在的 tenant_id 回傳 `E-2002 CROSS_TENANT_ACCESS_DENIED`（403）。C 端 API（`/api/v2/listings` 等）不需此 Header，跨租戶查詢不受限制。 |
| 錯誤格式 | `{ "error": { "code": "E-XXXX", "message": "...", "details": [...], "requestId": "...", "timestamp": "..." } }` |
| 分頁格式 | `?page=0&size=20&sort=createdAt,desc` |
| API 前綴 | 新增 API: `/api/v2/*`；v0.8 既有: `/api/v1/*` |

---

## 10. 前端路由

### 10.1 路由總覽（v0.9 完整版）

#### 10.1.1 C 端前台路由（繼承 v0.8 §6 + v0.9 擴展）

| 路由 | 對應模組 | 說明 | v0.9 |
|------|---------|------|------|
| `/` | — | 首頁（精選商品 + 熱門房型） | 不變 |
| `/products` | M01 | 商品列表（搜尋/篩選/分頁） | 不變 |
| `/products/[id]` | M01 | 商品詳情頁 | 不變 |
| `/rooms` | M02 | 民宿列表（地區/日期/人數，Phase 2+ 加入 LBS 地圖） | 不變 |
| `/rooms/[id]` | M02（**Phase 1**）/M12（**Phase 2-A**） | 房間詳情頁<br>**Phase 1**：M02 房源詳情頁，**靜態定價**——顯示 `listing.base_price`，不顯示動態計算後的每日價格<br>**Phase 2-A**：增加動態定價日曆模組（M12），顯示計算後的每日價格 | 不變 |
| `/cart` | M04 | 購物車（Phase 2） | 不變 |
| `/checkout` | M05/M07 | **結帳頁（Phase 1 為 Payment Mock 頁面）**——按鈕文案：「確認模擬支付」；Phase 1 直接觸發 `POST /api/orders` 並由 Payment Mock 將狀態推進至 `CREATED(=PAID)`，不串接真實金流。**Phase 2-B**：串接真實金流 API，按鈕文案改為「立即付款」。 | 不變 |
| `/orders` | M05 | 我的訂單（零售） | 不變 |
| `/orders/[id]` | M05/M11 | 訂單詳情（含物流追蹤） | 不變 |
| `/bookings` | M06 | 我的預訂（民宿） | 不變 |
| `/bookings/[id]` | M06 | 預訂詳情 | 不變 |
| `/auth/login` | M03 | 登入 | 不變 |
| `/auth/register` | M03 | 註冊 | 不變 |
| `/auth/profile` | M03 | 個人資料 | 不變 |
| `/auth/kyc` | M03 | KYC 實名認證（Phase 2） | 不變 |
| `/auth/oauth/:provider` | M03 | OAuth2 第三方登入（Phase 2，provider: google/line） | 不變 |
| `/messages` | M10 | 即時通訊（Phase 2） | 不變 |
| `/notifications` | M09 | 通知中心（Phase 2） | 不變 |
| **`/blog`** | **M15** | **CMS 貼文列表** | ★ 新增 |
| **`/blog/[slug]`** | **M15** | **貼文詳情（含嵌入購買卡片）** | ★ 新增 |
| **`/blog/category/[id]`** | **M15** | **貼文分類篩選** | ★ 新增 |
| **`/stores/[slug]`** | **M17** | **店鋪前台首頁** | ★ 新增 |
| **`/stores/[slug]/products`** | **M17/M01** | **店鋪商品列表** | ★ 新增 |
| **`/stores/[slug]/rooms`** | **M17/M02** | **店鋪房源列表** | ★ 新增 |
| **`/stores/[slug]/blog`** | **M17/M15** | **店鋪貼文列表** | ★ 新增 |

#### 10.1.2 B 端店鋪後台路由（v0.9 新增）

| 路由 | 對應模組 | 說明 |
|------|---------|------|
| `/dashboard` | M13/M17 | 店鋪總覽（營收/訂單/庫存摘要） |
| `/dashboard/listings` | M01/M02 | 商品/房源管理 |
| `/dashboard/listings/new` | M01/M02 | 新增 Listing |
| `/dashboard/listings/[id]/edit` | M01/M02 | 編輯 Listing |
| `/dashboard/orders` | M05 | 訂單管理 |
| `/dashboard/bookings` | M06 | 預訂管理 |
| `/dashboard/inventory` | M16 | 庫存台帳 |
| `/dashboard/purchase-orders` | M16 | 採購單管理 |
| `/dashboard/purchase-orders/new` | M16 | 新增採購單 |
| `/dashboard/suppliers` | M16 | 供應商管理 |
| `/dashboard/posts` | M15 | CMS 貼文管理 |
| `/dashboard/posts/new` | M15 | 新增貼文 |
| `/dashboard/posts/[id]/edit` | M15 | 編輯貼文 |
| `/dashboard/media` | M15 | 媒體庫 |
| `/dashboard/pricing` | M12 | 動態定價規則 |
| `/dashboard/pricing/calendar` | M12 | 定價日曆預覽 |
| `/dashboard/members` | M17 | 店鋪成員管理 |
| `/dashboard/settings` | M17 | 店鋪設定 |
| `/dashboard/revenue` | M13 | 營收報表 |

#### 10.1.3 平台管理台路由（繼承 v0.8 + v0.9 擴展）

| 路由 | 對應模組 | 說明 | v0.9 |
|------|---------|------|------|
| `/admin` | M14 | 平台總管理台 | 不變 |
| `/seller/dashboard` | M13 | 賣家工作台（v0.8 遺留，Phase 2 遷移至 /dashboard） | 不變 |
| `/host/dashboard` | M13 | 房東工作台（v0.8 遺留，Phase 2 遷移至 /dashboard） | 不變 |
| **`/admin/tenants`** | **M17** | **租戶管理列表** | ★ 新增 |
| **`/admin/tenants/[id]`** | **M17** | **租戶詳情 + Feature Toggle** | ★ 新增 |
| **`/admin/tenants/[id]/features`** | **M17** | **Feature Toggle 設定** | ★ 新增 |

---

## 11. 元件設計 (Component Design)

### 11.1 CMS 嵌入購買卡片元件

**`<ListingEmbedCard />`**

| Props | 類型 | 說明 |
|-------|------|------|
| listingId | UUID | Listing ID |
| listingType | 'PRODUCT' \| 'ROOM' | 類型 |
| title | string | 商品/房型名稱 |
| coverImageUrl | string | 封面圖 |
| basePrice | number | 原價 |
| currentPrice | number | 現價（動態定價後） |
| currency | string | 幣別 |
| availability | object | 庫存/可用性資訊 |
| tenantName | string | 所屬店鋪名稱 |
| ctaUrl | string | 點擊導向 URL |

**行為：**
- 從 `GET /api/v2/listings/:id/card` 取得即時資料
- Product 類型：顯示庫存數量 + 加入購物車按鈕
- Room 類型：顯示最近可用日期 + 查看日曆按鈕
- 價格有折扣時顯示刪除線原價 + 紅字現價

### 11.2 動態定價日曆元件

**`<PricingCalendar />`**

| Props | 類型 | 說明 |
|-------|------|------|
| roomListingId | UUID | 房間 Listing ID |
| initialMonth | Date | 初始顯示月份 |
| mode | 'view' \| 'edit' | 瀏覽模式 / 編輯模式（房東後台） |

**行為：**
- C 端：顯示每日價格、可用狀態（綠色=可訂、灰色=已訂/封鎖）
- B 端（edit 模式）：點擊日期可手動覆蓋價格；顯示規則來源標記

### 11.3 店鋪前台佈局元件

**`<StoreLayout />`**

| Props | 類型 | 說明 |
|-------|------|------|
| tenantSlug | string | 店鋪 URL slug |
| tenantName | string | 店鋪名稱 |
| logoUrl | string | Logo |
| navItems | NavItem[] | 導航項（商品/房源/貼文） |

#### 11.3.1 StoreFront 專屬元件（v0.9 新增）

**`<StoreHeroBanner />`**

| Props | 類型 | 說明 |
|-------|------|------|
| bannerImageUrl | string | 橫幅圖片 URL |
| tenantName | string | 店鋪名稱 |
| description | string | 店鋪描述 |
| ctaText | string | CTA 按鈕文字（如「查看商品」） |
| ctaUrl | string | CTA 點擊連結 |

**`<StoreFeaturedListings />`**

| Props | 類型 | 說明 |
|-------|------|------|
| title | string | 區塊標題（如「精選商品」） |
| listings | Listing[] | 精選 Listing 陣列 |
| viewAllUrl | string | 「查看全部」連結 |

### 11.4 ERP 庫存台帳元件

**`<InventoryLedger />`**

| Props | 類型 | 說明 |
|-------|------|------|
| skuId | UUID | SKU ID |
| movements | StockMovement[] | 異動記錄列表 |

**行為：**
- 顯示 total_qty / reserved_qty / available_qty 即時數值
- 異動記錄依時間倒序，可按 movement_type 篩選
- 低庫存時底色標紅

---

## 12. 使用者流程 (User Flows)

### 12.1 網友開店流程 (v0.9 新增)

```
1. Buyer 登入平台
2. 點擊「申請開店」
3. 填寫店鋪資訊（名稱/描述/經營類型）
4. 提交申請 → POST /api/v2/tenants/apply
5. 等待 Admin 審核
6. Admin 通過 → Tenant 狀態 ACTIVE
7. 用戶角色升級為 StoreOwner
8. 進入 /dashboard 開始經營
```

### 12.2 CMS 嵌入購買卡片流程 (v0.9 新增)

```
1. StoreOwner/Seller 在 Dashboard 建立 CMS 貼文
2. 在 Markdown 編輯器中插入 {{embed:listing:<listing_id>}}
3. 前端解析標記 → 呼叫 GET /api/v2/listings/:id/card
4. 渲染互動式 <ListingEmbedCard /> 元件
5. C 端瀏覽者看到即時價格與庫存
6. 點擊「加入購物車」或「查看詳情」→ 導向商品/房型頁
```

### 12.3 ERP 入庫 → C 端連動流程 (v0.9 新增)

```
1. Seller 在 Dashboard 建立採購單
2. 收貨確認 → PUT /api/v2/dashboard/purchase-orders/:id/receive
3. 系統產生 StockMovement (type=INBOUND)
4. product_inventory.total_qty += inbound_qty
5. available_qty 自動重算 (total_qty - reserved_qty)
6. C 端商品頁即時反映新庫存
```

### 12.4 動態定價流程 (v0.9 新增)

```
1. Host/StoreOwner 在 Dashboard 設定定價規則
2. 系統依規則優先級計算未來 90 天每日價格
3. 計算結果寫入 room_calendar.price
4. C 端房間詳情頁顯示每日動態價格
5. 預訂時以 room_calendar.price 為實際價格
```

### 12.5 首次購物流程（繼承 v0.8 Cindy Persona）

> **重要說明**：Cindy 首次購物流程**僅驗證 Retail（M01 商品）購買路徑**，Booking（M02 民宿預訂）路徑的端到端驗證在 Phase 2 才開放。Phase 1 **不需要填寫收貨地址**（因為是 Payment Mock + 模擬履約，無需真實配送地址）。地址收集介面預留至 Phase 2-B 實作。

```
1. Cindy 進入平台首頁
2. 搜尋商品 → GET /api/products?keyword=...
3. 瀏覽商品列表（M01）
4. 點擊商品詳情 → GET /api/products/:id
5. 註冊帳號 → POST /api/auth/register（M03，3 步完成）
6. 加入購物車（Phase 2 M04）或直接下單
7. 建立訂單 → POST /api/orders（M05，含 Idempotency-Key）
   - Phase 1：由 Payment Mock 直接標記為 CREATED(=PAID)，無需真實金流
8. 等待出貨 → 狀態機流轉 SHIPPING → DELIVERED → COMPLETED
```

### 12.6 房東日曆管理流程（繼承 v0.8 Yachi Persona）

> **重要說明**：Phase 1 **不含**建立預訂（POST /api/bookings），Yachi 在 Phase 1 僅能管理房源日曆和查詢/取消預訂，**建立預訂功能（M06 POST）於 Phase 2 開放**。

```
1. Yachi 登入平台 → 進入 /dashboard
2. 查看房源日曆 → GET /api/rooms/:id/slots
3. 封鎖日期 → room_calendar.status = BLOCKED 或 MAINTENANCE
4. 設定定價規則（Phase 2-A M12）
5. Phase 2：收到預訂通知
6. Phase 2：預訂狀態機自動流轉 CONFIRMED → CHECKED_IN → CHECKED_OUT → COMPLETED
7. 退房後日曆自動釋放（Phase 2）
```

### 12.7 C 端用戶瀏覽店鋪流程（v0.9 新增）

> **說明**：本流程描述 Guest/Buyer 瀏覽店鋪前台（`/stores/[slug]`）的完整路徑。

```
1. Guest/Buyer 進入 /stores/[slug]
2. 顯示 StoreFront：店鋪 Banner + 描述 + 精選商品/房源（由 StoreOwner 從 CMS 或手動設定）
3. 可切換至「商品」「房源」「貼文」列表
4. 點擊任一商品/房源進入標準 M01/M02 詳情頁
5. 點擊「加入購物車」或「建立訂單」按鈕時：
   - Guest：自動引導至簡化註冊流程（Email + 密碼，3 步驟）
   - Buyer：直接進入結帳流程
```

> **Guest → Buyer 轉換流程**：Guest（未登入）可瀏覽所有商品與房源。當 Guest 嘗試「加入購物車」或「建立訂單」時，系統自動引導至簡化註冊流程。**Buyer 角色於成功完成首次註冊後自動授予**，不需額外申請流程。

---

## 13. Phase 1 範圍（v0.6 — 規格驅動起步）

> **本節完整繼承 v0.8 §9，Phase 1 內容已凍結，不可變更。**

Phase 1 聚焦「雙引擎」核心前後台雛形，優先確保兩套庫存邏輯（M01/M02）的基礎建設，並擴展 M05 最小訂單子集以完成首次購物流程驗證。

### 13.1 Phase 1 範圍：M03 + M01 + M02 + M05（最小子集）

| 順序 | 模組 | 交付物 |
|------|------|--------|
| 1 | M03 會員系統 | 註冊/登入/JWT + Refresh Token + 黑名單 |
| 2 | M01 商品中心 | 商品列表 + 詳情頁（分頁/篩選/搜尋） |
| 3 | M02 房源中心 | 房間列表 + 詳情頁 + 日期格查詢（日曆） |
| 4 | M05 訂單履約（最小子集） | 訂單建立 + 狀態機流轉 + 取消 + Payment Mock |
| 5 | M06 民宿預訂（部分） | 預訂列表查詢 + 預訂詳情 + 取消；**不含**建立預訂（POST） |
| 6 | M05 Seller 訂單查詢 | Seller 可透過 `GET /api/v1/orders?merchantId={id}` 查詢店鋪訂單（Phase 1 Seller 的 merchantId 從 JWT payload 中的 tenant_id 取得） |
| 7 | Phase 1 RBAC | 角色權限僅涵蓋 M01/M02/M03/M05/M06(查詢)，M12/M15/M16/M17 屬 Phase 2-A 範圍 |

**Phase 1 不含**：SKU 管理、ElasticSearch、LBS 地圖、PostGIS、OAuth2、KYC、購物車、金流（即時支付）、即時通訊、通知、物流、動態定價、後台管理；**M06 建立預訂（POST /api/bookings）**；M06 修改預訂；M06 預訂提醒。

> **API 命名澄清（v0.9_R02_Loop_01 新增）**：`POST /api/v1/orders`（M05）**支援** `type=BOOKING`（作為 M05 的一部分，Phase 1 實作），其 Request Body 中的 `booking.slotId` 欄位用於建立 BOOKING 類型訂單。**但 `POST /api/v1/orders`（含 `type=BOOKING`）不等同於 `POST /api/v1/bookings`（M06 獨立預訂管理 API）**。兩者為不同 API：前者為 M05 訂單系統的一部分（Phase 1），後者為 M06 完整預訂管理功能（Phase 2，含獨立預訂記錄管理）。

### 13.2 M05 最小子集：明確定義

**[Specification] M05 最小子集 — 包含範圍（Phase 1 IN）**

| 編號 | 功能 | 說明 |
|------|------|------|
| 1 | 訂單建立 | 買家可建立零售訂單（M01 商品）或預訂訂單（M02 房源），一次僅限單類型 |
| 2 | 狀態機流轉 | 零售路徑 Phase 2+：`CREATED` → `PAID` → `SHIPPING` → `DELIVERED` → `COMPLETED`；零售路徑 Phase 1（Payment Mock）：`CREATED(=PAID)` → `SHIPPING` → `DELIVERED` → `COMPLETED`；預訂路徑 Phase 2+：`CREATED` → `PAID` → `CONFIRMED` → `CHECKED_IN` → `CHECKED_OUT` → `COMPLETED`；預訂路徑 Phase 1（Payment Mock）：`CREATED(=PAID)` → `CHECKED_IN` → `CHECKED_OUT` → `COMPLETED`（Phase 1 CONFIRMED 等效於 CREATED(=PAID)，無獨立狀態步驟） |
| 3 | 取消功能 | 支援買家主動取消（符合狀態機約束）；自動釋放關聯資源（庫存扣減回滾、日期格釋放） |
| 4 | 冪等性保障 | 所有狀態變更 API 支援 `Idempotency-Key` Header，Gateway 層 24h Redis 攔截重複請求 |
| 5 | 狀態日誌 | 每筆訂單的狀態變更均寫入 `order_state_log`，支援未來審計需求 |

**[Specification] M05 最小子集 — 排除範圍（Phase 1 OUT）**

| 編號 | 排除功能 | 說明 |
|------|---------|------|
| 1 | 退款處理 | Phase 1 不支援退款；僅支援取消（Cancel），資源釋放而非資金回流 |
| 2 | 電子發票開立 | 發票系統屬 Phase 2+ 範圍 |
| 3 | 訂單拆單 | 單一訂單僅包含同一類型商品；Phase 1 不支援跨 SKU 合併或拆分 |
| 4 | 金流扣款（即時） | Phase 1 模擬支付成功（Payment Mock）——`POST /api/orders` 建立訂單時直接標記為 `CREATED(=PAID)`，不串接真實金流 API，不存在中間 `PAID` 狀態等待 |
| 5 | 逆向物流追蹤 | 退貨、換貨流程不在 Phase 1 範圍 |
| 6 | Saga Pattern | Phase 1 為單一資料庫交易；Saga 分散式交易屬 Phase 2 |

### 13.3 Phase 1 行為約束

**[Specification] Phase 1 強制約束**

| 編號 | 約束項目 | 說明 |
|------|---------|------|
| 1 | 禁止混合訂單 | Phase 1 訂單僅支援單一類型：`RETAIL` 或 `BOOKING`，不可同時存在於同一筆訂單 |
| 2 | 不支援退款 | Phase 1 僅支援取消（Cancel）；取消後資源（庫存/日期格）釋放回系統，但不進行資金退回 |
| 3 | 訂單僅支援單類型 | 每筆訂單建立時需明確指定類型（`RETAIL` 或 `BOOKING`），類型一旦建立不可變更 |
| 4 | 支付模擬化 | Phase 1 支付環節為模擬流程（Payment Mock）。`POST /api/v1/orders` 建立訂單時，系統直接將狀態推進至 `CREATED(=PAID)`——即訂單建立時即等同於已支付（Payment Mock），無需等待真實金流回調。`CONFIRMED` 狀態在 Phase 1 邏輯中等效於 `CREATED(=PAID)`。 |
| 5 | 無跨模組事務 | Phase 1 不存在跨 M05/M06/M07 的分散式交易；所有狀態變更在單一資料庫事務內完成 |
| 6 | Phase 1 取消與狀態對齊 | Phase 1 取消規則以 `CREATED(=PAID)` 為主要取消觸發點（倉庫尚未發貨/房源尚未入住）。`CONFIRMED` 狀態在 Phase 1 邏輯中等效於 `CREATED(=PAID)`，取消補償邏輯與 Phase 2 一致。 |
| 7 | merchantId 來源 | Phase 1 Seller/Host 的 `merchantId` 從 JWT payload 中的 `tenant_id` 欄位取得，無需獨立 API 查詢。 |
| 8 | 取消操作 Atomic Transaction | Phase 1 取消操作中，訂單狀態變更（CANCELLED）與資源釋放（庫存 `reserved_qty` 扣減、日期格釋放）**必須在同一 DB Transaction 內完成**，不得拆分。若 DB 在兩者之間故障，Transaction 完整回滾，訂單仍為原狀態，庫存/日期格不釋放也不改變。 |

**[Test Cases] Phase 1 約束驗證**

| 編號 | 測試案例 | 驗證條件 |
|------|---------|---------|
| TC-P1-001 | GIVEN 建立訂單 WHEN 訂單同時包含商品與房源 THEN 系統拒絕並回傳 `MIXED_ORDER_NOT_ALLOWED` 錯誤碼 | Phase 1 強制 |
| TC-P1-002 | GIVEN 訂單已 `PAID` 狀態 WHEN 買家申請退款 THEN 系統拒絕並回傳 `REFUND_NOT_SUPPORTED_IN_PHASE1` 錯誤碼 | Phase 1 強制 |
| TC-P1-003 | GIVEN 建立訂單 WHEN 未攜帶 `Idempotency-Key` Header THEN 系統拒絕並回傳 `IDEMPOTENCY_KEY_REQUIRED` 錯誤碼 | Phase 1 強制 |
| TC-P1-004 | GIVEN 取消訂單 WHEN 訂單狀態為 `SHIPPING`（零售）或 `CHECKED_IN`（預訂） THEN 系統拒絕取消並回傳 `CANCEL_NOT_ALLOWED_IN_CURRENT_STATE` | Phase 1 強制 |
| TC-P1-005 | GIVEN 一筆 RETAIL 訂單（`status = CREATED(=PAID)`，`reserved_qty = 5`）WHEN 買家執行 `PUT /api/v1/orders/:id/cancel` THEN `orders.status = CANCELLED` 且 `product_inventory.reserved_qty -= 5` 在同一 Transaction 內完成 | Phase 1 強制（Atomic Transaction 約束第 8 條）|
| TC-P1-006 | GIVEN Phase 1 取消操作在 DB 更新訂單狀態後、釋放庫存前故障 WHEN 系統重啟或 Transaction 回滾 THEN 訂單仍為原狀態，`reserved_qty` 未變動，無不一致狀態 | Phase 1 強制 |

### 13.4 Phase 1 成功指標

**[Specification] 功能驗收標準（每模組交付物清單）**

| 模組 | 交付物 | 驗收條件 |
|------|--------|---------|
| M03 | JWT + Refresh Token 認證機制 | 1. 註冊/登入 API 回傳有效 JWT；2. Access Token 失效後可透過 Refresh Token 換發；3. 登出後 JWT 加入黑名單且被拒絕存取 |
| M03 | RBAC 權限控制 | 1. Buyer/Seller/Host/Admin 四種角色正確隔離；2. 未授權角色存取受限資源時回傳 403 |
| M03 | 使用者資料管理 | 1. `GET /api/users/me` 回傳完整個人資料；2. `PUT /api/users/me` 可更新非敏感欄位 |
| M01 | 商品列表與搜尋 | 1. `GET /api/products` 回傳分頁結果（預設 20 筆/頁）；2. 支援 `keyword`、`category`、`minPrice`、`maxPrice` 篩選；3. 搜尋延遲 < 300ms（p95） |
| M01 | 商品詳情頁 | 1. `GET /api/products/:id` 回傳完整商品資訊含庫存數量；2. 不存在的商品回傳 404 |
| M02 | 房源列表與篩選 | 1. `GET /api/rooms` 支援 `region`、`checkIn`、`checkOut`、`guests` 參數；2. 已預訂日期格不可選 |
| M02 | 日期格查詢 | 1. `GET /api/rooms/:id/slots` 回傳未來 90 天可用日曆；2. 占用衝突時回傳正確的已預訂狀態 |
| M05 | 訂單建立 | 1. `POST /api/orders` 成功回傳訂單 UUID；2. 庫存/日期格同步扣減；3. 冪等性 key 重複時回傳相同結果 |
| M05 | 狀態機流轉 | 1. 零售 Phase 2+：`CREATED→PAID→SHIPPING→DELIVERED→COMPLETED`；零售 Phase 1：`CREATED(=PAID)→SHIPPING→DELIVERED→COMPLETED`；2. 預訂 Phase 2+：`CREATED→PAID→CONFIRMED→CHECKED_IN→CHECKED_OUT→COMPLETED`；預訂 Phase 1：`CREATED(=PAID)→CHECKED_IN→CHECKED_OUT→COMPLETED`；3. 非法轉換被拒絕 |
| M05 | 取消功能 | 1. 符合約束的取消成功並釋放資源；2. `order_state_log` 記錄完整變更軌跡 |

**[Specification] 技術效能基準**

| 指標 | 目標值 | 測量方式 |
|------|--------|---------|
| API 回應時間（M01/M02 讀取） | p95 < 300ms | 端對端監控，含 DB 查詢時間 |
| API 回應時間（M03 認證） | p95 < 200ms | JWT 發放 + 驗證端點 |
| API 回應時間（M05 寫入） | p95 < 500ms | 訂單建立 + 狀態變更 |
| 並發用戶數（Phase 1 支援） | 50 simultaneous active users | 負載測試目標 |
| 訂單處理吞吐量 | ≥ 10 orders/second | 持續 30 秒壓測 |
| 系統可用性 | ≥ 99%（營運時間） | Uptime monitoring |
| 資料庫連線池 | Max 20 connections | Phase 1 單機部署 |

**[Specification] Phase 1 多租戶容量指標（v0.9 新增）**

> **說明**：v0.9 升級為 B2B2C 多租戶架構後，Phase 1 需支援基本的多租戶並發場景。

| 指標 | 目標值 | 說明 |
|------|--------|------|
| 同時活躍租戶數 | ≥ 10 | Phase 1 支援至少 10 個租戶同時運營 |
| 每租戶平均並發用戶數 | ≤ 5 | 每租戶平均 5 個並發用戶（50 / 10） |
| 資料庫連線池策略 | Max 20 connections | Phase 1 單機部署，若租戶數增長可在 Phase 2 擴展為每租戶獨立連線池 |
| API 限流策略 | 每租戶 100 req/min | 由 API Gateway 執行，超過限流回傳 429 |

**[Specification] 商業價值錨點 (Business Value Metrics)**
*備註：宣告 Phase 1 定位為「驗證核心交易流與框架」的內部測試 (Closed-Beta)。*

| BV 標號 | 指標名稱 | 測量公式 / 定義 | Phase 1 目標 | 說明 |
|---------|----------|-----------------|--------------|------|
| **BV-01** | 目標用戶數 | `count(users)` 於 Phase 1 期間註冊 | ≥ 100 | 首批內測用戶（含 Buyer/Seller/Host 混合角色） |
| **BV-02** | 買家轉化率 | `首次完成訂單買家數 / 總站活躍買家數` | ≥ 10% | 評估商品卡片到下單的漏斗轉換效率 |
| **BV-03** | 商家入駐活躍數 | `至少上架 1 個 Listing 的 Seller/Host 總數` | ≥ 30 (20S+10H)| 驗證店鋪後台與 Listing 抽象的易用性 |
| **BV-04** | 訂單履約完成率 | `COMPLETED 訂單數 / 總建立訂單數` | ≥ 80% | 評估訂單狀態機與跨子系統連動 (ERP) 的穩定性 |
| **BV-05** | GMV 模擬產值 | `sum(orders.total_amount where state=COMPLETED)`| ≥ NTD 50k | Phase 1 期間累積實體驗證訂單總金額 |
| **BV-06** | 取消與異常阻礙率 | `(CANCELLED 訂單 + E-40xx 交易錯誤) / 總交易數` | 綜合評分監控 | 驗證防超賣與 Q14 規則的阻斷與業務防護效益 |

### 13.5 MoSCoW 優先級校準（v1.0 更新）

**[MoSCoW] 完整優先級表（Victoria 校準，v1.0 用戶需求確認）**

**Must Have（必須有）— Phase 1 交付**

| 優先序 | 模組 | 功能 | 說明 |
|:----:|------|------|------|
| **1.** | **M12** | **動態定價引擎** | **🔼 從 Should Have 提升 — Must Have for Phase 1 B&B Core** |
| 2. | M03 | 會員系統核心 | 註冊/登入/JWT + Refresh Token + 黑名單 + RBAC |
| 3. | M01 | 商品中心核心 | 列表/詳情/分頁/篩選/搜尋 |
| 4. | M02 | 房源中心核心 | 列表/詳情/日期格查詢 + 地區/日期/人數篩選 |
| 5. | M05 | 訂單履約最小子集 | 訂單建立 + 狀態機流轉 + 取消 + 冪等性 |

**Should Have（應該有）— Phase 2 前期**

| 優先序 | 模組 | 功能 | 說明 |
|:----:|------|------|------|
| 6. | M04 | 購物車系統 | Redis Hash 存儲、優惠券、滿額折扣計算 |
| 7. | M06 | 預訂與日曆鎖定 | **Phase 1**：GET 查詢 + 取消；**Phase 2**：POST 建立預訂、Redis 分散式鎖防止 Overbooking、日期格索引 |
| 8. | M17 | 租戶/店鋪管理 | 開店申請 + 審核 + Feature Toggle |
| 9. | M09 | 訊息通知中心 | Email/SMS/App Push 通知模板化 |
| 10. | M11 | 物流追蹤 | 第三方物流 API 介接、運費模板 |
| 11. | **M18** | **知識管理系統** | **🆕 新增 — 媒體中心（Phase 1）** |

**Could Have（可以有）— Phase 2 後期**

| 優先序 | 模組 | 功能 | 說明 |
|:----:|------|------|------|
| 12. | M08 | 評價與社交互動 | 圖文評論、星級評分、商家回覆 |
| 13. | M10 | 即時通訊（IM） | WebSocket 私訊、未讀計數、快捷回覆 |

**Won't Have（暫時不做）— Phase 2+ 明確排除**

| 優先序 | 模組 | 排除功能 | 說明 |
|:----:|------|---------|------|
| 14. | M01 | SKU 多規格管理、組合包、ElasticSearch 全文檢索 | Phase 2+ |
| 15. | M02 | PostGIS LBS 地圖模式、景點距離計算 | Phase 2+ |
| 16. | M03 | OAuth2 第三方登入、KYC 實名認證 | Phase 2+ |
| 17. | M05 | 電子發票開立、拆單、退款 | Phase 2+ |
| 18. | M07 | Saga Pattern 分散式交易 | Phase 2+ |
| 19. | M13 | 商家工作台（Seller/Host 儀表板） | Phase 2+ |
| 20. | M14 | 平台總管理台、Admin 全站配置 | Phase 2+ |

### 13.6 Spec Gap（Phase 2 規劃時需補充）

| 編號 | 缺口描述 | 建議補充時機 |
|------|---------|------------|
| SG-001 | M05 退款流程規格（Phase 1 OUT） | Phase 2 規劃階段 |
| SG-002 | M07 Payment Mock 實作細節（Mock API 回傳格式） | Phase 1 實作前 |
| SG-003 | M05 取消補償邊界商業決策（取消後通知範圍） | Phase 2 規劃階段 |

---

## 14. Phase 2+ 規劃與範圍

### 14.1 Phase 分期總覽（v0.9 更新）

| Phase | 重點 | 新增/升級模組 |
|-------|------|-------------|
| **Phase 1** | 雙引擎核心雛形 | M03, M01, M02, M05, M06(部分) |
| **Phase 2-A** | 多租戶 + 動態定價 + CMS + ERP | M17, M12, M15, M16 |
| **Phase 2-B** | 購物車 + 金流 + 預訂完整化 | M04, M07, M06(完整), M09 |
| **Phase 3** | 社交 + 進階功能 | M08, M10, M11, M13, M14 |
| **Phase 4** | 規模化基礎設施 | ElasticSearch, PostGIS, MQ, CDN |

### 14.2 Phase 2-A 範圍（v0.9 新增內容優先交付）

| 模組 | 功能 | 交付物 |
|------|------|--------|
| M17 | 租戶/店鋪管理 ★ 搶先實作 | 開店申請 + 審核 + Feature Toggle + 店鋪 Profile。**實作順序約束：M17 必須在 M12、M15、M16 任何一個模組開始實作前完成，因為後續模組的 RBAC `*` 限制依賴 M17 的 Toggle 查詢。** |
| M12 | 動態定價引擎 | 平假日/季度/早鳥/長住/手動覆蓋規則 + 定價日曆預覽 |
| M15 | CMS 內容管理 | 貼文 CRUD + 嵌入購買卡片 + 媒體庫 |
| M16 | ERP 進銷存 | 採購入庫 + 庫存台帳 + 出庫管理 + C 端連動 |
| M01/M02 | Listing 抽象化 | 統一 Listing 模型遷移 + Listing API v2 |

#### 14.2.5 Phase 2-A 商業價值錨點（v0.9 新增）

| BV-2A 編號 | 指標名稱 | 測量公式 | Phase 2-A 目標 |
|------------|----------|----------|----------------|
| BV-2A-01 | 店鋪開通轉化率 | `ACTIVE tenants / 總申請數` | ≥ 70% |
| BV-2A-02 | CMS 內容觸發 GMV | `含有嵌入卡片的訂單金額 / 總 GMV` | ≥ 15% |
| BV-2A-03 | 動態定價採用率 | `使用動態定價的房源數 / 總房源數` | ≥ 40% |
| BV-2A-04 | ERP 庫存準確率 | `盤點差異次數 / 總盤點次數` | ≤ 2% |
| BV-2A-05 | 店鋪後台 DAU | 每日登入店鋪後台的 StoreOwner/Staff 數 | 待定（根據實際上線數據調整） |

### 14.3 Phase 2-A vs Phase 2-B 功能邊界矩陣與遷移策略

#### 14.3.1 功能邊界矩陣
| 模組 / 能力 | Phase 2-A | Phase 2-B | 說明 |
|-------------|-----------|-----------|------|
| **System Tenant** | **上線** (M17 租戶模型) | 擴充 | Phase 2-A 必須完成 System Tenant (`0000...001`) 資料庫關聯與歷史資料遷移 (Migration) |
| **RBAC 模型** | **User -> StoreOwner 擴展** | 完善 | 支援 JWT 中夾帶 TenantID 資訊以進行 RBAC (tenant_members 表) |
| **收貨地址管理** | 不支援 | **上線** | 屬於 B2C 核心，但因 Phase 1/2-A 專注核心交易，收貨地址延至 Phase 2-B (結購物車與物流) |
| **M12 動態定價** | **上線** | 擴充 | 提前支援營運需求 |

#### 14.3.2 歷史資料遷移策略 (Migration Strategy)
- **Tenant Migration**: Phase 1 的所有既有訂單與商品，在升級 Phase 2-A 時，Flyway script 必須強制將它們的 `tenant_id` 攔截並指向 System Tenant UUID (`00000000-0000-0000-0000-000000000001`)。
- **Supplier Migration (Phase 3)**: 關於 `supplier_id` (NULL) 的歷史採購單資料補齊決策，**[TBD: Phase 3 Migration 決策待定，等待 Koala Decision 選擇 Decision A 保留 NULL 抑或 Decision B 強制補齊]**。

### 14.4 Phase 3 範圍

參照 §13.5 MoSCoW 分類：
- Phase 3：Could Have 項目（M08 評價、M10 IM、M11 物流、M13/M14 後台完善）

---

## 15. M05 訂單模組獨立 SDD 規格

> **原始文件：** `005_M05_Spec.md` (M05-ORDER-SPEC.md)
> **版本：** v1.0.0 | **日期：** 2026-03-25 | **作者：** Amanda (sa-analyst)
> **狀態：** Phase 1 正式規格
> **本節完整繼承 v0.8 §10，已凍結，不可變更。**

### 15.1 模組概述

#### 15.1.1 模組定位

| 屬性 | 說明 |
|------|------|
| 模組編號 | M05 |
| 模組名稱 | Order Management（訂單管理） |
| 所屬 Phase | Phase 1 |
| 依賴模組 | M01（認證）, M02（商品）, M03（日曆預訂）, M04（支付） |
| API 前綴 | `/api/v1/orders` |

#### 15.1.2 業務範疇

M05 負責管理所有零售（RETAIL）與預訂（BOOKING）訂單的生命週期，涵蓋建立、查詢、狀態更新、取消及狀態日誌追蹤。

#### 15.1.3 Phase 1 約束（PC-001~PC-005）對 M05 的具體約束

| 約束編號 | 約束內容 | 對 M05 的具體影響 |
|----------|----------|------------------|
| PC-001 | Phase 1 僅支援「日曆時段」預設類型 | M05 僅處理 BOOKING 類型中 `slotType=SLOT` 的訂單 |
| PC-002 | Phase 1 不支援線上預覽與即時排程 | M05 狀態機不觸發 SCHEDULE_UPDATE 事件 |
| PC-003 | Phase 1 不支援差異化時段費率 | M05 忽略 `pricingRule` 中的 `differentialRate` 欄位 |
| PC-004 | Phase 1 商家上線門檻：7 日內有新用戶 | M05 訂單建立時校驗商家是否符合門檻（商家伙伴模組校驗） |
| PC-005 | Phase 1 不支援跨商家/跨品項優惠 | M05 不實作 cross-merchant/cross-item 優惠邏輯 |

### 15.2 API 端點完整 SDD 規格

#### 15.2.1 `POST /api/v1/orders` — 建立訂單

**[Specification]**

**功能：** 建立一筆新的 RETAIL 或 BOOKING 訂單。

**Request Body：**

```json
{
  "type": "RETAIL | BOOKING",
  "merchantId": "string (UUID)",
  "customerId": "string (UUID)",
  "items": [
    {
      "productId": "string (UUID)",
      "quantity": "integer (min: 1)",
      "unitPrice": "number (min: 0)",
      "subtotal": "number (min: 0)"
    }
  ],
  "booking": {
    "slotId": "string (UUID)",
    "date": "string (ISO 8601 date)",
    "startTime": "string (HH:mm)",
    "endTime": "string (HH:mm)",
    "slotType": "SLOT | RECURRING",
    "notes": "string (optional, max 500 chars)"
  },
  "totalAmount": "number (min: 0)",
  "currency": "string (ISO 4217, default: TWD)",
  "promoCode": "string (optional)",
  "preconditions": ["string"]
}
```

**前置條件（Preconditions）— 精確版（Errata §5 修正）：**
1. 商家狀態為 ACTIVE 且已通過 7 日新用戶門檻（PC-004）。
2. 客户已完成身份驗證，持有有效 JWT（來自 M01）。
3. 若 `type=BOOKING`，對應 `slotId` 必須存在且狀態為 AVAILABLE。
4. 若 `type=RETAIL`，庫存模組（M02）必須確認所有 `items` 有足夠庫存（EC-M04）。
5. `totalAmount` 必須與系統計算金額一致（容許 ±0.01 誤差，VR-M03）。
6. 促銷碼（若提供）必須尚未使用且未過期（VR-M06）。
7. **Phase 1 約束**：`items` 陣列長度不可超過 50 項（VR-M04）。

**後置條件（Postconditions）：**
1. 訂單狀態初始為 `CREATED`（**Phase 1：由 Payment Mock 直接標記為 `CREATED(=PAID)`——即訂單建立時即等同於已支付**）。
2. 庫存鎖定（RETAIL）或時段鎖定（BOOKING）已生效。**RETAIL 庫存扣減在同一 `@Transactional` 內完成：系統先校驗 `available_qty >= order_qty`，通過後於同一 Transaction 內執行 `reserved_qty += order_qty` 並遞增 `version`（樂觀鎖）。若樂觀鎖失敗（`WHERE version = V` 匹配失敗），系統自動重試最多 3 次；3 次後仍失敗則回傳 `E-4003 INSUFFICIENT_STOCK_DUE_TO_CONCURRENT_ACCESS`。**
3. 若有促銷碼，優惠已套用並記錄。
4. 事件 `ORDER_CREATED` 已發送至事件總線。

**Response（201 Created）：**

```json
{
  "orderId": "string (UUID)",
  "type": "RETAIL | BOOKING",
  "status": "CREATED",
  "merchantId": "string",
  "customerId": "string",
  "items": [...],
  "booking": {...},
  "totalAmount": "number",
  "currency": "string",
  "createdAt": "string (ISO 8601)",
  "updatedAt": "string (ISO 8601)"
}
```

**錯誤碼：**

| HTTP Status | Error Code | 說明 |
|--------------|------------|------|
| 400 | E-4001 | 請求體格式錯誤或必填欄位缺失 |
| 401 | E-2001 | 未提供或無效的 JWT |
| 403 | E-2002 | 商家未上線或客戶無權建立訂單 |
| 404 | E-1001 | slotId 不存在（BOOKING 類型） |
| 409 | E-4002 | 時段已被人預訂（BOOKING 衝突） |
| 422 | E-4003 | 庫存不足（RETAIL 類型） |
| 422 | E-4005 | 促銷碼無效或已過期 |

**[Test Cases]**

| 編號 | 測試案例 | GIVEN | WHEN | THEN |
|------|---------|-------|------|------|
| TC-M05-001 | 建立 RETAIL 訂單 — Happy Path | 客戶已登入，商家 ACTIVE，庫存充足 | POST /api/v1/orders (RETAIL) | 201，CREATED，庫存已鎖定 |
| TC-M05-002 | 建立 BOOKING 訂單 — Happy Path | 客戶已登入，slotId 存在且 AVAILABLE | POST /api/v1/orders (BOOKING) | 201，CREATED，時段已鎖定 |
| TC-M05-003 | 建立 BOOKING 訂單 — 時段衝突 | slotId 已被佔用 | POST /api/v1/orders (BOOKING) | 409，E-4002 |
| TC-M05-004 | 建立 RETAIL 訂單 — 庫存不足 | quantity > 實際庫存 | POST /api/v1/orders (RETAIL) | 422，E-4003 |
| TC-M05-005 | 建立訂單 — 促銷碼無效 | 促銷碼已過期 | POST /api/v1/orders | 422，E-4005 |
| TC-M05-006 | 建立 RETAIL 訂單 — 並發庫存扣減防護（v0.9_R02_Loop_01 新增） | SKU-001 的 `available_qty = 10`，100 個買家同時發出 `POST /api/v2/orders`（各 1 件） | 系統處理所有並發請求 | exactly 10 筆訂單成功（`status = CREATED`），`reserved_qty = 10`；其餘 90 筆回傳 `422 E-4003 INSUFFICIENT_STOCK_DUE_TO_CONCURRENT_ACCESS`；SKU-001 的 `available_qty` 最終為 0 |
| TC-M05-007 | 建立 RETAIL 訂單 — 庫存為 0 直接失敗 | SKU-002 的 `available_qty = 0` | 嘗試建立訂單 | 直接回傳 `422 E-4003`，不進入並發競爭 |
| TC-M05-008 | 建立訂單 — 無效 JWT | 未攜帶有效 JWT | POST /api/v2/orders | 401，E-2001 |
| TC-M05-009 | 建立訂單 — totalAmount 篡改 | totalAmount 與系統計算不符 | 系統校驗金額一致性 | 422，E-4006 |

#### 15.2.2 `GET /api/v1/orders` — 查詢訂單列表

**[Specification]**

**功能：** 根據過濾條件查詢訂單列表（支援分頁）。

**Query Parameters：**

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| merchantId | UUID | 否 | 商家 ID |
| customerId | UUID | 否 | 客户 ID |
| type | RETAIL \| BOOKING | 否 | 訂單類型 |
| status | string | 否 | 訂單狀態 |
| from | ISO 8601 datetime | 否 | 創建時間起 |
| to | ISO 8601 datetime | 否 | 創建時間訖 |
| page | integer (default: 1) | 否 | 頁碼 |
| limit | integer (default: 20, max: 100) | 否 | 每頁筆數 |

**前置條件：**
1. JWT 持有者為商家本人（merchantId 匹配）或系統管理員。
2. 若查詢 customerId，僅能查詢本人訂單。

**Response（200 OK）：**

```json
{
  "data": [
    {
      "orderId": "string",
      "type": "RETAIL | BOOKING",
      "status": "string",
      "merchantId": "string",
      "customerId": "string",
      "totalAmount": "number",
      "createdAt": "string"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 100,
    "totalPages": 5
  }
}
```

**[Test Cases]**

| 編號 | 測試案例 | GIVEN | WHEN | THEN |
|------|---------|-------|------|------|
| TC-M05-008 | 查詢訂單列表 — 商家查詢本人訂單 | 商家已登入 | GET /api/v1/orders?merchantId={ownId} | 200，僅返回該商家訂單 |
| TC-M05-009 | 查詢訂單列表 — 跨商家查詢被拒 | 商家嘗試查詢他人 merchantId | GET /api/v1/orders?merchantId={otherId} | 403，E-2002 |

#### 15.2.3 `GET /api/v1/orders/:id` — 查詢單筆訂單

**[Specification]**

**功能：** 根據訂單 ID 查詢完整訂單資訊。

**前置條件：**
1. JWT 持有者為訂單持有人（商家或客户）或系統管理員。
2. 訂單必須存在。

**Response（200 OK）：** 完整訂單物件（含 items、booking、state-log 摘要）。

**錯誤碼：**

| HTTP Status | Error Code | 說明 |
|--------------|------------|------|
| 401 | E-2001 | 無效 JWT |
| 403 | E-2002 | 無權查看此訂單 |
| 404 | E-1002 | 訂單不存在 |

**[Test Cases]**

| 編號 | 測試案例 | GIVEN | WHEN | THEN |
|------|---------|-------|------|------|
| TC-M05-010 | 查詢單筆訂單 — Happy Path | 客戶已登入且持有該訂單 | GET /api/v1/orders/{orderId} | 200，完整訂單資訊 |
| TC-M05-011 | 查詢單筆訂單 — 訂單不存在 | orderId 不存在 | GET /api/v1/orders/{invalidId} | 404，E-1002 |

#### 15.2.4 `PUT /api/v1/orders/:id` — 更新訂單

**[Specification]**

**功能：** 更新訂單資訊（僅允許特定欄位更新）。

**可更新欄位（RETAIL）：** `items`、`promoCode`、`notes`、`totalAmount`（需重新校驗）。

**可更新欄位（BOOKING）：** `notes`（PC-001 約束：不可更改時間欄位）。

**不可更新欄位：** `type`、`merchantId`、`customerId`、`booking.slotId`、`booking.date`、`booking.startTime`、`booking.endTime`。

**前置條件：**
1. 訂單狀態為 `CREATED` 或 `CONFIRMED`（已確認但未完成）。
2. JWT 持有者為商家或客户。
3. BOOKING 類型不可在 Phase 1 變更時段（PC-001）。

**Response（200 OK）：** 更新後的完整訂單物件。

**錯誤碼：**

| HTTP Status | Error Code | 說明 |
|--------------|------------|------|
| 400 | E-4001 | 嘗試更新不允許的欄位 |
| 404 | E-1002 | 訂單不存在 |
| 409 | E-4007 | 訂單狀態不允許更新 |

**[Test Cases]**

| 編號 | 測試案例 | GIVEN | WHEN | THEN |
|------|---------|-------|------|------|
| TC-M05-012 | 更新 RETAIL 訂單 — Happy Path | 訂單 CREATED，更新 items | PUT /api/v1/orders/{orderId} | 200，已更新 |
| TC-M05-013 | 更新 BOOKING 訂單 — 嘗試變更時段（Phase 1 禁止） | BOOKING 訂單，PC-001 約束 | 變更 booking.startTime | 400，E-4001 |
| TC-M05-014 | 更新已取消訂單 | 訂單 CANCELLED | PUT /api/v1/orders/{orderId} | 409，E-4007 |

#### 15.2.5 `PUT /api/v1/orders/:id/cancel` — 取消訂單

**[Specification]**

**功能：** 取消訂單並觸發相應的補償邏輯。

**Request Body：**

```json
{
  "reason": "string (max 500 chars)",
  "canceledBy": "MERCHANT | CUSTOMER | SYSTEM"
}
```

**狀態機轉換規則（雙路徑）：**

**RETAIL 路徑：**

```
CREATED ──[cancel by any]──→ CANCELLED
    │                              │
    ▼                              ▼
CONFIRMED ──[cancel by customer]──→ CANCELLED ──[refund triggered]──→ REFUNDED
    │                              │
    ▼                              ▼
COMPLETED (terminal)         (庫存釋放)
```

**RETAIL 取消補償邏輯：**
- 庫存釋放：實時歸還至 M02
- 退款：若已支付，觸發 M04 退款流程
- 優惠券：若已使用促銷碼，則退還（視優惠規則）

**BOOKING 路徑：**

```
CREATED ──[cancel by any]──→ CANCELLED ──[slot released]──→ (釋放時段)
    │
    ▼
CONFIRMED ──[cancel by customer before 24h]──→ CANCELLED
    │                                              (Q14: 24小時內不退款)
    ▼
CONFIRMED ──[cancel by customer after 24h]──→ CANCELLED ──[refund triggered]──→ REFUNDED
    │
    ▼
COMPLETED (terminal)
```

**BOOKING 取消補償邏輯（Q14 Decision Record）：**
- 取消時段釋放：立即回歸可用池
- 退款判定：
  - 距離預訂時段 ≥ 24 小時：全額退款
  - 距離預訂時段 < 24 小時：不退款（Q14 補償邊界）
- 取消方：商家主動取消 → 全額退款；客户取消 → 按上述規則處理

**Response（200 OK）：**

```json
{
  "orderId": "string",
  "status": "CANCELLED",
  "canceledAt": "string (ISO 8601)",
  "canceledBy": "string",
  "reason": "string",
  "refundStatus": "NONE | PENDING | COMPLETED",
  "refundAmount": "number (nullable)"
}
```

**錯誤碼：**

| HTTP Status | Error Code | 說明 |
|--------------|------------|------|
| 404 | E-1002 | 訂單不存在 |
| 409 | E-4007 | 訂單已取消或已完成 |
| 422 | E-4008 | 取消被拒（Q14 規則：不滿足退款條件但嘗試套用退款） |

**[Test Cases]**

| 編號 | 測試案例 | GIVEN | WHEN | THEN |
|------|---------|-------|------|------|
| TC-M05-015 | 取消 RETAIL 訂單 — 未支付 | RETAIL 訂單 CREATED，未支付 | 商家取消 | 200，CANCELLED，庫存釋放，無退款 |
| TC-M05-016 | 取消 BOOKING 訂單 — < 24h（不退款） | BOOKING 已確認，距時段 < 24h | 客户取消 | 200，CANCELLED，refundStatus=NONE |
| TC-M05-017 | 取消 BOOKING 訂單 — ≥ 24h（退款） | BOOKING 已確認，距時段 ≥ 24h | 客户取消 | 200，CANCELLED，refundStatus=PENDING |
| TC-M05-018 | 取消已完成訂單 | 訂單 COMPLETED | 任何方取消 | 409，E-4007 |

#### 15.2.6 `GET /api/v1/orders/:id/state-log` — 查詢狀態日誌

**[Specification]**

**功能：** 查詢訂單的完整狀態變更歷史。

**前置條件：**
1. JWT 持有者為訂單持有人或系統管理員。
2. 訂單必須存在。

**Response（200 OK）：**

```json
{
  "orderId": "string",
  "logs": [
    {
      "sequence": 1,
      "fromStatus": "null",
      "toStatus": "CREATED",
      "triggeredBy": "CUSTOMER",
      "triggeredAt": "string (ISO 8601)",
      "metadata": {}
    },
    {
      "sequence": 2,
      "fromStatus": "CREATED",
      "toStatus": "CONFIRMED",
      "triggeredBy": "MERCHANT",
      "triggeredAt": "string (ISO 8601)",
      "metadata": {
        "paymentId": "string"
      }
    }
  ]
}
```

**[Test Cases]**

| 編號 | 測試案例 | GIVEN | WHEN | THEN |
|------|---------|-------|------|------|
| TC-M05-019 | 查詢狀態日誌 — Happy Path | 客戶持有訂單 | GET /api/v1/orders/{orderId}/state-log | 200，logs 依 sequence 遞增 |

### 15.3 訂單狀態機定義

#### 15.3.1 狀態列舉

| 狀態 | 說明 | 終態 | Phase 1 等效狀態 |
|------|------|------|------------------|
| CREATED | 訂單已建立，初始狀態 | 否 | `CREATED(=PAID)` 等效 |
| PAID | 買家已支付（Phase 2+ 串接真實金流後的狀態） | 否 | Phase 1 由 Payment Mock 直接標記等效 PAID |
| CONFIRMED | 商家已確認（需支付或時段已鎖定） | 否 | Phase 1 等效於 `CREATED(=PAID)` |
| IN_PROGRESS | 服務/交易進行中 | 否 | 否 |
| COMPLETED | 已完成（服務交付或交易完成） | 是 | 是 |
| CANCELLED | 已取消 | 是 | 是 |
| REFUNDED | 已退款（從 CANCELLED 轉換而來） | 是 | Phase 1 暫不支援，保留為 Phase 2 預留終態 |

**[Phase 1 Payment Mock 約束]**
- Phase 1 支付環節為模擬流程，Order Service 在 `POST /api/orders` 建立訂單時，直接將狀態推進至 `CREATED(=PAID)`——即訂單建立時即等同於已支付，無需等待真實金流回調。
- `PAID` 狀態保留供 Phase 2+ 串接真實金流時使用。
- Phase 1 取消規則以 `CREATED(=PAID)` 為主要取消觸發點（倉庫尚未發貨/房源尚未入住）。

#### 15.3.1.1 Phase 1 取消行為矩陣 (Cancellation Behavior Matrix)
| 當前狀態 | Phase 1 等效狀態 | RETAIL 訂單 (實體商品) | BOOKING 訂單 (民宿房源) | 取消後動作 |
|----------|-----------------|-----------------------|-------------------------|------------|
| CREATED | `CREATED(=PAID)` | 允許取消（倉庫尚未發貨） | 允許取消 | 庫存/日曆格原子釋放（同一 Transaction） |
| CONFIRMED | `CREATED(=PAID)`（等效，**Phase 2+ only**） | **Phase 1 不存在 CONFIRMED 狀態，嚴禁實作**；Phase 2+ 才存在獨立 CONFIRMED 狀態並依 Q14 判斷 | **Phase 2+ only** | Phase 2+ Q14 退款規則（見 §15.3.1） |
| IN_PROGRESS | **Phase 1 不存在此狀態** | **不適用**（Phase 1 Payment Mock 直接跳過 CONFIRMED 和 IN_PROGRESS，CREATED(=PAID) 直接進入 SHIPPING 或 CHECKED_IN） | **不適用**（Phase 1 Payment Mock 直接跳過 CONFIRMED 和 IN_PROGRESS） | 此行空白屬預期行為，Phase 2+ 依據 Q14 判斷 |

#### 15.3.1.2 狀態與資源原子提交宣告 (Atomic Commit)
**[Architecture Rule]**：狀態機的變更 (如 `CREATED -> CANCELLED`) 與資源釋放 (Inventory/Calendar) 必須保證在同一個 Database Transaction 內完成原子提交 (Atomic Commit)。若包含第三方金流互動，需採 Saga Pattern，保證最終一致性。

#### 15.3.2 雙路徑狀態機矩陣

**[Specification] Phase 1 與 Phase 2+ 狀態機差異說明（v0.9_R02_Loop_01 修正）**

> **Phase 1 路徑（Payment Mock）**：
> - **零售**：`CREATED(=PAID)` → `SHIPPING` → `DELIVERED` → `COMPLETED`。Payment Mock 在 `POST /api/orders` 時直接達到 `CREATED(=PAID)` 等效狀態，`payment.received` 事件不存在，`CONFIRMED` 狀態不在 Phase 1 零售路徑中。
> - **預訂**：`CREATED(=PAID)` → `CHECKED_IN` → `CHECKED_OUT` → `COMPLETED`。Phase 1 `CONFIRMED` 等效於 `CREATED(=PAID)`，`CHECKED_IN` 前置抵達後自動確認，無獨立 CONFIRMED 步驟。
>
> **Phase 2+ 路徑（含真實金流）**：完整狀態機如下矩陣所列。

| 目前狀態 | 目標狀態 | RETAIL 觸發事件 | BOOKING 觸發事件 | 允許角色 | Phase 備註 |
|----------|----------|-----------------|-----------------|----------|-----------|
| (init) | CREATED | order.create | order.create | customer | Both |
| CREATED | CONFIRMED | payment.received（Phase 2+） | slot.confirmed（Phase 2+） | merchant | Phase 2+ only；Phase 1 無此轉換 |
| CREATED | CANCELLED | order.cancel | order.cancel | customer/merchant | Both |
| CONFIRMED | IN_PROGRESS | service.start | service.start | merchant | Phase 2+ only |
| CONFIRMED | CANCELLED | order.cancel | order.cancel | customer/merchant | Phase 2+ only |
| IN_PROGRESS | COMPLETED | service.end | service.end | merchant | Phase 2+ only |
| IN_PROGRESS | CANCELLED | — | — | (不允許) | Phase 2+ only |
| CANCELLED | REFUNDED | refund.completed | refund.completed | system | Phase 2+ only |

### 15.4 驗證規則（VR）對 M05 的約束

| VR 編號 | 規則內容 | M05 實作點 |
|---------|----------|-----------|
| VR-M03 | 訂單金額必須與品項加總一致（容許小數精度誤差 ±0.01） | `POST /api/v1/orders` 金額校驗 |
| VR-M04 | 單筆訂單品項數量上限 50 項 | `POST /api/v1/orders` items.length 校驗 |
| VR-M05 | 訂單取消後不可再更新 | `PUT /api/v1/orders/:id` 狀態校驗 |

### 15.5 M05 錯誤碼本（E-XXXX）

| 錯誤碼 | 說明 | HTTP Status |
|--------|------|-------------|
| E-1001 | 資源不存在（slotId, orderId 等） | 404 |
| E-1002 | 訂單不存在 | 404 |
| E-2001 | 未授權（JWT 無效或缺失） | 401 |
| E-2002 | 禁止訪問（無權限） | 403 |
| E-4001 | 請求格式錯誤或必填欄位缺失 | 400 |
| E-4002 | 資源衝突（時段已被預訂） | 409 |
| E-4003 | 庫存不足 | 422 |
| E-4005 | 促銷碼無效或已過期 | 422 |
| E-4006 | 金額校驗失敗（totalAmount 篡改） | 422 |
| E-4007 | 訂單狀態不允許此操作 | 409 |
| E-4008 | 取消被拒（Q14 規則不滿足） | 422 |

---

## 16. OpenAPI 錯誤回應與 API Schema

> **原始文件：** `008_API_Errors.md` (Appendix-A-OpenAPI.md)
> **版本：** v1.0.0 | **日期：** 2026-03-25 | **作者：** Amanda (sa-analyst)
> **狀態：** Phase 1 正式附錄
> **本節完整繼承 v0.8 §11。v0.9 修正：§9.17 全域 API 錯誤格式統一為此處的巢狀 `error` 包裹格式。**

### 16.1 ErrorResponse Schema

#### 16.1.1 完整 ErrorResponse 定義

```yaml
ErrorResponse:
  type: object
  required:
    - error
  properties:
    error:
      type: object
      required:
        - code
        - message
        - timestamp
      properties:
        code:
          type: string
          description: 錯誤碼，格式 E-{類別}{序號}
          example: E-4001
        message:
          type: string
          description: 人類可讀錯誤訊息
          example: "Missing required field: customerId"
        details:
          type: array
          description: 詳細錯誤資訊（選填）
          items:
            type: object
            properties:
              field:
                type: string
                description: 發生錯誤的欄位名稱
                example: "customerId"
              issue:
                type: string
                description: 該欄位的具體問題
                example: "Field is required but not provided"
        requestId:
          type: string
          format: uuid
          description: 請求追蹤 ID，用於日誌關聯
          example: "550e8400-e29b-41d4-a716-446655440000"
        timestamp:
          type: string
          format: date-time
          description: 錯誤發生時間（ISO 8601）
          example: "2026-03-25T01:08:00.000Z"
```

### 16.2 標準 HTTP 錯誤 Responses

#### 16.2.1 401 Unauthorized

```yaml
Unauthorized:
  description: 未授權訪問
  content:
    application/json:
      schema:
        $ref: '#/ErrorResponse'
      example:
        error:
          code: E-2001
          message: "Invalid or expired authentication token"
          details: []
          requestId: "550e8400-e29b-41d4-a716-446655440001"
          timestamp: "2026-03-25T01:08:00.000Z"
```

**觸發條件：**
- JWT 缺失（未在 Authorization header 提供）
- JWT 簽名無效或已過期
- JWT 格式不符合 Bearer token 規範

#### 16.2.2 403 Forbidden

```yaml
Forbidden:
  description: 禁止訪問（無權限）
  content:
    application/json:
      schema:
        $ref: '#/ErrorResponse'
      example:
        error:
          code: E-2002
          message: "Access denied: not authorized for this resource"
          details:
            - field: "merchantId"
              issue: "You do not own this resource"
          requestId: "550e8400-e29b-41d4-a716-446655440002"
          timestamp: "2026-03-25T01:08:00.000Z"
```

**觸發條件：**
- JWT 持有者嘗試訪問不屬於自己的資源
- 商家嘗試查詢/操作其他商家的訂單
- 客户嘗試查詢/操作他人的訂單

#### 16.2.3 404 Not Found

```yaml
NotFound:
  description: 資源不存在
  content:
    application/json:
      schema:
        $ref: '#/ErrorResponse'
      example:
        error:
          code: E-1001
          message: "Slot not found: 123e4567-e89b-12d3-a456-426614174000"
          details:
            - field: "slotId"
              issue: "Slot does not exist or is not available"
          requestId: "550e8400-e29b-41d4-a716-446655440003"
          timestamp: "2026-03-25T01:08:00.000Z"
```

**常見 404 場景：**

| Error Code | 資源類型 | 說明 |
|------------|----------|------|
| E-1001 | slotId | 時段不存在或狀態非 AVAILABLE |
| E-1002 | orderId | 訂單不存在或已刪除 |
| E-1003 | merchantId | 商家不存在或狀態非 ACTIVE |
| E-1004 | customerId | 客户不存在 |

#### 16.2.4 409 Conflict

```yaml
Conflict:
  description: 資源衝突
  content:
    application/json:
      schema:
        $ref: '#/ErrorResponse'
      example:
        error:
          code: E-3001
          message: "Time slot already booked"
          details:
            - field: "slotId"
              issue: "Slot is no longer available"
          requestId: "550e8400-e29b-41d4-a716-446655440004"
          timestamp: "2026-03-25T01:08:00.000Z"
```

**常見 409 場景：**

| Error Code | 衝突類型 | 說明 |
|------------|----------|------|
| E-3001 | 時段衝突 | BOOKING 訂單建立時 slotId 已被佔用 |
| E-3002 | 狀態衝突 | 嘗試在不可取消的狀態下取消訂單 |

### 16.3 Phase 1 API 錯誤碼對應總表

#### 16.3.1 M01 商家模組

| API | HTTP Method | 錯誤碼 | HTTP Status | 觸發條件 |
|-----|-------------|--------|-------------|----------|
| POST /api/v1/merchants | POST | E-2001 | 401 | JWT 無效 |
| POST /api/v1/merchants | POST | E-4001 | 400 | 必填欄位缺失 |
| POST /api/v1/merchants | POST | E-4001 | 400 | 商家名稱不合規（VR-M02） |
| PUT /api/v1/merchants/:id/status | PUT | E-2001 | 401 | JWT 無效 |
| PUT /api/v1/merchants/:id/status | PUT | E-2002 | 403 | 非商家本人或管理員 |
| PUT /api/v1/merchants/:id/status | PUT | E-1003 | 404 | 商家不存在 |
| PUT /api/v1/merchants/:id/status | PUT | E-4001 | 400 | 狀態轉換不合法 |

#### 16.3.2 M02 商品模組

| API | HTTP Method | 錯誤碼 | HTTP Status | 觸發條件 |
|-----|-------------|--------|-------------|----------|
| POST /api/v1/products | POST | E-2001 | 401 | JWT 無效 |
| POST /api/v1/products | POST | E-2002 | 403 | 非商家本人 |
| POST /api/v1/products | POST | E-1003 | 404 | 商家不存在 |
| GET /api/v1/products | GET | E-2001 | 401 | JWT 無效 |
| GET /api/v1/products | GET | E-1003 | 404 | 商家不存在 |

#### 16.3.3 M03 日曆模組

| API | HTTP Method | 錯誤碼 | HTTP Status | 觸發條件 |
|-----|-------------|--------|-------------|----------|
| POST /api/v1/slots | POST | E-2001 | 401 | JWT 無效 |
| POST /api/v1/slots | POST | E-2002 | 403 | 非商家本人 |
| POST /api/v1/slots | POST | E-1003 | 404 | 商家不存在 |
| POST /api/v1/slots | POST | E-4001 | 400 | 時段格式錯誤 |
| GET /api/v1/slots | GET | E-2001 | 401 | JWT 無效 |
| GET /api/v1/slots | GET | E-1003 | 404 | 商家不存在 |

#### 16.3.4 M04 支付模組

| API | HTTP Method | 錯誤碼 | HTTP Status | 觸發條件 |
|-----|-------------|--------|-------------|----------|
| POST /api/v1/payments | POST | E-2001 | 401 | JWT 無效 |
| POST /api/v1/payments | POST | E-1002 | 404 | 訂單不存在 |
| POST /api/v1/payments | POST | E-3002 | 409 | 訂單狀態不允許支付 |
| POST /api/v1/payments | POST | E-4002 | 422 | 支付金額與訂單不符 |
| POST /api/v1/payments | POST | E-5002 | 503 | 支付網關暫時不可用 |

#### 16.3.5 M05 訂單模組

| API | HTTP Method | 錯誤碼 | HTTP Status | 觸發條件 |
|-----|-------------|--------|-------------|----------|
| POST /api/v1/orders | POST | E-2001 | 401 | JWT 無效 |
| POST /api/v1/orders | POST | E-2002 | 403 | 商家未上線（PC-004） |
| POST /api/v1/orders | POST | E-1001 | 404 | slotId 不存在（BOOKING） |
| POST /api/v1/orders | POST | E-3001 | 409 | 時段已被預訂（BOOKING） |
| POST /api/v1/orders | POST | E-4003 | 422 | 庫存不足（RETAIL） |
| POST /api/v1/orders | POST | E-4001 | 400 | 必填欄位缺失 |
| POST /api/v1/orders | POST | E-4012 | 400 | 嘗試建立混合訂單 (MIXED_ORDER_NOT_ALLOWED) |
| POST /api/v1/orders | POST | E-4002 | 422 | 促銷碼無效或已過期 |
| POST /api/v1/orders | POST | E-4006 | 422 | totalAmount 校驗失敗 |
| POST /api/v1/orders | POST | E-4004 | 422 | 前置條件未滿足 |
| GET /api/v1/orders | GET | E-2001 | 401 | JWT 無效 |
| GET /api/v1/orders | GET | E-2002 | 403 | 跨商家查詢 |
| GET /api/v1/orders/:id | GET | E-2001 | 401 | JWT 無效 |
| GET /api/v1/orders/:id | GET | E-2002 | 403 | 無權查看此訂單 |
| GET /api/v1/orders/:id | GET | E-1002 | 404 | 訂單不存在 |
| PUT /api/v1/orders/:id | PUT | E-2001 | 401 | JWT 無效 |
| PUT /api/v1/orders/:id | PUT | E-4001 | 400 | 嘗試更新不允許的欄位（PC-001） |
| PUT /api/v1/orders/:id | PUT | E-1002 | 404 | 訂單不存在 |
| PUT /api/v1/orders/:id | PUT | E-3002 | 409 | 訂單狀態不允許更新 |
| PUT /api/v1/orders/:id/cancel | PUT | E-2001 | 401 | JWT 無效 |
| PUT /api/v1/orders/:id/cancel | PUT | E-1002 | 404 | 訂單不存在 |
| PUT /api/v1/orders/:id/cancel | PUT | E-3002 | 409 | 訂單已取消或已完成 |
| PUT /api/v1/orders/:id/cancel | PUT | E-4008 | 422 | Q14 規則不滿足（取消前 < 24 小時且嘗試套用退款） |
| PUT /api/v1/orders/:id/cancel | PUT | E-4013 | 422 | Phase 1 不支援退款操作 (PHASE1_REFUND_NOT_SUPPORTED) |
| PUT /api/v1/orders/:id/cancel | PUT | E-4014 | 422 | 訂單品項數量超限 (ORDER_ITEMS_EXCEED_LIMIT) |
| GET /api/v1/orders/:id/state-log | GET | E-2001 | 401 | JWT 無效 |
| GET /api/v1/orders/:id/state-log | GET | E-2002 | 403 | 無權查看 |
| GET /api/v1/orders/:id/state-log | GET | E-1002 | 404 | 訂單不存在 |

### 16.4 全域 ErrorResponse 元數據

#### 16.4.1 Standard Headers（所有 Error Response 皆包含）

| Header | 說明 | 範例 |
|--------|------|------|
| X-Request-ID | 請求追蹤 ID，與 error.requestId 一致 | 550e8400-e29b-41d4-a716-446655440000 |
| X-Content-Type-Options | 固定值 | nosniff |
| X-Frame-Options | 點擊劫持防護 | DENY |
| Content-Type | 固定值 | application/json; charset=utf-8 |

#### 16.4.2 Rate Limiting 錯誤（429 Too Many Requests）

```yaml
TooManyRequests:
  description: 請求頻率超限
  headers:
    X-RateLimit-Limit:
      description: 速率限制閾值
      schema:
        type: integer
    X-RateLimit-Remaining:
      description: 剩餘請求配額
      schema:
        type: integer
    X-RateLimit-Reset:
      description: 配額重置時間戳（Unix epoch）
      schema:
        type: integer
    Retry-After:
      description: 建議等待秒數
      schema:
        type: integer
  content:
    application/json:
      schema:
        $ref: '#/ErrorResponse'
      example:
        error:
          code: E-6001
          message: "Rate limit exceeded. Please retry after 60 seconds."
          details: []
          requestId: "550e8400-e29b-41d4-a716-446655440005"
          timestamp: "2026-03-25T01:08:00.000Z"
```

---

## 17. 勘誤與補正記錄（Errata）

> **原始文件：** `011_Supporting_Errata.md` (Errata-01.md)
> **版本：** v1.0.0 | **日期：** 2026-03-25 | **作者：** Amanda (sa-analyst)
> **適用版本：** E-Commerce_Spec v0.7
> **本節完整繼承 v0.8 §12。**

### 17.1 勘誤摘要

本章為 E-Commerce_Spec 的正式勘誤補正記錄，基於覆檢過程中發現的 8 項缺口（3 MUST + 5中高優先級）所建立。所有補正內容與原規格具有同等約束力。

**補正項目清單：**

1. G-1：M05 模組獨立 SDD 規格（→ §15）
2. G-2：RTM 文件建立（→ §18）
3. G-3：Errata #1 正式成文（本章）
4. G-4：附錄A OpenAPI（→ §16）
5. G-5：附錄B Sprint Entry Criteria（→ §19）
6. G-6：Q14 Decision Record（→ §17.4）
7. G-7：Ken 人物誌旅程節點補正（原規格 §7.2 更新）
8. G-8：M06 Phase 歸屬歧義消除（原規格 §6, §9.7, §13.1 更新）

### 17.2 錯誤碼統一映射表

#### 17.2.1 錯誤碼結構

所有 Phase 1 API 錯誤碼採用 E-{類別}{序號} 格式，共 8 組。

#### 17.2.2 錯誤碼映射表

| 錯誤碼 | HTTP Status | 錯誤類別 | 說明 | 發生時機 | 範例訊息 |
|--------|-------------|----------|------|----------|----------|
| E-1001 | 404 | 資源不存在 | 指定的資源（slotId, productId 等）不存在 | GET/POST 引用無效 ID | "Slot not found: {slotId}" |
| E-1002 | 404 | 訂單不存在 | 訂單 ID 不存在或已刪除 | GET/PUT /orders/:id | "Order not found: {orderId}" |
| E-1003 | 404 | 商家不存在 | 商家 ID 不存在或狀態非 ACTIVE | GET/POST 引用無效商家 | "Merchant not found or inactive" |
| E-1004 | 404 | 客户不存在 | 客户 ID 不存在 | GET 引用無效客户 | "Customer not found: {customerId}" |
| E-2001 | 401 | 未授權 | JWT 缺失、過期或簽名無效 | 所有需認證 API | "Invalid or expired authentication token" |
| E-2002 | 403 | 禁止訪問 | JWT 持有者無權執行此操作；跨租戶操作（X-Tenant-ID 不在成員清單）被拒絕 | 跨商家/跨用戶操作；多租戶跨tenant操作 | "Access denied: not authorized for this resource" |
| E-2003 | 400 | 租戶上下文未指定 | 多租戶用戶未透過 `X-Tenant-ID` Header 明確指定操作目標租戶 | B 端 API 多租戶場景 | "Your account is associated with multiple tenants. Please specify X-Tenant-ID header to identify the target tenant." |
| E-2020 | 403 | 租戶功能未啟用 | 嘗試使用該租戶未開通的 Feature Toggle 功能（如 `BOOKING_ENABLED = false` 時建立房源；`RETAIL_ENABLED = false` 時上架商品）| B 端 Feature Toggle 限制場景 | "房源管理功能尚未啟用，請聯繫平台管理員。" |
| E-3001 | 409 | 資源衝突 | 時段已被預訂或商品庫存衝突 | POST /orders (BOOKING) | "Time slot already booked" |
| E-3002 | 409 | 狀態衝突 | 訂單當前狀態不允許此操作 | PUT /orders/:id/cancel | "Order cannot be cancelled in current state" |
| E-4001 | 400 | 請求格式錯誤 | 必填欄位缺失或類型錯誤；PostEmbed 嵌入重複 listing_id | 所有 POST/PUT | "Missing required field: {field}" |
| E-4002 | 422 | 商業規則衝突 | 促銷碼無效、金額校驗失敗等 | POST /orders | "Promotion code invalid or expired" |
| E-4003 | 422 | 庫存不足 | 一般庫存不足；並發庫存扣減時超額（樂觀鎖重試耗盡仍失敗）| POST /orders (RETAIL) | 一般：`"Insufficient stock for product: {productId}"`；並發超額重試耗盡：`"INSUFFICIENT_STOCK_DUE_TO_CONCURRENT_ACCESS: 庫存不足，請稍後重試"` |
| E-4004 | 422 | 預條件未滿足 | 前置條件（preconditions）未滿足 | POST /orders | "Precondition not met: {condition}" |
| E-4012 | 400 | 混合商品 | Phase 1 不支援在一筆訂單內混搭商品與房源 | POST /orders | "Mixed order not allowed" |
| E-4013 | 422 | 退款不支援 | Phase 1 尚未實裝退款金流 | PUT /orders/:id/cancel | "Refund not supported in Phase 1" |
| E-4014 | 422 | 訂單品項超限 | 單筆訂單品項數量超過 50 項上限 | POST /orders | "Order items exceed limit of 50" |
| E-5001 | 500 | 系統錯誤 | 內部系統錯誤（非業務邏輯） | 所有 API | "Internal server error" |
| E-5002 | 503 | 服務不可用 | 依賴服務（DB/支付/通知）暫時不可用 | 所有 API | "Service temporarily unavailable" |

#### 17.2.3 標準錯誤 Response Schema

```json
{
  "error": {
    "code": "E-XXXX",
    "message": "string (人類可讀錯誤訊息)",
    "details": [
      {
        "field": "string (若適用於欄位級錯誤)",
        "issue": "string"
      }
    ],
    "requestId": "string (UUID，用於日誌追蹤)",
    "timestamp": "string (ISO 8601)"
  }
}
```

### 17.3 User Story 清單（US-001~US-012）

#### 17.3.1 預訂買家相關

**US-001：快速預訂時段**
- **作為** 預訂買家 **我希望** 快速選擇時段並完成預訂 **以便** 節省時間
- **驗收標準：** 從進站到完成預訂 ≤ 5 分鐘；最多 3 步驟完成預訂；預訂成功後即時收到確認通知
- **對應：** API `POST /api/v1/orders` | BV-04 | TC-M05-002

**US-002：即時確認與憑證**
- **作為** 預訂買家 **我希望** 收到即時確認與電子憑證 **以便** 確保預訂已成立
- **驗收標準：** 訂單建立後 3 秒內回傳確認；回應包含完整訂單詳情與 orderId
- **對應：** API `POST /api/v1/orders` | BV-01 | TC-M05-002

**US-005：靈活取消與退款**
- **作為** 預訂買家 **我希望** 在需要時取消預訂並依政策獲得退款 **以便** 因應行程變更
- **驗收標準：** 取消前 ≥ 24h 全額退款；取消前 < 24h 不退款（Q14）；取消後即時收到退款狀態通知
- **對應：** API `PUT /api/v1/orders/:id/cancel` | BV-03 | TC-M05-016, TC-M05-017

**US-006：查詢預訂狀態與歷史**
- **作為** 預訂買家 **我希望** 隨時查詢預訂狀態與完整狀態日誌 **以便** 掌握行程進度
- **驗收標準：** 可查詢任意時間範圍內的訂單；狀態日誌包含所有狀態變更的 timestamp 與觸發者
- **對應：** API `GET /api/v1/orders`, `GET /api/v1/orders/:id/state-log` | TC-M05-008, TC-M05-019

**US-009：修改預訂時間（Phase 2）**
- **作為** 預訂買家 **我希望** 修改已確認的預訂時間 **以便** 因應臨時行程變動
- **驗收標準：** 僅允許在原時段 24h 前修改；不可修改已完成的預訂
- **對應 Phase：** Phase 2（PC-001 約束）

**US-010：使用優惠券**
- **作為** 預訂買家 **我希望** 在結帳時套用優惠碼 **以便** 節省費用
- **驗收標準：** 促銷碼正確套用並即時反映在 totalAmount；無效或過期時明確提示
- **對應：** API `POST /api/v1/orders` | TC-M05-005

**US-011：系統自動提醒（Phase 2）**
- **作為** 預訂買家 **我希望** 在預訂時段前收到系統提醒 **以便** 不忘記預訂行程
- **驗收標準：** 預訂前 24h 自動發送提醒；支援 Email / SMS / Push 三種渠道
- **對應 Phase：** Phase 2

**US-012：明瞭取消政策**
- **作為** 預訂買家 **我希望** 在預訂前清楚了解取消與退款政策 **以便** 做出明智的預訂決定
- **驗收標準：** 系統在確認預訂前顯示 Q14 取消政策摘要；取消時清楚說明是否可獲得退款
- **對應：** API `PUT /api/v1/orders/:id/cancel` | Q14 | TC-M05-016, TC-M05-017

#### 17.3.2 零售买家相關

**US-008：合併結帳**
- **作為** 零售买家 **我希望** 在一次結帳中購買多個品項 **以便** 一次購完所需物品
- **驗收標準：** 單筆訂單可包含來自同一商家的多個品項；Phase 1 不支援跨商家訂單（PC-005）
- **對應：** API `POST /api/v1/orders` | PC-005 | TC-M05-001

#### 17.3.3 商家相關

**US-003：系統引導上線**
- **作為** 新商家 **我希望** 系統引導我完成上線流程 **以便** 快速開始營業
- **驗收標準：** 商家 PENDING → ACTIVE 有明確觸發條件；7 日新用戶門檻可追蹤與顯示
- **對應：** API `POST /api/v1/merchants` | PC-004 | TC-M01-010

**US-004：訂單儀表板**
- **作為** 商家 **我希望** 在後台看到即時訂單狀態 **以便** 掌握營運狀況
- **驗收標準：** 可查詢所有與本人相關的訂單；狀態即時更新；不可查詢其他商家訂單
- **對應：** API `GET /api/v1/orders` | TC-M05-008, TC-M05-009

**US-007：流暢支付體驗**
- **作為** 買家 **我希望** 支付流程流暢無阻 **以便** 順利完成交易
- **驗收標準：** 首次支付成功率 ≥ 95%；支付失敗時有明確錯誤提示與重試指引
- **對應：** API `POST /api/v1/payments` | BV-05 | TC-M04-001

**US-014：支付失敗通知提醒**
- **作為** 買家 **我希望** 支付失敗或超時後收到明確的通知 **以便** 重新進行支付
- **驗收標準：** 若支付超時或金流阻斷，系統發送 Email/Push 通知提示訂單未完成，並提供重試連結。
- **對應：** M09 模組 | TC-M04-003

### 17.4 Decision Record（決策記錄）

#### 17.4.1 Q1：商家上線門檻

| 欄位 | 內容 |
|------|------|
| Decision ID | Q1 |
| 決策主題 | 商家上線門檻設定 |
| 決策日期 | 2026-03-25 |
| 決策者 | Koala, Victoria, Amanda, S-David |
| 選項評估 | 門檻：0 新用戶 / 3 日新用戶 / **7 日新用戶（選擇）** / 14 日新用戶 |
| **最終決策** | **7 日內有新用戶** |
| 理由 | 平衡商家品質與上線門檻；7 日足以驗證商家真實需求，又不會過度延長上線時間 |
| Spec Clause | §3.2, §6.1, PC-004, RTM-A-004 |

#### 17.4.2 Q2：預設時段類型

| 欄位 | 內容 |
|------|------|
| Decision ID | Q2 |
| 決策主題 | Phase 1 預設時段類型 |
| **最終決策** | **SLOT（日曆時段）** |
| 理由 | SLOT 為日曆直觀呈現，適合預訂型服務；Phase 1 聚焦核心預訂流程 |
| Spec Clause | §3.2, PC-001, RTM-A-001 |

#### 17.4.3 Q5：支付架構模式

| 欄位 | 內容 |
|------|------|
| Decision ID | Q5 |
| 決策主題 | 支付架構模式 |
| **最終決策** | **payment-ready（先支付後確認）** |
| 理由 | 避免支付成功但時段被搶走的矛盾；確保CREATED → CONFIRMED 狀態轉換的確定性 |
| Spec Clause | §7.4, EC-M03, RTM-B-008 |

#### 17.4.4 Q9：庫存管理模式

| 欄位 | 內容 |
|------|------|
| Decision ID | Q9 |
| 決策主題 | 庫存管理模式與超賣防範策略 |
| **最終決策** | **超賣防範：RETAIL 訂單建立時必須校驗可用庫存** |
| 理由 | 避免 Retail 模式下超賣導致客訴；BOOKING 模式下 slot 本身具有天然隔離性 |
| Spec Clause | §7.2, EC-M04, RTM-B-009 |

#### 17.4.5 Q14：取消補償邊界

| 欄位 | 內容 |
|------|------|
| Decision ID | Q14 |
| 決策主題 | 取消補償邊界與退款規則 |
| 決策日期 | 2026-03-25 |
| 決策者 | Koala, Victoria, Amanda, S-David |
| 選項評估 | 取消補償：**24 小時邊界（選擇）** / 48 小時邊界 / 全額退款 / 不可取消 |
| **最終決策** | **取消前 ≥ 24 小時：全額退款；取消前 < 24 小時：不退款** |
| 理由 | 24 小時為服務業通用門檻；兼顧商家損失控制與買家合理權益 |
| Spec Clause | §7.5, M05-§2.5, RTM-DR-005 |
| 衝突買家場景 | Ken（預訂買家）案例：預訂後 2 小時決定取消，因時間太短不符合 24 小時門檻，無法獲得退款 |
| 痛感節點對應 | Ken §7.2「回覆不及時」痛點加劇因素 |

### 17.5 前置條件措辭修正

原規格中 `POST /api/v1/orders` 的前置條件措辭存在模糊性，修正後精確表述已整合至 §15.2.1。

**影響 API：** `POST /api/v1/orders`
**影響 TC：** TC-M05-001~TC-M05-007
**Spec Clause 更新：** §9.6

### 17.6 Errata 狀態追蹤

| Errata ID | 補正項目 | 狀態 | 整合位置 | 備註 |
|-----------|---------|------|----------|------|
| G-1 | M05 獨立 SDD 規格 | ✅ 已完成 | §15 | |
| G-2 | RTM 文件 | ✅ 已完成 | §18 | |
| G-3 | Errata #1 成文 | ✅ 已完成 | §17 | 本章 |
| G-4 | 附錄A OpenAPI | ✅ 已完成 | §16 | |
| G-5 | 附錄B Sprint Entry | ✅ 已完成 | §19 | |
| G-6 | Q14 Decision Record | ✅ 已完成 | §17.4.5 | |
| G-7 | Ken 人物誌補正 | ✅ 已整合 | §7.2 Seller | v0.8 已補正 |
| G-8 | M06 Phase 歧義消除 | ✅ 已整合 | §6.4, §9.7, §13.1 | v0.8 已補正 |

---

## 18. 需求追蹤矩陣（RTM）

> **原始文件：** `010_Supporting_RTM.md` (RTM.md)
> **版本：** v1.0.0 | **日期：** 2026-03-25 | **作者：** Amanda (sa-analyst)
> **狀態：** Phase 1 正式文件
> **本節完整繼承 v0.8 §13。**

### 18.1 概覽

本章為 NextKeyAI E-Commerce System Phase 1 完整需求追蹤矩陣（Requirements Traceability Matrix），涵蓋 4 大類別共 43 條追蹤項目，確保每條需求均可追溯至規格條款、API 端點及測試案例。

### 18.2 類別 A：Phase 1 強制約束（PC）

| RTM-ID | 需求編號 | 需求內容 | Spec Clause | API Endpoint | Test Cases | Priority | Status |
|--------|----------|----------|-------------|---------------|-------------|----------|--------|
| RTM-A-001 | PC-001 | Phase 1 僅支援「日曆時段」預設類型（slotType=SLOT） | §3.2, §13.1 | POST /api/v1/orders | TC-M05-013, TC-M03-001 | MUST | TRACED |
| RTM-A-002 | PC-002 | Phase 1 不支援線上預覽與即時排程 | §3.2, §13.1 | N/A（功能約束） | TC-M03-005 | MUST | TRACED |
| RTM-A-003 | PC-003 | Phase 1 不支援差異化時段費率 | §3.2, §13.1 | POST /api/v1/orders | TC-M05-001 | MUST | TRACED |
| RTM-A-004 | PC-004 | Phase 1 商家上線門檻：7 日內有新用戶 | §3.2, §13.1 | POST /api/v1/orders | TC-M01-010 | MUST | TRACED |
| RTM-A-005 | PC-005 | Phase 1 不支援跨商家/跨品項優惠 | §3.2, §13.1 | POST /api/v1/orders | TC-M05-005 | MUST | TRACED |

**類別 A 覆核總結：** 5/5 條款已追蹤，覆核率 100%。

### 18.3 類別 B：業務驗證規則（VR / LR / EC）

#### 18.3.1 VR — 驗證規則（Validation Rules）

| RTM-ID | 需求編號 | 需求內容 | Spec Clause | API Endpoint | Test Cases | Priority | Status |
|--------|----------|----------|-------------|---------------|-------------|----------|--------|
| RTM-B-001 | VR-M01 | 商家 ID 格式必須為有效 UUID v4 | §4.1 | All M01 APIs | TC-M01-001 | MUST | TRACED |
| RTM-B-002 | VR-M02 | 商家名稱長度 2~100 字元，不可為純空白 | §4.1 | POST /api/v1/merchants | TC-M01-002 | MUST | TRACED |
| RTM-B-003 | VR-M03 | 訂單金額必須與品項加總一致（容許 ±0.01 誤差） | §9.6, M05-§4 | POST /api/v1/orders | TC-M05-007 | MUST | TRACED |
| RTM-B-004 | VR-M04 | 單筆訂單品項數量上限 50 項 | §9.6, M05-§4 | POST /api/v1/orders | TC-M05-004 | MUST | TRACED |
| RTM-B-005 | VR-M05 | 訂單取消後不可再更新 | §9.6, M05-§2.4 | PUT /api/v1/orders/:id | TC-M05-014 | MUST | TRACED |

#### 18.3.2 LR — 生命週期規則（Lifecycle Rules）

| RTM-ID | 需求編號 | 需求內容 | Spec Clause | API Endpoint | Test Cases | Priority | Status |
|--------|----------|----------|-------------|---------------|-------------|----------|--------|
| RTM-B-006 | LR-M01 | 商家狀態變更：PENDING → ACTIVE（通過門檻） | §6.2 | PUT /api/v1/merchants/:id/status | TC-M01-011 | HIGH | TRACED |
| RTM-B-007 | LR-M02 | 訂單狀態機：CREATED → CONFIRMED → COMPLETED/CANCELLED | §9.6, M05-§3 | PUT /api/v1/orders/:id/cancel | TC-M05-015~018 | MUST | TRACED |

#### 18.3.3 EC — 錯誤處理約束（Error Constraints）

| RTM-ID | 需求編號 | 需求內容 | Spec Clause | API Endpoint | Test Cases | Priority | Status |
|--------|----------|----------|-------------|---------------|-------------|----------|--------|
| RTM-B-008 | EC-M03 | 支付失敗時，訂單狀態不得變更為 CONFIRMED | §9.8 | POST /api/v1/payments | TC-M04-003 | HIGH | TRACED |
| RTM-B-009 | EC-M04 | 庫存不足時，訂單建立必須回傳 422 E-4003 | §9.3, §9.6 | POST /api/v1/orders | TC-M05-004 | MUST | TRACED |
| RTM-B-010 | EC-M05 | 時段衝突時，訂單建立必須回傳 409 E-4002 | §9.6 | POST /api/v1/orders | TC-M05-003 | MUST | TRACED |

**類別 B 覆核總結：** 10/10 條款已追蹤，覆核率 100%。

### 18.4 類別 C：技術效能基準（P0）

| RTM-ID | 需求編號 | 需求內容 | Spec Clause | API Endpoint | Test Cases | Priority | Status |
|--------|----------|----------|-------------|---------------|-------------|----------|--------|
| RTM-C-001 | P0-01 | API 回應時間 P95 ≤ 200ms（清單查詢） | §13.4 | GET /api/v1/orders | TC-PERF-001 | MUST | TRACED |
| RTM-C-002 | P0-02 | API 回應時間 P95 ≤ 500ms（複雜查詢含 JOIN） | §13.4 | GET /api/v1/orders/:id/state-log | TC-PERF-002 | MUST | TRACED |
| RTM-C-003 | P0-03 | 系統可用性 ≥ 99.5%（月正常運行時間） | §13.4 | All APIs | TC-PERF-003 | MUST | TRACED |
| RTM-C-004 | P0-04 | 並發支援：支援同時 100 個活躍預訂連線 | §13.4 | POST /api/v1/orders (booking) | TC-PERF-004 | HIGH | TRACED |
| RTM-C-005 | P0-05 | 資料持久性：每筆交易完成後 1 秒內寫入資料庫 | §13.4 | All write APIs | TC-PERF-005 | MUST | TRACED |
| RTM-C-006 | P0-06 | 錯誤恢復：系統崩潰後 30 秒內自動恢復 | §13.4 | N/A（可用性設計） | TC-PERF-006 | HIGH | TRACED |

**類別 C 覆核總結：** 6/6 條款已追蹤，覆核率 100%。

### 18.5 類別 D：商業價值錨點（BV）

| RTM-ID | 需求編號 | 需求內容 | Spec Clause | 對應 User Story | 驗證方式 | Priority | Status |
|--------|----------|----------|-------------|-----------------|----------|----------|--------|
| RTM-D-001 | BV-01 | 預訂完成率（BOOKING 類型從 CREATED → COMPLETED）≥ 80% | §13.4 | US-001, US-002 | 系統儀表板統計 | MUST | TRACED |
| RTM-D-002 | BV-02 | 商家上線後 7 日內平均訂單量 ≥ 5 單 | §13.4 | US-003, US-004 | 商家後台統計 | HIGH | TRACED |
| RTM-D-003 | BV-03 | 取消率（從 CREATED → CANCELLED）≤ 15% | §13.4 | US-005 | 系統儀表板統計 | HIGH | TRACED |
| RTM-D-004 | BV-04 | 用戶從進站到完成預訂 ≤ 5 分鐘 | §13.4 | US-001, US-006 | UX 埋點統計 | MUST | TRACED |
| RTM-D-005 | BV-05 | 支付成功率 ≥ 95%（首次支付即成功） | §13.4 | US-007 | 支付系統統計 | MUST | TRACED |

**類別 D 覆核總結：** 5/5 條款已追蹤，覆核率 100%。

### 18.6 User Story 追蹤矩陣

| RTM-ID | US 編號 | User Story | Accep. Criteria | 對應 API | Test Cases | Priority |
|--------|---------|------------|------------------|----------|-------------|----------|
| RTM-US-001 | US-001 | 作為預訂買家，我希望快速預訂時段，以便節省時間 | 5 分鐘內完成預訂 | POST /api/v1/orders | TC-M05-002 | MUST |
| RTM-US-002 | US-002 | 作為預訂買家，我希望收到即時確認，以便安心 | 訂單建立後 3 秒內收到確認 | POST /api/v1/orders | TC-M05-002 | MUST |
| RTM-US-003 | US-003 | 作為新商家，我希望系統引導上線，以便快速開始營業 | 7 日新用戶門檻可追蹤 | POST /api/v1/merchants | TC-M01-010 | MUST |
| RTM-US-004 | US-004 | 作為新商家，我希望看到訂單儀表板，以便掌握營運狀況 | 可查詢即時訂單狀態 | GET /api/v1/orders | TC-M05-008 | MUST |
| RTM-US-005 | US-005 | 作為預訂買家，我希望取消訂單，以便因應變更 | 24 小時外可取消並退款 | PUT /api/v1/orders/:id/cancel | TC-M05-016, TC-M05-017 | MUST |
| RTM-US-006 | US-006 | 作為預訂買家，我希望查看預訂狀態，以便掌握行程 | 可查詢完整狀態日誌 | GET /api/v1/orders/:id/state-log | TC-M05-019 | MUST |
| RTM-US-007 | US-007 | 作為預訂買家，我希望支付流暢，以便完成交易 | 支付成功率 ≥ 95% | POST /api/v1/payments | TC-M04-001 | MUST |
| RTM-US-008 | US-008 | 作為零售买家，我希望合併結帳，以便一次購完 | 一次結帳多品項 | POST /api/v1/orders | TC-M05-001 | HIGH |
| RTM-US-009 | US-009 | 作為預訂買家，我希望修改預訂時間，以便因應變動 | Phase 2 需求（見§13.1） | PUT /api/v1/orders/:id | TC-M05-013 (negative) | MEDIUM |
| RTM-US-010 | US-010 | 作為預訂買家，我希望使用優惠券，以便節省費用 | 促銷碼正確套用 | POST /api/v1/orders | TC-M05-005 | HIGH |
| RTM-US-011 | US-011 | 作為預訂買家，我希望系統自動提醒，以便不忘記行程 | 預訂前 24 小時提醒 | POST /api/v1/bookings/:id/remind | (Phase 2) | MEDIUM |
| RTM-US-012 | US-012 | 作為預訂買家，我希望查看取消政策，以便明瞭退款規則 | 明確的 Q14 補償邊界 | PUT /api/v1/orders/:id/cancel | TC-M05-016, TC-M05-017 | HIGH |
| RTM-US-014 | US-014 | 作為買家，我希望收到支付失敗通知，以便重新操作 | 明確的超時與失敗重試指引 | M09 整合 | TC-M04-003 | HIGH |

#### 17.3.2 Guest 相關 User Story（v0.9 新增）

| RTM-ID | US 編號 | User Story | Accep. Criteria | 對應 API | Test Cases | Priority |
|--------|---------|------------|------------------|----------|-------------|----------|
| RTM-US-G1 | US-G1 | 作為 Guest，**我希望**在未登入的情況下瀏覽商品與房源，以便在決定是否註冊前先評估平台內容品質 | Guest 可完整瀏覽所有商品列表、房源日曆、定價；所有加入購物車/建立訂單/即時通訊的按鈕點擊後引導至登入/註冊頁 | N/A（功能約束） | TC-G1-001、TC-G1-002（v0.9_R02_Loop_01 新增） | MUST |
| RTM-US-G2 | US-G2 | 作為 Guest，**我希望**用 Email + 密碼 3 步驟完成註冊，以便快速從 Guest 轉為 Buyer 開始購物 | 註冊流程從進站到完成註冊 ≤ 2 分鐘；驗證 Email 即時發送；註冊完成後自動登入並回到原頁面 | POST /api/auth/register | TC-G2-001（v0.9_R02_Loop_01 新增） | MUST |

**[Test Cases] Guest User Story（v0.9_R02_Loop_01 新增）**

| 編號 | 測試案例 | GIVEN | WHEN | THEN |
|------|---------|-------|------|------|
| TC-G1-001 | Guest 瀏覽權限 — 公開列表不需 JWT | 未登入 Guest | 訪問 `GET /api/v2/listings`、`GET /api/products`、`GET /api/rooms` | HTTP 200，回傳所有公開可見的 listings/products/rooms，不需 JWT |
| TC-G1-002 | Guest 寫入操作被引導至登入 | 未登入 Guest | 嘗試 `POST /api/orders`、`POST /api/cart/items` 或訪問 `/api/v2/dashboard/*` | HTTP 401（或引導至登入頁），不回傳任何買家資料 |
| TC-G2-001 | Guest 3 步驟註冊 → Buyer 自動授予 | 用戶提交有效 Email + 密碼註冊表 | 系統處理 `POST /api/auth/register` | 3 分鐘內帳號 ACTIVE，自動賦予 Buyer 角色，JWT 回傳後自動登入<br>**Phase 1 限制**：真實 Email 通知屬 Phase 2，Phase 1 為 Mock Email；TC 需標注此限制 |

### 18.7 Decision Record 追蹤矩陣

| RTM-ID | Decision ID | 決策主題 | Spec Clause | 約束條款 | Status |
|--------|-------------|----------|-------------|----------|--------|
| RTM-DR-001 | Q1 | 商家上線門檻：7 日內有新用戶 | §3.2, §6.1 | PC-004 | TRACED |
| RTM-DR-002 | Q2 | 預設時段類型：日曆時段（SLOT） | §3.2 | PC-001 | TRACED |
| RTM-DR-003 | Q5 | 支付架構模式：payment-ready | §9.8 | EC-M03 | TRACED |
| RTM-DR-004 | Q9 | 庫存管理模式：超賣防範策略 | §9.3 | EC-M04 | TRACED |
| RTM-DR-005 | Q14 | 取消補償邊界：24 小時退款規則 | §9.6, M05-§2.5 | E-4008 | TRACED |

### 18.8 RTM 覆核摘要

| 類別 | 追蹤項目數 | 已追蹤數 | 覆核率 |
|------|-----------|---------|--------|
| A：強制約束（PC） | 5 | 5 | 100% |
| B：業務驗證（VR/LR/EC） | 10 | 10 | 100% |
| C：技術效能（P0） | 6 | 6 | 100% |
| D：商業價值（BV） | 5 | 5 | 100% |
| **合計** | **26** | **26** | **100%** |

| 類別 | 追蹤項目數 | 已追蹤數 | 覆核率 |
|------|-----------|---------|--------|
| User Stories（US） | 12 | 12 | 100% |
| Decision Records（Q） | 5 | 5 | 100% |
| **合計** | **17** | **17** | **100%** |

**RTM 總覆核率口徑說明（v0.9_R02_Loop_02 更新）：**
- 基礎追蹤矩陣：43/43 條款（基礎覆核率 100%）
- Guest User Stories（US-G1、US-G2）TC 欄位原為 TBD，Loop 1 已補齊（TC-G1-001、TC-G1-002、TC-G2-001），現已完整覆核。
- M12/M15/M16/M17 Phase 2-A 專項 TC（Loop 2 新增）：12 項 TC 已補入 §18.9。
- RTM 覆核率：43/43（100%）

---

## 18.9 Phase 2-A M12/M15/M16/M17 專項 Test Cases（v0.9_R02_Loop_02 新增）

> **說明**：本節為 S-Quincy QA Loop 2 審查發現的 M12/M15/M16/M17 模組防禦機制測試案例，基於 QA Review 02 §缺失的 Test Cases 段落，共 12 項，全部已由 Victoria 確認並補入 PRD。

### 18.9.1 M12 動態定價 Test Cases

| 編號 | 測試案例 | GIVEN | WHEN | THEN |
|------|---------|-------|------|------|
| TC-LO2-M12-001 | pricing_rules 50 條 active 上限 | Room1 已有 50 條 active pricing_rules | Host 嘗試建立第 51 條規則（`POST /api/v2/dashboard/pricing-rules`） | 系統回傳 `400 E-4001 RULE_LIMIT_EXCEEDED`，`message: "每間房的定價規則上限為 50 條，請先停用舊規則後再新增"`；`pricing_rules` 表無新增記錄 |
| TC-LO2-M12-002 | pricing_rules 時間範圍重疊衝突檢核 | Room1 已有 active WEEKDAY_WEEKEND 規則（valid_from=2026-01-01, valid_to=2026-12-31） | Host 建立另一條 WEEKDAY_WEEKEND 規則（valid_from=2026-06-01, valid_to=2026-08-31） | 系統回傳 `400 E-4001`，`message: "新規則與現有同類型規則時間範圍重疊，請先編輯現有規則的有效期間"` |
| TC-LO2-M12-003 | pricing_rules 覆蓋後歷史可查（gap 回退 base_price） | Room1 的 WEEKDAY_WEEKEND 規則 RuleA（2026 全年）被軟刪除，RuleB 為新規則（2026 下半年） | 2026-03-15 查詢定價日曆 | 使用 `listing.base_price`（因為 RuleB 的 valid_from=2026-06-01，覆蓋前日期無 active 規則） |
| TC-LO2-M12-004 | pricing_preview API 過去日期校驗 | 當前日期為 2026-03-30 | Host 嘗試 `POST /api/v2/dashboard/rooms/:id/pricing-preview`（startDate=2026-03-01, endDate=2026-05-31） | 系統回傳 `400 E-4001`，`message: "定價預覽不支援過去日期"` |

### 18.9.2 M15 CMS Test Cases

| 編號 | 測試案例 | GIVEN | WHEN | THEN |
|------|---------|-------|------|------|
| TC-LO2-M15-001 | PostEmbed 刪除後 Cascade 行為 | Post A 嵌入了 Listing L（`post_embeds` 已有記錄） | Seller 刪除 Listing L（`listings.status = DELETED`） | `post_embeds` 中關聯 L 的記錄自動刪除（CASCADE）；`GET /api/v2/posts/A` 不顯示嵌入卡片，直接跳過該嵌入位置 |
| TC-LO2-M15-002 | PostEmbed 嵌入已下架 Listing 的卡片呈現 | Post A 嵌入了 Listing L（L 已改為 `INACTIVE`） | C 端用戶訪問 `GET /api/v2/posts/A` | 嵌入卡片位置顯示「此商品已下架」，`ctaUrl = '/stores/{slug}'`，不回傳 404 |
| TC-LO2-M15-003 | PostEmbed 嵌入語法重複校驗 | 編輯貼文內容，`{{embed:listing:550e8400-...}}` 出現兩次 | `PUT /api/v2/dashboard/posts/:id` | 系統在寫入前回傳 `400 E-4001`，`message: "EMBED_DUPLICATE_LISTING: listing_id 550e8400-... appears more than once in this post content"` |

### 18.9.3 M16 ERP Test Cases

| 編號 | 測試案例 | GIVEN | WHEN | THEN |
|------|---------|-------|------|------|
| TC-LO2-M16-001 | PurchaseOrder receive 的 Transaction Atomic 測試 | 採購單 PO1（3項品項），第一項品項 SKU-001 的 `receive` 時系統中途崩潰（在 StockMovement 寫入後、inventory 更新前） | 重啟後查詢 PO1 狀態和 SKU-001 的 `product_inventory.total_qty` | PO1 狀態仍為 `SUBMITTED`，`total_qty` 未增加，無孤立的 StockMovement INBOUND 記錄 |
| TC-LO2-M16-002 | PurchaseOrder 部分收貨後取消 | 採購單 PO1（3項品項），第一項 SKU-001 已 `received_qty=10` | StoreOwner 嘗試 cancel PO1 | 系統拒絕，回傳 `409 E-4007`，`message: "Cannot cancel partially received purchase order"`；PO1 狀態保持 `PARTIAL_RECEIVED` |

### 18.9.4 M17 租戶管理 Test Cases

| 編號 | 測試案例 | GIVEN | WHEN | THEN |
|------|---------|-------|------|------|
| TC-LO2-M17-001 | 新建租戶時 Feature Toggle 自動初始化 | Admin 通過新店鋪申請（`tenants.status` → `ACTIVE`） | 查詢 `tenant_feature_toggles` 表 | 自動生成所有 Feature Toggle 的預設值紀錄（`RETAIL_ENABLED=true`, `BOOKING_ENABLED=false` 等）；Host 嘗試建立房源（`listingType=ROOM`）時，正常被 `BOOKING_ENABLED=false` 阻擋 |
| TC-LO2-M17-002 | 多租戶切換時 Feature Toggle 各自獨立 | David 同時隸屬 TenantA（`BOOKING_ENABLED=true`）和 TenantB（`BOOKING_ENABLED=false`） | David 分別以 `X-Tenant-ID: TenantA` 和 `X-Tenant-ID: TenantB` 訪問 `POST /api/v2/dashboard/listings` | TenantA 可成功建立 ROOM 類型 Listing；TenantB 回傳 `403 E-2020 FEATURE_DISABLED_FOR_TENANT` |
| TC-LO2-M17-003 | MAINTENANCE 狀態下的 Booking，Admin Dashboard 緊急警告 | Booking B001 的入住日期在 12 小時後，`room_calendar.status` 被改為 `MAINTENANCE`，`booking_id = B001` | Admin 登入 Dashboard | `MaintenanceWarnings` 列表顯示 B001，紅色標注「緊急：入住前 24 小時」，顯示房客 Email 供人工通知 |

---

## 19. Sprint Planning 入場門票與凍結簽核

> **原始文件：** `012_Supporting_Sprint-Entry.md` (Appendix-B-Sprint-Entry.md)
> **版本：** v1.0.0 | **日期：** 2026-03-25 | **作者：** Amanda (sa-analyst)
> **狀態：** Phase 1 正式附錄
> **本節完整繼承 v0.8 §14。**

### 19.1 Sprint Planning 入場門票（Entry Criteria）

#### 19.1.1 門票結構

| 欄位 | 說明 |
|------|------|
| 門票編號 | SE-Phase1-{序號} |
| 條件描述 | 具體的入場條件 |
| 負責人 | Owner |
| 狀態 | Not Started / In Progress / Completed / Blocked |
| 驗證方式 | 如何確認該條件已滿足 |

#### 19.1.2 Sprint Planning 入場門票（共 6 項）

**SE-Phase1-001：SDD 規格文件完整性**
1. 條件描述：Phase 1 四大模組（M01, M02, M03, M05）均有完整 SDD 規格與 TC
2. 負責人：Amanda
3. 狀態：Completed
4. 驗證方式：所有模組 SPEC + TC 文件存在且通過覆檢

**SE-Phase1-002：RTM 追蹤矩陣建立完成**
1. 條件描述：RTM 已建立，涵蓋所有 4 類別 43 條款
2. 負責人：Amanda
3. 狀態：Completed
4. 驗證方式：RTM 存在且覆核率 100%

**SE-Phase1-003：Errata #1 補正完成**
1. 條件描述：Errata 已建立，包含錯誤碼表、12 條 User Story、5 條 Decision Record
2. 負責人：Amanda
3. 狀態：Completed
4. 驗證方式：Errata 存在且包含所有五項補正內容

**SE-Phase1-004：API 規格 OpenAPI 文件完成**
1. 條件描述：OpenAPI 已建立，包含 ErrorResponse Schema 與 Phase 1 所有 API 錯誤碼對應表
2. 負責人：Amanda
3. 狀態：Completed
4. 驗證方式：OpenAPI 文件存在且覆核率 100%

**SE-Phase1-005：Ken 人物誌旅程節點補正完成**
1. 條件描述：§7.2 Ken 的 Current Journey 已補足障礙節點與痛感節點標註
2. 負責人：Amanda
3. 狀態：✅ Completed（v0.8 已整合至 §7.2）

**SE-Phase1-006：M06 Phase 歧義消除**
1. 條件描述：§6, §9.7, §13.1 中 M06 Phase 歸屬已明確定義
2. 負責人：Amanda
3. 狀態：✅ Completed（v0.8 已整合至 §6.4, §9.7, §13.1）

### 19.2 Sprint Entry 覆核摘要

| 門票編號 | 入場條件 | 負責人 | 狀態 | 備註 |
|----------|----------|--------|------|------|
| SE-Phase1-001 | SDD 規格完整性 | Amanda | ✅ Completed | |
| SE-Phase1-002 | RTM 建立 | Amanda | ✅ Completed | |
| SE-Phase1-003 | Errata #1 完成 | Amanda | ✅ Completed | |
| SE-Phase1-004 | OpenAPI 文件完成 | Amanda | ✅ Completed | |
| SE-Phase1-005 | Ken 人物誌補正 | Amanda | ✅ Completed | v0.8 已整合 |
| SE-Phase1-006 | M06 Phase 歧義消除 | Amanda | ✅ Completed | v0.8 已整合 |

**入場門票達成率：6/6（100%）**

### 19.3 M03 凍結簽核清單

#### 19.3.1 簽核背景

M03（日曆預訂模組）在 Phase 1 開發期間需要進行規格凍結（Feature Freeze），所有 M03 相關的 API 設計與行為在凍結後不可隨意變更。如有變更需求，必須經過四方簽核程序。

#### 19.3.2 簽核矩陣

| 簽核角色 | 簽核人 | 簽核職責 | 簽核狀態 |
|----------|--------|----------|----------|
| 產品負責人（PO） | Koala | 確認業務需求未受影響 | Pending |
| 技術負責人（Tech Lead） | S-David | 確認技術可行性與架構一致性 | Pending |
| 規格總監（SDD Director） | Amanda | 確認規格完整性與 TC 覆蓋 | Pending |
| QA 負責人 | Victoria | 確認測試覆蓋度足夠 | Pending |

#### 19.3.3 簽核條件

四方必須全部同意以下條件，方可解除 M03 凍結：

1. M03 API 端點數量與行為與 SDD 規格一致
2. M03 所有 TC 案例已通過單元測試
3. M03 與 M05（訂單模組）的介面整合測試通過
4. M03 符合 Phase 1 約束（PC-001~PC-005）

#### 19.3.4 簽核時程

| 里程碑 | 預計完成日期 | 狀態 |
|--------|-------------|------|
| SDD 規格 M03 模組完成 | 2026-03-25 | ✅ |
| M03 Feature Freeze 生效 | 待 Sprint Planning 確認 | ⏳ |
| M03 TC 單元測試完成 | Sprint 2 結束時 | ⏳ |
| 四方簽核完成 | Sprint 2 結案前 | ⏳ |
| M03 凍結解除（若有變更需求） | Sprint 3 開始前 | ⏳ |

#### 19.3.5 簽核記錄

| 簽核人 | 簽核日期 | 簽核結果 | 備註 |
|--------|----------|----------|------|
| Koala | — | 待簽核 | |
| S-David | — | 待簽核 | |
| Amanda | — | 待簽核 | |
| Victoria | — | 待簽核 | |

### 19.4 Sprint Entry Checklist（教練用）

#### 19.4.1 教練在 Sprint Planning 開始前的檢查清單

1. SE-Phase1-001 至 SE-Phase1-006 狀態均為 Completed
2. 所有 5 支 M05 API 已建立完整 SDD 規格
3. RTM 覆核率達 100%
4. Errata 包含完整的錯誤碼表、User Story 與 Decision Record
5. OpenAPI 包含完整的 ErrorResponse Schema
6. M03 凍結簽核清單已建立，四方簽核狀態已記錄

---

## 20. 自我糾錯檢核 (Self-Correction Checklist)

### 20.1 v0.9 升版自我糾錯

| 編號 | 檢核問題 | 回答 | 對應章節 |
|------|---------|------|---------|
| SC-001 | 「商品」Entity 如何同時相容實體物品與虛擬房型？ | 引入 Listing 統一抽象層，Product/Room 各有專屬特化表 | §5 |
| SC-002 | 多租戶架構下，各「網友店鋪」的圖片庫與進銷存資料是否確保了邏輯隔離？ | ✅ tenant_id + S3 路徑分桶 + Hibernate Filter 自動注入 | §8.7 |
| SC-003 | CMS 嵌入購買卡片如何保證即時資料正確性？ | 前端每次渲染時呼叫 `/api/v2/listings/:id/card` 取得即時資料（非快取） | §6.6.3 |
| SC-004 | ERP 入庫如何即時連動 C 端可售量？ | StockMovement 事件 → product_inventory.total_qty 原子更新 → available_qty 為 computed column | §6.7.3, §5.4 |
| SC-005 | 動態定價規則疊加時如何處理衝突？ | 按 priority 欄位排序，高優先覆蓋低優先；MANUAL_OVERRIDE 為最高優先 | §5.5 |
| SC-006 | v0.8 的 `booking_slots` / `seasonal_pricing` / `room_blackout_dates` 在 v0.9 中是否有明確遷移路徑？ | ✅ `booking_slots` → `room_calendar`；`seasonal_pricing` → `pricing_rules` (SEASONAL)；`room_blackout_dates` → `room_calendar` status=BLOCKED | §5.5 |
| SC-007 | v0.8 §7.9 的錯誤格式 `{ "code":... }` 與 §11/§12 的巢狀 `{ "error": {...} }` 格式不一致，是否已修正？ | ✅ v0.9 §9.17 統一採用巢狀 `error` 包裹格式，與 §16/§17 一致 | §9.17, §16.1 |
| SC-008 | Phase 1 內容（§13）是否完整保留且未被修改？ | ✅ Phase 1 範圍、約束、測試案例、成功指標、MoSCoW 全數保留 | §13 |
| SC-009 | v0.8 的 43 條 RTM 追蹤項目是否全數保留？ | ✅ 43/43 條款完整保留於 §18 | §18 |
| SC-010 | v0.8 的 19 個 M05 測試案例是否全數保留？ | ✅ TC-M05-001 ~ TC-M05-019 完整保留於 §15 | §15 |

---

## 21. 規格版本控制

| 版本 | 日期 | 變更內容 | 核准 |
|------|------|---------|------|
| v0.1 | 2026-03-23 | 初始草稿（Clean Architecture + DDD 結構） | — |
| v0.2 | 2026-03-23 | Section 4 升級企業級雙引擎架構（M01~M14）；同步更新 API、資料庫、路由 | — |
| v0.3 | 2026-03-23 | 修正 QA 5 項阻塞問題：新增 saga_events/compensation_log/oauth_accounts/kyc_applications 表；新增 OAuth2/KYC 明確 API；將 PostGIS/ES/RabbitMQ 移至 Phase 2+ 未來基礎設施；user_profiles 欄位完整定義 | — |
| v0.5 | 2026-03-23 | 修正 QA N-04 觀察項：Saga API 說明明確標注支撐 M05/M06/M07 三模組 | ❌ 待 Koala 確認 |
| v0.7 | 2026-03-25 | Amanda 整體覆檢補正：M06 Phase 1/2 歧義消除（§4.3/§7.6/§9.1）；M05/M03/M01/M02 各有獨立 SDD SPEC；新增 Errata-01.md + RTM.md + Appendix-A/B；Ken 人物誌旅程 G-7 補正；G-8 M06 歧義消除 | ✅ Amanda 覆檢完成 · Victoria 最終確認 · 待 Koala 核准 |
| **v0.8** | **2026-03-26** | **整合六份附屬文件為單一完整規格文件：M05 SDD 規格（§10）、OpenAPI 錯誤回應（§11）、勘誤與補正（§12）、需求追蹤矩陣（§13）、Sprint Entry（§14）；SE-Phase1-005/006 狀態更新為 Completed；新增目錄** | **待 Koala 核准** |
| **v0.9** | **2026-03-27** | **六項新需求整合：(1) 統一商品/服務模型含動態定價 (§5)；(2) CMS 內容驅動銷售 (M15, §6.6)；(3) 一體化進銷存 ERP (M16, §6.7)；(4) SaaS 平台化多租戶架構 B2B2C (M17, §4)；(5) 友善前台+強大後台管理 (§10-§12)；(6) 網友開店 RBAC 權限矩陣 (§7.3)。本文件為完整獨立規格，已全面整合 v0.8 所有內容。** | **待 Koala 核准** |
| **v0.9_R01** | **2026-03-30** | **Loop 01 迭代修正（基於 PM QA Iteration 1）：**<br>• CR-001+CR-014：狀態機與 Payment Mock 衝突修正（§3.3, §13.3, §15.3.1）<br>• CR-003+CR-011：M06 Phase 1/2 歸屬混淆消除（§12.5, §12.6）<br>• CR-013+QA-CR-003：Phase 1 RBAC 含 Phase 2+ 模組問題（§7.3）<br>• QA-CR-001：/checkout 前端路由 Payment Mock 頁面定義（§10.1.1）<br>• QA-CR-002：M05 SDD Phase 1 Payment Mock 約束（§15.2.1, §15.3.1）<br>• CR-002：Phase 2-A 商業價值指標（§14.2.5）<br>• CR-004：Phase 1 Seller 訂單查詢 API（§9.5, §13.1）<br>• CR-005：M01 圖片上傳 API（§9.12）<br>• CR-006：M04 促銷 CRUD API（§9.5.1）<br>• CR-007：採購審批流程（§6.7.2, §9.15）<br>• CR-008：StoreFront 內容定義（§11.3.1, §12.7）<br>• CR-009：資料隱私合規框架（§1.5）<br>• CR-010：結算系統定義（§6.2.1, §8.2.9）<br>• CR-012：Guest→Buyer 轉換流程（§7.1, §12.7）<br>• CR-015：pricing_rules 數量約束（§5.5.1）<br>• CR-016：room_calendar 動態更新觸發點（§5.5.2）<br>• CR-017：stock_movements Phase 1 假設（§6.7.3, §8.2.10）<br>• CR-018：並發入庫 version 遞增策略（§5.4）<br>• CR-019：/rooms/[id] Phase 標註拆分（§10.1.1）<br>• CR-020：Cancellation Matrix Phase 1 等效狀態（§15.3.1.1）<br>• CR-021：Guest User Story（§17.3.2）<br>• QA-CR-004：多租戶容量指標（§13.4）<br>• QA-CR-005：MAINTENANCE 狀態定義（§5.5.3）<br>• QA-CR-006：pricing_rules JSON 描述修正（§5.5）<br>• QA-CR-007：GET /api/orders 描述修正（§9.5）<br>• QA-CR-008：StoreStaff 細粒度權限 Phase 說明（§7.4）<br>• QA-CR-009：settlement_statements 資料表（§8.1.5, §8.2.9）<br>• QA-CR-010：VR-M04 錯誤碼 E-4014（§16.3.5, §17.2.2）<br>• QA-CR-011：merchantId 來源說明（§13.3）<br>共計：21 Victoria CR + 11 QA 額外發現 = 32 項修改 | **待 Koala 核准** |
| **v0.9_R02_Loop_02** | **2026-03-30** | **Loop 2 迭代修正（基於 QA Review 02）：**<br>• Q-LO2-001：Phase 1 狀態機 CONFIRMED 矛盾消除（§3.3, §15.3.1.1）；移除 Cancellation Matrix CONFIRMED 行；明確 order_state_log 初始記錄<br>• Q-LO2-002：ERP receive Transaction Atomic 保障（§6.7.3, §9.15）<br>• Q-LO2-003：PostEmbed ON DELETE CASCADE + INACTIVE/DELETED 卡片處理（§6.6.3, §8.2.5）<br>• Q-LO2-004：pricing_rules 時間範圍重疊衝突檢核（§5.5.1）；新增 TC-PR-003/004<br>• Q-LO2-005：CREDIT_NOTE 獨立表 credit_notes schema（§6.2.1, §8.2.10）；settlement_statements 新增 REVERSED 狀態<br>• Q-LO2-006：settlement_statements 重複 timestamp 區塊清除（已確認無重複）<br>• Q-LO2-101：MAINTENANCE 狀態 price = NULL 處理（§5.5.3, §6.6.3）<br>• Q-LO2-102：room_calendar 狀態轉換矩陣（§5.5.3）<br>• Q-LO2-103：pricing_rules 50 條上限 API 校驗（§9.13）<br>• Q-LO2-104：PurchaseOrder 取消後庫存不回滾 + 取消後通知（§9.15）<br>• Q-LO2-105：多租戶 tenant_id NOT NULL + FK + INDEX 全面覆蓋（§8.2 各 schema）<br>• Q-LO2-106：MAINTENANCE Booking Admin Dashboard MaintenanceWarnings 空白期替代方案（§5.5.3）<br>• Q-LO2-201：pricing_rules priority Tie-Breaking（§5.5）<br>• Q-LO2-202：stock_movements.order_item_id Phase 1 = null 前端約定（§9.15）<br>• Q-LO2-203：PostEmbed Markdown 注入防護（§6.6.3, §9.14）<br>• Q-LO2-204：M17 搶先實作 + Feature Toggle 自動初始化時序（§6.8.2, §14.2）<br>• 新增 Test Cases：TC-LO2-M12-001~004、TC-LO2-M15-001~003、TC-LO2-M16-001~002、TC-LO2-M17-001~003（共 12 項）<br>共計：16 項 QA 回應全部採納、12 項 Test Cases 新增 | **待 Koala 核准** |
| **v1.0** | **2026-04-09** | **七項改善整合（基於 PRD v0.9_R02 審視報告）：**<br>• M12 動態定價：Phase 2-A → Phase 1 Must Have（§6.9, §13.5）<br>• M18 知識管理：新模組新增（§1.4, §6.10, §13.5）；詳細規格見 M18_Knowledge_Management_SPEC.md<br>• API 版本策略：統一為 /api/v2/ 前綴（§9.6, §9.7, §15）<br>• Test Case 編號：TC-M05-006~009 重新編號消除重複（§15.2.1）<br>• Payment Mock API：新增實作細節（§9.14）<br>• 開店申請表單：新增欄位定義（§9.10.1）<br>• M06 Phase 邊界：澄清 Phase 1 僅開放 GET/取消，POST 建立屬 Phase 2（§6.4, §6.6） | ✅ 已確認 |

---

## 22. 勘誤記錄 / Errata

| 編號 | 問題描述 | 處理方式 | 參考章節 |
|------|---------|---------|---------|
| ER-001 | v0.9 API 版本策略不一致：§9 為 `/api/orders`，§15 為 `/api/v1/orders` | 已統一為 `/api/v2/` | §9.6, §15 |
| ER-002 | TC-M05-006/TC-M05-007 編號重複 | 已重新編號為 TC-M05-006~009 | §15.2.1 |
| ER-003 | M06 Phase 1/2 邊界描述不一致 | 已澄清：Phase 1 僅 GET/取消，POST 建立屬 Phase 2 | §6.4, §6.6 |

_本文檔為 E-Commerce System 的完整合併規格文件 v1.0，所有內容具有同等約束力。v0.9 所有內容已完整整合，本文件可獨立使用，無需參照 v0.9。_

[Next Action] 請確認本規格文件 v1.0，確認後啟動 Phase 2-A 實作。

