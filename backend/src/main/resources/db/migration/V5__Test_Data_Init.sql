-- V5__Test_Data_Init.sql
-- NextKey E-Commerce Platform - Sprint 6 QA 測試資料初始化
-- Created: 2026-04-30
-- 用途: 建立測試 Tenant、User、Listing 供 QA 驗證使用

-- =============================================
-- 測試 Tenant (nextkey-test-tenant)
-- =============================================
INSERT INTO tenants (id, name, slug, status, description, metadata)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'nextkey-test-tenant',
    'nextkey-test',
    'ACTIVE',
    'Test Tenant for Sprint 6 QA Validation',
    '{"type": "TEST", "sprint": "SPRINT_06"}'
);

-- =============================================
-- 測試 User (test-store-owner@nextkeytest.com)
-- 密碼: Test123! (BCrypt hash)
-- =============================================
INSERT INTO users (id, email, password_hash, full_name, role, tenant_id, status, email_verified)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    'test-store-owner@nextkeytest.com',
    '$2a$10$rV6LRKkFlBOxpLpLzC9Yx.8vEJ6Q9vKz5x5x5x5x5x5x5x5x5x5x5',
    'Test Store Owner',
    'STORE_OWNER',
    '11111111-1111-1111-1111-111111111111',
    'ACTIVE',
    TRUE
);

-- =============================================
-- TenantMember (STORE_OWNER role)
-- =============================================
INSERT INTO tenant_members (tenant_id, user_id, store_role)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'STORE_OWNER'
);

-- =============================================
-- 測試 Listing (Room Type, 啟用 BOOKING_ENABLED)
-- =============================================
INSERT INTO listings (id, tenant_id, listing_type, title, description, cover_image_url, status, owner_id, base_price, currency, tags, metadata)
VALUES (
    '33333333-3333-3333-3333-333333333333',
    '11111111-1111-1111-1111-111111111111',
    'ROOM',
    'Test Room Listing - Deluxe Suite',
    'A comfortable test room for QA validation',
    'https://example.com/test-room.jpg',
    'ACTIVE',
    '22222222-2222-2222-2222-222222222222',
    1500.00,
    'TWD',
    '["test", "qa", "room"]',
    '{"booking_enabled": true, "test_data": true}'
);

-- =============================================
-- Room Details (for Room listing type)
-- =============================================
INSERT INTO rooms (listing_id, location, latitude, longitude, max_guests, amenities, check_in_time, check_out_time, room_count)
VALUES (
    '33333333-3333-3333-3333-333333333333',
    'Test Location, Taipei',
    25.0330,
    121.5654,
    4,
    '{"wifi": true, "tv": true, "ac": true, "breakfast": true}',
    '15:00',
    '11:00',
    1
);

-- =============================================
-- 測試 Feature Toggles (針對測試 Tenant)
-- =============================================
INSERT INTO tenant_feature_toggles (tenant_id, feature_key, is_enabled, config)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'RETAIL_ENABLED', FALSE, '{}'),
    ('11111111-1111-1111-1111-111111111111', 'BOOKING_ENABLED', TRUE, '{"default": true}'),
    ('11111111-1111-1111-1111-111111111111', 'CMS_ENABLED', FALSE, '{}'),
    ('11111111-1111-1111-1111-111111111111', 'DYNAMIC_PRICING_ENABLED', TRUE, '{}');

-- =============================================
-- 範例 Pricing Rules (動態定價)
-- =============================================
INSERT INTO pricing_rules (id, tenant_id, room_listing_id, rule_type, rule_name, priority, config, valid_from, valid_to, is_active)
VALUES
    (
        '44444444-4444-4444-4444-444444444444',
        '11111111-1111-1111-1111-111111111111',
        '33333333-3333-3333-3333-333333333333',
        'WEEKEND_SURCHARGE',
        '週末加價規則',
        10,
        '{"surcharge_percent": 20, "days": ["Saturday", "Sunday"]}',
        '2026-01-01',
        '2026-12-31',
        TRUE
    ),
    (
        '55555555-5555-5555-5555-555555555555',
        '11111111-1111-1111-1111-111111111111',
        '33333333-3333-3333-3333-333333333333',
        'EARLY_BIRD',
        '早鳥優惠',
        5,
        '{"discount_percent": 15, "advance_days": 7}',
        '2026-01-01',
        '2026-12-31',
        TRUE
    );