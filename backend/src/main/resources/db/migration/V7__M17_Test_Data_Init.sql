-- V7__M17_Test_Data_Init.sql
-- NextKey E-Commerce Platform - Sprint 7 M17 E2E 測試資料初始化
-- Created: 2026-05-02
-- 用途: 建立 M17 開店申請、審核流程所需的完整測試資料
-- 需要支援:
--   AT-M17-001: 開店申請流程
--   AT-M17-002: Admin 審核開店申請
--   AT-M17-003: Feature Toggle 更新
--   AT-M17-004: 店鋪 Profile 更新

-- =============================================
-- 1. E2E 測試用 ADMIN 用戶 (admin@nextkey.local / Test123!)
-- 密碼 hash 與 Test123! 匹配（由 Spring BCryptPasswordEncoder 產生）
-- =============================================
INSERT INTO users (id, email, password_hash, full_name, role, status, email_verified)
VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'admin@nextkey.local',
    '$2a$10$juUhq3y1wExU.I9ialD3Yuk6CrIsP37Z5//q/b1vR2jGIUlYgvkcq',
    'E2E Test Admin',
    'SUPER_ADMIN',
    'ACTIVE',
    TRUE
)
ON CONFLICT (email) DO UPDATE SET
    password_hash = EXCLUDED.password_hash,
    full_name = EXCLUDED.full_name,
    role = EXCLUDED.role;

-- =============================================
-- 2. PENDING_REVIEW 狀態的測試租戶 (for AT-M17-002 審核測試)
-- =============================================
INSERT INTO tenants (id, name, slug, status, description, contact_email, contact_phone, metadata)
VALUES (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    'pending-review-tenant',
    'pending-test',
    'PENDING_REVIEW',
    'Test Tenant for M17 Approval Flow - Pending Review',
    'pending-owner@e2e-test.com',
    '0911111111',
    '{"type": "TEST", "sprint": "SPRINT_07", "businessType": "RETAIL"}'
)
ON CONFLICT (slug) DO UPDATE SET
    status = EXCLUDED.status,
    description = EXCLUDED.description;

-- =============================================
-- 3. 已通過審核的 ACTIVE 租戶 (for AT-M17-003/004 測試)
-- =============================================
INSERT INTO tenants (id, name, slug, status, description, contact_email, contact_phone, metadata)
VALUES (
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    'active-test-tenant',
    'active-test',
    'ACTIVE',
    'Active Test Tenant for Feature Toggle Testing',
    'active-owner@e2e-test.com',
    '0922222222',
    '{"type": "TEST", "sprint": "SPRINT_07"}'
)
ON CONFLICT (slug) DO UPDATE SET
    status = EXCLUDED.status,
    description = EXCLUDED.description;

-- =============================================
-- 4. PENDING_REVIEW 租戶的擁有者 (STORE_OWNER)
-- 這個用戶將申請開店，等待審核
-- =============================================
INSERT INTO users (id, email, password_hash, full_name, role, status, email_verified)
VALUES (
    'dddddddd-dddd-dddd-dddd-dddddddddddd',
    'pending-owner@e2e-test.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3.T8B0DwMiDPQ9QqOCHq',
    'Pending Store Owner',
    'STORE_OWNER',
    'ACTIVE',
    TRUE
)
ON CONFLICT (email) DO NOTHING;

INSERT INTO tenant_members (tenant_id, user_id, store_role)
VALUES (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    'dddddddd-dddd-dddd-dddd-dddddddddddd',
    'STORE_OWNER'
)
ON CONFLICT (tenant_id, user_id) DO NOTHING;

-- =============================================
-- 5. ACTIVE 租戶的擁有者 (STORE_OWNER) - for AT-M17-003/004
-- =============================================
INSERT INTO users (id, email, password_hash, full_name, role, status, email_verified)
VALUES (
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
    'active-owner@e2e-test.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3.T8B0DwMiDPQ9QqOCHq',
    'Active Store Owner',
    'STORE_OWNER',
    'ACTIVE',
    TRUE
)
ON CONFLICT (email) DO NOTHING;

INSERT INTO tenant_members (tenant_id, user_id, store_role)
VALUES (
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
    'STORE_OWNER'
)
ON CONFLICT (tenant_id, user_id) DO NOTHING;

-- =============================================
-- 6. 一般 BUYER 用戶 (for AT-M17-001 申請開店測試)
-- 密碼同樣是 admin123
-- =============================================
INSERT INTO users (id, email, password_hash, full_name, role, status, email_verified)
VALUES (
    'ffffffff-ffff-ffff-ffff-ffffffffffff',
    'buyer-test@e2e-test.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3.T8B0DwMiDPQ9QqOCHq',
    'E2E Buyer User',
    'BUYER',
    'ACTIVE',
    TRUE
)
ON CONFLICT (email) DO NOTHING;

-- =============================================
-- 7. ACTIVE 租戶的 Feature Toggles (for AT-M17-003)
-- =============================================
INSERT INTO tenant_feature_toggles (tenant_id, feature_key, is_enabled, config)
VALUES
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'RETAIL_ENABLED', TRUE, '{"default": true}'),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'BOOKING_ENABLED', FALSE, '{}'),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'CMS_ENABLED', TRUE, '{}'),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'DYNAMIC_PRICING_ENABLED', TRUE, '{}'),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'MAX_PRODUCTS', TRUE, '{"value": 100}'),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'MAX_ROOMS', TRUE, '{"value": 20}'),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'MAX_POSTS', TRUE, '{"value": 50}'),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'COMMISSION_RATE', TRUE, '{"value": 0.05}'),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'PROMO_ENABLED', FALSE, '{}')
ON CONFLICT (tenant_id, feature_key) DO UPDATE SET
    is_enabled = EXCLUDED.is_enabled,
    config = EXCLUDED.config;

-- =============================================
-- 8. ACTIVE 租戶的測試 Listing
-- =============================================
INSERT INTO listings (id, tenant_id, listing_type, title, description, cover_image_url, status, owner_id, base_price, currency, tags, metadata)
VALUES (
    '99999999-9999-9999-9999-999999999999',
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    'ROOM',
    'E2E Test Room - Standard Suite',
    'A comfortable test room for E2E validation',
    'https://example.com/e2e-test-room.jpg',
    'ACTIVE',
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
    2500.00,
    'TWD',
    '["e2e", "test", "room"]',
    '{"booking_enabled": true, "test_data": true}'
)
ON CONFLICT DO NOTHING;

INSERT INTO rooms (listing_id, location, latitude, longitude, max_guests, amenities, check_in_time, check_out_time, room_count)
VALUES (
    '99999999-9999-9999-9999-999999999999',
    'E2E Test Location, Taipei',
    25.0330,
    121.5654,
    4,
    '{"wifi", "tv", "ac", "breakfast"}',
    '15:00',
    '11:00',
    2
)
ON CONFLICT DO NOTHING;

-- =============================================
-- 驗證查詢 (可用於除錯)
-- =============================================
-- SELECT 'ADMIN Users:' as info;
-- SELECT id, email, full_name, role FROM users WHERE role = 'SUPER_ADMIN';
--
-- SELECT 'PENDING_REVIEW Tenants:' as info;
-- SELECT id, name, status FROM tenants WHERE status = 'PENDING_REVIEW';
--
-- SELECT 'ACTIVE Tenants:' as info;
-- SELECT id, name, status FROM tenants WHERE status = 'ACTIVE';
--
-- SELECT 'Feature Toggles for ACTIVE tenant:' as info;
-- SELECT feature_key, is_enabled, config FROM tenant_feature_toggles WHERE tenant_id = 'cccccccc-cccc-cccc-cccc-cccccccccccc';
