-- V26__Create_Faq_Categories_Table.sql
-- FAQ 分類表

CREATE TABLE faq_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    icon VARCHAR(50),
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_faq_categories_slug ON faq_categories(slug);
CREATE INDEX idx_faq_categories_sort_order ON faq_categories(sort_order);

COMMENT ON TABLE faq_categories IS 'FAQ 分類表';
COMMENT ON COLUMN faq_categories.name IS '分類名稱';
COMMENT ON COLUMN faq_categories.slug IS 'URL 友好的標識符';