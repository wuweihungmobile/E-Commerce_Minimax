-- V33__Add_Publish_Schedule_To_Knowledge_Articles.sql
-- 知識庫文章發布排程 - 新增排程發布欄位

-- 新增排程發布時間欄位
ALTER TABLE knowledge_articles ADD COLUMN scheduled_publish_at TIMESTAMP;
COMMENT ON COLUMN knowledge_articles.scheduled_publish_at IS '排程發布時間 - 設定此時間後自動發布';

-- published_at 欄位已在 V24 建表時新增，此處僅更新 comment
COMMENT ON COLUMN knowledge_articles.published_at IS '實際發布時間';

-- 為排程查詢建立索引
CREATE INDEX idx_knowledge_articles_scheduled_publish ON knowledge_articles(scheduled_publish_at)
    WHERE scheduled_publish_at IS NOT NULL;

-- 升級 published_at 索引為偏向已發布文章的 partial index（V24 建的是全欄索引）
DROP INDEX IF EXISTS idx_knowledge_articles_published_at;
CREATE INDEX idx_knowledge_articles_published_at ON knowledge_articles(published_at DESC)
    WHERE published_at IS NOT NULL;