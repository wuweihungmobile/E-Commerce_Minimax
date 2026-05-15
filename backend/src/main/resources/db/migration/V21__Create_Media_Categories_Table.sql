-- M18: 媒體中心資料表
-- Media Categories (分類表)

CREATE TABLE media_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    parent_id UUID REFERENCES media_categories(id) ON DELETE SET NULL,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for tenant queries
CREATE INDEX idx_media_categories_tenant_id ON media_categories(tenant_id);
-- Index for parent_id (hierarchical queries)
CREATE INDEX idx_media_categories_parent_id ON media_categories(parent_id);

COMMENT ON TABLE media_categories IS 'M18 媒體分類表 - 支援階層式分類';
COMMENT ON COLUMN media_categories.parent_id IS '上層分類 ID，NULL 表示根分類';