-- V16__M15_API_Test_Tenant_Init.sql
-- M15 CMS API 自動化測試所需的測試資料
-- Created: 2026-05-05
-- Purpose: 支援 M15_API_Automation_Test.sh 中的 TC-M15-001~004 等測試案例

-- =============================================
-- 1. 建立測試 Tenant（M15 API 測試專用）
-- ID: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11
-- 這是 M15_API_Automation_Test.sh 中使用的 Tenant ID
-- =============================================
INSERT INTO tenants (id, name, slug, status, description, contact_email, contact_phone, metadata)
VALUES (
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'M15 API 自動化測試店鋪',
    'm15-api-test-store',
    'ACTIVE',
    'M15 CMS API 自動化測試專用店鋪',
    'm15-api-test@nextkey.com',
    '0912345678',
    '{"type": "RETAIL"}'
)
ON CONFLICT (slug) DO NOTHING;

-- =============================================
-- 2. 建立測試 User（Store Owner for M15 API Test）
-- =============================================
INSERT INTO users (id, email, password_hash, full_name, role, status, tenant_id, created_at, updated_at)
VALUES (
    'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'm15-api-owner@test.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMye1J4G4CzY6QoL0HhT4rDz6LQH7uFKXzi',  -- 預設密碼: test123
    'M15 API 測試店主',
    'STORE_OWNER',
    'ACTIVE',
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (email) DO NOTHING;

-- =============================================
-- 3. 建立測試 Tenant Member 關聯
-- =============================================
INSERT INTO tenant_members (id, tenant_id, user_id, store_role, joined_at)
VALUES (
    'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'STORE_OWNER',
    CURRENT_TIMESTAMP
)
ON CONFLICT (tenant_id, user_id) DO NOTHING;

-- =============================================
-- 4. 初始化 CMS_ENABLED Feature Toggle
-- =============================================
INSERT INTO tenant_feature_toggles (id, tenant_id, feature_key, is_enabled, enabled_at, created_at)
VALUES (
    gen_random_uuid(),
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'CMS_ENABLED',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

-- =============================================
-- 5. 建立測試 Post Category
-- =============================================
INSERT INTO post_categories (id, tenant_id, name, slug, description, sort_order, is_active)
VALUES (
    'd0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    '測試分類',
    'test-category',
    'M15 API 自動化測試用分類',
    1,
    true
)
ON CONFLICT DO NOTHING;

-- =============================================
-- 6. 建立測試 Listing（商品）
-- =============================================
INSERT INTO listings (id, tenant_id, owner_id, listing_type, title, description, base_price, cover_image_url, status, metadata, tags, created_at, updated_at)
VALUES (
    'e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'PRODUCT',
    'M15 API 測試商品',
    '這是一個用於 M15 API 自動化測試的商品',
    999.00,
    'https://example.com/test-product.jpg',
    'ACTIVE',
    '{"sku": "M15-TEST-001"}',
    '["測試", "M15"]',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT DO NOTHING;

-- =============================================
-- 7. 建立測試 Listing（房型）
-- =============================================
INSERT INTO listings (id, tenant_id, owner_id, listing_type, title, description, base_price, cover_image_url, status, metadata, tags, created_at, updated_at)
VALUES (
    'f0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'ROOM',
    'M15 API 測試房型',
    '這是一個用於 M15 API 自動化測試的房型',
    2500.00,
    'https://example.com/test-room.jpg',
    'ACTIVE',
    '{"maxGuests": 4}',
    '["測試", "M15"]',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT DO NOTHING;

-- =============================================
-- 驗證查詢（可用於除錯）
-- =============================================
-- SELECT * FROM tenants WHERE id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11';
-- SELECT * FROM users WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11';
-- SELECT * FROM tenant_feature_toggles WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11';
-- SELECT * FROM listings WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11';