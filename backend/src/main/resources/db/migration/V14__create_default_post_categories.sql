-- V14__create_default_post_categories.sql
-- M15 CMS: 建立預設分類
-- Created: 2026-05-03
-- 用途: 為每個已存在的 Tenant 建立預設分類
-- 前置需求: V11 (post_categories 表), V9 (System Tenant)

-- =============================================
-- 為所有已存在的 Tenant 建立預設分類
-- =============================================
INSERT INTO post_categories (id, tenant_id, name, slug, description, sort_order)
SELECT
    gen_random_uuid(),
    t.id,
    'Uncategorized',
    'uncategorized-' || t.id::text,
    'Default post category',
    0
FROM tenants t
WHERE t.slug != 'system'
ON CONFLICT DO NOTHING;

-- 驗證建立成功
-- SELECT tenant_id, name, slug FROM post_categories WHERE slug LIKE 'uncategorized%';