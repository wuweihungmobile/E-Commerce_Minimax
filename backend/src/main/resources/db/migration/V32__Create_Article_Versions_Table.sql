-- V32__Create_Article_Versions_Table.sql
-- 知識庫文章版本控制 - 建立文章版本歷史表

-- 文章版本歷史表
CREATE TABLE article_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    article_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    tags TEXT[],
    category_id UUID,
    is_published BOOLEAN DEFAULT false,
    is_pinned BOOLEAN DEFAULT false,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001',
    CONSTRAINT fk_article_versions_article FOREIGN KEY (article_id)
        REFERENCES knowledge_articles(id) ON DELETE CASCADE,
    CONSTRAINT fk_article_versions_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_article_versions_category FOREIGN KEY (category_id)
        REFERENCES knowledge_categories(id) ON DELETE SET NULL,
    CONSTRAINT fk_article_versions_user FOREIGN KEY (created_by)
        REFERENCES users(id) ON DELETE SET NULL
);

-- 索引
CREATE INDEX idx_article_versions_article_id ON article_versions(article_id);
CREATE INDEX idx_article_versions_tenant_id ON article_versions(tenant_id);
CREATE INDEX idx_article_versions_created_at ON article_versions(created_at DESC);

-- 註解
COMMENT ON TABLE article_versions IS '知識庫文章版本歷史 - 用於版本控制和恢復';
COMMENT ON COLUMN article_versions.article_id IS '文章 ID';
COMMENT ON COLUMN article_versions.version_number IS '版本號';
COMMENT ON COLUMN article_versions.title IS '版本標題快照';
COMMENT ON COLUMN article_versions.content IS '版本內容快照';