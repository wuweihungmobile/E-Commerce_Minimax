-- M18: 媒體資產表
-- Media Assets (媒體資產表)

CREATE TABLE media_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    category_id UUID REFERENCES media_categories(id) ON DELETE SET NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    tags TEXT[] DEFAULT '{}',
    usage_count INTEGER DEFAULT 0,
    alt_text VARCHAR(255),
    title VARCHAR(255),
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for tenant queries
CREATE INDEX idx_media_assets_tenant_id ON media_assets(tenant_id);
-- Index for category_id
CREATE INDEX idx_media_assets_category_id ON media_assets(category_id);
-- Index for mime_type (filter by type)
CREATE INDEX idx_media_assets_mime_type ON media_assets(mime_type);
-- Index for non-deleted assets
CREATE INDEX idx_media_assets_not_deleted ON media_assets(id) WHERE is_deleted = FALSE;

COMMENT ON TABLE media_assets IS 'M18 媒體資產表 - 儲存所有上傳的媒體檔案';
COMMENT ON COLUMN media_assets.tags IS '標籤陣列，用於搜尋和分類';
COMMENT ON COLUMN media_assets.usage_count IS '被引用次數，追蹤該檔案在哪裡被使用';
COMMENT ON COLUMN media_assets.is_deleted IS '軟刪除標記';