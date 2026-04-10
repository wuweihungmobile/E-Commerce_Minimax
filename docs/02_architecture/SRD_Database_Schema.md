# E-Commerce 系統 — 資料庫 Schema 技術規格 v1.0

> **文檔類型**: SRD - Database Schema Specification
> **版本**: v1.0
> **依據**: E-Commerce_SRD_System_Architecture.md, E-Commerce_FRD_v1.0.md
> **建立日期**: 2026-04-09
> **作者**: Marcus (SD-Architect)
> **Phase**: Phase 1 (Must Have)

---

## 📋 文檔元數據

| 項目 | 內容 |
|-----|------|
| **專案名稱** | E-Commerce B2B2C 多租戶電子商務平台 |
| **資料庫類型** | PostgreSQL 16 |
| **多租戶策略** | Shared Schema + Tenant ID + Hibernate Filter |
| ** Migration 工具** | Flyway 9.x |
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

---

## 1. 實體關係圖 (Entity Relationship Diagram)

### 1.1 核心實體關係

```
┌──────────────┐       ┌──────────────────┐       ┌──────────────┐
│   tenants    │       │ tenant_members   │       │    users    │
├──────────────┤       ├──────────────────┤       ├──────────────┤
│ id (PK)     │◄──────│ tenant_id (FK)  │       │ id (PK)     │
│ store_name  │       │ user_id (FK)     │◄──────│ email       │
│ status      │       │ role             │       │ status      │
│ business_   │       │ joined_at        │       │ user_type   │
│ type        │       └──────────────────┘       └──────┬───────┘
└──────┬───────┘                                          │
       │                                                  │
       │ 1:N                                              │ 1:N
       ▼                                                  ▼
┌──────────────┐       ┌──────────────────┐       ┌──────────────┐
│   listings   │       │ tenant_feature_  │       │ user_       │
├──────────────┤       │ toggles         │       │ profiles    │
│ id (PK)     │       ├──────────────────┤       ├──────────────┤
│ tenant_id    │◄─────│ tenant_id (FK)  │       │ user_id (PK)│
│ listing_type│       │ feature_key     │       │ display_    │
│ title       │       │ is_enabled      │       │ name        │
│ status      │       │ requested_at    │       │ phone       │
│ base_price  │       │ enabled_at      │       │ avatar_url  │
└──────┬───────┘       └──────────────────┘       └──────────────┘
       │
       │ 1:1 (listing_type)
       ├────────────────────────┐
       ▼                        ▼
┌──────────────┐       ┌──────────────────┐
│  products   │       │      rooms       │
├──────────────┤       ├──────────────────┤
│ listing_id  │       │ listing_id (PK) │
│ category    │       │ location         │
│ brand       │       │ latitude         │
└──────────────┘       │ longitude       │
                       │ max_guests      │
                       │ amenities       │
                       │ check_in_time   │
                       │ check_out_time │
                       └────────┬─────────┘
                                │ 1:N
                                ▼
                       ┌──────────────────┐
                       │  room_calendar   │
                       ├──────────────────┤
                       │ listing_id (FK)  │
                       │ calendar_date    │
                       │ status           │
                       │ price_override   │
                       └──────────────────┘

┌──────────────┐       ┌──────────────────┐
│   orders     │       │  pricing_rules   │
├──────────────┤       ├──────────────────┤
│ id (PK)     │       │ id (PK)          │
│ tenant_id   │       │ tenant_id (FK)   │
│ user_id     │       │ listing_id       │
│ order_number│       │ rule_type        │
│ status      │       │ rule_name        │
│ total_amount│       │ adjustment_type  │
│ payment_    │       │ adjustment_value│
│ method      │       │ priority         │
│ created_at  │       │ is_active        │
└──────┬───────┘       │ conditions       │
       │                └──────────────────┘
       │ 1:N
       ▼
┌──────────────┐
│ order_items  │
├──────────────┤
│ id (PK)     │
│ order_id (FK)│
│ listing_id  │
│ sku_id       │
│ quantity     │
│ unit_price  │
│ subtotal    │
└──────────────┘
```

---

## 2. 資料表詳細定義

### 2.1 租戶相關表 (Tenant Tables)

#### 2.1.1 `tenants` - 租戶/店鋪主檔

```sql
-- ============================================
-- Table: tenants
-- Description: 租戶/店鋪主檔案
-- Module: M17 租戶管理
-- ============================================
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_name VARCHAR(100) NOT NULL,
    store_description TEXT,
    business_type VARCHAR(20) NOT NULL CHECK (business_type IN ('RETAIL_ONLY', 'BOOKING_ONLY', 'HYBRID')),
    contact_email VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(20),
    business_license_url VARCHAR(500),
    logo_url VARCHAR(500),
    cover_image_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED', 'ACTIVE')),
    owner_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP WITH TIME ZONE,
    approved_by UUID,
    rejected_at TIMESTAMP WITH TIME ZONE,
    rejected_by UUID,
    rejection_reason TEXT,
    
    -- 約束
    CONSTRAINT uk_tenants_store_name UNIQUE (store_name),
    CONSTRAINT uk_tenants_contact_email UNIQUE (contact_email)
);

-- 索引
CREATE INDEX idx_tenants_status ON tenants(status);
CREATE INDEX idx_tenants_owner_id ON tenants(owner_id);
CREATE INDEX idx_tenants_business_type ON tenants(business_type);
CREATE INDEX idx_tenants_created_at ON tenants(created_at DESC);

-- 註解
COMMENT ON TABLE tenants IS '租戶/店鋪主檔案';
COMMENT ON COLUMN tenants.business_type IS 'RETAIL_ONLY: 僅零售, BOOKING_ONLY: 僅民宿, HYBRID: 混合型';
COMMENT ON COLUMN tenants.status IS 'PENDING: 待審核, APPROVED: 審核通過, REJECTED: 審核駁回, SUSPENDED: 已停權, ACTIVE: 正常營運';
```

#### 2.1.2 `tenant_members` - 租戶成員關聯

```sql
-- ============================================
-- Table: tenant_members
-- Description: 租戶成員多對多關聯表
-- Module: M17 租戶管理
-- ============================================
CREATE TABLE tenant_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'STAFF')),
    display_name VARCHAR(100),
    invited_by UUID,
    invited_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    joined_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'REMOVED')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- 約束
    CONSTRAINT uk_tenant_member UNIQUE (tenant_id, user_id),
    CONSTRAINT fk_tenant_members_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_tenant_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_tenant_members_tenant_id ON tenant_members(tenant_id);
CREATE INDEX idx_tenant_members_user_id ON tenant_members(user_id);
CREATE INDEX idx_tenant_members_role ON tenant_members(role);

COMMENT ON TABLE tenant_members IS '租戶成員多對多關聯表';
COMMENT ON COLUMN tenant_members.role IS 'OWNER: 擁有者, ADMIN: 管理員, STAFF: 員工';
```

#### 2.1.3 `tenant_feature_toggles` - 功能開關

```sql
-- ============================================
-- Table: tenant_feature_toggles
-- Description: 租戶功能開關狀態
-- Module: M17 租戶管理, BR-FT-001
-- ============================================
CREATE TABLE tenant_feature_toggles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    feature_key VARCHAR(50) NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    requested_at TIMESTAMP WITH TIME ZONE,
    requested_by UUID,
    enabled_at TIMESTAMP WITH TIME ZONE,
    enabled_by UUID,
    disabled_at TIMESTAMP WITH TIME ZONE,
    disabled_by UUID,
    notes TEXT,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- 約束
    CONSTRAINT uk_tenant_feature UNIQUE (tenant_id, feature_key),
    CONSTRAINT fk_tenant_feature_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT chk_feature_key CHECK (feature_key IN (
        'RETAIL_ENABLED', 'BOOKING_ENABLED', 'CMS_ENABLED', 
        'ERP_ENABLED', 'DYNAMIC_PRICING_ENABLED', 'PROMO_ENABLED'
    ))
);

-- 索引
CREATE INDEX idx_tenant_feature_tenant_id ON tenant_feature_toggles(tenant_id);
CREATE INDEX idx_tenant_feature_key ON tenant_feature_toggles(feature_key);
CREATE INDEX idx_tenant_feature_enabled ON tenant_feature_toggles(is_enabled) WHERE is_enabled = TRUE;

COMMENT ON TABLE tenant_feature_toggles IS '租戶功能開關狀態';
COMMENT ON COLUMN tenant_feature_toggles.feature_key IS '功能鍵: RETAIL_ENABLED, BOOKING_ENABLED, CMS_ENABLED, ERP_ENABLED, DYNAMIC_PRICING_ENABLED, PROMO_ENABLED';
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
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    user_type VARCHAR(20) NOT NULL DEFAULT 'BUYER' CHECK (user_type IN ('BUYER', 'SELLER', 'HOST', 'ADMIN')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified_at TIMESTAMP WITH TIME ZONE,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    suspended_at TIMESTAMP WITH TIME ZONE,
    suspended_by UUID,
    suspension_reason TEXT,
    deleted_at TIMESTAMP WITH TIME ZONE,
    
    -- 約束
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- 索引
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_user_type ON users(user_type);
CREATE INDEX idx_users_created_at ON users(created_at DESC);

COMMENT ON TABLE users IS '使用者主檔案';
COMMENT ON COLUMN users.user_type IS 'BUYER: 買家, SELLER: 賣家, HOST: 民宿主人, ADMIN: 平台管理員';
COMMENT ON COLUMN users.status IS 'ACTIVE: 正常, SUSPENDED: 停權, DELETED: 已刪除';
```

#### 2.2.2 `user_profiles` - 使用者擴展檔

```sql
-- ============================================
-- Table: user_profiles
-- Description: 使用者擴展資訊
-- Module: M03 認證系統
-- ============================================
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    display_name VARCHAR(100),
    phone VARCHAR(20),
    avatar_url VARCHAR(500),
    gender VARCHAR(10) CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY')),
    date_of_birth DATE,
    bio TEXT,
    default_address_id UUID,
    notification_preferences JSONB DEFAULT '{}',
    preferences JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- 約束
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_user_profiles_user_id ON user_profiles(user_id);
CREATE INDEX idx_user_profiles_display_name ON user_profiles(display_name);

COMMENT ON TABLE user_profiles IS '使用者擴展資訊';
```

#### 2.2.3 `refresh_tokens` - Refresh Token 儲存

```sql
-- ============================================
-- Table: refresh_tokens
-- Description: Refresh Token 儲存表
-- Module: M03 認證系統
-- ============================================
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    device_info JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_by UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- 約束
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
CREATE INDEX idx_refresh_tokens_active ON refresh_tokens(user_id, revoked_at) WHERE revoked_at IS NULL;

COMMENT ON TABLE refresh_tokens IS 'Refresh Token 儲存表';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'Token 的 SHA-256 哈希值，用於安全儲存和查找';
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
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    listing_type VARCHAR(20) NOT NULL CHECK (listing_type IN ('PRODUCT', 'ROOM')),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    cover_image_url VARCHAR(500),
    images TEXT[],
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'DELETED')),
    owner_id UUID NOT NULL,
    base_price DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'TWD',
    tags TEXT[],
    location VARCHAR(200),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    max_guests INT,
    amenities TEXT[],
    check_in_time TIME DEFAULT '15:00',
    check_out_time TIME DEFAULT '11:00',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    version INT NOT NULL DEFAULT 1,
    
    -- 約束
    CONSTRAINT fk_listings_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_listings_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_base_price_positive CHECK (base_price > 0)
);

-- 索引
CREATE INDEX idx_listings_tenant_id ON listings(tenant_id);
CREATE INDEX idx_listings_owner_id ON listings(owner_id);
CREATE INDEX idx_listings_type ON listings(listing_type);
CREATE INDEX idx_listings_status ON listings(status);
CREATE INDEX idx_listings_created_at ON listings(created_at DESC);
CREATE INDEX idx_listings_location ON listings(location);
CREATE UNIQUE INDEX idx_listings_tenant_type_status ON listings(tenant_id, listing_type, status);

-- 租戶隔離索引（重要：用於 Hibernate Filter）
CREATE INDEX idx_listings_tenant_id_status ON listings(tenant_id, status);

COMMENT ON TABLE listings IS '統一商品/房源抽象表，支援 PRODUCT 和 ROOM 兩種類型';
COMMENT ON COLUMN listings.listing_type IS 'PRODUCT: 實體商品, ROOM: 民宿房源';
COMMENT ON COLUMN listings.status IS 'DRAFT: 草稿, ACTIVE: 上架, INACTIVE: 下架, DELETED: 已刪除';
```

#### 2.3.2 `products` - 商品特化資料

```sql
-- ============================================
-- Table: products
-- Description: 商品特化資料（PRODUCT 型 Listing 專用）
-- Module: M01 商品中心
-- ============================================
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID NOT NULL UNIQUE,
    category VARCHAR(50) NOT NULL,
    subcategory VARCHAR(50),
    brand VARCHAR(100),
    model VARCHAR(100),
    weight DECIMAL(10, 3),
    weight_unit VARCHAR(10) DEFAULT 'g',
    dimensions JSONB,
    ingredients TEXT,
    nutrition_info JSONB,
    shelf_life VARCHAR(100),
    storage_conditions VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- 約束
    CONSTRAINT fk_products_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_products_listing_id ON products(listing_id);
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_brand ON products(brand);

COMMENT ON TABLE products IS '商品特化資料，僅用於 listing_type = PRODUCT 的房源';
COMMENT ON COLUMN products.dimensions IS 'JSON 格式: {"length": 10, "width": 5, "height": 3, "unit": "cm"}';
```

#### 2.3.3 `product_inventory` - 庫存管理

```sql
-- ============================================
-- Table: product_inventory
-- Description: 商品庫存管理
-- Module: M01 商品中心, BR-M01-004
-- ============================================
CREATE TABLE product_inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID NOT NULL,
    sku_id UUID,
    quantity INT NOT NULL DEFAULT 0,
    reserved_quantity INT NOT NULL DEFAULT 0,
    available_quantity INT GENERATED ALWAYS AS (quantity - reserved_quantity) STORED,
    low_stock_threshold INT DEFAULT 10,
    reorder_point INT,
    warehouse_location VARCHAR(50),
    last_restocked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 1,
    
    -- 約束
    CONSTRAINT fk_inventory_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
    CONSTRAINT chk_quantity_non_negative CHECK (quantity >= 0),
    CONSTRAINT chk_reserved_non_negative CHECK (reserved_quantity >= 0),
    CONSTRAINT chk_available_non_negative CHECK (available_quantity >= 0)
);

-- 索引
CREATE UNIQUE INDEX idx_inventory_listing_sku ON product_inventory(listing_id, sku_id) WHERE sku_id IS NOT NULL;
CREATE UNIQUE INDEX idx_inventory_listing_no_sku ON product_inventory(listing_id) WHERE sku_id IS NULL;
CREATE INDEX idx_inventory_low_stock ON product_inventory(listing_id, available_quantity) WHERE available_quantity <= low_stock_threshold;

COMMENT ON TABLE product_inventory IS '商品庫存管理';
COMMENT ON COLUMN product_inventory.reserved_quantity IS '預留數量（訂單保留中）';
COMMENT ON COLUMN product_inventory.available_quantity IS '可用數量 = quantity - reserved_quantity';
```

#### 2.3.4 `rooms` - 房源特化資料

```sql
-- ============================================
-- Table: rooms
-- Description: 房源特化資料（ROOM 型 Listing 專用）
-- Module: M02 房源中心
-- ============================================
CREATE TABLE rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID NOT NULL UNIQUE,
    total_rooms INT NOT NULL DEFAULT 1,
    available_rooms INT NOT NULL DEFAULT 1,
    floor INT,
    room_size DECIMAL(8, 2),
    room_size_unit VARCHAR(10) DEFAULT '坪',
    bed_type VARCHAR(50),
    bed_count INT,
    max_adults INT DEFAULT 2,
    max_children INT DEFAULT 0,
    room_view VARCHAR(50),
    smoking_allowed BOOLEAN DEFAULT FALSE,
    pet_allowed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- 約束
    CONSTRAINT fk_rooms_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
    CONSTRAINT chk_available_rooms CHECK (available_rooms >= 0 AND available_rooms <= total_rooms)
);

-- 索引
CREATE INDEX idx_rooms_listing_id ON rooms(listing_id);

COMMENT ON TABLE rooms IS '房源特化資料，僅用於 listing_type = ROOM 的房源';
```

#### 2.3.5 `room_calendar` - 日曆可用性

```sql
-- ============================================
-- Table: room_calendar
-- Description: 房源日曆可用性與價格
-- Module: M02 房源中心
-- ============================================
CREATE TABLE room_calendar (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID NOT NULL,
    calendar_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'BOOKED', 'BLOCKED', 'MAINTENANCE')),
    available_count INT NOT NULL DEFAULT 0,
    booked_count INT NOT NULL DEFAULT 0,
    price_override DECIMAL(12, 2),
    price_type VARCHAR(20) CHECK (price_type IN ('WEEKDAY', 'WEEKEND', 'HOLIDAY', 'PEAK_SEASON')),
    base_price_at_date DECIMAL(12, 2),
    min_stay_days INT DEFAULT 1,
    max_stay_days INT DEFAULT 30,
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- 約束
    CONSTRAINT fk_room_calendar_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
    CONSTRAINT uk_room_calendar_date UNIQUE (listing_id, calendar_date)
);

-- 索引
CREATE UNIQUE INDEX idx_room_calendar_listing_date ON room_calendar(listing_id, calendar_date);
CREATE INDEX idx_room_calendar_date ON room_calendar(calendar_date);
CREATE INDEX idx_room_calendar_status ON room_calendar(status) WHERE status != 'AVAILABLE';

COMMENT ON TABLE room_calendar IS '房源日曆可用性與每日價格';
COMMENT ON COLUMN room_calendar.status IS 'AVAILABLE: 可預訂, BOOKED: 已被預訂, BLOCKED: 房東封鎖, MAINTENANCE: 維護中';
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
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number VARCHAR(50) NOT NULL UNIQUE,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED' CHECK (status IN ('CREATED', 'PAID', 'SHIPPING', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'REFUNDING', 'REFUNDED')),
    total_amount DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'TWD',
    discount_amount DECIMAL(12, 2) DEFAULT 0,
    final_amount DECIMAL(12, 2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL DEFAULT 'MOCK',
    payment_id VARCHAR(100),
    payment_status VARCHAR(20) DEFAULT 'PENDING',
    notes TEXT,
    shipping_address JSONB,
    tracking_number VARCHAR(100),
    idempotency_key UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP WITH TIME ZONE,
    shipped_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    cancelled_by UUID,
    cancellation_reason TEXT,
    version INT NOT NULL DEFAULT 1,
    
    -- 約束
    CONSTRAINT fk_orders_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_orders_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_final_amount_positive CHECK (final_amount >= 0)
);

-- 索引
CREATE UNIQUE INDEX idx_orders_order_number ON orders(order_number);
CREATE INDEX idx_orders_tenant_id ON orders(tenant_id);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX idx_orders_idempotency ON orders(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- 租戶隔離索引
CREATE INDEX idx_orders_tenant_status ON orders(tenant_id, status);

COMMENT ON TABLE orders IS '訂單主檔案';
COMMENT ON COLUMN orders.status IS 'CREATED: 已建立, PAID: 已付款, SHIPPING: 已出貨, DELIVERED: 已送達, COMPLETED: 已完成, CANCELLED: 已取消, REFUNDING: 退款中, REFUNDED: 已退款';
COMMENT ON COLUMN orders.payment_method IS 'Phase 1 固定為 MOCK';
```

#### 2.4.2 `order_items` - 訂單明細

```sql
-- ============================================
-- Table: order_items
-- Description: 訂單明細項目
-- Module: M05 訂單履約
-- ============================================
CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    listing_id UUID NOT NULL,
    sku_id UUID,
    title VARCHAR(200) NOT NULL,
    cover_image_url VARCHAR(500),
    listing_type VARCHAR(20) NOT NULL CHECK (listing_type IN ('PRODUCT', 'ROOM')),
    quantity INT NOT NULL DEFAULT 1,
    unit_price DECIMAL(12, 2) NOT NULL,
    subtotal DECIMAL(12, 2) NOT NULL,
    discount_amount DECIMAL(12, 2) DEFAULT 0,
    final_price DECIMAL(12, 2) NOT NULL,
    check_in_date DATE,
    check_out_date DATE,
    guests INT,
    booking_nights INT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- 約束
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE RESTRICT,
    CONSTRAINT chk_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_unit_price_positive CHECK (unit_price >= 0)
);

-- 索引
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_listing_id ON order_items(listing_id);
CREATE INDEX idx_order_items_sku_id ON order_items(sku_id) WHERE sku_id IS NOT NULL;

COMMENT ON TABLE order_items IS '訂單明細項目';
COMMENT ON COLUMN order_items.listing_type IS 'PRODUCT: 商品訂購, ROOM: 民宿預訂';
```

#### 2.4.3 `order_state_log` - 狀態異動日誌

```sql
-- ============================================
-- Table: order_state_log
-- Description: 訂單狀態異動日誌（狀態機審計軌跡）
-- Module: M05 訂單履約
-- ============================================
CREATE TABLE order_state_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    sequence INT NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    reason TEXT,
    operator_id UUID NOT NULL,
    operator_type VARCHAR(20) NOT NULL CHECK (operator_type IN ('BUYER', 'SELLER', 'SYSTEM', 'ADMIN')),
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- 約束
    CONSTRAINT fk_order_state_log_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT uk_order_state_sequence UNIQUE (order_id, sequence)
);

-- 索引
CREATE INDEX idx_order_state_log_order_id ON order_state_log(order_id);
CREATE INDEX idx_order_state_log_created_at ON order_state_log(created_at);

COMMENT ON TABLE order_state_log IS '訂單狀態異動日誌，用於狀態機審計和追蹤';
COMMENT ON COLUMN order_state_log.operator_type IS 'BUYER: 買家操作, SELLER: 賣家操作, SYSTEM: 系統自動, ADMIN: 管理員操作';
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
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    listing_id UUID,
    rule_type VARCHAR(30) NOT NULL CHECK (rule_type IN (
        'WEEKDAY', 'WEEKEND', 'HOLIDAY', 'PEAK_SEASON',
        'EARLY_BIRD', 'LAST_MINUTE', 'LONG_STAY', 'OVERRIDE'
    )),
    rule_name VARCHAR(100) NOT NULL,
    description TEXT,
    adjustment_type VARCHAR(20) NOT NULL CHECK (adjustment_type IN ('MULTIPLIER', 'PERCENTAGE', 'FIXED')),
    adjustment_value DECIMAL(10, 4) NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    conditions JSONB,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    start_date DATE,
    end_date DATE,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 1,
    
    -- 約束
    CONSTRAINT fk_pricing_rules_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_pricing_rules_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_pricing_rules_tenant_id ON pricing_rules(tenant_id);
CREATE INDEX idx_pricing_rules_listing_id ON pricing_rules(listing_id) WHERE listing_id IS NOT NULL;
CREATE INDEX idx_pricing_rules_type ON pricing_rules(rule_type);
CREATE INDEX idx_pricing_rules_active ON pricing_rules(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_pricing_rules_priority ON pricing_rules(priority DESC);

-- 租戶隔離索引
CREATE INDEX idx_pricing_rules_tenant_listing ON pricing_rules(tenant_id, listing_id) WHERE listing_id IS NOT NULL;

COMMENT ON TABLE pricing_rules IS '動態定價規則';
COMMENT ON COLUMN pricing_rules.adjustment_type IS 'MULTIPLIER: 倍率 (1.3=漲30%), PERCENTAGE: 百分比 (-0.10=打9折), FIXED: 固定價格';
COMMENT ON COLUMN pricing_rules.conditions IS 'JSON 格式觸發條件: {"daysOfWeek": ["FRIDAY", "SATURDAY"], "minDaysBeforeCheckIn": 7}';
```

#### 2.5.2 `pricing_overrides` - 手動價格覆蓋

```sql
-- ============================================
-- Table: pricing_overrides
-- Description: 手動價格覆蓋（優先於所有規則）
-- Module: M12 動態定價引擎, API-M12-006
-- ============================================
CREATE TABLE pricing_overrides (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID NOT NULL,
    override_date DATE NOT NULL,
    price DECIMAL(12, 2) NOT NULL,
    reason VARCHAR(200),
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    
    -- 約束
    CONSTRAINT fk_pricing_overrides_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
    CONSTRAINT uk_pricing_override_date UNIQUE (listing_id, override_date),
    CONSTRAINT chk_override_price_positive CHECK (price > 0)
);

-- 索引
CREATE UNIQUE INDEX idx_pricing_overrides_listing_date ON pricing_overrides(listing_id, override_date);
CREATE INDEX idx_pricing_overrides_date ON pricing_overrides(override_date);

COMMENT ON TABLE pricing_overrides IS '手動價格覆蓋，優先於所有動態定價規則';
COMMENT ON COLUMN pricing_overrides.override_date IS '被覆蓋的日期';
```

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

```
┌─────────────────────────────────────────────────────────────────┐
│                      租戶狀態機                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│    PENDING ──────► APPROVED ──────► ACTIVE                     │
│        │              │               │                        │
│        │              │               │                        │
│        ▼              ▼               ▼                        │
│    REJECTED       SUSPENDED        SUSPENDED                  │
│                                                                 │
│  ════════════════════════════════════════════════════════════  │
│  狀態流轉說明：                                                │
│  • PENDING → APPROVED: 管理員審核通過                          │
│  • PENDING → REJECTED: 管理員審核駁回                          │
│  • APPROVED/ACTIVE → SUSPENDED: 管理員停權                    │
│  • SUSPENDED → ACTIVE: 管理員解除停權                         │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. 多租戶隔離實現 (Multi-Tenant Isolation)

### 4.1 Hibernate Filter 配置

```java
// 在 BaseEntity 或每個多租戶 Entity 上配置
@MappedSuperclass
public abstract class TenantAwareEntity {
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    // Getter/Setter
}

// 在 Repository 層啟用 Filter
@Repository
public interface ListingRepository extends JpaRepository<Listing, UUID> {
    
    // 所有查詢自動附加 tenant_id 條件
    List<Listing> findByStatus(ListingStatus status);
    
    // 若需跨租戶查詢（如管理員），使用 @Query 並明確指定
    @Query("SELECT l FROM Listing l WHERE l.id = :id")
    Optional<Listing> findByIdAdmin(@Param("id") UUID id);
}
```

### 4.2 Flyway Migration 命名規範

```
db/migration/
├── V1.0.0__init_schema.sql           # 初始 schema（所有表）
├── V1.0.1__create_tenants.sql         # 租戶相關表
├── V1.0.2__create_users.sql           # 用戶相關表
├── V1.0.3__create_listings.sql        # 商品/房源表
├── V1.0.4__create_orders.sql          # 訂單相關表
├── V1.0.5__create_pricing.sql         # 動態定價表
└── V1.1.0__seed_initial_data.sql     # 初始資料
```

### 4.3 租戶隔離最佳實踐

```sql
-- 範例：带租戶隔離的查詢
-- 所有查詢都應該隐含 tenant_id 條件

-- ✅ 正確：通過 Hibernate Filter 自動添加
SELECT * FROM listings WHERE status = 'ACTIVE';
-- Hibernate 會自動轉換為：
-- SELECT * FROM listings WHERE status = 'ACTIVE' AND tenant_id = :currentTenantId;

-- ❌ 錯誤：跨租戶查詢（除非是管理員）
SELECT * FROM listings WHERE status = 'ACTIVE' AND tenant_id != :currentTenantId;
```

---

## 5. 索引設計總結

### 5.1 核心查詢索引

| 表格 | 索引名 | 欄位 | 類型 | 用途 |
|------|--------|------|------|------|
| `tenants` | idx_tenants_status | status | B-tree | 狀態篩選 |
| `tenants` | idx_tenants_owner_id | owner_id | B-tree | 擁有者查詢 |
| `users` | idx_users_email | email | B-tree | 登入查詢 |
| `listings` | idx_listings_tenant_type_status | (tenant_id, listing_type, status) | B-tree | 店鋪列表 |
| `listings` | idx_listings_tenant_id_status | (tenant_id, status) | B-tree | 租戶隔離 |
| `orders` | idx_orders_tenant_status | (tenant_id, status) | B-tree | 店鋪訂單 |
| `orders` | idx_orders_user_id | user_id | B-tree | 買家訂單 |
| `room_calendar` | idx_room_calendar_listing_date | (listing_id, calendar_date) | B-tree | 日曆查詢 |
| `pricing_rules` | idx_pricing_rules_tenant_listing | (tenant_id, listing_id) | B-tree | 規則查詢 |

### 5.2 唯一約束索引

| 表格 | 約束名 | 欄位 | 說明 |
|------|--------|------|------|
| `tenants` | uk_tenants_store_name | store_name | 店鋪名稱唯一 |
| `tenants` | uk_tenants_contact_email | contact_email | Email 唯一 |
| `users` | uk_users_email | email | Email 唯一 |
| `refresh_tokens` | uk_refresh_tokens_token_hash | token_hash | Token 唯一 |
| `orders` | uk_orders_order_number | order_number | 訂單號唯一 |
| `room_calendar` | uk_room_calendar_date | (listing_id, calendar_date) | 每日唯一 |
| `pricing_overrides` | uk_pricing_override_date | (listing_id, override_date) | 覆蓋日期唯一 |

---

## 6. 約束與觸發器

### 6.1 業務約束 (Check Constraints)

```sql
-- 租戶業務類型
ALTER TABLE tenants ADD CONSTRAINT chk_business_type 
    CHECK (business_type IN ('RETAIL_ONLY', 'BOOKING_ONLY', 'HYBRID'));

-- 訂單狀態
ALTER TABLE orders ADD CONSTRAINT chk_order_status 
    CHECK (status IN ('CREATED', 'PAID', 'SHIPPING', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'REFUNDING', 'REFUNDED'));

-- 房源日曆狀態
ALTER TABLE room_calendar ADD CONSTRAINT chk_calendar_status 
    CHECK (status IN ('AVAILABLE', 'BOOKED', 'BLOCKED', 'MAINTENANCE'));

-- 價格正值約束
ALTER TABLE listings ADD CONSTRAINT chk_base_price_positive CHECK (base_price > 0);
ALTER TABLE orders ADD CONSTRAINT chk_final_amount_positive CHECK (final_amount >= 0);
```

### 6.2 時間戳自動更新觸發器

```sql
-- 自動更新 updated_at 的函數
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 套用於所有主要表格
CREATE TRIGGER update_tenants_updated_at
    BEFORE UPDATE ON tenants
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_listings_updated_at
    BEFORE UPDATE ON listings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

---

## 7. 修訂歷史

| 版本 | 日期 | 作者 | 變更說明 |
|------|------|------|----------|
| v1.0 | 2026-04-09 | Marcus (SD-Architect) | 初始版本，包含 Phase 1 所有表格定義 |

---

**文檔版本**: AISDLC v0.09
**模板維護**: AISDLC Framework Team
**最後更新**: 2026-04-09
