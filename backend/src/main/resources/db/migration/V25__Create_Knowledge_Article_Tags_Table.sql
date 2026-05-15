-- V25: Create Knowledge Article Tags Table
-- M18 知識庫文章標籤關聯表

CREATE TABLE knowledge_article_tags (
    article_id UUID NOT NULL,
    tag VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_knowledge_article_tags_article FOREIGN KEY (article_id) REFERENCES knowledge_articles(id) ON DELETE CASCADE,
    CONSTRAINT pk_knowledge_article_tags PRIMARY KEY (article_id, tag)
);

CREATE INDEX idx_knowledge_article_tags_tag ON knowledge_article_tags(tag);

COMMENT ON TABLE knowledge_article_tags IS 'M18 知識庫文章標籤關聯表';
COMMENT ON COLUMN knowledge_article_tags.article_id IS '文章 ID';
COMMENT ON COLUMN knowledge_article_tags.tag IS '標籤';