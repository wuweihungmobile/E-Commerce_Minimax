-- V23: Create Knowledge Categories Table
-- M18 知識庫分類表

CREATE TABLE knowledge_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    icon VARCHAR(50),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_knowledge_categories_slug UNIQUE (slug)
);

CREATE INDEX idx_knowledge_categories_slug ON knowledge_categories(slug);
CREATE INDEX idx_knowledge_categories_sort_order ON knowledge_categories(sort_order);

COMMENT ON TABLE knowledge_categories IS 'M18 知識庫分類表';
COMMENT ON COLUMN knowledge_categories.id IS '分類 ID';
COMMENT ON COLUMN knowledge_categories.slug IS 'URL slug';
COMMENT ON COLUMN knowledge_categories.name IS '分類名稱';
COMMENT ON COLUMN knowledge_categories.description IS '分類描述';
COMMENT ON COLUMN knowledge_categories.icon IS '圖示名稱';
COMMENT ON COLUMN knowledge_categories.sort_order IS '排序順序';