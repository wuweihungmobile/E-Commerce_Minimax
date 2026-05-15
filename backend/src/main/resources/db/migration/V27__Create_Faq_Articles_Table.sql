-- V27__Create_Faq_Articles_Table.sql
-- FAQ 文章表

CREATE TABLE faq_articles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES faq_categories(id) ON DELETE CASCADE,
    question VARCHAR(500) NOT NULL,
    answer TEXT NOT NULL,
    slug VARCHAR(100) NOT NULL,
    sort_order INTEGER DEFAULT 0,
    view_count INTEGER DEFAULT 0,
    is_pinned BOOLEAN DEFAULT FALSE,
    is_published BOOLEAN DEFAULT TRUE,
    published_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(slug)
);

CREATE INDEX idx_faq_articles_category_id ON faq_articles(category_id);
CREATE INDEX idx_faq_articles_slug ON faq_articles(slug);
CREATE INDEX idx_faq_articles_sort_order ON faq_articles(sort_order);
CREATE INDEX idx_faq_articles_is_published ON faq_articles(is_published);
CREATE INDEX idx_faq_articles_view_count ON faq_articles(view_count DESC);

COMMENT ON TABLE faq_articles IS 'FAQ 文章表';
COMMENT ON COLUMN faq_articles.question IS '問題';
COMMENT ON COLUMN faq_articles.answer IS '回答';