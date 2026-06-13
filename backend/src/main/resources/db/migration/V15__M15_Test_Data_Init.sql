-- V15__M15_Test_Data_Init.sql
-- M15 CMS 測試資料初始化
-- Created: 2026-05-03

-- =============================================
-- 1. 建立測試 Tenant（T1-M15）
-- =============================================
INSERT INTO tenants (id, name, slug, status, description, contact_email, contact_phone, metadata)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'M15 測試店鋪',
    'm15-test-store',
    'ACTIVE',
    'M15 CMS 測試用店鋪',
    'm15-test@nextkey.com',
    '0912345678',
    '{"type": "RETAIL"}'
)
ON CONFLICT ON CONSTRAINT tenants_pkey DO NOTHING;

-- =============================================
-- 2. 建立測試 Tenant（T2-M15）
-- =============================================
INSERT INTO tenants (id, name, slug, status, description, contact_email, contact_phone, metadata)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    'M15 測試店鋪 2',
    'm15-test-store-2',
    'ACTIVE',
    'M15 CMS 第二測試店鋪',
    'm15-test-2@nextkey.com',
    '0923456789',
    '{"type": "RETAIL"}'
)
ON CONFLICT ON CONSTRAINT tenants_pkey DO NOTHING;

-- =============================================
-- 3. 建立測試 User（U1-M15-StoreOwner）
-- =============================================
INSERT INTO users (id, email, password_hash, full_name, role, status, tenant_id, created_at, updated_at)
VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'm15-owner@test.com',
    '$2a$10$dummy_hash_for_testing',
    'M15 測試店主',
    'STORE_OWNER',
    'ACTIVE',
    '11111111-1111-1111-1111-111111111111',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (email) DO NOTHING;

-- =============================================
-- 4. 建立測試 Post Category
-- =============================================
INSERT INTO post_categories (id, tenant_id, name, slug, description, sort_order, is_active)
VALUES (
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    '11111111-1111-1111-1111-111111111111',
    '促銷活動',
    'promotion',
    '促銷相關文章',
    1,
    true
)
ON CONFLICT DO NOTHING;

-- =============================================
-- 5. 建立測試 Listing（商品）
-- =============================================
INSERT INTO listings (id, tenant_id, owner_id, listing_type, title, description, base_price, cover_image_url, status, metadata, tags, created_at, updated_at)
VALUES (
    'dddddddd-dddd-dddd-dddd-dddddddddddd',
    '11111111-1111-1111-1111-111111111111',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'PRODUCT',
    'M15 測試商品',
    '這是一個用於 M15 嵌入卡片測試的商品',
    999.00,
    'https://example.com/product.jpg',
    'ACTIVE',
    '{"sku": "TEST-001"}',
    '["測試", "M15"]',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT DO NOTHING;

-- =============================================
-- 6. 建立測試 Listing（房型）
-- =============================================
INSERT INTO listings (id, tenant_id, owner_id, listing_type, title, description, base_price, cover_image_url, status, metadata, tags, created_at, updated_at)
VALUES (
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
    '11111111-1111-1111-1111-111111111111',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'ROOM',
    'M15 測試房型',
    '這是一個用於 M15 嵌入卡片測試的房型',
    2500.00,
    'https://example.com/room.jpg',
    'ACTIVE',
    '{"maxGuests": 4}',
    '["測試", "M15"]',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT DO NOTHING;

-- =============================================
-- 7. 建立 INACTIVE 測試 Listing（已下架）
-- =============================================
INSERT INTO listings (id, tenant_id, owner_id, listing_type, title, description, base_price, cover_image_url, status, metadata, tags, created_at, updated_at)
VALUES (
    'ffffffff-ffff-ffff-ffff-ffffffffffff',
    '11111111-1111-1111-1111-111111111111',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'PRODUCT',
    'M15 已下架商品',
    '這是一個已下架的商品，用於測試「已下架」狀態顯示',
    599.00,
    'https://example.com/inactive.jpg',
    'INACTIVE',
    '{"sku": "TEST-INACTIVE"}',
    '["測試", "已下架"]',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT DO NOTHING;

-- =============================================
-- 8. 初始化 CMS_ENABLED Feature Toggle
-- =============================================
INSERT INTO tenant_feature_toggles (id, tenant_id, feature_key, is_enabled, config, created_at)
VALUES (
    gen_random_uuid(),
    '11111111-1111-1111-1111-111111111111',
    'CMS_ENABLED',
    true,
    '{}',
    CURRENT_TIMESTAMP
)
ON CONFLICT DO NOTHING;

-- =============================================
-- 9. 建立測試 Post（含嵌入）
-- =============================================
INSERT INTO posts (id, tenant_id, author_id, title, slug, content, status, category_id, tags, view_count, published_at, created_at, updated_at)
VALUES (
    '99999999-9999-9999-9999-999999999999',
    '11111111-1111-1111-1111-111111111111',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'M15 測試貼文',
    'm15-test-post',
    '# M15 測試貼文\n\n這是一篇測試文章。\n\n{{embed:listing:dddddddd-dddd-dddd-dddd-dddddddddddd}}\n\n測試嵌入商品卡片。',
    'PUBLISHED',
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    '["測試", "M15"]',
    42,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (slug) DO NOTHING;

-- =============================================
-- 10. 建立 Post Embed 關聯
-- =============================================
INSERT INTO post_embeds (id, post_id, listing_id, embed_order, created_at)
VALUES (
    gen_random_uuid(),
    '99999999-9999-9999-9999-999999999999',
    'dddddddd-dddd-dddd-dddd-dddddddddddd',
    0,
    CURRENT_TIMESTAMP
)
ON CONFLICT DO NOTHING;

-- 驗證查詢
-- SELECT * FROM posts WHERE slug = 'm15-test-post';
-- SELECT * FROM post_embeds WHERE post_id = '99999999-9999-9999-9999-999999999999';
-- SELECT * FROM listings WHERE id IN ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee');