-- V10__create_posts_table.sql
-- M15 CMS: 貼文資料表
-- Created: 2026-05-03
-- 用途: 建立 posts 表，支援 CMS 貼文功能
-- 前置需求: V1 (tenants 表), V9 (System Tenant)

-- =============================================
-- 1. Posts 表結構
-- =============================================
CREATE TABLE posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    author_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(355) NOT NULL UNIQUE,
    content TEXT,
    excerpt VARCHAR(500),
    featured_image_url VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    category_id UUID,
    tags JSONB DEFAULT '[]',
    view_count INTEGER DEFAULT 0,
    published_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT posts_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

-- 建立索引
CREATE INDEX idx_posts_tenant_id ON posts(tenant_id);
CREATE INDEX idx_posts_author_id ON posts(author_id);
CREATE INDEX idx_posts_status ON posts(status);
CREATE INDEX idx_posts_slug ON posts(slug);
CREATE INDEX idx_posts_category_id ON posts(category_id);
CREATE INDEX idx_posts_published_at ON posts(published_at DESC);

COMMENT ON TABLE posts IS 'CMS Posts table for store blog/articles';
COMMENT ON COLUMN posts.status IS 'Post status: DRAFT, PUBLISHED, ARCHIVED';
COMMENT ON COLUMN posts.slug IS 'URL-friendly identifier, auto-generated from title';