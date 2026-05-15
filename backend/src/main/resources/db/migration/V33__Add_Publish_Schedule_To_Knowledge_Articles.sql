-- V33__Add_Publish_Schedule_To_Knowledge_Articles.sql
-- 知識庫文章發布排程 - 新增排程發布欄位

-- 新增排程發布時間欄位
ALTER TABLE knowledge_articles ADD COLUMN scheduled_publish_at TIMESTAMP;
COMMENT ON COLUMN knowledge_articles.scheduled_publish_at IS '排程發布時間 - 設定此時間後自動發布';

-- 新增實際發布時間欄位（区分草稿和已發布）
ALTER TABLE knowledge_articles ADD COLUMN published_at TIMESTAMP;
COMMENT ON COLUMN knowledge_articles.published_at IS '實際發布時間';

-- 為排程查詢建立索引
CREATE INDEX idx_knowledge_articles_scheduled_publish ON knowledge_articles(scheduled_publish_at)
    WHERE scheduled_publish_at IS NOT NULL;

CREATE INDEX idx_knowledge_articles_published_at ON knowledge_articles(published_at DESC)
    WHERE published_at IS NOT NULL;