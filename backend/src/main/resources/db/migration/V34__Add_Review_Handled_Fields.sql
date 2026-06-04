-- V34__Add_Review_Handled_Fields.sql
-- 新增評價處理狀態欄位

ALTER TABLE reviews
ADD COLUMN IF NOT EXISTS is_handled BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS handled_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN IF NOT EXISTS handled_by UUID REFERENCES users(id);

-- 建立索引
CREATE INDEX IF NOT EXISTS idx_reviews_is_handled ON reviews(is_handled);

COMMENT ON COLUMN reviews.is_handled IS '評價是否已處理';
COMMENT ON COLUMN reviews.handled_at IS '處理時間';
COMMENT ON COLUMN reviews.handled_by IS '處理人 ID';