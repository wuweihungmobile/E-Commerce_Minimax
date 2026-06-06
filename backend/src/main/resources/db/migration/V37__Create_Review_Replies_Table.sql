-- V37__Create_Review_Replies_Table.sql
-- Sprint 16 US-001: 從 Review.sellerReply 抽離為獨立 ReviewReply Entity
-- 原因：支援多商家回覆、覆寫歷史、稽核軌跡
--
-- ON DELETE CASCADE 設計理由：
-- - 評價被刪除時，連帶回覆也應刪除（避免孤兒資料）
-- - 評價為軟刪除（is_visible = false），實際不會刪除
-- - 此 CASCADE 為保險措施，防止硬刪除時的孤兒資料
--
-- UNIQUE constraint on review_id：
-- - 每個 Review 最多一個 Reply（一對一關係）
-- - 防止重複回覆（搭配 Service 層 existsByReviewId 雙重防護）

CREATE TABLE IF NOT EXISTS review_replies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    replier_id UUID NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_review_replies_review_id UNIQUE (review_id)
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_review_replies_review_id ON review_replies(review_id);
CREATE INDEX IF NOT EXISTS idx_review_replies_replier_id ON review_replies(replier_id);
CREATE INDEX IF NOT EXISTS idx_review_replies_created_at ON review_replies(created_at);

-- 註解
COMMENT ON TABLE review_replies IS '評價回覆表 - 商家/房東對評價的回覆（每個評價最多一個回覆）';
COMMENT ON COLUMN review_replies.replier_id IS '回覆者用戶 ID（通常是 Listing 擁有者）';
COMMENT ON COLUMN review_replies.content IS '回覆內容（最多 1000 字元）';
COMMENT ON CONSTRAINT uk_review_replies_review_id ON review_replies IS '每個評價最多一個回覆（一對一關係約束）';

