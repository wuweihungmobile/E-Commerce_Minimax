-- V24: Create Knowledge Articles Table
-- M18 知識庫文章表

CREATE TABLE knowledge_articles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    category_id UUID NOT NULL,
    author_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    slug VARCHAR(200) NOT NULL,
    content TEXT,
    excerpt TEXT,
    cover_image_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    view_count INTEGER NOT NULL DEFAULT 0,
    is_pinned BOOLEAN NOT NULL DEFAULT false,
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_knowledge_articles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_knowledge_articles_category FOREIGN KEY (category_id) REFERENCES knowledge_categories(id) ON DELETE RESTRICT,
    CONSTRAINT fk_knowledge_articles_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_knowledge_articles_slug UNIQUE (tenant_id, slug),
    CONSTRAINT chk_knowledge_articles_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX idx_knowledge_articles_tenant_id ON knowledge_articles(tenant_id);
CREATE INDEX idx_knowledge_articles_category_id ON knowledge_articles(category_id);
CREATE INDEX idx_knowledge_articles_author_id ON knowledge_articles(author_id);
CREATE INDEX idx_knowledge_articles_slug ON knowledge_articles(slug);
CREATE INDEX idx_knowledge_articles_status ON knowledge_articles(status);
CREATE INDEX idx_knowledge_articles_published_at ON knowledge_articles(published_at);

COMMENT ON TABLE knowledge_articles IS 'M18 知識庫文章表';
COMMENT ON COLUMN knowledge_articles.id IS '文章 ID';
COMMENT ON COLUMN knowledge_articles.tenant_id IS '租戶 ID';
COMMENT ON COLUMN knowledge_articles.category_id IS '分類 ID';
COMMENT ON COLUMN knowledge_articles.author_id IS '作者 ID';
COMMENT ON COLUMN knowledge_articles.title IS '文章標題';
COMMENT ON COLUMN knowledge_articles.slug IS 'URL slug';
COMMENT ON COLUMN knowledge_articles.content IS 'Markdown 內容';
COMMENT ON COLUMN knowledge_articles.excerpt IS '文章摘要';
COMMENT ON COLUMN knowledge_articles.cover_image_url IS '封面圖 URL';
COMMENT ON COLUMN knowledge_articles.status IS '狀態: DRAFT/PUBLISHED/ARCHIVED';
COMMENT ON COLUMN knowledge_articles.view_count IS '瀏覽次數';
COMMENT ON COLUMN knowledge_articles.is_pinned IS '是否置頂';
COMMENT ON COLUMN knowledge_articles.published_at IS '發布時間';