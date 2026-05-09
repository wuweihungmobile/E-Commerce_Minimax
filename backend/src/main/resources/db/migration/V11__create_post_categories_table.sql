-- V11__create_post_categories_table.sql
-- M15 CMS: 貼文分類資料表
-- Created: 2026-05-03
-- 用途: 建立 post_categories 表，支援貼文分類管理
-- 前置需求: V1 (tenants 表)

-- =============================================
-- 1. Post Categories 表結構
-- =============================================
CREATE TABLE post_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(110) NOT NULL,
    description VARCHAR(500),
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT post_categories_tenant_slug_unique UNIQUE (tenant_id, slug)
);

-- 建立索引
CREATE INDEX idx_post_categories_tenant_id ON post_categories(tenant_id);
CREATE INDEX idx_post_categories_slug ON post_categories(slug);
CREATE INDEX idx_post_categories_sort_order ON post_categories(sort_order);

COMMENT ON TABLE post_categories IS 'Post categories for CMS';
COMMENT ON COLUMN post_categories.slug IS 'URL-friendly identifier, unique within tenant';