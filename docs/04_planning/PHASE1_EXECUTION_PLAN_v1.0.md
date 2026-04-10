# Phase 1 開發執行計劃 / Phase 1 Execution Plan

> **狀態**: v1.0 - 初始版本
> **建立日期**: 2026-04-08
> **預計完成**: 2026-10-08 (6 個月)
> **團隊**: 1 人 (全端開發)
> **預算**: AI Token 優化

---

## 1. 開發範疇 (Scope)

### 1.1 Phase 1 功能矩陣

| ID | 模組 | 功能 | 優先級 | 備註 |
|----|------|------|--------|------|
| M03 | 會員系統 | 會員註冊/登入/RBAC/JWT | P0 | 含 OAuth (Google/Facebook/Apple) |
| M17 | 多租戶系統 | 租戶隔離/Feature Toggle | P0 | Phase 1 單一自營，未來開放多店 |
| M01 | 商品中心 | Product CRUD/分類/搜尋 | P0 | 基於 Listing 統一抽象 |
| M02 | 房源中心 | Room CRUD/民宿管理 | P0 | 基於 Listing 統一抽象 |
| M04 | 購物車 | Redis 購物車/優惠券 | P0 | |
| M05 | 訂單履約 | 狀態機/取消回滾 | P0 | Payment Mock |
| M06 | 預訂日曆 | Redis 分散式鎖 | P0 | 防重複預訂 |
| M07 | 支付系統 | LinePay + 信用卡 | P0 | Payment Mock |
| M11 | 物流追蹤 | 黑貓 + 新竹 | P1 | |
| M12 | 動態定價 | 平假日/旺季/早鳥/長住 | P0 | |
| M13 | 商家工作台 | 儀表板/訂單/營收 | P1 | 基礎版 |
| M14 | 平台管理 | Admin/Feature Toggle | P1 | |

### 1.2 Phase 2+ 延後功能

| ID | 模組 | 功能 | 備註 |
|----|------|------|------|
| M08 | 評價系統 | 圖文評論/星級評分 | Phase 2 |
| M09 | 通知系統 | Email/SMS/RabbitMQ | Phase 2 |
| M10 | IM 通訊 | WebSocket 私訊 | Phase 2 |
| M15 | CMS | 內容發布/嵌入式卡片 | Phase 2 |
| M16 | 進銷存 | 採購/庫存/盤點 | Phase 2 |
| - | 行動端 | iOS/Android/macOS App | Phase 2 |
| - | Apple Pay | 真實 LinePay | 未來 |

---

## 2. 技術架構

### 2.1 技術棧

| 層級 | 技術 | 版本 |
|------|------|------|
| 前端 | Next.js (App Router) + TypeScript + Tailwind CSS | Next.js 15 |
| 後端 | Spring Boot + Java 21 + Clean Architecture + DDD | Spring Boot 3.2 |
| 資料庫 | PostgreSQL | 16 |
| 快取 | Redis | 7 |
| DB 遷移 | Flyway | - |
| E2E 測試 | Playwright | - |
| CI/CD | GitHub Actions | - |
| 認證 | JWT + OAuth2 | - |

### 2.2 資料庫連線

```
Host: 192.168.1.133
Database: nextkeytest
Account: koala/koala5
```

### 2.3 系統架構圖

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend                              │
│                   Next.js (App Router)                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │  Shop    │  │ Booking  │  │  Admin   │  │ Dashboard│   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└────────────────────────┬────────────────────────────────────┘
                         │ REST API
┌────────────────────────▼────────────────────────────────────┐
│                      Backend                                │
│            Spring Boot 3.2 (Java 21)                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                 API Layer                             │  │
│  │   /api/v2/storefront  │  /api/v2/dashboard  │ /api/* │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │               Core Services                           │  │
│  │  M03│M17│M01│M02│M04│M05│M06│M07│M11│M12│M13│M14   │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Domain / Infrastructure                  │  │
│  │     JPA │ Redis │ Security │ External APIs           │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────┬────────────────────────────────────┘
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
   ┌──────────┐    ┌──────────┐    ┌──────────┐
   │PostgreSQL│    │  Redis   │    │External  │
   │  192.168 │    │  Cache   │    │ APIs     │
   │  .1.133  │    │  Lock    │    │(LinePay) │
   └──────────┘    └──────────┘    └──────────┘
```

---

## 3. 時間線 (Timeline)

### 3.1 6 個月開發規劃

```
Month 1: 基礎架構 + 會員系統
├── Week 1-2: 專案初始化
│   ├── Next.js 專案建立
│   ├── Spring Boot 專案建立
│   ├── PostgreSQL 資料庫設定
│   ├── Redis 設定
│   └── GitHub Actions CI/CD 骨架
├── Week 3-4: M03 會員系統
│   ├── 會員註冊/登入 API
│   ├── JWT + Refresh Token
│   └── RBAC 權限系統
└── Week 4: M03 OAuth
    ├── Google OAuth
    ├── Facebook OAuth
    └── Apple Sign-In

Month 2: 核心模組 (商品 + 房源)
├── Week 5-6: M17 多租戶系統
│   ├── 租戶資料模型
│   ├── TenantContext Filter
│   ├── Feature Toggle 機制
│   └── Hibernate Tenant Filter
├── Week 7-8: M01 商品中心
│   ├── Listing 統一抽象
│   ├── Product CRUD
│   ├── 分類與標籤
│   └── 搜尋 API
└── Week 8: M02 房源中心 (Part 1)
    ├── Room CRUD
    └── 基礎房源管理

Month 3: 民宿 + 動態定價
├── Week 9-10: M02 房源中心 (Part 2)
│   ├── room_calendar 設計
│   ├── 民宿預訂流程
│   └── 地圖服務整合
├── Week 11-12: M12 動態定價
│   ├── pricing_rules 設計
│   ├── 平假日/旺季定價
│   ├── 早鳥/長住優惠
│   └── 手動覆蓋
└── Week 12: M06 預訂日曆
    └── Redis 分散式鎖

Month 4: 電商交易閉環
├── Week 13-14: M04 購物車
│   ├── Redis 購物車設計
│   ├── 優惠券系統
│   └── 滿額折扣計算
├── Week 15-16: M05 訂單履約
│   ├── 訂單狀態機
│   ├── 庫存扣減
│   ├── 取消/回滾機制
│   └── Payment Mock
└── Week 16: M07 支付系統
    ├── LinePay Mock
    └── 信用卡 Mock

Month 5: 物流 + 後台管理
├── Week 17-18: M11 物流追蹤
│   ├── 黑貓物流 API
│   ├── 新竹物流 API
│   └── 物流狀態追蹤
├── Week 19-20: M13 商家工作台
│   ├── 儀表板
│   ├── 訂單管理
│   └── 營收分析
└── Week 20-21: M14 平台管理
    ├── Admin 功能
    ├── Feature Toggle UI
    └── 平台設定

Month 6: 測試 + 部署 + Buffer
├── Week 22-23: 系統整合測試
│   ├── API 整合測試
│   ├── E2E 測試 (Playwright)
│   └── 效能測試
├── Week 24: 部署上線
│   ├── 生產環境設定
│   ├── GitHub Actions 部署
│   └── 文件更新
└── Week 25-26: Buffer + 優化
    └── 緩衝突發問題
```

---

## 4. Sprint 規劃 (每 2 週一個 Sprint)

| Sprint | 週次 | 主要交付 |
|--------|------|----------|
| Sprint 0 | W1-2 | 專案初始化 (Infra) |
| Sprint 1 | W3-4 | M03 會員系統 + OAuth |
| Sprint 2 | W5-6 | M17 多租戶 + M01 商品 |
| Sprint 3 | W7-8 | M02 房源 + 動態定價基礎 |
| Sprint 4 | W9-10 | M12 動態定價 + M06 日曆 |
| Sprint 5 | W11-12 | M04 購物車 |
| Sprint 6 | W13-14 | M05 訂單履約 + M07 支付 |
| Sprint 7 | W15-16 | M11 物流追蹤 |
| Sprint 8 | W17-18 | M13 商家工作台 |
| Sprint 9 | W19-20 | M14 平台管理 |
| Sprint 10 | W21-22 | 整合測試 |
| Sprint 11 | W23-24 | 部署上線 |
| Sprint 12 | W25-26 | Buffer 優化 |

---

## 5. 資料庫 Schema 規劃

### 5.1 Core Tables

```
┌─────────────────────────────────────────────────────────────┐
│                      Core Tables                             │
├─────────────────────────────────────────────────────────────┤
│ tenants (租戶)                                              │
│ ├── id: UUID (PK)                                          │
│ ├── name: VARCHAR                                          │
│ ├── status: ENUM(PENDING_REVIEW/ACTIVE/SUSPENDED)         │
│ ├── created_at, updated_at                                 │
│                                                              │
│ users (用戶)                                                │
│ ├── id: UUID (PK)                                          │
│ ├── email: VARCHAR UNIQUE                                   │
│ ├── password_hash: VARCHAR                                 │
│ ├── role: ENUM(GUEST/BUYER/SELLER/HOST/ADMIN/SUPER_ADMIN) │
│ ├── tenant_id: UUID (FK, nullable)                        │
│ ├── created_at, updated_at                                 │
│                                                              │
│ tenant_members (租戶成員)                                   │
│ ├── id: UUID (PK)                                          │
│ ├── tenant_id: UUID (FK)                                   │
│ ├── user_id: UUID (FK)                                     │
│ ├── store_role: ENUM(STORE_OWNER/STORE_STAFF)            │
│                                                              │
│ tenant_feature_toggles (功能開關)                           │
│ ├── id: UUID (PK)                                          │
│ ├── tenant_id: UUID (FK)                                   │
│ ├── feature_key: VARCHAR                                   │
│ ├── is_enabled: BOOLEAN                                    │
│ ├── config: JSONB                                          │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 E-Commerce Tables

```
┌─────────────────────────────────────────────────────────────┐
│                   E-Commerce Tables                          │
├─────────────────────────────────────────────────────────────┤
│ listings (統一抽象)                                         │
│ ├── id: UUID (PK)                                          │
│ ├── tenant_id: UUID (FK)                                   │
│ ├── listing_type: ENUM(PRODUCT/ROOM)                       │
│ ├── title, description, cover_image_url                    │
│ ├── status: ENUM(DRAFT/ACTIVE/INACTIVE/DELETED)           │
│ ├── owner_id: UUID (FK)                                   │
│ ├── base_price: DECIMAL                                    │
│ ├── currency: VARCHAR(3)                                    │
│ ├── tags: TEXT[]                                           │
│                                                              │
│ products (商品)                                             │
│ ├── listing_id: UUID (FK, UNIQUE)                         │
│ ├── category, brand, weight_grams                          │
│                                                              │
│ product_skus (SKU 規格)                                     │
│ ├── id: UUID (PK)                                          │
│ ├── product_listing_id: UUID (FK)                         │
│ ├── sku_code: VARCHAR UNIQUE                               │
│ ├── spec_name                                             │
│ ├── price_override: DECIMAL                                │
│ ├── status                                                 │
│                                                              │
│ product_inventory (SKU 庫存)                               │
│ ├── sku_id: UUID (FK, UNIQUE)                             │
│ ├── total_qty, reserved_qty, available_qty                │
│ ├── version (樂觀鎖)                                       │
│                                                              │
│ rooms (民宿房間)                                            │
│ ├── listing_id: UUID (FK, UNIQUE)                         │
│ ├── location, latitude, longitude                         │
│ ├── max_guests, amenities                                 │
│ ├── check_in_time, check_out_time                         │
│                                                              │
│ room_calendar (日曆)                                        │
│ ├── id: UUID (PK)                                         │
│ ├── room_listing_id: UUID (FK)                            │
│ ├── calendar_date: DATE                                    │
│ ├── status: ENUM(AVAILABLE/BOOKED/BLOCKED/MAINTENANCE)   │
│ ├── price: DECIMAL                                        │
│ ├── booking_id: UUID (FK, nullable)                        │
│                                                              │
│ pricing_rules (動態定價)                                    │
│ ├── id: UUID (PK)                                         │
│ ├── tenant_id, room_listing_id                            │
│ ├── rule_type: ENUM(WEEKDAY_WEEKEND/SEASONAL/...)        │
│ ├── rule_name, priority, config: JSONB                    │
│ ├── valid_from, valid_to                                  │
│ ├── is_active                                             │
└─────────────────────────────────────────────────────────────┘
```

### 5.3 Order & Payment Tables

```
┌─────────────────────────────────────────────────────────────┐
│                  Order & Payment Tables                      │
├─────────────────────────────────────────────────────────────┤
│ orders (訂單)                                               │
│ ├── id: UUID (PK)                                          │
│ ├── tenant_id, user_id, order_type                         │
│ ├── status: ENUM(CREATED/SHIPPING/DELIVERED/COMPLETED)   │
│ ├── total_amount: DECIMAL                                 │
│ ├── payment_status: ENUM(PENDING/PAID/REFUNDED)           │
│ ├── shipping_address: TEXT                               │
│ ├── created_at, updated_at                                 │
│                                                              │
│ order_items (訂單項目)                                      │
│ ├── id: UUID (PK)                                         │
│ ├── order_id: UUID (FK)                                   │
│ ├── listing_id: UUID (FK)                                 │
│ ├── sku_id: UUID (FK, nullable)                          │
│ ├── quantity, unit_price, subtotal                        │
│                                                              │
│ order_state_log (狀態日誌)                                  │
│ ├── id: UUID (PK)                                         │
│ ├── order_id: UUID (FK)                                   │
│ ├── sequence: INTEGER                                     │
│ ├── from_status, to_status                                │
│ ├── changed_at                                            │
│                                                              │
│ payments (支付)                                             │
│ ├── id: UUID (PK)                                         │
│ ├── order_id: UUID (FK)                                   │
│ ├── payment_method: ENUM(LINEPAY/CREDIT_CARD/MOCK)      │
│ ├── amount: DECIMAL                                       │
│ ├── status: ENUM(PENDING/SUCCESS/FAILED)                 │
│ ├── transaction_id: VARCHAR                              │
│ ├── created_at                                            │
└─────────────────────────────────────────────────────────────┘
```

---

## 6. API 設計概要

### 6.1 API 路由結構

```
/api/v2/
├── storefront/              # C 端前台
│   ├── listings            # 商品/房源列表
│   ├── listings/:id       # 商品/房源詳情
│   ├── cart                # 購物車
│   ├── orders              # 訂單
│   └── payments           # 支付
│
├── dashboard/             # B 端店鋪後台
│   ├── listings           # 商品/房源管理
│   ├── orders             # 訂單管理
│   ├── analytics          # 營收分析
│   ├── calendar           # 預訂日曆
│   └── settings           # 店鋪設定
│
└── admin/                  # Admin 管理
    ├── tenants            # 租戶管理
    ├── users              # 用戶管理
    ├── feature-toggles   # 功能開關
    └── platform          # 平台設定
```

### 6.2 核心 API 列表

| API | Method | 說明 |
|-----|--------|------|
| POST | /api/v2/auth/register | 會員註冊 |
| POST | /api/v2/auth/login | 會員登入 |
| POST | /api/v2/auth/refresh | 刷新 Token |
| GET | /api/v2/listings | 列表查詢 |
| GET | /api/v2/listings/:id | 詳情查詢 |
| POST | /api/v2/dashboard/listings | 建立商品/房源 |
| GET | /api/v2/cart | 取得購物車 |
| POST | /api/v2/cart/items | 加入購物車 |
| POST | /api/v2/orders | 建立訂單 |
| GET | /api/v2/orders/:id | 訂單詳情 |
| POST | /api/v2/payments/mock | Payment Mock |
| GET | /api/v2/dashboard/analytics | 營收分析 |

---

## 7. 第三方整合

### 7.1 Phase 1 整合

| 服務 | 類型 | 狀態 | 備註 |
|------|------|------|------|
| Google OAuth | 認證 | ✅ Phase 1 | |
| Facebook OAuth | 認證 | ✅ Phase 1 | |
| Apple Sign-In | 認證 | ✅ Phase 1 | |
| LinePay | 支付 | 🔜 未來 | Phase 1 Payment Mock |
| 信用卡 | 支付 | 🔜 未來 | Phase 1 Payment Mock |
| 黑貓物流 | 物流 | ✅ Phase 1 | |
| 新竹物流 | 物流 | ✅ Phase 1 | |

### 7.2 Phase 2 整合

| 服務 | 類型 | 備註 |
|------|------|------|
| LinePay | 支付 | 真實 API |
| Apple Pay | 支付 | 需要商家認證 |
| 蝦皮物流 | 物流 | |
| 7-11 超商 | 物流 | |
| 全家超商 | 物流 | |
| RabbitMQ | 訊息佇列 | M09 通知系統 |

---

## 8. 非功能性需求

### 8.1 效能目標

| 指標 | 目標 |
|------|------|
| API 回應時間 (P95) | < 200ms |
| 首頁載入時間 | < 3s |
| 併發使用者 | 100+ |
| 資料庫連線池 | 20 |

### 8.2 安全需求

- JWT Access Token: 15min
- JWT Refresh Token: 7 days
- 密碼: BCrypt
- KYC 資料: AES-256 加密
- API Rate Limiting: 100 req/min

### 8.3 合規需求

- 台灣個資法遵循
- 電子支付法規預留
- PCI-DSS 預留（未來）

---

## 9. 風險與緩解

| 風險 | 可能性 | 影響 | 緩解策略 |
|------|--------|------|----------|
| 範圍蔓延 | 高 | 高 | 嚴格遵守 Phase 1 範圍，延後功能統一記錄 |
| 技術難點 | 中 | 中 | 預留 Buffer 時間，寻求社区支持 |
| 第三方 API 變更 | 低 | 中 | 封裝 Adapter Pattern |
| 效能瓶頸 | 中 | 中 | Redis 快取，分庫分表預留 |

---

## 10. 文件交付 (最小化)

根據「最小化文件」原則，Phase 1 僅保留必要文件：

| 文件 | 路徑 | 說明 |
|------|------|------|
| PRD | docs/01_requirements/E-Commerce_PRD_v0.9_R02_Final.md | 現有完整 PRD |
| Phase 1 執行計劃 | docs/04_planning/PHASE1_EXECUTION_PLAN_v1.0.md | 本文檔 |
| API Spec | docs/02_architecture/API_SPEC_v1.0.md | 核心 API 規格 |
| DB Schema | docs/02_architecture/DB_SCHEMA_v1.0.md | 資料庫結構 |
| README | docs/README.md | 專案說明 |

---

## 11. 版本歷史

| 版本 | 日期 | 變更 |
|------|------|------|
| v1.0 | 2026-04-08 | 初始版本 |

---

**下一步**: 開始執行 Sprint 0 - 專案初始化
