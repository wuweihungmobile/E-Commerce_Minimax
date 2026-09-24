# E-Commerce 系統 — 資料庫 Schema 技術規格 v1.0

> **文檔類型**: SRD - Database Schema Specification
> **版本**: v2.0（Sprint 111：全文依實作重寫）
> **權威來源**: `backend/src/main/resources/db/migration/` 的 Flyway 遷移
> **依據**: E-Commerce_SRD_System_Architecture.md, E-Commerce_FRD_v1.0.md
> **建立日期**: 2026-04-09
> **作者**: Marcus (SD-Architect)
> **Phase**: Phase 1 (Must Have)
>
> **🔴 本文件的 §1~§6 由 `scripts/validate-schema-doc.sh` 自動驗證。**
> 資料表 DDL 是從「乾淨 DB 套完全部 Flyway 遷移」匯出的，不是手寫的；
> 手改 DDL 而未同步遷移，或改了遷移而未更新本文件，守門都會失敗。

---

## 📋 文檔元數據

| 項目 | 內容 |
|-----|------|
| **專案名稱** | E-Commerce B2B2C 多租戶電子商務平台 |
| **資料庫類型** | PostgreSQL 18 |
| **多租戶策略** | Shared Schema + Tenant ID Column + **每個查詢明確過濾**（**未**使用 Hibernate Filter，見 §4） |
| ** Migration 工具** | Flyway（`V{整數}__{描述}.sql`，目前 70 個遷移／65 張表） |
| **系統架構** | Clean Architecture + DDD |

---

## 📌 文檔追蹤

### 上游文檔
- **SRD 系統架構**: [SRD_System_Architecture.md](./SRD_System_Architecture.md)
- **FRD**: [E-Commerce_FRD_v1.0.md](../01_requirements/E-Commerce_FRD_v1.0.md)
- **PRD**: [E-Commerce_PRD_v1.0_Final.md](../01_requirements/E-Commerce_PRD_v1.0_Final.md)

### 下游文檔
- **API 規格**: [API_Index.md](./API_Index.md)
- **測試計劃**: [docs/03_testing/](../03_testing/)

### 已封存
- **原設計稿（v1.0，從未與實作對齊）**: [archive/SRD_Database_Schema_designdraft.md](./archive/SRD_Database_Schema_designdraft.md)
  — 保留設計期意圖供考據，**不可作為開發依據**。

---

## 1. 實體關係圖 (Entity Relationship Diagram)

### 1.1 核心實體關係

> 以下為**實作現況**（Sprint 111 依 Flyway 遷移重繪）。欄位僅列關聯鍵與代表性欄位，完整定義見 §2。

```
┌────────────────┐       ┌────────────────────┐       ┌──────────────────┐
│    tenants     │       │  tenant_members    │       │      users       │
├────────────────┤       ├────────────────────┤       ├──────────────────┤
│ id (PK)        │◄──────│ tenant_id (FK)     │       │ id (PK)          │
│ name           │       │ user_id (FK)       │──────►│ email (UK)       │
│ slug (UK)      │       │ store_role         │       │ role             │
│ status         │       │ status             │       │ tenant_id        │
│ commission_rate│       │ invited_by / _at   │       │ status           │
│ connect_*      │       │ joined_at          │       │ kyc_*            │
└──────┬─────────┘       └────────────────────┘       └────────┬─────────┘
       │                                                       │
       │ 1:N                                                   │ 1:N
       ▼                                                       ▼
┌────────────────┐       ┌────────────────────┐       ┌──────────────────┐
│    listings    │       │ tenant_feature_    │       │  refresh_tokens  │
├────────────────┤       │ toggles            │       ├──────────────────┤
│ id (PK)        │       ├────────────────────┤       │ id (PK)          │
│ tenant_id (FK) │◄──────│ tenant_id (FK)     │       │ user_id (FK)     │
│ listing_type   │       │ feature_key        │       │ token_hash (UK)  │
│ title          │       │ is_enabled         │       │ expires_at       │
│ status         │       │ config (JSONB)     │       │ revoked          │
│ base_price     │       │ enabled_at         │       └──────────────────┘
│ owner_id       │       │ disabled_at        │
└──────┬─────────┘       └────────────────────┘   ⚠️ user_profiles 從未實作，
       │                                              欄位併入 users（見 §2.2.2）
       │ 1:1（依 listing_type）
       ├──────────────────────────┐
       ▼                          ▼
┌────────────────┐       ┌────────────────────┐
│    products    │       │       rooms        │
├────────────────┤       ├────────────────────┤
│ listing_id (PK)│       │ listing_id (PK)    │
│ category       │       │ location           │
│ brand          │       │ latitude/longitude │
│ weight_grams   │       │ max_guests         │
│ dimensions_cm  │       │ amenities          │
└──────┬─────────┘       │ check_in/out_time  │
       │ 1:1             │ room_count         │
       ▼                 └─────────┬──────────┘
┌────────────────┐                 │ 1:N
│ product_skus   │                 ▼
├────────────────┤       ┌────────────────────┐
│ id (PK)        │       │   room_calendar    │
└──────┬─────────┘       ├────────────────────┤
       │ 1:1             │ id (PK)            │
       ▼                 │ room_listing_id(FK)│
┌────────────────┐       │ calendar_date      │
│product_inventory│      │ status             │
├────────────────┤       │ price              │
│ sku_id (PK,FK) │       │ booking_id         │
│ total_qty      │       └────────────────────┘
│ reserved_qty   │
│ available_qty  │  ⚠️ 主鍵是 sku_id（不是 id），
│  （GENERATED） │     且掛在 product_skus 之下，
│ version        │     不是 listings
└────────────────┘

┌────────────────┐       ┌────────────────────┐
│     orders     │       │   pricing_rules    │
├────────────────┤       ├────────────────────┤
│ id (PK)        │       │ id (PK)            │
│ tenant_id      │       │ tenant_id          │
│ user_id        │       │ listing_id         │
│ order_type     │       │ room_listing_id    │
│ status         │       │ rule_type          │
│ total_amount   │       │ rule_name          │
│ shipping_fee   │       │ priority           │
│ promo_code     │       │ config (JSONB)     │
│ discount_amount│       │ valid_from / _to   │
└──────┬─────────┘       │ is_active          │
       │ 1:N             └────────────────────┘
       ▼                  ⚠️ pricing_overrides 從未實作；手動覆蓋
┌────────────────┐           以 rule_type='MANUAL_OVERRIDE' + config
│  order_items   │           承載（見 §2.5.2）
├────────────────┤
│ id (PK)        │       ┌────────────────────┐
│ order_id (FK)  │       │  order_state_log   │
│ listing_id     │       ├────────────────────┤
│ sku_id         │◄──────│ order_id (FK)      │
│ quantity       │       │ from_status        │
│ unit_price     │       │ to_status          │
│ subtotal       │       │ changed_by         │
└────────────────┘       └────────────────────┘
```

> **本文件涵蓋範圍**：實作共有 **65 張資料表**，本節與 §2 只涵蓋其中的 **14 張核心表**
> （租戶／用戶／商品房源／訂單／定價）。其餘 51 張（金流、CMS、ERP、客服、通知、評價等）
> 未收錄於本文件，請直接參閱 `backend/src/main/resources/db/migration/` 的 Flyway 遷移。

---

## 2. 資料表詳細定義

### 2.1 租戶相關表 (Tenant Tables)

#### 2.1.1 `tenants` - 租戶/店鋪主檔

> `tenants` 紀錄**只在 Admin 核准開店申請時才被建立**，且建立即為 `ACTIVE`
> （PRD §4.3、FRD BR-M17-001）。審核前的申請資料在 `tenant_applications`，不在本表。
> 因此 `status` 的 `PENDING_REVIEW`／`REJECTED` 為生產不可達的歷史保留值。

```sql
-- ============================================
-- Table: tenants
-- Description: 租戶/店鋪主檔案
-- Module: M17 租戶管理
-- ============================================
CREATE TABLE tenants (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    logo_url VARCHAR(500),
    contact_email VARCHAR(255),
    contact_phone VARCHAR(50),
    commission_rate DOUBLE PRECISION DEFAULT 0.05,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    stripe_connect_account_id VARCHAR(255),
    connect_onboarding_status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    connect_charges_enabled BOOLEAN NOT NULL DEFAULT false,
    connect_payouts_enabled BOOLEAN NOT NULL DEFAULT false,
    purchase_order_approval_threshold NUMERIC(12,2),

    -- 約束
    CONSTRAINT tenants_pkey PRIMARY KEY (id),
    CONSTRAINT tenants_slug_key UNIQUE (slug)
);

-- 索引
CREATE INDEX idx_tenants_slug ON tenants (slug);
CREATE INDEX idx_tenants_status ON tenants (status);
```

#### 2.1.2 `tenant_members` - 租戶成員關聯

```sql
-- ============================================
-- Table: tenant_members
-- Description: 租戶成員多對多關聯表
-- Module: M17 租戶管理
-- ============================================
CREATE TABLE tenant_members (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    store_role VARCHAR(50) NOT NULL DEFAULT 'STORE_OWNER',
    invited_by UUID,
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    invited_at TIMESTAMP WITH TIME ZONE,

    -- 約束
    CONSTRAINT tenant_members_pkey PRIMARY KEY (id),
    CONSTRAINT tenant_members_tenant_id_user_id_key UNIQUE (tenant_id, user_id),
    CONSTRAINT tenant_members_invited_by_fkey FOREIGN KEY (invited_by) REFERENCES users(id),
    CONSTRAINT tenant_members_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT tenant_members_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT tenant_members_status_check CHECK (status IN ('INVITED', 'ACTIVE', 'REMOVED'))
);

-- 索引
CREATE INDEX idx_tenant_members_status ON tenant_members (status);
CREATE INDEX idx_tenant_members_tenant ON tenant_members (tenant_id);
CREATE INDEX idx_tenant_members_user ON tenant_members (user_id);
```

#### 2.1.3 `tenant_feature_toggles` - 功能開關

```sql
-- ============================================
-- Table: tenant_feature_toggles
-- Description: 租戶功能開關狀態
-- Module: M17 租戶管理, BR-FT-001
-- ============================================
CREATE TABLE tenant_feature_toggles (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    feature_key VARCHAR(100) NOT NULL,
    is_enabled BOOLEAN DEFAULT false,
    config JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    enabled_at TIMESTAMP WITH TIME ZONE,
    disabled_at TIMESTAMP WITH TIME ZONE,

    -- 約束
    CONSTRAINT tenant_feature_toggles_pkey PRIMARY KEY (id),
    CONSTRAINT tenant_feature_toggles_tenant_id_feature_key_key UNIQUE (tenant_id, feature_key),
    CONSTRAINT tenant_feature_toggles_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_feature_toggles_tenant ON tenant_feature_toggles (tenant_id);
```

---

### 2.2 用戶相關表 (User Tables)

#### 2.2.1 `users` - 使用者主檔

```sql
-- ============================================
-- Table: users
-- Description: 使用者主檔案
-- Module: M03 認證系統
-- ============================================
CREATE TABLE users (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    full_name VARCHAR(200),
    phone VARCHAR(50),
    avatar_url VARCHAR(500),
    role VARCHAR(50) NOT NULL DEFAULT 'BUYER',
    tenant_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    email_verified BOOLEAN DEFAULT false,
    phone_verified BOOLEAN DEFAULT false,
    kyc_status VARCHAR(20) DEFAULT 'NONE',
    kyc_id_number_encrypted VARCHAR(500),
    kyc_id_card_front_url VARCHAR(500),
    kyc_id_card_back_url VARCHAR(500),
    last_login_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- 約束
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_email_key UNIQUE (email)
);

-- 索引
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_status ON users (status);
CREATE INDEX idx_users_tenant ON users (tenant_id);
```

#### 2.2.2 `user_profiles` - 使用者擴展檔

> **🔴 未實作（Sprint 111 查證）**
>
> 本表**從未被實作**。設計期規劃的擴展檔欄位（姓名、電話、頭像、KYC 等）最終**併入 `users` 主檔**：
> `full_name`、`phone`、`avatar_url`、`kyc_status`、`kyc_id_number_encrypted`、
> `kyc_id_card_front_url`、`kyc_id_card_back_url`、`metadata`。
> 程式碼中沒有 `UserProfile` entity。請見 §2.2.1 `users`。


#### 2.2.3 `refresh_tokens` - Refresh Token 儲存

```sql
-- ============================================
-- Table: refresh_tokens
-- Description: Refresh Token 儲存表
-- Module: M03 認證系統
-- ============================================
CREATE TABLE refresh_tokens (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    device_info VARCHAR(500),
    ip_address VARCHAR(50),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN DEFAULT false,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- 約束
    CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash),
    CONSTRAINT refresh_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
```

---

### 2.3 商品/房源相關表 (Listing Tables)

#### 2.3.1 `listings` - 統一商品/房源主檔

```sql
-- ============================================
-- Table: listings
-- Description: 統一商品/房源抽象表
-- Module: M01 商品中心, M02 房源中心
-- ============================================
CREATE TABLE listings (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    listing_type VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    cover_image_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    owner_id UUID NOT NULL,
    base_price NUMERIC(12,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'TWD',
    tags JSONB DEFAULT '[]'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- 約束
    CONSTRAINT listings_pkey PRIMARY KEY (id),
    CONSTRAINT listings_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT listings_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_listings_created ON listings (created_at DESC);
CREATE INDEX idx_listings_owner ON listings (owner_id);
CREATE INDEX idx_listings_status ON listings (status);
CREATE INDEX idx_listings_tenant ON listings (tenant_id);
CREATE INDEX idx_listings_type ON listings (listing_type);
```

#### 2.3.2 `products` - 商品特化資料

```sql
-- ============================================
-- Table: products
-- Description: 商品特化資料（PRODUCT 型 Listing 專用）
-- Module: M01 商品中心
-- ============================================
CREATE TABLE products (
    listing_id UUID NOT NULL,
    category VARCHAR(50),
    brand VARCHAR(100),
    weight_grams INTEGER,
    dimensions_cm VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- 約束
    CONSTRAINT products_pkey PRIMARY KEY (listing_id),
    CONSTRAINT products_listing_id_fkey FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE
);
```

#### 2.3.3 `product_inventory` - 庫存管理

```sql
-- ============================================
-- Table: product_inventory
-- Description: 商品庫存管理
-- Module: M01 商品中心, BR-M01-004
-- ============================================
CREATE TABLE product_inventory (
    sku_id UUID NOT NULL,
    total_qty INTEGER DEFAULT 0,
    reserved_qty INTEGER DEFAULT 0,
    available_qty INTEGER GENERATED ALWAYS AS ((total_qty - reserved_qty)) STORED,
    version BIGINT DEFAULT 0,
    low_stock_threshold INTEGER DEFAULT 10,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- 約束
    CONSTRAINT product_inventory_pkey PRIMARY KEY (sku_id),
    CONSTRAINT product_inventory_sku_id_fkey FOREIGN KEY (sku_id) REFERENCES product_skus(id) ON DELETE CASCADE
);
```

#### 2.3.4 `rooms` - 房源特化資料

```sql
-- ============================================
-- Table: rooms
-- Description: 房源特化資料（ROOM 型 Listing 專用）
-- Module: M02 房源中心
-- ============================================
CREATE TABLE rooms (
    listing_id UUID NOT NULL,
    location VARCHAR(200),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    max_guests INTEGER DEFAULT 2,
    amenities JSONB DEFAULT '[]'::jsonb,
    check_in_time time without time zone DEFAULT '15:00:00'::time without time zone,
    check_out_time time without time zone DEFAULT '11:00:00'::time without time zone,
    room_count INTEGER DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    open_until_date DATE,
    booking_window_days INTEGER,

    -- 約束
    CONSTRAINT rooms_pkey PRIMARY KEY (listing_id),
    CONSTRAINT rooms_listing_id_fkey FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE
);
```

#### 2.3.5 `room_calendar` - 日曆可用性

```sql
-- ============================================
-- Table: room_calendar
-- Description: 房源日曆可用性與價格
-- Module: M02 房源中心
-- ============================================
CREATE TABLE room_calendar (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    room_listing_id UUID NOT NULL,
    calendar_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    price NUMERIC(12,2),
    booking_id UUID,

    -- 約束
    CONSTRAINT room_calendar_pkey PRIMARY KEY (id),
    CONSTRAINT room_calendar_room_listing_id_calendar_date_key UNIQUE (room_listing_id, calendar_date),
    CONSTRAINT room_calendar_room_listing_id_fkey FOREIGN KEY (room_listing_id) REFERENCES listings(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_room_calendar_date ON room_calendar (calendar_date);
CREATE INDEX idx_room_calendar_room_date ON room_calendar (room_listing_id, calendar_date);
CREATE INDEX idx_room_calendar_status ON room_calendar (status);
```

---

### 2.4 訂單相關表 (Order Tables)

#### 2.4.1 `orders` - 訂單主檔

```sql
-- ============================================
-- Table: orders
-- Description: 訂單主檔案
-- Module: M05 訂單履約
-- ============================================
CREATE TABLE orders (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    order_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    total_amount NUMERIC(12,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'TWD',
    shipping_address TEXT,
    shipping_recipient_name VARCHAR(200),
    shipping_phone VARCHAR(50),
    notes TEXT,
    guest_count INTEGER,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    shipping_fee NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    promo_code VARCHAR(50),
    discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    settled_statement_id UUID,

    -- 約束
    CONSTRAINT orders_pkey PRIMARY KEY (id),
    CONSTRAINT orders_settled_statement_id_fkey FOREIGN KEY (settled_statement_id) REFERENCES settlement_statements(id),
    CONSTRAINT orders_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT orders_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 索引
CREATE INDEX idx_orders_created ON orders (created_at DESC);
CREATE INDEX idx_orders_settled_statement ON orders (settled_statement_id) WHERE (settled_statement_id IS NOT NULL);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_tenant ON orders (tenant_id);
CREATE INDEX idx_orders_unsettled ON orders (tenant_id, created_at) WHERE ((settled_statement_id IS NULL) AND ((status)::text = ANY ((ARRAY['DELIVERED'::character varying, 'COMPLETED'::character varying])::text[])));
CREATE INDEX idx_orders_user ON orders (user_id);
```

#### 2.4.2 `order_items` - 訂單明細

```sql
-- ============================================
-- Table: order_items
-- Description: 訂單明細項目
-- Module: M05 訂單履約
-- ============================================
CREATE TABLE order_items (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    listing_id UUID NOT NULL,
    sku_id UUID,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    subtotal NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- 約束
    CONSTRAINT order_items_pkey PRIMARY KEY (id),
    CONSTRAINT order_items_listing_id_fkey FOREIGN KEY (listing_id) REFERENCES listings(id),
    CONSTRAINT order_items_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT order_items_sku_id_fkey FOREIGN KEY (sku_id) REFERENCES product_skus(id)
);

-- 索引
CREATE INDEX idx_order_items_listing ON order_items (listing_id);
CREATE INDEX idx_order_items_order ON order_items (order_id);
```

#### 2.4.3 `order_state_log` - 狀態異動日誌

```sql
-- ============================================
-- Table: order_state_log
-- Description: 訂單狀態異動日誌（狀態機審計軌跡）
-- Module: M05 訂單履約
-- ============================================
CREATE TABLE order_state_log (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    sequence INTEGER NOT NULL,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    changed_by UUID,
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- 約束
    CONSTRAINT order_state_log_pkey PRIMARY KEY (id),
    CONSTRAINT order_state_log_changed_by_fkey FOREIGN KEY (changed_by) REFERENCES users(id),
    CONSTRAINT order_state_log_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_order_state_log_order ON order_state_log (order_id);
CREATE INDEX idx_order_state_log_sequence ON order_state_log (order_id, sequence);
```

---

### 2.5 動態定價相關表 (Pricing Tables)

#### 2.5.1 `pricing_rules` - 動態定價規則

```sql
-- ============================================
-- Table: pricing_rules
-- Description: 動態定價規則
-- Module: M12 動態定價引擎, BR-M12-001
-- ============================================
CREATE TABLE pricing_rules (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    room_listing_id UUID,
    rule_type VARCHAR(20) NOT NULL,
    rule_name VARCHAR(100) NOT NULL,
    priority INTEGER DEFAULT 0,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    valid_from DATE NOT NULL,
    valid_to DATE NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    listing_id UUID,

    -- 約束
    CONSTRAINT pricing_rules_pkey PRIMARY KEY (id),
    CONSTRAINT pricing_rules_listing_id_fkey FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
    CONSTRAINT pricing_rules_room_listing_id_fkey FOREIGN KEY (room_listing_id) REFERENCES listings(id) ON DELETE CASCADE,
    CONSTRAINT pricing_rules_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_pricing_rules_active ON pricing_rules (is_active);
CREATE INDEX idx_pricing_rules_listing_id ON pricing_rules (listing_id);
CREATE INDEX idx_pricing_rules_room ON pricing_rules (room_listing_id);
CREATE INDEX idx_pricing_rules_tenant ON pricing_rules (tenant_id);
```

#### 2.5.2 `pricing_overrides` - 手動價格覆蓋

> **🔴 未實作（Sprint 111 查證）**
>
> 本表**從未被實作**。設計期規劃的「手動價格覆蓋」最終**併入 `pricing_rules`**，
> 以 `rule_type = 'MANUAL_OVERRIDE'` 加上 `config` JSONB 承載覆蓋內容
> （見 `PricingService.java:279,330`、`PricingRule.PricingRuleType`）。請見 §2.5.1 `pricing_rules`。

### 2.6 已知範圍外資料表（技術債，DEF-068）

> 🔴 **本清單受 `check_schema_doc.py` 主動守門**：Flyway 遷移新增的任何表，若沒有出現在
> §2 的逐欄 DDL 小節、PRD §8.2 的欄位清單、或本清單中，`make validate-schema-doc` 會直接失敗。
> 這逼開發者在新增表時做出明確決策——補齊正式文件，或至少把表名列進這裡承認技術債——
> 而不是像過去一樣「文件沒寫的表，漂移完全不會被發現」（DEF-068 的成因）。
>
> 下表只列「實作存在、本文件目前未提供逐欄 DDL」的表，依模組分類供快速查找，**不是**逐欄規格；
> 需要正式欄位定義時仍以 Flyway 遷移或 `make sync-schema-doc` 產生的 DDL 為權威來源。
> 日後若要補正式規格，把表名從本清單移到 §2 對應小節（含 DDL 區塊）即可。

| 表名 | 模組 |
|------|------|
| `addresses` | 會員（地址簿） |
| `oauth_accounts` | 會員（第三方登入） |
| `carts` | 購物車 |
| `cart_items` | 購物車 |
| `bookings` | 訂房 |
| `booking_reviews` | 訂房評價 |
| `product_skus` | 商品 SKU |
| `promo_codes` | 促銷 |
| `promo_code_usages` | 促銷 |
| `payments` | 金流 |
| `processed_stripe_events` | 金流（Stripe Webhook 冪等） |
| `logistics` | 物流 |
| `shipping_templates` | 物流 |
| `reviews` | 評論 |
| `review_replies` | 評論 |
| `conversations` | 即時聊天 |
| `messages` | 即時聊天 |
| `notifications` | 通知 |
| `notification_history` | 通知 |
| `notification_templates` | 通知 |
| `user_notification_preferences` | 通知 |
| `support_tickets` | 客服 |
| `support_messages` | 客服 |
| `cms_banners` | CMS |
| `cms_pages` | CMS |
| `post_categories` | CMS／內容 |
| `media_categories` | 媒體／CMS |
| `knowledge_categories` | 知識庫 |
| `knowledge_articles` | 知識庫 |
| `knowledge_article_tags` | 知識庫 |
| `faq_categories` | 知識庫／FAQ |
| `faq_articles` | 知識庫／FAQ |
| `article_versions` | 知識庫（版本歷史） |
| `adjustment_statements` | ERP／結算 |
| `transfers` | ERP／結算 |
| `audit_log` | 稽核（現行） |
| `audit_logs` | 稽核（`V1__Initial_Schema.sql` 建立，與 `audit_log` 並存，未查證是否重複——非 DEF-068 範圍，暫列存查） |
| `_media_assets_backup` | 備份表（`V38` 遷移殘留，非 DEF-068 範圍） |
| `listings_tags_backup` | 備份表（`V8` 遷移殘留，非 DEF-068 範圍） |

---

## 3. 狀態機定義 (State Machine Definitions)

### 3.1 訂單狀態流轉

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           訂單狀態機                                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│    CREATED ──────┬──────────────────────┬──────────────┐                │
│        │         │                      │              │                │
│        │         │                      │              ▼                │
│        │         │                      │         CANCELLED            │
│        │         │                      │              │                │
│        ▼         │                      │              │                │
│     PAID ◄───────┘                      │              │                │
│        │                                 │              │                │
│        ▼                                 │              │                │
│    SHIPPING ────────────────────────────┼──────────────┤                │
│        │                                 │              │                │
│        ▼                                 │              │                │
│    DELIVERED ────────────────────────────┼──────────────┤                │
│        │                                 │              │                │
│        ├──────────────┬──────────────────┘              │                │
│        │             │                                 │                │
│        ▼             ▼                                 │                │
│   COMPLETED     REFUNDING                              │                │
│                      │                                 │                │
│                      ▼                                 │                │
│                  REFUNDED                             │                │
│                                                         │                │
│  ══════════════════════════════════════════════════════│                │
│  狀態流轉說明：                                          │                │
│  • CREATED → PAID: 付款完成（Phase 1 為 MOCK）         │                │
│  • CREATED/PAID → CANCELLED: 買家/管理員取消           │                │
│  • PAID/SHIPPING → DELIVERED: 物流確認送達             │                │
│  • DELIVERED → COMPLETED: 買家確認完成                │                │
│  • DELIVERED → REFUNDING: 買家申請退款                 │                │
└─────────────────────────────────────────────────────────────────────────┘
```

> **Sprint 111 補正：本圖漏了 `CONFIRMED`。** `Order.OrderStatus` 實際有 **9 個值**
> （`CREATED, PAID, CONFIRMED, SHIPPING, DELIVERED, COMPLETED, CANCELLED, REFUNDING, REFUNDED`），
> 上圖只畫了 8 個。
>
> `CONFIRMED` **不由付款或物流流程自動產生**——付款成功寫入的是 `PAID`
> （`PaymentStateService:134`），物流寫入的是 `SHIPPING`／`DELIVERED`
> （`LogisticsService:95,208`）。它只能經由通用狀態更新端點設定
> （`OrderService.updateOrderStatus`，L598 以 `OrderStatus.valueOf(targetStatus)` 直接寫入）。
>
> 而 **`LogisticsService:63` 要求訂單必須是 `CONFIRMED` 才能建立物流**，否則拋 `E_5001`。
> 也就是說正常出貨路徑是 `PAID →（賣家手動確認）→ CONFIRMED → SHIPPING`。
> 本文件只陳述現況，**不主張這是缺陷**——「賣家確認後才出貨」是合理設計；
> 是否該有專屬的確認端點而非走通用 `updateOrderStatus`，未在本輪判斷。

### 3.2 Listing 狀態流轉

```
┌─────────────────────────────────────────────────────────────────┐
│                      Listing 狀態機                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│    DRAFT ──────► ACTIVE ──────► INACTIVE                       │
│      │             │               │                           │
│      │             │               │                           │
│      ▼             ▼               ▼                           │
│   DELETED       DELETED         DELETED                       │
│                                                                 │
│  ════════════════════════════════════════════════════════════  │
│  狀態流轉說明：                                                │
│  • DRAFT → ACTIVE: 店家上架房源                                │
│  • ACTIVE → INACTIVE: 店家下架房源                            │
│  • ACTIVE/INACTIVE → DELETED: 店家刪除房源                    │
│  • 任何狀態 → DELETED: 店家刪除（軟刪除）                    │
└─────────────────────────────────────────────────────────────────┘
```

### 3.3 租戶/店鋪狀態流轉

> **Sprint 111 更正**：原圖畫的是 `PENDING → APPROVED → ACTIVE` 的單一實體狀態機，
> 那是**第三個版本**——與 PRD v1.0 原文（`PENDING_REVIEW → ACTIVE`）和實作都不一樣。
> 實作是**兩張表、兩個生命週期**（PRD §4.3、FRD BR-M17-001，Sprint 110 已同步）。

```
階段一：開店申請 (tenant_applications.status)

  [網友送出申請] → PENDING ──[Admin 核准]──→ APPROVED ─┐
                      │                                 │
                      └──[Admin 駁回]──→ REJECTED       │ 核准的同一交易內
                         （不建立 Tenant，可重新申請）  │ 才建立 Tenant
                                                        │
階段二：店鋪 (tenants.status)                           │
                                                        ▼
                            ┌──────────────────────→ ACTIVE → [運營中]
                            │                           │
              [Admin 恢復]  │                           │ [Admin 暫停]
                            │                           ▼
                            └────────────────────── SUSPENDED (違規)
                                                        │
                                                        │ [Admin 終止]
                                                        ▼
                                                   TERMINATED
```

**要點**：
- `tenants` 紀錄只在核准當下建立，且**建立即為 `ACTIVE`**——不存在「先建店鋪再審核」。
- `tenants.status` 的 `PENDING_REVIEW`／`REJECTED` 為生產不可達的歷史保留值
  （enum 與 DB 預設值仍保留以維持相容，見 §2.1.1）。
- 🔴 資料庫層**沒有** CHECK 約束把關這些狀態值（見 §6.1），合法值僅由 Java enum 與應用層維護。

---

## 4. 多租戶隔離實現 (Multi-Tenant Isolation)

> **🔴 Sprint 111 重寫。原文描述的機制在這個 codebase 中不存在，且該描述是危險的。**
>
> 原 §4.1／§4.3 宣稱「所有查詢自動附加 `tenant_id` 條件」「Hibernate 會自動轉換」，並示範了一個
> `TenantAwareEntity` 基底類別。查證結果：**專案中沒有任何 `@FilterDef`／`@Filter`
> （全 codebase 零命中），也沒有 `TenantAwareEntity` 這個類別。**
>
> 為什麼這件事重要：照原文理解，開發者會以為「只要 entity 有 `tenant_id`，查詢就自動被隔離」，
> 因而在新增 Service 方法時不做租戶檢查。追蹤表上 **DEF-023／024／037／040／041／057**
> 這一整串「某某方法沒有租戶過濾」的 IDOR 缺陷，正是這個誤解會導致的結果。

### 4.1 實際機制：ThreadLocal 上下文 + 每個查詢明確過濾

租戶隔離**不是自動的**，由兩個部分組成：

1. **`TenantContextFilter`**（`api/filter/`）：每個 HTTP 請求進來時，依 JWT 與 `X-Tenant-ID`
   header 決定當前租戶，寫入 `TenantContext` 的 `ThreadLocal`（另存當前 user）。
2. **每一個查詢／服務方法自行過濾**：Repository 端多以
   `findByIdAndTenantId(id, tenantId)` 這類**明確帶租戶條件的方法**取代 `findById`；
   Service 端則呼叫 `TenantContext.getCurrentTenant()` 自行比對擁有權。
   全 codebase 約有 **226 處**這類明確呼叫。

```java
// ✅ 實際做法：Repository 明確帶 tenantId
Optional<Order> findByIdAndTenantId(UUID orderId, UUID tenantId);

// ✅ 實際做法：Service 明確比對擁有權
UUID currentTenant = TenantContext.getCurrentTenant();
if (!order.getTenantId().equals(currentTenant)) {
    throw new BusinessException(ErrorCode.E_1007);   // 403 跨租戶存取
}
```

### 4.2 這個設計的後果：漏一個就是一個 IDOR

因為沒有任何自動兜底，**每新增一個讀寫租戶資料的方法，都必須自己記得加過濾**。
漏掉不會有編譯錯誤、不會有測試自動失敗，只會安靜地變成一個跨租戶存取漏洞。

🔴 **新增 Service 方法時的檢查點**：
- 用了 `findById` 而不是 `findByIdAndTenantId` 嗎？
- 有沒有拿 `TenantContext.getCurrentTenant()` 比對過擁有權？
- 若刻意跨租戶（平台級 Admin 功能），是否明確檢查了角色而非「忘了加」？
  ——「同類別其他方法都做了、只有這一個沒做」是**自相矛盾**，通常就是漏了。

### 4.3 Flyway Migration 命名規範（實際）

實際採用的是**單一整數版號**，不是原文寫的 `V1.0.0` 語意化版號：

```
backend/src/main/resources/db/migration/
├── V1__Initial_Schema.sql              # 初始 schema
├── V4__Tenant_Application.sql          # 開店申請表
├── V7__M17_Test_Data_Init.sql          # M17 測試固件
├── ...
└── V70__Add_Promo_Code_To_Orders_And_Usages.sql
```

目前共 **70 個遷移檔**，建出 **65 張資料表**。命名慣例為
`V{整數版號}__{描述}.sql`（雙底線分隔），版號連續遞增、不重複使用。

---

## 5. 索引設計總結

> 以下為**實作現況**（Sprint 111 自 Flyway 建成的 DB 匯出），僅涵蓋 §2 收錄的 14 張核心表。

### 5.1 一般索引

| 表格 | 索引名 | 欄位 |
|------|--------|------|
| `listings` | idx_listings_created | (created_at DESC) |
| `listings` | idx_listings_owner | (owner_id) |
| `listings` | idx_listings_status | (status) |
| `listings` | idx_listings_tenant | (tenant_id) |
| `listings` | idx_listings_type | (listing_type) |
| `order_items` | idx_order_items_listing | (listing_id) |
| `order_items` | idx_order_items_order | (order_id) |
| `order_state_log` | idx_order_state_log_order | (order_id) |
| `order_state_log` | idx_order_state_log_sequence | (order_id, sequence) |
| `orders` | idx_orders_created | (created_at DESC) |
| `orders` | idx_orders_status | (status) |
| `orders` | idx_orders_tenant | (tenant_id) |
| `orders` | idx_orders_user | (user_id) |
| `pricing_rules` | idx_pricing_rules_active | (is_active) |
| `pricing_rules` | idx_pricing_rules_listing_id | (listing_id) |
| `pricing_rules` | idx_pricing_rules_room | (room_listing_id) |
| `pricing_rules` | idx_pricing_rules_tenant | (tenant_id) |
| `refresh_tokens` | idx_refresh_tokens_hash | (token_hash) |
| `refresh_tokens` | idx_refresh_tokens_user | (user_id) |
| `room_calendar` | idx_room_calendar_date | (calendar_date) |
| `room_calendar` | idx_room_calendar_room_date | (room_listing_id, calendar_date) |
| `room_calendar` | idx_room_calendar_status | (status) |
| `tenant_feature_toggles` | idx_feature_toggles_tenant | (tenant_id) |
| `tenant_members` | idx_tenant_members_status | (status) |
| `tenant_members` | idx_tenant_members_tenant | (tenant_id) |
| `tenant_members` | idx_tenant_members_user | (user_id) |
| `tenants` | idx_tenants_slug | (slug) |
| `tenants` | idx_tenants_status | (status) |
| `users` | idx_users_email | (email) |
| `users` | idx_users_role | (role) |
| `users` | idx_users_status | (status) |
| `users` | idx_users_tenant | (tenant_id) |

⚠️ **`product_inventory`、`products`、`rooms` 目前沒有任何額外索引**（僅主鍵）。
本文件不主張它們「應該」有——如需評估，請另行以查詢計畫佐證，不要據本表推論。

### 5.2 唯一約束

| 表格 | 約束名 | 欄位 |
|------|--------|------|
| `tenants` | tenants_slug_key | (slug) |
| `users` | users_email_key | (email) |
| `tenant_members` | tenant_members_tenant_id_user_id_key | (tenant_id, user_id) |
| `tenant_feature_toggles` | tenant_feature_toggles_tenant_id_feature_key_key | (tenant_id, feature_key) |
| `room_calendar` | room_calendar_room_listing_id_calendar_date_key | (room_listing_id, calendar_date) |
| `refresh_tokens` | refresh_tokens_token_hash_key | (token_hash) |

⚠️ **`tenants` 的 `name`／`contact_email` 沒有唯一約束**（設計稿曾規劃 `uk_tenants_store_name`、
`uk_tenants_contact_email`，實作中不存在）；`orders` 沒有訂單號欄位，自然也沒有對應唯一約束。

---

## 6. 約束與觸發器

### 6.1 CHECK 約束（實作現況）

§2 收錄的 14 張核心表中，**只有一個 CHECK 約束**：

```sql
-- tenant_members.status
CONSTRAINT tenant_members_status_check CHECK (status IN ('INVITED', 'ACTIVE', 'REMOVED'))
```

🔴 **這是一個值得注意的事實，不是筆誤**：`tenants.status`、`orders.status`、`listings.status`、
`room_calendar.status` 等狀態欄位在資料庫層**都沒有 CHECK 約束**，合法值僅由 Java entity 的
enum 與應用層把關。設計稿曾規劃 `chk_business_type`、`chk_order_status`、`chk_calendar_status`、
`chk_base_price_positive`、`chk_final_amount_positive` 等約束，**這些在實作中都不存在**
（其中 `business_type`、`final_amount` 連欄位本身都不存在）。

本文件只陳述現況，**不主張應該補上**——加 DB 層約束會影響既有資料與遷移，屬產品/架構決策。

### 6.2 時間戳自動更新觸發器（實作現況）

```sql
-- 自動更新 updated_at 的函數（實作中確實存在）
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';
```

§2 收錄的 14 張表中，**有掛上此觸發器的只有 6 張**：

| 表格 | 觸發器名 |
|------|----------|
| `tenants` | update_tenants_updated_at |
| `users` | update_users_updated_at |
| `listings` | update_listings_updated_at |
| `products` | update_products_updated_at |
| `rooms` | update_rooms_updated_at |
| `orders` | update_orders_updated_at |

⚠️ **`tenant_feature_toggles`、`pricing_rules`、`product_inventory` 有 `updated_at` 欄位但沒有觸發器**
——這三張表的 `updated_at` 由 Hibernate（`@UpdateTimestamp` 等）在應用層維護，**繞過 SQL 直接更新
資料庫時不會自動推進**。本文件只陳述現況，不主張補上觸發器。

（全庫另有 7 個同型觸發器掛在本文件未收錄的表上：`bookings`、`carts`、`payments`、`product_skus`、
`purchase_orders`、`suppliers`、`tenant_applications`。）

---

## 7. 修訂歷史

| 版本 | 日期 | 作者 | 變更說明 |
|------|------|------|----------|
| v1.0 | 2026-04-09 | Marcus (SD-Architect) | 初始版本，包含 Phase 1 所有表格定義 |
| v2.0 | 2026-09-02 | Sprint 111 (DEF-062) | **全文依實作重寫。** 以「乾淨 DB 套完 70 個 Flyway 遷移」為權威基準比對，原 v1.0 所記 16 張表**全部漂移**（117 個幽靈欄位、55 個未記載欄位、2 張表從未實作、`product_inventory` 連主鍵都寫錯）。本版：§1 ERD 重繪；§2 的 14 張表 DDL 全部自 DB 匯出；`user_profiles`／`pricing_overrides` 改標為未實作並說明由誰取代；§3.1 補上遺漏的 `CONFIRMED`；§3.3 租戶狀態機改為兩表兩階段；**§4 重寫**（原文宣稱的 Hibernate Filter 自動隔離並不存在）；§5／§6 索引與約束改為實測清單。原設計稿封存於 `archive/`。新增 `scripts/validate-schema-doc.sh` 守門防止再漂移。 |

---

**文檔版本**: AISDLC v0.09
**模板維護**: AISDLC Framework Team
**最後更新**: 2026-09-02（Sprint 111）
