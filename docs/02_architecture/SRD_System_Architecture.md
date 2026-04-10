# E-Commerce System — 系統需求文檔 (SRD) v1.0

> **文檔類型**: SRD (System Requirements Document)
> **版本**: v1.0
> **依據**: E-Commerce_PRD_v1.0_Final.md, E-Commerce_FRD_v1.0.md
> **建立日期**: 2026-04-09
> **作者**: Marcus (SD-Architect)
> **AISDLC 版本**: v0.09

---

## 📋 文檔元數據

| 項目 | 內容 |
|-----|------|
| **專案名稱** | E-Commerce B2B2C 多租戶電子商務平台 |
| **系統類型** | B2B2C 多租戶電子商務 + 民宿預訂系統 |
| **文檔狀態** | Draft |
| **System Architect** | Marcus (SD-Architect) |
| **最後更新** | 2026-04-09 |

---

## 📌 文檔追蹤

### 上游文檔
- **PRD 連結**: [E-Commerce_PRD_v1.0_Final.md](../01_requirements/E-Commerce_PRD_v1.0_Final.md)
- **FRD 連結**: [E-Commerce_FRD_v1.0.md](../01_requirements/E-Commerce_FRD_v1.0.md)

### 下游文檔
- **API 規格**: [docs/02_architecture/API_Index.md](./API_Index.md)
- **AT 連結**: [docs/03_testing/](../03_testing/) (驗收測試)

---

## 1. 技術架構總覽 (Technical Overview)

### 1.1 系統架構圖

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         B2B2C Platform                              │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    Frontend (Next.js 15)                    │   │
│  │    App Router + TypeScript + Tailwind CSS + Zustand         │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                   │                                  │
│                                   ▼                                  │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                      API Gateway (Spring Boot)                  │   │
│  │                    /api/v2/* → Multi-Tenant Aware            │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                   │                                  │
│         ┌─────────────────────────┼─────────────────────────┐       │
│         ▼                         ▼                         ▼       │
│  ┌─────────────┐           ┌─────────────┐           ┌─────────┐  │
│  │  Retail     │           │  Booking    │           │  Platform│  │
│  │  Engine     │           │  Engine     │           │  Infra  │  │
│  │  (M01,M05)  │           │  (M02,M06)  │           │ (M03,M17)│  │
│  └─────────────┘           └─────────────┘           └─────────┘  │
│         │                         │                         │       │
│         │         ┌──────────────┘                         │       │
│         │         ▼                                        │       │
│         │  ┌─────────────────┐                             │       │
│         │  │  Dynamic        │                             │       │
│         │  │  Pricing (M12)  │                             │       │
│         │  └─────────────────┘                             │       │
│         │                                                    │       │
│         └──────────────────────┬───────────────────────────┘       │
│                                ▼                                    │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    PostgreSQL 16 (Shared Schema)             │   │
│  │              Tenant ID Isolation + Hibernate Filter          │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                │                                    │
│                                ▼                                    │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                      Redis 7 (Cache Layer)                   │   │
│  │         Cart / Distributed Lock / Rate Limit / Pricing        │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 後端分層架構（Clean Architecture + DDD + Multi-Tenancy）

```
com.nextkey.ecommerce/
├── api/                                    # Presentation Layer
│   ├── controller/
│   │   ├── storefront/                    # C 端前台 API (/api/v2/listings)
│   │   ├── dashboard/                     # B 端店鋪 API (/api/v2/dashboard/*)
│   │   └── admin/                         # 平台管理 API (/api/v2/admin/*)
│   ├── filter/
│   │   ├── JwtAuthFilter.java             # JWT 認證過濾器
│   │   └── TenantContextFilter.java        # ★ 租戶上下文注入
│   ├── interceptor/                        # 攔截器
│   └── dto/
│       ├── request/                       # 請求 DTO
│       └── response/                      # 回應 DTO
│
├── core/                                   # Application Layer (Use Cases)
│   ├── auth/                              # M03 認證服務
│   ├── listing/                           # ★ 統一商品/房源 Use Cases
│   ├── retail/                           # M01 零售特化邏輯
│   ├── booking/                           # M02/M06 預訂特化邏輯
│   ├── order/                             # M05 訂單履約
│   ├── pricing/                           # ★ M12 動態定價引擎
│   └── tenant/                            # M17 租戶管理
│
├── domain/                                 # Domain Layer
│   ├── entity/                            # 領域實體
│   ├── vo/                                # Value Objects
│   ├── repository/                        # Repository 介面
│   ├── service/                           # Domain Services
│   └── event/                             # Domain Events
│
├── infrastructure/                         # Infrastructure Layer
│   ├── persistence/                       # JPA Repository 實作
│   │   ├── adapter/                       # Repository 介面卡
│   │   └── repository/                   # JPA Repository
│   ├── redis/                             # Redis 客戶端
│   ├── security/                          # Spring Security 配置
│   └── config/                           # 各類設定
│
└── shared/                                 # Shared Utilities
    ├── exception/                         # 統一異常處理
    ├── constant/                          # 常數定義
    ├── util/                              # 工具類
    └── dto/                               # 共用 DTO
```

### 1.3 技術選型

| 層級 | 技術 | 版本 | 備註 |
|------|------|------|------|
| **前端框架** | Next.js 15 | 15.x | App Router + SSR + SSG |
| **前端語言** | TypeScript | 5.x | 強型別 |
| **前端樣式** | Tailwind CSS | 3.x | Utility-First |
| **前端狀態** | Zustand | 4.x | 輕量狀態管理 |
| **後端框架** | Spring Boot | 3.2.x | Java 21 |
| **後端 ORM** | Spring Data JPA | 3.2.x | Hibernate 6.x |
| **資料庫遷移** | Flyway | 9.x | Versioned Migration |
| **資料庫** | PostgreSQL | 16.x | Shared Schema |
| **快取** | Redis | 7.x | 分散式鎖/限流/定價快取 |
| **API 文件** | SpringDoc OpenAPI | 2.x | Swagger UI |
| **建置工具** | Gradle (Kotlin DSL) | 8.x | 快速建置 |
| **測試框架** | JUnit 5 + Mockito | 5.x | 單元測試 |
| **容器化** | Docker + Docker Compose | 24.x | 開發環境 |
| **CI/CD** | GitHub Actions | — | 自動化建置測試 |

---

## 2. 多租戶架構 (Multi-Tenancy)

### 2.1 租戶隔離策略

**採用策略**: Shared Schema + Tenant ID + Hibernate Filter

| 策略 | 優點 | 缺點 | 適用場景 |
|------|------|------|----------|
| **Shared Schema + Tenant ID** | 成本低、易維護 | 需注意索引設計 | Phase 1中小規模 |
| Separate Database | 隔離性最好 | 成本高、管理複雜 | 大型客戶 |
| Separate Schema | 平衡方案 | 中等成本 | 中大型多租戶 |

### 2.2 TenantContext 流程

```
┌──────────┐     ┌─────────────────┐     ┌──────────────────┐     ┌───────────┐
│ API      │ ──▶ │ JwtAuthFilter   │ ──▶ │TenantContextFilter│ ──▶ │ Controller│
│ Request  │     │ (驗證 JWT)      │     │ (注入 TenantCtx)  │     │ (業務)   │
└──────────┘     └─────────────────┘     └──────────────────┘     └───────────┘
                                                                    │
    ┌───────────────────────────────────────────────────────────────┘
    ▼
┌──────────────────────────────────────────────────────────────┐
│                  Hibernate Filter                            │
│  @FilterDef(name="tenantFilter", ...)                       │
│  @Filter(name="tenantFilter", condition="tenant_id = :tid")  │
└──────────────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────────────┐
│              自動注入 WHERE tenant_id = :tid                 │
└──────────────────────────────────────────────────────────────┘
```

### 2.3 資料隔離實現

**Hibernate Filter 配置**:
```java
@Entity
@Table(name = "listings")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Listing {
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    // ...
}
```

**Repository 層**:
```java
@Repository
public interface ListingRepository extends JpaRepository<Listing, UUID> {
    
    @Query("SELECT l FROM Listing l WHERE l.status = 'ACTIVE'")
    List<Listing> findActiveListings(); // Hibernate 自動注入 TenantContext
}
```

---

## 3. 統一商品/服務模型 (Unified Listing Model)

### 3.1 統一 Listing 抽象

```
┌─────────────────────────────────────────────┐
│           listings (統一抽象表)             │
├─────────────────────────────────────────────┤
│ id: UUID (PK)                               │
│ tenant_id: UUID (FK)                        │
│ listing_type: ENUM('PRODUCT', 'ROOM')       │
│ title: VARCHAR(200)                          │
│ description: TEXT                           │
│ cover_image_url: VARCHAR(500)                │
│ status: ENUM('DRAFT','ACTIVE','INACTIVE')   │
│ owner_id: UUID (FK → users.id)              │
│ base_price: DECIMAL(12,2)                   │
│ currency: VARCHAR(3)                        │
│ tags: TEXT[]                                │
│ created_at: TIMESTAMP                       │
│ updated_at: TIMESTAMP                       │
└─────────────────────────────────────────────┘
                      │
         ┌────────────┴────────────┐
         ▼                         ▼
┌─────────────────────┐   ┌─────────────────────┐
│    products         │   │       rooms         │
│  (PRODUCT 特有)     │   │   (ROOM 特有)       │
├─────────────────────┤   ├─────────────────────┤
│ listing_id: FK      │   │ listing_id: FK      │
│ category            │   │ location            │
│ brand               │   │ latitude/longitude │
│ weight/dimensions   │   │ max_guests          │
└─────────────────────┘   │ amenities          │
                          │ check_in/out_time  │
                          └─────────────────────┘
```

### 3.2 設計理由

| 設計決策 | 理由 |
|---------|------|
| **單一 listings 表** | 減少冗餘，查詢效率高 |
| **listing_type 區分** | 支援零售(PRODUCT)和民宿(ROOM) |
| **統一 base_price** | 動態定價可複用相同邏輯 |
| **Product/Room 特化表** | 僅儲存各自特有的欄位 |

---

## 4. API 設計

### 4.1 API 版本策略

| 策略 | 說明 |
|------|------|
| **路徑版本** | `/api/v2/{resource}` |
| **API 版本升級時機** | Breaking Changes 發生時 |
| **向下相容** | 舊版本至少維護 6 個月 |

### 4.2 API 統一回應格式

```json
{
  "code": 200,
  "message": "Success",
  "data": { ... },
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 4.3 錯誤回應格式

```json
{
  "code": 400,
  "message": "Validation failed",
  "errors": [
    {
      "field": "email",
      "message": "Email 格式不正確",
      "code": "INVALID_FORMAT"
    }
  ],
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

---

## 5. 安全架構 (Security Architecture)

### 5.1 認證機制

**JWT 雙令牌機制**:
- Access Token: 30 分鐘有效
- Refresh Token: 30 天有效，HttpOnly Cookie

**Token Payload**:
```json
{
  "sub": "user-uuid",
  "email": "user@example.com",
  "roles": ["BUYER", "SELLER"],
  "tenantId": "tenant-uuid",
  "iat": 1712640000,
  "exp": 1712641800
}
```

### 5.2 授權控制

**RBAC 角色層級**:
```
SuperAdmin
    └── Admin
          └── StoreOwner
                └── StoreStaff
                      └── Seller / Host
                            └── Buyer
                                  └── Guest
```

### 5.3 安全防護

| 防護機制 | 實作 |
|---------|------|
| **SQL Injection** | JPA PreparedStatement |
| **XSS** | 輸出編碼 + CSP Header |
| **CSRF** | SameSite Cookie + CSRF Token |
| **Rate Limiting** | Redis + 令牌桶演算法 |
| **密碼儲存** | bcrypt (cost factor = 12) |

---

## 6. 資料模型 (Data Model)

### 6.1 核心資料表

| 表格名 | 說明 | Phase |
|--------|------|-------|
| `tenants` | 租戶/店鋪主檔 | Phase 1 |
| `tenant_members` | 租戶成員關聯 | Phase 1 |
| `tenant_feature_toggles` | 功能開關 | Phase 1 |
| `users` | 使用者主檔 | Phase 1 |
| `user_profiles` | 使用者拡張檔 | Phase 1 |
| `listings` | 統一商品/房源 | Phase 1 |
| `products` | 商品特化資料 | Phase 1 |
| `product_skus` | SKU 規格 | Phase 2 |
| `product_inventory` | 庫存管理 | Phase 1 |
| `rooms` | 房源特化資料 | Phase 1 |
| `room_calendar` | 日曆可用性 | Phase 1 |
| `orders` | 訂單主檔 | Phase 1 |
| `order_items` | 訂單明細 | Phase 1 |
| `order_state_log` | 狀態異動日誌 | Phase 1 |
| `pricing_rules` | 動態定價規則 | Phase 1 |

### 6.2 詳細 ERD（實體關係圖）

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              E-Commerce 系統 ERD                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌──────────────┐       ┌──────────────────┐       ┌──────────────┐           │
│  │   tenants    │       │ tenant_members   │       │    users    │           │
│  ├──────────────┤       ├──────────────────┤       ├──────────────┤           │
│  │ id (PK)     │◄──────│ tenant_id (FK)  │       │ id (PK)     │           │
│  │ store_name  │       │ user_id (FK)     │◄──────│ email       │           │
│  │ status      │       │ role             │       │ status      │           │
│  │ business_   │       └──────────────────┘       └──────┬───────┘           │
│  │ type        │                                        │                    │
│  └──────┬───────┘                                        │ 1:N                │
│         │                                                 ▼                    │
│         │ 1:N                  ┌──────────────────┐       ┌──────────────┐     │
│         ▼                      │ tenant_feature_  │       │ user_       │     │
│  ┌──────────────┐              │ toggles         │       │ profiles    │     │
│  │   listings   │              ├──────────────────┤       ├──────────────┤     │
│  ├──────────────┤              │ tenant_id (FK)  │       │ user_id (PK)│     │
│  │ id (PK)     │              │ feature_key     │       │ display_    │     │
│  │ tenant_id   │◄─────────────│ is_enabled      │       │ name        │     │
│  │ listing_type│              └──────────────────┘       └──────────────┘     │
│  │ title       │                                                               │
│  │ base_price  │              ┌──────────────────┐                             │
│  └──────┬───────┘              │  refresh_tokens │                             │
│         │                      ├──────────────────┤                             │
│         │ 1:1                  │ user_id (FK)    │                             │
│    ┌────┴────┐                │ token_hash      │                             │
│    ▼         ▼                └──────────────────┘                             │
│ ┌──────┐ ┌────────┐                                                          │
│ │products│ │ rooms  │                                                          │
│ ├───────┤ ├────────┤                                                          │
│ │listing │ │listing │                                                          │
│ │_id(FK)│ │_id(FK) │                                                          │
│ └───────┘ └───┬────┘                                                          │
│               │ 1:N                                                           │
│               ▼                                                                │
│      ┌──────────────────┐                                                     │
│      │  room_calendar   │                                                     │
│      ├──────────────────┤                                                     │
│      │ listing_id (FK)  │                                                     │
│      │ calendar_date    │                                                     │
│      │ status           │                                                     │
│      └──────────────────┘                                                     │
│                                                                                 │
│  ┌──────────────┐       ┌──────────────────┐       ┌──────────────┐          │
│  │   orders     │       │   order_items   │       │ pricing_     │          │
│  ├──────────────┤       ├──────────────────┤       │ rules        │          │
│  │ id (PK)     │◄──────│ order_id (FK)   │       ├──────────────┤          │
│  │ tenant_id   │       │ listing_id (FK)  │       │ tenant_id   │          │
│  │ user_id     │       └──────────────────┘       │ listing_id  │          │
│  │ status      │                                 └──────┬───────┘          │
│  │ total_amount│                                         │                   │
│  └──────┬───────┘                                         │                   │
│         │                                                 ▼                   │
│         │ 1:N              ┌──────────────────┐     ┌──────────────┐         │
│         └─────────────────>│ order_state_log │     │pricing_over  │         │
│                            ├──────────────────┤     │ rides        │         │
│                            │ order_id (FK)   │     ├──────────────┤         │
│                            │ from_status     │     │ listing_id   │         │
│                            │ to_status       │     │ override_date│         │
│                            └──────────────────┘     └──────────────┘         │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 6.3 狀態機定義

#### 6.3.1 訂單狀態流轉

```
    CREATED ──────┬──────────────────────┬──────────────┐
        │         │                      │              │
        │         │                      │              ▼
        │         │                      │         CANCELLED
        ▼         │                      │              │
     PAID ◄───────┘                      │              │
        │                                 │              │
        ▼                                 │              │
    SHIPPING ────────────────────────────┼──────────────┤
        │                                 │              │
        ▼                                 │              │
    DELIVERED ────────────────────────────┼──────────────┤
        │                                 │              │
        ├──────────────┬──────────────────┘              │
        ▼              ▼                                 │
   COMPLETED      REFUNDING                              │
        │              ▼                                 │
        │          REFUNDED                              │
        │                                                 │
        ▼                                                 ▼
   ┌─────────────────────────────────────────────────────────────┐
   │                    狀態流轉說明                             │
   ├─────────────────────────────────────────────────────────────┤
   │ CREATED → PAID:     付款完成（Phase 1 MOCK 立即完成）      │
   │ CREATED/PAID → CANCELLED: 買家或管理員取消                │
   │ PAID/SHIPPING → DELIVERED: 物流確認送達                   │
   │ DELIVERED → COMPLETED: 買家確認完成                       │
   │ DELIVERED → REFUNDING: 買家申請退款                       │
   │ REFUNDING → REFUNDED: 退款完成                            │
   └─────────────────────────────────────────────────────────────┘
```

#### 6.3.2 Listing 狀態流轉

```
       DRAFT ──────► ACTIVE ──────► INACTIVE
         │             │               │
         ▼             ▼               ▼
      DELETED       DELETED         DELETED
```

#### 6.3.3 租戶/店鋪狀態流轉

```
       PENDING ──────► APPROVED ──────► ACTIVE
           │              │               │
           ▼              ▼               ▼
       REJECTED      SUSPENDED        SUSPENDED
```

### 6.4 索引設計

**複合索引**:
- `listings`: `(tenant_id, listing_type, status)`
- `orders`: `(tenant_id, user_id, status)`
- `room_calendar`: `(listing_id, calendar_date)` — UNIQUE
- `pricing_rules`: `(tenant_id, listing_id)` WHERE listing_id IS NOT NULL

**租戶隔離索引（用於 Hibernate Filter）**:
- `listings`: `(tenant_id, status)`
- `orders`: `(tenant_id, status)`

### 6.5 詳細資料表清單

| 表格名 | 說明 | 詳細定義 |
|--------|------|----------|
| `tenants` | 租戶/店鋪主檔 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#21-tenants---租戶店鋪主檔) |
| `tenant_members` | 租戶成員關聯 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#22-tenant_members---租戶成員關聯) |
| `tenant_feature_toggles` | 功能開關 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#23-tenant_feature_toggles---功能開關) |
| `users` | 使用者主檔 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#24-users---使用者主檔) |
| `user_profiles` | 使用者擴展檔 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#25-user_profiles---使用者擴展檔) |
| `refresh_tokens` | Refresh Token 儲存 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#26-refresh_tokens---refresh-token-儲存) |
| `listings` | 統一商品/房源 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#27-listings---統一商品房源主檔) |
| `products` | 商品特化資料 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#28-products---商品特化資料) |
| `product_inventory` | 庫存管理 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#29-product_inventory---庫存管理) |
| `rooms` | 房源特化資料 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#210-rooms---房源特化資料) |
| `room_calendar` | 日曆可用性 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#211-room_calendar---日曆可用性) |
| `orders` | 訂單主檔 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#212-orders---訂單主檔) |
| `order_items` | 訂單明細 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#213-order_items---訂單明細) |
| `order_state_log` | 狀態異動日誌 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#214-order_state_log---狀態異動日誌) |
| `pricing_rules` | 動態定價規則 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#215-pricing_rules---動態定價規則) |
| `pricing_overrides` | 手動價格覆蓋 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#216-pricing_overrides---手動價格覆蓋) |

---

## 7. 部署架構 (Deployment Architecture)

### 7.1 容器架構

```
┌──────────────────────────────────────────────────────────────┐
│                    Docker Compose (Development)               │
├──────────────────────────────────────────────────────────────┤
│  ┌────────────┐  ┌────────────┐  ┌────────────────────────┐   │
│  │  frontend  │  │   backend  │  │     postgres        │   │
│  │  (Next.js)  │  │  (Spring)  │  │     (Database)      │   │
│  │   Port:3000 │  │   Port:8080│  │     Port:5432       │   │
│  └────────────┘  └────────────┘  └────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                    Redis                              │   │
│  │                    Port:6379                         │   │
│  └──────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

### 7.2 生產環境架構（未來規劃）

| 元件 | 規格 | 高可用 |
|------|------|--------|
| **ALB** | AWS Application LB | Multi-AZ |
| **Backend** | 4x t3.medium | Auto Scaling |
| **PostgreSQL** | RDS Multi-AZ | 自動容錯移轉 |
| **Redis** | ElastiCache Cluster | Multi-AZ |

---

## 8. 追溯性 (Traceability)

### 8.1 需求追蹤矩陣

| PRD Feature | FRD Business Rule | User Story | API |
|------------|-------------------|------------|-----|
| F-M01-001 | BR-M01-001 | US-M01-001 | API-M01-001 |
| F-M01-002 | BR-M01-002 | US-M01-002 | API-M01-002 |
| F-M12-001 | BR-M12-001 | US-M12-001 | API-M12-001 |
| F-M17-001 | BR-GEN-001, BR-GEN-002 | US-M17-001 | API-M17-001 |

---

## 9. 修訂歷史

| 版本 | 日期 | 作者 | 變更說明 |
|------|------|------|----------|
| v1.0 | 2026-04-09 | Marcus (SD-Architect) | 初始版本 |

---

**文檔版本**: AISDLC v0.09
**模板維護**: AISDLC Framework Team
**最後更新**: 2026-04-09
