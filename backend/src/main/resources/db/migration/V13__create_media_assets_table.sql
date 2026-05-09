-- V13__create_media_assets_table.sql
-- M15 CMS: 媒體資源資料表
-- Created: 2026-05-03
-- 用途: 建立 media_assets 表，支援媒體庫功能
-- 前置需求: V1 (tenants 表)

-- =============================================
-- 1. Media Assets 表結構
-- =============================================
CREATE TABLE media_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    uploader_id UUID NOT NULL REFERENCES users(id),
    file_name VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    width INTEGER,
    height INTEGER,
    duration_seconds INTEGER,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 建立索引
CREATE INDEX idx_media_assets_tenant_id ON media_assets(tenant_id);
CREATE INDEX idx_media_assets_uploader_id ON media_assets(uploader_id);
CREATE INDEX idx_media_assets_file_type ON media_assets(file_type);
CREATE INDEX idx_media_assets_created_at ON media_assets(created_at DESC);

COMMENT ON TABLE media_assets IS 'Media library for tenant assets (images, videos, documents)';
COMMENT ON COLUMN media_assets.file_type IS 'Asset type: IMAGE, VIDEO, DOCUMENT';
COMMENT ON COLUMN media_assets.file_path IS 'S3/MinIO storage path: /{tenant_id}/media/{uuid}/{filename}';